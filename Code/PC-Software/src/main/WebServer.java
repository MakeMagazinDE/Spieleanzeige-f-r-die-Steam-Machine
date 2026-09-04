package main;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
public class WebServer {

    private HttpServer server;
    public void hostImage(String imagePath) {
        Path path = Paths.get(imagePath);

        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Datei nicht gefunden: " + imagePath);
        }

        try {
            server = HttpServer.create(new InetSocketAddress(2385), 0);
        } catch (IOException e) {
            throw new RuntimeException("Server konnte nicht gestartet werden", e);
        }

        server.createContext("/", new ImageHandler(path));
        server.setExecutor(null);
        server.start();

        System.out.println("Webserver läuft auf http://localhost:2385/");
        System.out.println("Liefert Bild aus: " + path.toAbsolutePath());
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private static class ImageHandler implements HttpHandler {

        private final Path imagePath;

        ImageHandler(Path imagePath) {
            this.imagePath = imagePath;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            byte[] imageBytes = Files.readAllBytes(imagePath);

            String contentType = URLConnection.guessContentTypeFromName(imagePath.toString());
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, imageBytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(imageBytes);
            }
        }
    }
}
