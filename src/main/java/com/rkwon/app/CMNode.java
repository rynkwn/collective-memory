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

import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.ReceiverAdapter;

public class CMNode {

	public static final String CM_KEYWORD = "COLLECTIVE_MEMORY"; // 17 characters/bytes
	public static final int CM_KEYWORD_LENGTH = CM_KEYWORD.length();
	public static final String CM_MULTICAST_MEETUP_ADDRESS = "230.0.0.1";
	public static final int CM_MULTICAST_BUFFER_SIZE = 256;
	
	public static final String CM_JGROUP_CLUSTER_NAME = "COLLECTIVE_MEMORY_JGROUP_CLUSTER";

	public static final int CM_MULTICAST_RECEIVE_PORT = 21345;

	public static final int CM_PERSONAL_STANDARD_PORT = 51325; // Basically a random number.
	
	// In ms, how long should we wait after joining for us to have added our local shepherd (if she exists?)
	public static final long CM_WAIT_TIME_ON_JOIN = 10 * 1000;
	
	// A list of nodes to try to connect to if we can't find any through our JGroup.
	public static final NodeMetadata[] CM_HARDCODED_NODES = {
		new NodeMetadata("137.165.168.16", 51325), // My IP address while at Williams.
		new NodeMetadata("137.165.8.105", 51325), // The IP Address of red.cs.williams.edu
	};
	
	// Directory location for CM files we're asked to store by our shepherd.
	public static final String CM_STORAGE_DIRECTORY = System.getProperty("user.home") + File.separator + "collective_memory" + File.separator + "stored"; 
	
	// Directory location for user file requests:
	// We default to where we think their downloads directory should be...
	public String downloadLocation = System.getProperty("user.home") + File.separator + "Downloads";
	
	////////////////////
	//
	// Packet IDs
	//
	
	// Join packet ids.
	public static final short PACKET_JOIN_DIRECT_REQUEST_ID = 42;
	public static final short PACKET_SHEPHERD_SET_REQUEST_ID = 5413;
	public static final short PACKET_JOIN_REPLY_ID = 1121;
	
	// File packet ids.
	public static final short PACKET_REQUEST_FILE_ID = 28;
	public static final short PACKET_DOWNLOAD_FILE_ID = 26;
	
	public static final short PACKET_PING_REQUEST_ID = 1123;
	

	////////////////////
	//
	// Attributes
	//

	// JGROUPS Stuff:
	// http://www.jgroups.org/tutorial4/index.html#_writing_a_simple_application
	public JChannel channel;
	
	public String ipAddress;
	public int port; // Note: this is the SERVER's port.

	// Message openings.
	public Server server;
	public AsyncClient client;

	// List of other node metadata.
	public ArrayList<NodeMetadata> shepherdNodes = new ArrayList<NodeMetadata>();
	
	// List of files this node is storing for the network. 
	// NOTE, this list does not contain files the node decides to manually GET/download.
	public ArrayList<FileMetadata> storedFiles = new ArrayList<FileMetadata>();
	public HashSet<String> requestedFiles = new HashSet<String>();

	// Whether this node is a shepherd.
	public boolean isShepherd;
	public NodeMetadata myShepherd;
	
