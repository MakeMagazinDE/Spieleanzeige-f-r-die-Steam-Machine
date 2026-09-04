package main;

import java.awt.EventQueue;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JButton;
import javax.swing.JCheckBox;

public class Window {

	private static Helper Hilfe = new Helper();
	private WebServer doremi = new WebServer();
	private JFrame frmSteampanel;

	private static String BilderOrdner = new String();
	private static boolean saved = false;
	private static String[] bilder = null;

	private volatile boolean running = false;
	private Thread workerThread;
	private static GameFinder TheFinder = new GameFinder();
	String currentGame = null;
	static int anzahl = 0;
	
	Helper IP = new Helper();

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		BilderOrdner = Hilfe.SaveHandler();
		System.out.println("HIER DER ORDNER DER GENUTZT WIRD" + BilderOrdner);
		if(BilderOrdner != null) {
			saved = true;
			bilder = Hilfe.leseBildnamenOhneEndung(Hilfe.SaveHandler());
			anzahl = bilder.length;
		}
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					Window window = new Window();
					window.frmSteampanel.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Create the application.
	 */
	public Window() {
		initialize();
	}

	/**
	 * Initialize the contents of the frame.
	 */
	private void initialize() {
		frmSteampanel = new JFrame();
		frmSteampanel.setTitle("Steam-Panel");
		frmSteampanel.setResizable(false);
		frmSteampanel.setBounds(100, 100, 442, 440);
		frmSteampanel.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frmSteampanel.setLocationRelativeTo(null);
		frmSteampanel.getContentPane().setLayout(null);

		JLabel lblNewLabel = new JLabel();
		if(saved) {
			lblNewLabel.setText(BilderOrdner);
		} else {
			lblNewLabel.setText("Empty");
		}
		lblNewLabel.setBounds(24, 39, 170, 29);
		frmSteampanel.getContentPane().add(lblNewLabel);

		JButton btnBilderordnerWhlen = new JButton("Bilderordner wählen");
		btnBilderordnerWhlen.setBounds(24, 12, 157, 27);

		btnBilderordnerWhlen.addActionListener(e -> {
		    String pfad = Hilfe.OrdnerSucher();
		    if (pfad != null) {
		        System.out.println("Gewählter Ordner: " + pfad);
		        lblNewLabel.setText(pfad); // z. B. im Label anzeigen
		        
		        Hilfe.erstelleTxtDatei(System.getProperty("user.home") + "/.MakeMagazin/", "FrontPanel.txt", pfad);
		    }
		});

		frmSteampanel.getContentPane().add(btnBilderordnerWhlen);

		JButton btnStart = new JButton("START!");
		btnStart.setBounds(24, 332, 105, 27);
		frmSteampanel.getContentPane().add(btnStart);

		JLabel ipLabel = new JLabel("IP:");
		ipLabel.setBounds(24, 223, 135, 25);
		frmSteampanel.getContentPane().add(ipLabel);

		JLabel lblAnzahlBilder = new JLabel("Anzahl Bilder - Ordner: " + anzahl);
		lblAnzahlBilder.setBounds(24, 246, 153, 25);
		frmSteampanel.getContentPane().add(lblAnzahlBilder);

		JLabel showIP;
		showIP = new JLabel("" + Hilfe.ip());
		showIP.setBounds(188, 223, 236, 25);
		frmSteampanel.getContentPane().add(showIP);

		btnStart.addActionListener(e -> {
			if (!running) {
				// --- Start ---
				running = true;
				btnStart.setText("Stop");

				btnBilderordnerWhlen.setEnabled(false);
				

				workerThread = new Thread(() -> {
					while (running) {
						String gameName = null;
						
						try {
							gameName = GameFinder.GetGame(bilder);
						} catch (IOException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						}
							if(gameName != null && currentGame != gameName) {
								currentGame = gameName;
								doremi.stop();
								doremi.hostImage(Hilfe.SaveHandler() + "/" + gameName + ".png");
							}

						try {
							Thread.sleep(5000);
						} catch (InterruptedException ex) {
							Thread.currentThread().interrupt();
							break;
						}
					}
				});
				workerThread.setDaemon(true);
				workerThread.start();

			} else {
				// --- Stop ---
				running = false;
				btnStart.setText("START!");
				doremi.stop();
				btnBilderordnerWhlen.setEnabled(true);
			}
		});
	}

}