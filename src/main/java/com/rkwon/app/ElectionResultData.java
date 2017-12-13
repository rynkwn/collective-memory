package com.rkwon.app;

/*
 * A data class for exchanging information during/after an election on results.
 */
public class ElectionResultData {
	
	public NodeMetadata electedShepherd;
	public NodeMetadata sender;
	public boolean completed;
	public boolean isQuery; 
	public boolean isResponse;

	public ElectionResultData(NodeMetadata electedShepherd,
							  NodeMetadata sender,
							  boolean completed,
							  boolean isQuery, 
							  boolean isResponse) {
		this.electedShepherd = electedShepherd;
		this.sender = sender;
		this.completed = completed;
		this.isQuery = isQuery;
		this.isResponse = isResponse;
	}
}
