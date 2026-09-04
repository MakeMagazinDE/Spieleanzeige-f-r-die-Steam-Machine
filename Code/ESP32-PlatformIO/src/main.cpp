#include <Arduino.h>
#include <WiFi.h>
#include <HTTPClient.h>
#include <PNGdec.h>

#include "DEV_Config.h"
#include "EPD_5in83g.h"


// ---------- WLAN ----------
#define WIFI_SSID     "xx"
#define WIFI_PASSWORD "xx"

#define IMAGE_URL "http://xxx:2385"


#define CHECK_INTERVAL_MS      5000UL  
// Mindestabstand zwischen zwei tatsächlichen e-Paper Voll-Refreshes.
// Waveshare empfiehlt >= 180s, um das Panel nicht zu belasten/beschädigen.
#define MIN_REFRESH_INTERVAL_MS 180000UL   


#define EPD_WIDTH_BYTES (EPD_5IN83G_WIDTH / 4)
#define EPD_BUFFER_SIZE ((uint32_t)EPD_WIDTH_BYTES * EPD_5IN83G_HEIGHT)


static uint8_t *epdBuffer = nullptr;

static uint32_t lastImageHash = 0;
static bool haveLastHash = false;
static unsigned long lastCheckMs = 0;
static unsigned long lastRefreshMs = 0;


static uint32_t fnv1aHash(const uint8_t *data, size_t len) {
  uint32_t hash = 2166136261u;
  for (size_t i = 0; i < len; i++) {
    hash ^= data[i];
    hash *= 16777619u;
  }
  return hash;
}


static void setPixel(int x, int y, uint8_t colorCode) {
  if (x < 0 || x >= EPD_5IN83G_WIDTH || y < 0 || y >= EPD_5IN83G_HEIGHT) return;
  uint32_t byteIndex = (uint32_t)y * EPD_WIDTH_BYTES + (x / 4);
  uint8_t pixelInByte = x % 4; // 0..3
  uint8_t shift = (3 - pixelInByte) * 2; // Pixel 0 -> Shift 6, Pixel 3 -> Shift 0

  uint8_t mask = ~(0x03 << shift);
  epdBuffer[byteIndex] = (epdBuffer[byteIndex] & mask) | ((colorCode & 0x03) << shift);
}


static uint8_t nearestPanelColor(uint8_t r, uint8_t g, uint8_t b) {
  struct { uint8_t r, g, b, code; } palette[] = {
    {0,   0,   0,   EPD_5IN83G_BLACK},
    {255, 255, 255, EPD_5IN83G_WHITE},
    {255, 220, 0,   EPD_5IN83G_YELLOW},
    {200, 0,   0,   EPD_5IN83G_RED},
  };

  long bestDist = LONG_MAX;
  uint8_t bestCode = EPD_5IN83G_WHITE;
  for (auto &p : palette) {
    long dr = (long)r - p.r;
    long dg = (long)g - p.g;
    long db = (long)b - p.b;
    long dist = dr * dr + dg * dg + db * db;
    if (dist < bestDist) {
      bestDist = dist;
      bestCode = p.code;
    }
  }
  return bestCode;
}

static PNG png;

static int pngDrawCallback(PNGDRAW *pDraw) {
  static uint16_t lineBuffer[EPD_5IN83G_WIDTH];
  png.getLineAsRGB565(pDraw, lineBuffer, PNG_RGB565_LITTLE_ENDIAN, 0xFFFFFFFF);

  int y = pDraw->y;
  int w = pDraw->iWidth < EPD_5IN83G_WIDTH ? pDraw->iWidth : EPD_5IN83G_WIDTH;
  for (int x = 0; x < w; x++) {
    uint16_t px = lineBuffer[x];
    // RGB565 -> RGB888
    uint8_t r = (px >> 11) & 0x1F; r = (r * 255) / 31;
    uint8_t g = (px >> 5) & 0x3F;  g = (g * 255) / 63;
    uint8_t b = px & 0x1F;         b = (b * 255) / 31;
    setPixel(x, y, nearestPanelColor(r, g, b));
  }
  return 1; // 1 = weiter dekodieren, 0 würde abbrechen
}


