package com.rkwon.app;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/*
 * A class to hold metadata about a specific file.
 */
public class FileMetadata {
	String fileName;
	Path filePath;
	
	File file;
	
	public FileMetadata(String fileName, String filePath) {
		this.fileName = fileName;
		this.filePath = Paths.get(filePath);
		file = new File(filePath);
	}
	
	/*
	 * Tries to return a byte array representation of the file.
	 * If this fails, we return null.
	 */
	public byte[] convertFileToByteArray() {
		try {
			return Files.readAllBytes(filePath);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return null;
	}
	
	/*
	 * Produces a filename that we'll save the file under. 
	 */
	public String makeFileName() {
		return "";
	}
}
