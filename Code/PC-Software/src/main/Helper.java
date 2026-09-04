package main;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InterfaceAddress;
import javax.swing.JFileChooser;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public class Helper {
	
	public String ip() {
	    String result = null;
	    try {
	        Enumeration<NetworkInterface> networkInterfaceEnumeration = NetworkInterface.getNetworkInterfaces();
	        while (networkInterfaceEnumeration.hasMoreElements()) {
	            for (InterfaceAddress interfaceAddress : networkInterfaceEnumeration.nextElement().getInterfaceAddresses())
	                if (interfaceAddress.getAddress().isSiteLocalAddress())
	                    result = interfaceAddress.getAddress().getHostAddress();
	        }
	    } catch (SocketException e) {
	        e.printStackTrace();
	    }
	    return result;
	}
    
    public static String OrdnerSucher() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Ordner auswählen");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);

        int returnValue = chooser.showOpenDialog(null);

        if (returnValue == JFileChooser.APPROVE_OPTION) {
            return chooser.getSelectedFile().getAbsolutePath();
        } else {
            return null;
        }
    }

    public static String SaveHandler() {
    	File f = new File(System.getProperty("user.home") + "/.MakeMagazin/FrontPanel.txt");
    	if(f.exists() && !f.isDirectory()) {
    	    return RaeadSave(System.getProperty("user.home") + "/.MakeMagazin/FrontPanel.txt");
    	}
    	return null;
    }
    
    public void erstelleTxtDatei(String ordnerPfad, String dateiName, String inhalt) {
        File ordner = new File(ordnerPfad);

        if (!ordner.exists()) {
            ordner.mkdirs(); // Ordner erstellen, falls nicht vorhanden
        }

        File datei = new File(ordner, dateiName.endsWith(".txt") ? dateiName : dateiName + ".txt");

        if (datei.exists()) {
            datei.delete(); // Alte Datei löschen, falls vorhanden
        }

        try (FileWriter writer = new FileWriter(datei)) {
            writer.write(inhalt);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private static String RaeadSave(String dateiPfad) {
        try (BufferedReader reader = new BufferedReader(new FileReader(dateiPfad))) {
            return reader.readLine();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    public String[] leseBildnamenOhneEndung(String ordnerPfad) {
        File ordner = new File(ordnerPfad);

        File[] dateien = ordner.listFiles((dir, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".png");
        });

        if (dateien == null) {
            return new String[0];
        }

        String[] namen = new String[dateien.length];

        for (int i = 0; i < dateien.length; i++) {
            String dateiName = dateien[i].getName();
            int punktIndex = dateiName.lastIndexOf('.');
            namen[i] = (punktIndex > 0) ? dateiName.substring(0, punktIndex) : dateiName;
        }

        return namen;
    }
}
