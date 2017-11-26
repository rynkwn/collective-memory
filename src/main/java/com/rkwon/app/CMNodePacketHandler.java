package com.rkwon.app;

import java.io.IOException;

import nl.pvdberg.pnet.client.Client;
import nl.pvdberg.pnet.event.PacketHandler;
import nl.pvdberg.pnet.packet.Packet;

/*
 * A class to handle the separate kinds of packet data we expect to receive.
 */
public class CMNodePacketHandler implements PacketHandler {
	
	public void handlePacket(final Packet p, final Client c) throws IOException {
		
	}
}
