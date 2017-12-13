package com.rkwon.app;

/*
 * A data class for exchanging information during/after an election on results.
 */
public class ElectionResultData {
	
	public NodeMetadata electedShepherd;
	public boolean electionCompleted;
	public boolean response;

	public ElectionResultData(NodeMetadata electedShepherd, boolean electionCompleted, boolean response) {
		this.electedShepherd = electedShepherd;
		this.electionCompleted = electionCompleted;
		this.response = response;
	}
}
