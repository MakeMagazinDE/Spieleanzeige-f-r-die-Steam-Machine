package main;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class GameFinder {
	
	public static String GetGame(String[] Bilder) throws IOException {
		String line;
		Process proc = Runtime.getRuntime().exec("ps -ef");
		InputStream stream = proc.getInputStream();
		BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
		String result = null;
		try {
			while ((line = reader.readLine()) != null) {
				System.out.println(line);
				for (int i = 0; i < Bilder.length; i++) {
					int found = line.indexOf(Bilder[i]);
						if (found != -1) {
							System.out.println("I FOUND: " + Bilder[i]);
							result = Bilder[i];
							break;
						}
				}
				if (result != null) {
					break;
				}
			}
		} finally {
			reader.close();
			proc.destroy();
			try {
				proc.waitFor();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		return result;
	}

}