static void checkAndMaybeDisplayImage() {
  Serial.println("Pruefe auf neues Bild...");

  HTTPClient http;
  http.begin(IMAGE_URL);
  int httpCode = http.GET();

  if (httpCode != HTTP_CODE_OK) {
    Serial.printf("GET fehlgeschlagen, HTTP-Code: %d\n", httpCode);
    http.end();
    return;
  }

  int len = http.getSize();
  if (len <= 0) {
    Serial.println("Ungueltige Content-Length");
    http.end();
    return;
  }

  uint8_t *pngBuf = (uint8_t *)ps_malloc(len);
  if (!pngBuf) {
    pngBuf = (uint8_t *)malloc(len); // Fallback auf internen RAM
  }
  if (!pngBuf) {
    Serial.println("Kein Speicher fuer PNG-Download");
    http.end();
    return;
  }

  WiFiClient *stream = http.getStreamPtr();
  size_t received = 0;
  unsigned long lastDataMs = millis();
  const unsigned long STALL_TIMEOUT_MS = 15000; // 15s ohne neue Daten -> abbrechen

  while (http.connected() && received < (size_t)len) {
    size_t avail = stream->available();
    if (avail) {
      size_t toRead = min(avail, (size_t)(len - received));
      received += stream->readBytes(pngBuf + received, toRead);
      lastDataMs = millis();
    } else {
      if (millis() - lastDataMs > STALL_TIMEOUT_MS) {
        Serial.println("Download haengt fest (Timeout), breche ab.");
        break;
      }
      delay(1);
    }
  }
  http.end();

  if (received != (size_t)len) {
    Serial.println("Download unvollstaendig, ueberspringe.");
    free(pngBuf);
    return;
  }

  uint32_t newHash = fnv1aHash(pngBuf, received);
  bool contentChanged = !haveLastHash || (newHash != lastImageHash);

  if (!contentChanged) {
    Serial.println("Kein neues Bild (Inhalt unveraendert).");
    free(pngBuf);
    return; // Bild unverändert -> nichts zu tun
  }

  bool minIntervalOk = (millis() - lastRefreshMs) >= MIN_REFRESH_INTERVAL_MS;
  if (haveLastHash && !minIntervalOk) {

    Serial.println("Neues Bild erkannt, aber Mindest-Refresh-Abstand noch nicht erreicht.");
    free(pngBuf);
    return;
  }

  Serial.printf("Neues Bild erkannt (%u Bytes) -> aktualisiere Display.\n", (unsigned)received);

  memset(epdBuffer, (EPD_5IN83G_WHITE << 6) | (EPD_5IN83G_WHITE << 4) | (EPD_5IN83G_WHITE << 2) | EPD_5IN83G_WHITE, EPD_BUFFER_SIZE);

  int rc = png.openRAM(pngBuf, received, pngDrawCallback);
  if (rc == PNG_SUCCESS) {
    png.decode(NULL, 0);
    png.close();
  } else {
    Serial.printf("PNG-Decode fehlgeschlagen, Code: %d\n", rc);
  }

  free(pngBuf);

  EPD_5IN83G_Init();

  EPD_5IN83G_Display(epdBuffer);
  EPD_5IN83G_Sleep();

  lastImageHash = newHash;
  haveLastHash = true;
  lastRefreshMs = millis();
  Serial.println("Display aktualisiert.");
}

static void connectWiFi() {
  Serial.printf("Verbinde mit WLAN '%s' ...\n", WIFI_SSID);
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  unsigned long start = millis();
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
    if (millis() - start > 20000) {
      Serial.println("\nWLAN-Verbindung fehlgeschlagen, versuche erneut...");
      WiFi.disconnect();
      WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
      start = millis();
    }
  }
  Serial.printf("\nWLAN verbunden, IP: %s\n", WiFi.localIP().toString().c_str());
}

void setup() {
  Serial.begin(115200);
  delay(1000);

  epdBuffer = (uint8_t *)ps_malloc(EPD_BUFFER_SIZE);
  if (!epdBuffer) {
    Serial.println("PSRAM-Allokation fehlgeschlagen, versuche internen RAM...");
    epdBuffer = (uint8_t *)malloc(EPD_BUFFER_SIZE);
  }
  if (!epdBuffer) {
    Serial.println("FATAL: Kein Speicher fuer Framebuffer");
    while (true) delay(1000);
  }

  DEV_Module_Init();
  EPD_5IN83G_Init();


  #define DIAGNOSTIC_COLOR_TEST 0
  #if DIAGNOSTIC_COLOR_TEST
    Serial.println("=== DIAGNOSE: Clear(WHITE) ===");
    EPD_5IN83G_Clear(EPD_5IN83G_WHITE);
    delay(5000);

    Serial.println("=== DIAGNOSE: Clear(BLACK) ===");
    EPD_5IN83G_Clear(EPD_5IN83G_BLACK);
    delay(5000);

    Serial.println("=== DIAGNOSE: Clear(YELLOW) ===");
    EPD_5IN83G_Clear(EPD_5IN83G_YELLOW);
    delay(5000);

    Serial.println("=== DIAGNOSE: Clear(RED) ===");
    EPD_5IN83G_Clear(EPD_5IN83G_RED);
    delay(5000);

    Serial.println("=== DIAGNOSE: zurueck zu WHITE ===");
  #endif

  EPD_5IN83G_Clear(EPD_5IN83G_WHITE);

  connectWiFi();

  checkAndMaybeDisplayImage();
  lastCheckMs = millis();
}

void loop() {
  if (WiFi.status() != WL_CONNECTED) {
    connectWiFi();
  }

  if (millis() - lastCheckMs >= CHECK_INTERVAL_MS) {
    lastCheckMs = millis();
    checkAndMaybeDisplayImage();
  }
}