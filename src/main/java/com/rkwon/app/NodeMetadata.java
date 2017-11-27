package com.rkwon.app;

import java.util.HashMap;

/*
 * A small data class meant to hold IP Address-Port Number pairs.
 */
public class NodeMetadata {

	public String ipAddress;
	public int port;
	
	/*
	 * Produces a NodeMetadata object given the results of CMNode.parseNodeIdentifierData()
	 */
	public NodeMetadata(HashMap<String, String> parsedData) {
		ipAddress = parsedData.get("ipAddress");
		port = Integer.parseInt(parsedData.get("port"));
	}
	
	public NodeMetadata(String ipAddress, int port) {
		this.ipAddress = ipAddress;
		this.port = port;
	}
}