	// Special flags
	public boolean waitingForShepherdResponse = false;

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
		setup();
	}
	
	public void setup() {
		System.out.println("\n\nCreating myself as a CM node...");
		try {
			channel = new JChannel();
			
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
			packetDistributer.addHandler(PACKET_JOIN_DIRECT_REQUEST_ID, new CMNodeDirectJoinHandler(this));
			packetDistributer.addHandler(PACKET_SHEPHERD_SET_REQUEST_ID, new CMNodeSetShepherdHandler(this));
			
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
			
			System.out.println("Setting up storage directory in: " + CM_STORAGE_DIRECTORY);
			File storageDirs = new File(CM_STORAGE_DIRECTORY);
			storageDirs.mkdirs();
			
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

		try {

			// Send the data.
			System.out.println("Sending the data to multicast meetup address: " + CM_MULTICAST_MEETUP_ADDRESS);
			
			channel.connect(CM_JGROUP_CLUSTER_NAME);
			String payload = CM_KEYWORD + "-" + ipAddress + "-" + port;
			Message msg = new Message(null, payload);
			channel.send(msg);

			System.out.println("Packet sent. Closing multicast socket...");
			//channel.close();
			
		} catch (Exception e) {
			System.out.println("We can't join! Error:");
			e.printStackTrace();
		}
		
		// Wait some amount of time so we can process join responses and
		// hopefully process our local shepherd's responses.
		try {
			System.out.println("\n\nWaiting for responses...");
			Thread.sleep(CM_WAIT_TIME_ON_JOIN);
			System.out.println("\n\nDone waiting for responses...");
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		// In this case, we weren't able to find any shepherd nodes.
		// It's possible that the multicast discovery didn't work, so let's
		// try to manually connect to some hard coded nodes.
		/*
		 * In this case, we have a hard-coded list of nodes to try to connect to.
		 * If none of them connect... We're on our own.
		 */
		if(noShepherdNodesFound()) {
			
			Packet joinDirectRequestPacket = new PacketBuilder(Packet.PacketType.Request)
											.withID(PACKET_JOIN_DIRECT_REQUEST_ID)
											.withString(formatNodeIdentifierData())
											.build();
			
			for(NodeMetadata nm : CM_HARDCODED_NODES) {
				
				// Make sure that you can't ask yourself who should be your shepherd.
				if(! formatNodeIdentifierData().equals(nm.toString())) {
					
					// If we successfully connect and send them a joinDirectRequest packet, 
					// we break.
					if(send(nm, joinDirectRequestPacket)) {
						waitingForShepherdResponse = true;
						break;
					}
				}
			}
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
		NodeMetadata nm = new NodeMetadata(inviteeIPAddress, port);		
		asyncSend(nm, joinAcceptPacket);
	}

	/*
	 * Sits at the CM multicast address and responds to node join requests when
	 * they come in.
	 */
	public void receiveNewNodes() {
			
		System.out.println("\n\nBeginning welcoming committee for new nodes...");
		System.out.println("Waiting for join requests.");
		
		channel.setReceiver(new ReceiverAdapter(){
			public void receive(Message msg) {
				System.out.println("Join request received.");
				String data = msg.getObject();
				
				// Grab the IP address as part of the message.
				String senderIpAddress = msg.getSrc().toString();
				//senderIpAddress = senderIpAddress.substring(senderIpAddress.indexOf(':'));
				
				System.out.println("Requester src: " + senderIpAddress);
				System.out.println("Requester data: " + data);
				
				String keyword = data.substring(0, CM_KEYWORD_LENGTH);
				System.out.println("Keyword in join request is: " + keyword);

				if (keyword.equals(CM_KEYWORD)) {

					// We add 1 to get rid of "CM_KEYWORD-" and capture only
					// "IP ADDRESS-PORT"
					String payload = data.substring(CM_KEYWORD_LENGTH + 1);
					
					System.out.println("Parsing out payload as: " + payload);

					// joinerData[0] contains IP Address as string
					// joinerData[1] contains port number as string.
					String[] joinerData = payload.split("-");
					acceptJoinRequest(joinerData[0],
							Integer.parseInt(joinerData[1]));
				}
			}
		});
		
		// TODO: Below is for debugging only. Remove after I get the group thing working.
		/*
		String payload = CM_KEYWORD + "-" + ipAddress + "-" + port;
		Message msg = new Message(null, payload);
		try {
			channel.send(msg);
		} catch (Exception e) {
			e.printStackTrace();
		}
		*/
		
		// We should be on a separate thread, so we busy wait.
		while(true);
			
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
		
		// If we had to directly connect to a node, and we managed to send a message.
		// If we wait forever... we should probably restart the node.
		// TODO: waitingForShepherdResponse is a pretty shaky design decision.
		if(waitingForShepherdResponse) {
			System.out.println("I'm waiting for a shepherd response, so I won't become a shepherd.");
			return false;
		}
		
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
	 * If we were waiting for a shepherd response, set our shepherd accordingly.
	 * 
	 * If we weren't, we don't do anything.
	 */
	public void setShepherd(NodeMetadata proposedShepherd) {
		System.out.println("\n\nBeginning a set shepherd process for: " + proposedShepherd.toString());
		System.out.println("If no 'new shepherd set' seen, then set failed.");
		if(waitingForShepherdResponse) {
			waitingForShepherdResponse = false;
			discoverNewShepherd(proposedShepherd); //TODO: Do I need this?
			myShepherd = proposedShepherd;
			
			System.out.println("New shepherd set.");
		}
		
		System.out.println("My shepherd is: " + myShepherd.toString());
	}
	
	/*
	 * Asynchronously send a message to node nm.
	 */
	public void asyncSend(NodeMetadata nm, Packet data) {
		String ipAddress = nm.ipAddress;
		int port = nm.port;
		
		// Connect to the node.
		System.out.println("Attempting connection to " + ipAddress + ":" + port);
		client.connectAsync(ipAddress, port, new AsyncListener() {
			public void onCompletion(final boolean success) {
				System.out.println("Connection status: " + success);
			}
		});

		// Send that node the packet.
		System.out.println("Attempting to send packet data...");
		client.sendAsync(data, new AsyncListener() {
			public void onCompletion(final boolean success) {
				System.out.println("Send status: " + success);
			}
		});
	}
	
	/*
	 * Synchronously send a message to node nm.
	 */
	public boolean send(NodeMetadata nm, Packet data) {
		String ipAddress = nm.ipAddress;
		int port = nm.port;
		
		// _TODO_: Find out if this is actually synchronous or not. Given that we have an async client.
		// Seems synchronous.

		// Connect to the node.
		System.out.println("Attempting connection to " + ipAddress + ":" + port);
		boolean connectStatus = client.connect(ipAddress, port);
		System.out.println("Connect status: " + connectStatus);

		// Send that node the packet.
		System.out.println("Attempting to send packet data...");
		boolean sendStatus = client.send(data);
		System.out.println("Send status: " + sendStatus);
		
		return connectStatus && sendStatus;
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
	 * Parse a data string into IP Address, port, and filename. 
	 * Reverses formatNodeIdentifierDataAndFile()
	 */
	public HashMap<String, String> parseNodeIdentifierAndFileNameData(String data) {
		HashMap<String, String> parsedData = new HashMap<String, String>();

		String[] splitData = data.split("\n");
		parsedData.put("ipAddress", splitData[0]);
		parsedData.put("port", splitData[1]);
		parsedData.put("fileName", splitData[2]);
		
		return parsedData;
	}

	/*
	 * Produce a string which identifies this node, and how to reach this node.
	 */
	public String formatNodeIdentifierData() {
    	return ipAddress + "-" + port;
    }
	
	/*
	 * Produces a string that identifies this node, and also contains a file name.
	 */
	public String formatNodeIdentifierDataAndFile(String fileName) {
		return ipAddress + "\n" + port + "\n" + fileName;
	}
	
	/*
	 * Given a node's metadata, we look in our list of shepherds for a shepherd
	 * that shares the same IP prefix.
	 * 
	 * If we can't find a shepherd, we return our own node metadata and act as interim
	 * shepherd.
	 */
	public NodeMetadata findShepherdForNode(NodeMetadata node) {
		String nodeIpPrefix = Utility.prefixIpAddress(node.ipAddress);
		
		for(NodeMetadata nm : shepherdNodes) {
			if(Utility.prefixIpAddress(nm.ipAddress).equals(nodeIpPrefix))
				return nm;
		}
		
		return new NodeMetadata(ipAddress, port);
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
	
	/*
	 * Returns true if we don't find any shepherd nodes.
	 */
	public boolean noShepherdNodesFound() {
		return shepherdNodes.size() == 0;
	}

	/*
	 * Given a file name, returns the associated file metadata if we have it.
	 * If we don't, it returns NULL.
	 */
	public FileMetadata getMetadataForFile(String fileName) {
		for(FileMetadata fm : storedFiles) {
			if(fm.fileName.equals(fileName)) {
				return fm;
			}
		}
		
		return null;
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
