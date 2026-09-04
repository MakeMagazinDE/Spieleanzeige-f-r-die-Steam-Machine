package main;

import java.io.*;

public class Main {	
	
	static Window GUI = new Window();
	@SuppressWarnings("deprecation")
	public static void main (String args[]) throws IOException{
		Window.main(args);
		String line;
		Process proc = Runtime.getRuntime().exec("ps -ef");
		try (InputStream stream = proc.getInputStream();
		     BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
			//Parsing the input stream.
			while ((line = reader.readLine()) != null) {
				System.out.println(line);
			}
		} finally {
			proc.destroy();
			try {
				proc.waitFor();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		
		Helper IP = new Helper();
		IP.ip();
		//System.out.println(System.getProperty("user.home") + "/.MakeMagazin/FrontPanel.txt");
	}

}