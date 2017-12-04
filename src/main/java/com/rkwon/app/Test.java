package com.rkwon.app;

import java.io.File;

public class Test {
	
	public static void main(String[] args) {
		System.out.println(System.getProperty("user.home") + File.separator + "collective_memory" + File.separator + "stored");
		String CM_STORAGE_DIRECTORY = System.getProperty("user.home") + File.separator + "collective_memory" + File.separator + "stored";
		File storageDirs = new File(CM_STORAGE_DIRECTORY);
		storageDirs.mkdirs();
	}

}
