package com.rkwon.app;

/*
 * A data class to format an InformShepherdDeath packet.
 */
public class InformShepherdDeathData {
	
	public NodeMetadata sender;
	public NodeMetadata nominatedShepherd;
	public boolean response;
	
	public InformShepherdDeathData(NodeMetadata sender, NodeMetadata nominatedShep, boolean response) {
		this.sender = sender;
		this.nominatedShepherd = nominatedShep;
		this.response = response;
	}
}
