package com.rkwon.app;

import java.util.HashMap;

/*
 * A small data class meant to hold IP Address-Port Number pairs.
 */
public class NodeMetadata {

	public String ipAddress;
	public int port;
	
	// nodeId is a poor man's private/public key. We exchange it between the shepherd and the
	// node to allow some level of mutual authentication. We have a TODO to improve this eventually.
	public int nodeId;
	
	// The time in millis since epoch when we consider this node to be dead.
	public long timeConsideredDead;
	
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
	
	public NodeMetadata(String ipAddress, int port, int nodeId) {
		this.ipAddress = ipAddress;
		this.port = port;
		this.nodeId = nodeId;
	}
	
	public NodeMetadata(String ipAddress, int port, int nodeId, long timeConsideredDead) {
		this.ipAddress = ipAddress;
		this.port = port;
		this.nodeId = nodeId;
		this.timeConsideredDead = timeConsideredDead;
	}
	
	/*
	 * Updates the time this node is considered dead.
	 */
	public void updateTimeConsideredDead(long deathTime) {
		timeConsideredDead = deathTime;
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
	
	/*
	 * Compares this NodeMetadata with another one, specifically
	 * by comparing IP Addresses.
	 */
	public int compare(NodeMetadata nm) {
		return compareIpAddresses(nm.ipAddress);
	}
	
	/*
	 * Returns ipAddress-port-nodeId
	 * 
	 * 
	 */
	public String toStringWithAuth() {
		return ipAddress + "-" + port + "-" + nodeId;
	}
	
	/*
	 * Should be the same as CMNode's formatNodeIdentifierData method.
	 */
	public String toString() {
		return ipAddress + "-" + port;
	}
}
