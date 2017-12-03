package com.rkwon.app;

import java.io.File;
import java.util.HashMap;

public class Test {
	
	public static void main(String[] args) {
		String test = "123\n4idjaijs\ntest";
		
		HashMap<String, String> parsedData = new HashMap<String, String>();

		String[] splitData = test.split("\n");
		parsedData.put("ipAddress", splitData[0]);
		parsedData.put("port", splitData[1]);
		parsedData.put("fileName", splitData[2]);
		
		System.out.println(parsedData);
	}

}
