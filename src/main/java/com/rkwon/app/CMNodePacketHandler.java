package com.rkwon.app;

import java.io.IOException;
import java.util.HashMap;

import nl.pvdberg.pnet.client.Client;
import nl.pvdberg.pnet.event.PacketHandler;
import nl.pvdberg.pnet.packet.Packet;
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
		PacketReader reader = new PacketReader(p);
		
		// Data should be delimited by 
		String data = reader.readString();
		HashMap<String, String> parsedData = host.parseNodeIdentifierData(data);
		
		NodeMetadata newShepherd = new NodeMetadata(parsedData);
		host.discoverNewShepherd(newShepherd);
	}
}
