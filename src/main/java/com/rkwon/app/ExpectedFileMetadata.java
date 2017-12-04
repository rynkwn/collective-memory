package com.rkwon.app;

/*
 * Contains some data about a file we're expecting should we ever get
 * a download offer.
 */
public class ExpectedFileMetadata {
	public String fileName;
	
	// Has our shepherd told us to download this file?
	public boolean shepherdMandated;
	
	// Do we personally want this file?
	public boolean personallyWanted;
	
	public ExpectedFileMetadata(String fileName, boolean shepherdMandated, boolean personallyWanted) {
		this.fileName = fileName;
		this.shepherdMandated = shepherdMandated;
		this.personallyWanted = personallyWanted;
	}
}
