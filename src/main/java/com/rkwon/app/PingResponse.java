package com.rkwon.app;

import java.util.ArrayList;

/*
 * A data class to hold what we expect in a Ping response.
 */
public class PingResponse {

	// TODO: There's gotta be a smarter way to do this than this. But speed is of the essence right now.
	// This might be a bit much, but...
	// Alternatively, I could do this for all my various response things. Might also make sense.
	int nodeId;
	ArrayList<String> fileNames;
	ArrayList<String> peers;
	
	public PingResponse(int nodeId, ArrayList<String> fileNames, ArrayList<String> peers) {
		this.nodeId = nodeId;
		this.fileNames = fileNames;
		this.peers = peers;
	}
}
