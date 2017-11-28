package com.rkwon.app;

import nl.pvdberg.pnet.client.*;
import nl.pvdberg.pnet.client.util.*;
import nl.pvdberg.pnet.event.AsyncListener;
import nl.pvdberg.pnet.event.DistributerListener;
import nl.pvdberg.pnet.event.PacketDistributer;
import nl.pvdberg.pnet.packet.*;
import nl.pvdberg.pnet.server.*;
import nl.pvdberg.pnet.server.util.*;

import java.util.*;
import java.net.*;
import java.io.*;

public class CMNode {

	public static final String CM_KEYWORD = "COLLECTIVE_MEMORY"; // 17 characters/bytes
	public static final int CM_KEYWORD_LENGTH = CM_KEYWORD.length();
	public static final String CM_MULTICAST_MEETUP_ADDRESS = "230.0.0.1";
	public static final int CM_MULTICAST_BUFFER_SIZE = 256;

	public static final int CM_MULTICAST_RECEIVE_PORT = 21345;

	public static final int CM_PERSONAL_STANDARD_PORT = 51325; // Basically a random number.
	
	// In ms, how long should we wait after joining for us to have added our local shepherd (if she exists?)
	public static final long CM_WAIT_TIME_ON_JOIN = 10 * 1000; 
	
	////////////////////
	//
	// Packet IDs
	//
	
	public static final short PACKET_JOIN_REPLY_ID = 1121;
	public static final short PACKET_PING_REQUEST_ID = 1123;

	////////////////////
	//
	// Attributes
	//

	public String ipAddress;
	public int port; // Note: this is the SERVER's port.

	// Message openings.
	public Server server;
	public AsyncClient client;

	// List of other node metadata.
	public ArrayList<NodeMetadata> shepherdNodes = new ArrayList<NodeMetadata>();

	// Whether this node is a shepherd.
	public boolean isShepherd;
	public NodeMetadata myShepherd;

	// IP Address.
	// Port
	// List of other Nodes (their IP Addresses.)
	// HashSet of files + file metadata. A "file descriptor ;D ;D ;D" object.

	// Receiving socket (TCP)
	//

	// NOTES TO SELF:
	// https://github.com/PvdBerg1998/PNet#creating-a-server
	// For TCP Connections.

	/*
	 * Perform initial setup and attribute creation.
	 */
	public CMNode() {
		System.out.println("\n\nCreating myself as a CM node...");
		try {
			ipAddress = Utility.getIP();
			System.out.println("My IP Address is: " + ipAddress);
			
			port = CM_PERSONAL_STANDARD_PORT; // TODO: We should try other ports
												// if this one cannot be bound
												// to.
			System.out.println("My chosen port number is: " + port);

			// Start server to receive future packets.
			// TODO: Make sure the server is asynchronous, otherwise we'll have
			// problems!
			server = new PlainServer();
			
			// Add packet handlers.
			PacketDistributer packetDistributer = new PacketDistributer();
			packetDistributer.addHandler(PACKET_JOIN_REPLY_ID, new CMNodeJoinHandler(this));
			
			server.setListener(new DistributerListener(packetDistributer));
			server.start(port);
			System.out.println("Started my server.");

			// Start client to send future packets.
			// Client is made asynchronous.
			client = new AsyncClient(new PlainClient());
			System.out.println("Created my client.");
			
			isShepherd = false;
			myShepherd = null;
			System.out.println("I'm not a shepherd.");
			
		} catch (Exception e) {
			System.out.println("CM Node creation failed!");
		}
	}

	////////////////////////////////////////////////////////
	//
	// JOIN PHASE
	//

