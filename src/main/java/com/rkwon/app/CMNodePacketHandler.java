package com.rkwon.app;

import java.io.IOException;
import java.util.HashMap;

import nl.pvdberg.pnet.client.Client;
import nl.pvdberg.pnet.event.PacketHandler;
import nl.pvdberg.pnet.packet.Packet;
import nl.pvdberg.pnet.packet.PacketBuilder;
import nl.pvdberg.pnet.packet.PacketReader;

/*
 * A class to handle the separate kinds of packet data we expect to receive.
 */
public class CMNodePacketHandler implements PacketHandler {
	// 
	
	public void handlePacket(final Packet p, final Client c) throws IOException {
		PacketReader reader = new PacketReader(p);
		
	}
}

/*
 * Responds to a direct join request, sending a response that 
 * contains who your shepherd should be.
 */
class CMNodeDirectJoinHandler implements PacketHandler {
	
	// The host who's receiving the responses.
	public CMNode host;
	
	public CMNodeDirectJoinHandler(CMNode host) {
		this.host = host;
	}
	
	/*
	 * Client c is the sender, who is here attempting to directly join the network through me.
	 */
	public void handlePacket(final Packet p, final Client c) throws IOException {
		System.out.println("\n\nReceiving direct join request...");
		PacketReader reader = new PacketReader(p);
		
		// Data should be delimited by "-"
		String data = reader.readString();
		HashMap<String, String> parsedData = host.parseNodeIdentifierData(data);
		
		System.out.println("Parsed data is: " + parsedData);
		
		NodeMetadata newNode = new NodeMetadata(parsedData);
		NodeMetadata shepherd = host.findShepherdForNode(newNode);
		
		Packet setShepherdRequest = new PacketBuilder(Packet.PacketType.Request)
										.withID(CMNode.PACKET_SHEPHERD_SET_REQUEST_ID)
										.withString(shepherd.toString())
										.build();
		
		host.asyncSend(newNode, setShepherdRequest);
	}
}


/*
 * Responds to a request for me to set my shepherd handler.
 */
class CMNodeSetShepherdHandler implements PacketHandler {
	
	// The host who's receiving the responses.
	public CMNode host;
	
	public CMNodeSetShepherdHandler(CMNode host) {
		this.host = host;
	}
	
	/*
	 * Client c is the sender
	 */
	public void handlePacket(final Packet p, final Client c) throws IOException {
		System.out.println("\n\nReceiving set shepherd request...");
		PacketReader reader = new PacketReader(p);
		
		// Data should be delimited by "-"
		String data = reader.readString();
		HashMap<String, String> parsedData = host.parseNodeIdentifierData(data);
		
		System.out.println("Parsed data is: " + parsedData);
		
		NodeMetadata proposedShepherd = new NodeMetadata(parsedData);
		host.setShepherd(proposedShepherd);
	}
}


/*
 * Receives join responses after sending out our multicast "I'd like to join!" message.
 */
class CMNodeJoinHandler implements PacketHandler {
	
	// The host who's receiving the responses.
	public CMNode host;
	
	public CMNodeJoinHandler(CMNode host) {
		this.host = host;
	}
	
	/*
	 * Client c is the sender, and presumably a shepherd.
	 */
	public void handlePacket(final Packet p, final Client c) throws IOException {
		System.out.println("\n\nReceiving join reply packet...");
		PacketReader reader = new PacketReader(p);
		
		// Data should be delimited by "-"
		String data = reader.readString();
		HashMap<String, String> parsedData = host.parseNodeIdentifierData(data);
		
		System.out.println("Parsed data is: " + parsedData);
		
		NodeMetadata newShepherd = new NodeMetadata(parsedData);
		host.discoverNewShepherd(newShepherd);
		System.out.println("Added new shepherd.");
	}
}
