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
	
	/*
	 * Sees if this node's IP address shares the same prefix as ipAddr.
	 */
	public boolean sharesPrefix(String ipAddrPrefix) {
		String myIpPrefix = Utility.prefixIpAddress(ipAddress);
		
		return myIpPrefix.equals(ipAddrPrefix);
	}
	
	/*
	 * Compares this IP Address with a provided one.
	 * If this method returns a negative value, then otherIpAddress is lexicographically greater.
	 * If positive, this IP address is greater than otherIpAddress.
	 */
	public int compareIpAddresses(String otherIpAddress) {
		return ipAddress.compareTo(otherIpAddress);
	}
}