	/*
	 * Join the CM network.
	 * 
	 * We do this by connecting to a specific multicast address and announcing
	 * our presence. We announce by sending a message CM_KEYWORD-IP ADDRESS-PORT
	 * delimited by '-'
	 */
	public void join() {
		System.out.println("\n\nStarting join request...");
		byte[] buf = new byte[CM_MULTICAST_BUFFER_SIZE];

		// Insert the keyword to distinguish our traffic from random people.
		Utility.stringToBytes(CM_KEYWORD + "-", buf, 0);

		// Determine offset to insert data. We add 1 to account for the "-"
		int ipDataOffset = CM_KEYWORD_LENGTH + 1;

		try {
			// Get our IP Address.
			String myIPAddress = Utility.getIP();
			Utility.stringToBytes(myIPAddress + "-", buf, ipDataOffset);

			// Again, we add 1 to account for the extra "-"
			int portDataOffset = ipDataOffset + myIPAddress.length() + 1;
			Utility.stringToBytes(port + "", buf, portDataOffset);
			System.out.println("Final payload format: " + new String(buf));

			// Send the data.
			System.out.println("Sending the data to multicast meetup address: " + CM_MULTICAST_MEETUP_ADDRESS);
			DatagramSocket socket = new DatagramSocket(4445); // Host port
																// doesn't
																// matter here.
			InetAddress group = InetAddress
					.getByName(CM_MULTICAST_MEETUP_ADDRESS);
			DatagramPacket packet = new DatagramPacket(buf, buf.length, group,
					CM_MULTICAST_RECEIVE_PORT);
			socket.send(packet);

			System.out.println("Packet sent. Closing multicast socket...");
			socket.close();
		} catch (Exception e) {
			System.out.println("We can't join! Error:");
			e.printStackTrace();
		}
		
		// TODO:
		// Wait some amount of time so we can process join responses and
		// hopefully process our local shepherd's responses.
		try {
			System.out.println("\n\nWaiting for responses...");
			Thread.sleep(CM_WAIT_TIME_ON_JOIN);
			System.out.println("\n\nDone waiting for responses...");
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	/*
	 * As a shepherd, I'll send my information to some new node trying to join the network.
	 */
	public void acceptJoinRequest(String inviteeIPAddress, int port) {
		Packet joinAcceptPacket = new PacketBuilder(Packet.PacketType.Reply)
										.withID(PACKET_JOIN_REPLY_ID)
										.withString(formatNodeIdentifierData())
										.build();
		
		// Attempt to connect to the new node.
		System.out.println("\n\nResponding to join request...");
		System.out.println("Attempting connection to " + inviteeIPAddress + ":" + port);
		client.connectAsync(inviteeIPAddress, port, new AsyncListener() {
			public void onCompletion(final boolean success) {
				System.out.println("Connection status: " + success);
			}
		});
		
		// Send that new node my identifying information.
		System.out.println("Attempting to send my shepherd data...");
		client.sendAsync(joinAcceptPacket, new AsyncListener() {
			public void onCompletion(final boolean success) {
				System.out.println("Send status: " + success);
			}
		});
	}

	/*
	 * Sits at the CM multicast address and responds to node join requests when
	 * they come in.
	 */
	public void receiveNewNodes() {
		try {
			
			System.out.println("\n\nBeginning welcoming committee for new nodes...");
			
			// TODO: Multicast probably not set up for communication across different routers. At least for IPv4
			MulticastSocket socket = new MulticastSocket(CM_MULTICAST_RECEIVE_PORT);
			socket.setInterface(InetAddress.getByName(ipAddress));
			System.out.println("Bound multicast socket to " + CM_MULTICAST_RECEIVE_PORT);
			
			InetAddress meetupAddress = InetAddress
					.getByName(CM_MULTICAST_MEETUP_ADDRESS);
			System.out.println("Going to meetup address: " + CM_MULTICAST_MEETUP_ADDRESS);
			
			socket.joinGroup(meetupAddress);
			System.out.println("Joined multicast group.");

			DatagramPacket packet;

			while (isShepherd) {
				System.out.println("Waiting for join requests.");
				
				byte[] buf = new byte[CM_MULTICAST_BUFFER_SIZE];
				
				packet = new DatagramPacket(buf, buf.length);
				socket.receive(packet);
				
				System.out.println("Join request received.");
				
				String keyword = new String(packet.getData(), 0,
						CM_KEYWORD_LENGTH);
				System.out.println("Keyword in join request is: " + keyword);

				if (keyword.equals(CM_KEYWORD)) {

					// We add 1 to get rid of "CM_KEYWORD-" and capture only
					// "IP ADDRESS-PORT"
					String payload = new String(packet.getData(),
							CM_KEYWORD_LENGTH + 1, packet.getLength());
					
					System.out.println("Parsing out payload as: " + payload);

					// joinerData[0] contains IP Address as string
					// joinerData[1] contains port number as string.
					String[] joinerData = payload.split("-");
					acceptJoinRequest(joinerData[0],
							Integer.parseInt(joinerData[1]));
				}
			}

			// We're no longer welcoming new nodes.
			// TODO: Note, may not be called, since shepherd is a life-sentence.
			socket.leaveGroup(meetupAddress);
			socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/*
	 * Become a shepherd if we pass the shepherd test.
	 */
	public void tryToBecomeShepherd() {
		isShepherd = shepherdTest();
	}
	
	
	////////////////////////////////////////////////////////
	//
	// MONITOR PHASE
	//
	
	/*
	 * This method is meant to run on a separate thread.
	 * 
	 * If we're a shepherd, we wait at the multicast address to welcome new nodes
	 * and to do intelligent replication of our sheep nodes.
	 * 
	 * If we're not a shepherd, we periodically ping the multicast address to 
	 * see if the shepherd's gone offline (in which case we try to become the shepherd),
	 * and to let the shepherd know that we exist/acknowledge the shepherd.
	 */
	public void monitor() {
		
		// We want this method to run on a separate thread.
		if(isShepherd) {
			
		} else {
			
		}
	}

	////////////////////////////////////////////////////////
	//
	// UTILITY METHODS
	//
	
	/*
	 * Determine whether or not I should become a shepherd based on 
	 * the information I have! As a side effect,
	 * sets the "myShepherd" attribute.
	 * 
	 * We want one shepherd per AS. So given our own IP address
	 * (such as: 137.165.168.16), we grab the first two "chunks."
	 * 
	 * In this case 137.165, and see if any other shepherd nodes
	 * share that prefix. If any do, we don't become a shepherd.
	 * If none do. We become a shepherd.
	 * 
	 * If I'm a shepherd, and another node in the same AS is a shepherd,
	 * then the node with the lexicographically smaller IP address resigns.
	 */
	public boolean shepherdTest() {
		System.out.println("\n\nBeginning shepherd test...");
		
		String myIpPrefix = Utility.prefixIpAddress(ipAddress);
		System.out.println("My IP prefix is: " + myIpPrefix);
		
		for(NodeMetadata nm : shepherdNodes) {
			if(nm.sharesPrefix(myIpPrefix)) {
				
				if(isShepherd && nm.compareIpAddresses(ipAddress) > 0) {
					
					// If I'm already a shepherd, and the other node has a lexicogrpahically
					// greater IP Address, then I resign.
					System.out.println("Resigning shepherd status. My IP: " + ipAddress + ", other ip: " + nm.ipAddress);
					myShepherd = nm;
					return false;
					
				} else {
					// I don't need to be a shepherd.
					System.out.println("Not becoming a shepherd. Existing shepherd IP Address: " + nm.ipAddress);
					myShepherd = nm;
					return false;
				}
			}
		}
		
		System.out.println("I'm becoming a shepherd. Great.");
		myShepherd = null;
		return true;
	}
	
	/* 
	 * Parse node identifier data and return the parsed data in a way that makes sense.
	 * This is the reverse method of formatNodeIdentifierData.
	 * 
	 * Consumed by NodeMetadata to produce a NodeMetadata object.
	 */
	public HashMap<String, String> parseNodeIdentifierData(String data) {
		HashMap<String, String> parsedData = new HashMap<String, String>();
		
		String[] splitData = data.split("-");
		parsedData.put("ipAddress", splitData[0]);
		parsedData.put("port", splitData[1]);
		
		return parsedData;
	}

	/*
	 * Produce a string which identifies this node, and how to reach this node.
	 */
	public String formatNodeIdentifierData() {
    	return ipAddress + "-" + port;
    }

	/*
	 * Adds a new shepherd node to the nodes this node knows about. Only really
	 * valuable if this node is also a shepherd node.
	 */
	public void discoverNewShepherd(NodeMetadata shepherd) {
		/*
		 * TODO: Determine if our shepherding duties conflict. I.e., if we're
		 * both in the same AS. If their IP address is lexicographically greater
		 * than mine, I should stop being a shepherd. Otherwise, I keep being a
		 * shepherd.
		 */
		shepherdNodes.add(shepherd);
	}
	
	/*
	 * Forgets about all shepherds we may know about.
	 */
	public void clearShepherdKnowledge() {
		shepherdNodes.clear();
	}

	

	// //////////////////////////////////////////////////////
	//
	// MAIN METHOD
	//

	public static void main(String[] args) {
		// Create myself as a CMNode.
		CMNode me = new CMNode();
		
		// Request to join the network.
		me.join();
		
		// See if I should be a shepherd.
		me.tryToBecomeShepherd();
		
		// Then proceed as usual.
		// If shepherd, should sit around the multicast address.
		// Then do a lot of other things as well.
		
		// If not shepherd, just sit happy and occasionally get lists of available files. Probably.
		
		Monitor monitorPhase = new Monitor(me);
		monitorPhase.run();
		
		// Now, start up the normal CLI interface to request files, propose files, and such.
		System.out.println("\n\nBooting up workhorse...");
	}
}

/*
 * Monitor performs the monitoring phase of 
 */
class Monitor implements Runnable {
	
	// Every so often, 
	public static final long MINIMUM_TIME_BETWEEN_PINGS = 60 * 1000;
	
	// The node doing the monitoring.
	public CMNode node;
	
	public Monitor(CMNode node) {
		this.node = node;
	}

	/*
	 * This method is meant to run on a separate thread.
	 * 
	 * If we're a shepherd, we wait at the multicast address to welcome new nodes.
	 * 
	 * If we're not a shepherd, we periodically ping our shepherd to see if he/she's gone offline
	 * (in which case we try to become the shepherd), and to let the shepherd know that we exist/
	 * acknowledge the shepherd.
	 */
	public void run() {
		while(true) {
			if(node.isShepherd) {
				node.receiveNewNodes();
			} else {
				
				while(!node.isShepherd) {
					// Add a number of milliseconds between 1-10 minutes randomly so we
					// don't get the chance to all gang up on our shepherd at once.
					// Also to reduce number of pings in general.
					long maxNumMillisToDelay = 600 * 1000;
					Random rand = new Random();
					long randomAdditionalTime = (long)(rand.nextDouble() * maxNumMillisToDelay);
					
					try {
						Thread.sleep(MINIMUM_TIME_BETWEEN_PINGS + randomAdditionalTime);
						// TODO: Now that I'm here, I should ping my shepherd to see if they're
						// alive and to let them know that I exist!
					} catch (InterruptedException e) {
						e.printStackTrace();
						break;
					}
				}
			}
		}
	}
	
}
