package com.rkwon.app;

/*
 * A data class to format an InformShepherdDeath packet.
 */
public class InformShepherdDeathData {
	
	public NodeMetadata sender;
	public NodeMetadata nominatedShepherd;
	
	public InformShepherdDeathData(NodeMetadata sender, NodeMetadata nominatedShep) {
		this.sender = sender;
		this.nominatedShepherd = nominatedShep;
	}
}
