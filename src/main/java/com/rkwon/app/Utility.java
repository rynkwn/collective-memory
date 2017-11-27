package com.rkwon.app;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;

/*
 * A class containing utility methods.
 */
public class Utility {
	
	/*
	 * Given an IP Address like 137.165.168.16
	 * return the first two "chunks."
	 * 
	 * E.g., 137.165 in this case.
	 */
	public static String prefixIpAddress(String ipAddress) {
		// TODO: Determine if this is actually a good way to prefix IP Addresses.
		String[] chunks = ipAddress.split("\\.");
		
		return chunks[0] + "." + chunks[1];
	}
	
	// Taken from:
	// http://stackoverflow.com/questions/2939218/getting-the-external-ip-address-in-java
	public static String getIP() throws Exception {
		URL whatismyip = new URL("http://checkip.amazonaws.com");
		BufferedReader in = null;
		try {
			in = new BufferedReader(new InputStreamReader(
					whatismyip.openStream()));
			String ip = in.readLine();
			return ip;
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	/*
	 * Write String in byte representation to a byte array in place.
	 * 
	 * If there's insufficient space to store the entire string, we write as
	 * much as we can.
	 */
	public static void stringToBytes(String str, byte[] ar, int startIndex) {
		byte[] byteRep = str.getBytes();

		for (int strIndex = 0, i = startIndex; i < ar.length
				&& strIndex < byteRep.length; strIndex++, i++) {
			ar[i] = byteRep[strIndex];
		}
	}
}
