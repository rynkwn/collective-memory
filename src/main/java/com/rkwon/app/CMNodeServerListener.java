package com.rkwon.app;

import java.io.IOException;

import nl.pvdberg.pnet.client.Client;
import nl.pvdberg.pnet.event.*;
import nl.pvdberg.pnet.packet.Packet;

public class CMNodeServerListener implements PNetListener {
	
	public void onConnect(final Client c) {
		
	}
    
	public void onDisconnect(final Client c) {
		
	}

    public void onReceive(final Packet p, final Client c) throws IOException {
    	
    }
}
