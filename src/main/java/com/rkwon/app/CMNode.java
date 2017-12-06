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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

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
	
	// Maximum number of proposed files allowed for a node.
	public static final int CM_MAXIMUM_PROPOSED_FILES = 10;
	
	// A list of nodes to try to connect to if we can't find any through our JGroup.
	public static final NodeMetadata[] CM_HARDCODED_NODES = {
		new NodeMetadata("137.165.74.28", 51325), // My IP address while at Williams.
		new NodeMetadata("137.165.8.105", 51325), // The IP Address of red.cs.williams.edu
	};
	
	// The base directory for many of our special collective memory files.
	public static final String CM_BASE_DIR = System.getProperty("user.home") + File.separator + "collective_memory";
	
	// Directory location for CM files we're asked to store by our shepherd.
	public static final String CM_STORAGE_DIRECTORY = CM_BASE_DIR + File.separator + "stored";
	
	// Directory location for files that have been proposed to us as a shepherd.
	// If we're not a shepherd, this directory is unused.
	public static final String CM_PROPOSE_DIRECTORY = CM_BASE_DIR + File.separator + "proposed";
	
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
	public static final short PACKET_PROPOSE_FILE_ID = 13; // Unlucky.
	
	// Shepherd-node communication packet ids.
	public static final short PACKET_MANDATE_DOWNLOAD_ID = 623;
	
	public static final short PACKET_PING_REQUEST_ID = 1123;
	public static final short PACKET_PING_RESPONSE_ID = 3211;
	

	////////////////////
	//
	// Attributes
	//

	// JGROUPS Stuff:
	// http://www.jgroups.org/tutorial4/index.html#_writing_a_simple_application
	public JChannel channel;
	
	public String ipAddress;
	public int port; // Note: this is the SERVER's port.
	
	// This is a special ID known only to me and my shepherd. I expect this to be sent
	// back to me in packets that require verification that it was sent by my shepherd.
	// TODO: In the future, it makes more sense to have packets signed by public/private keys, but this is more lightweight.
	public int nodeId;

	// Message openings.
	public Server server;
	public AsyncClient client;

	// List of other node metadata.
	public ArrayList<NodeMetadata> shepherdNodes = new ArrayList<NodeMetadata>();
	
	// List of files this node is storing for the network. 
	// NOTE, this list does not contain files the node decides to manually GET/download.
	public ArrayList<FileMetadata> storedFiles = new ArrayList<FileMetadata>();
	
	// The set of files the user expects to download.
	public HashMap<String, ExpectedFileMetadata> requestedFiles = new HashMap<String, ExpectedFileMetadata>();

	// Whether this node is a shepherd.
	public boolean isShepherd;
	public NodeMetadata myShepherd;
	
	// Maps fileName -> Node Identifiers.
	public HashMap<String, ArrayList<String>> networkFiles = new HashMap<String, ArrayList<String>>(); 
	
	// Maps IP Address-port -> node metadata object.
	public HashMap<String, NodeMetadata> flock = new HashMap<String, NodeMetadata>();
	
	// A list of known files for this node.
	public ArrayList<String> files = new ArrayList<String>();
	
	// A list of network peers for this node. 
	public ArrayList<String> peers = new ArrayList<String>();
	
	// Tracking which nodes have proposed how many files.
	// NOTE: Key is NodeMetadata.toString().
	public HashMap<String, Integer> numProposals = new HashMap<String, Integer>();
	
	// Directory location for user file requests:
	// We default to where we think their downloads directory should be...
	public String downloadLocation = System.getProperty("user.home") + File.separator + "Downloads";
	
	// Pointer is used for things like proposing.
	public File pointer = new File(downloadLocation);
	
	// Special flags
	public boolean waitingForShepherdResponse = false;
	
	// TODO: If ping responses become any more complicated, may be subject to race conditions. Careful. Or add locks.
	public boolean waitingForPingResponse = true;
	public boolean receivedPingResponse = false;

	// NOTES TO SELF:
	// https://github.com/PvdBerg1998/PNet#creating-a-server
	// For TCP Connections.

	/*
	 * Perform initial setup and attribute creation.
	 */
	public CMNode() {
		setup();
	}
	
	/*
	 * Perform initial setup of the node.
	 */
	public void setup() {
		System.out.println("\n\nCreating myself as a CM node...");
		try {
			channel = new JChannel();
			
			ipAddress = Utility.getIP();
			nodeId = new Random().nextInt(Integer.MAX_VALUE);
			System.out.println("My IP Address is: " + ipAddress);
			
			port = CM_PERSONAL_STANDARD_PORT; // TODO: We should try other ports
												// if this one cannot be bound
												// to.
			System.out.println("My chosen port number is: " + port);

			// Start server to receive future packets.
			server = new PlainServer();
			
			// Add packet handlers.
			PacketDistributer packetDistributer = new PacketDistributer();
			packetDistributer.addHandler(PACKET_JOIN_REPLY_ID, new CMNodeJoinHandler(this));
			packetDistributer.addHandler(PACKET_JOIN_DIRECT_REQUEST_ID, new CMNodeDirectJoinHandler(this));
			
			packetDistributer.addHandler(PACKET_SHEPHERD_SET_REQUEST_ID, new CMNodeSetShepherdHandler(this));
			
			packetDistributer.addHandler(PACKET_REQUEST_FILE_ID, new CMNodeRequestFileHandler(this));
			packetDistributer.addHandler(PACKET_DOWNLOAD_FILE_ID, new CMNodeFileDownloadHandler(this));
			packetDistributer.addHandler(PACKET_PROPOSE_FILE_ID, new CMNodeProposalHandler(this));
			packetDistributer.addHandler(PACKET_MANDATE_DOWNLOAD_ID, new CMNodeFileMandateHandler(this));
			
			packetDistributer.addHandler(PACKET_PING_REQUEST_ID, new CMNodePingHandler(this));
			packetDistributer.addHandler(PACKET_PING_RESPONSE_ID, new CMNodePingResponseHandler(this));
			
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
			
			System.out.println("Setting up proposal directory in: " + CM_PROPOSE_DIRECTORY);
			File proposalDirs = new File(CM_PROPOSE_DIRECTORY);
			proposalDirs.mkdirs();
			
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
		send(nm, joinAcceptPacket);
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
		Monitor monitorPhase = new Monitor(this);
		Thread monitorThread = new Thread(monitorPhase);
		monitorThread.start();
	}
	
	////////////////////////////////////////////////////////
	//
	// NODE PROTOCOLS
	//
	
	public void propose(String fileName, String filePath) {
		System.out.println("\n\nCreating file proposal...");
		System.out.println("File name: " + fileName);
		System.out.println("File path: " + filePath);
		FileMetadata fm = new FileMetadata(fileName, filePath);
		
		if(fm.exists()) {
			System.out.println("File found. Creating byte representation");
			byte[] file = fm.convertFileToByteArray();
			
			if(file != null) {
				System.out.println("Conversion successful. Preparing to send.");
				send(myShepherd, buildFileProposalPacket(fileName, file));
			} else {
				System.out.println("Conversion failed.");
			}
		} else {
			System.out.println("File not found. Failed.");
		}
	}
	
	/*
	 * Ping our shepherd with some data about ourselves.
	 */
	public void ping(boolean async) {
		if(async) {
			asyncSend(myShepherd, buildPingPacket());
		} else {
			send(myShepherd, buildPingPacket());
		}
	}
	
	/*
	 * Creates and sends a GET request to our shepherd.
	 */
	public void get(String fileName) {
		if(isShepherd) {
			// If we're a shepherd, we should directly ask the node that holds it.
			String identifier = getRandomNodeHoldingFile(fileName);
			NodeMetadata nodeHoldingFile = new NodeMetadata(parseNodeIdentifierData(identifier));

			requestFile(nodeHoldingFile, fileName, false);
		} else {
			requestFile(myShepherd, fileName, false);
		}
	}
	
	
	////////////////////////////////////////////////////////
	//
	// COMMAND LINE INTERFACE
	//
	
	/*
	 * Reads in user commands and performs accordingly.
	 */
	public void cli() {
		Scanner scan = new Scanner(System.in);
		
		System.out.println("Welcome to Collective Memory!");
		prompt();
		String input = scan.nextLine();
		System.out.println("\n");
		
		while(! input.equalsIgnoreCase("q")) {
			if(input.equalsIgnoreCase("help")) {
				help();
			} else if(input.equalsIgnoreCase("list")) {
				list();
			} else if(input.equalsIgnoreCase("peers")) {
				peers();
			} else if(input.equalsIgnoreCase("ddir")) {
				ddir();
			} else if(input.equalsIgnoreCase("ls")) {
				ls();
			} else if(input.equalsIgnoreCase("pwd")) { 
				pwd();
			} else if(input.startsWith("cd")) {
				String argument = input.substring(input.indexOf(" ") + 1);
				cd(argument);
			}
			else if(input.startsWith("get") || input.startsWith("GET")) {
				try {
					int index = Integer.parseInt(input.split(" ")[1]);
					getCLI(index);
				} catch(Exception e) {
					System.out.println("ERROR: See `help` for help on commands");
				}
			} else if(input.startsWith("propose") || input.startsWith("PROPOSE")) {
				try {
					String argument = input.substring(input.indexOf(" ") + 1);
					propose(argument, pointer.getCanonicalPath() + File.separator + argument);
				} catch(Exception e) {
					System.out.println("ERROR: See `help` for help on commands");
					e.printStackTrace();
				}
			} else {
				System.out.println("I'm sorry, I didn't understand that.");
			}
			
			System.out.println("\n");
			prompt();
			input = scan.nextLine();
		}
		
		scan.close();
	}
	
	/*
	 * A small prompt for the user.
	 */
	public void prompt() {
		System.out.println("Type out a command, or print 'help' for help.");
	}
	
	/*
	 * Prints out a help menu!
	 */
	public void help() {
		System.out.println("********************************");
		System.out.println("HELP MENU:\n");
		
		System.out.println("`help`: Prints this menu");
		System.out.println("`ls`: Lists all files in our pointer's local directory.");
		System.out.println("`pwd`: Displays the current location of our pointer.");
		System.out.println("`cd (directory)`: Move the pointer into the new directory");
		System.out.println("`cd ..`: Move the pointer up one level.");
		
		System.out.println("");
		System.out.println("`list`: Lists all files we know about");
		System.out.println("`peers`: Lists all other nodes we know about");
		System.out.println("`ddir`: Print out the current download directory");
		System.out.println();
		System.out.println("`get (number)`: Download the corresponding file as shown in `list`");
		System.out.println("`propose (filename)`: Propose a local file for upload into the network.");
		
		if(isShepherd) {
			System.out.println();
			System.out.println("SECRET SHEPHERD COMMANDS:");
			
		}
		
		System.out.println("********************************");
	}
	
	/*
	 * List all known files in the network.
	 */
	public void list() {
		System.out.println("********************************");
		System.out.println("KNOWN FILES:\n");
		
		int count = 0;
		for(String file : files) {
			System.out.print(count + "\t"); System.out.println(file);
			count++;
		}
		System.out.println("********************************");
	}
	
	/*
	 * List all known peers in the network.
	 */
	public void peers() {
		System.out.println("********************************");
		System.out.println("KNOWN PEERS:\n");
		
		for(String peer : peers) {
			System.out.print(peer);
			if(peer.equals(formatNodeIdentifierData())) {
				System.out.print("\t" + "(ME)");
			}
			
			if(myShepherd != null && peer.equals(myShepherd.toString())) {
				System.out.print("\t" + "(MY SHEPHERD)");
			}
			
			System.out.println();
		}
		System.out.println("********************************");
	}
	
	/*
	 * Print the download directory.
	 */
	public void ddir() {
		System.out.println("********************************");
		System.out.println("DOWNLOAD DIRECTORY:\n");
		
		System.out.println(downloadLocation);
		System.out.println("********************************");
	}
	
	/*
	 * Print all files local to the pointer.
	 */
	public void ls() {
		System.out.println("********************************");
		System.out.println("LOCAL FILES AT POINTER:\n");
		
		for(String fileName : pointer.list()) {
			System.out.println(fileName);
		}
		System.out.println("********************************");
	}
	
	/*
	 * Print pointer location.
	 */
	public void pwd() {
		System.out.println("********************************");
		System.out.println("CURRENT POINTER LOCATION:\n");
		
		try {
			System.out.println(pointer.getCanonicalPath());
		} catch (IOException e) {
			System.out.println("ERROR: Getting pathname failed!");
			e.printStackTrace();
		}
		System.out.println("********************************");
	}
	
	/*
	 * Move pointer into a new directory.
	 */
	public void cd(String dir) {
		System.out.println("********************************");
		System.out.println("MOVING POINTER:\n");
		
		File newLoc;
		try {
			newLoc = new File(pointer.getCanonicalPath(), dir);
			if(newLoc.exists()) {
				pointer = newLoc;
				System.out.println("Moved pointer successfully!");
				System.out.println(pointer.getCanonicalPath());
			} else {
				System.out.println("Pointer destination doesn't exist. Move failed");
			}
		} catch (IOException e) {
			System.out.println("ERROR: Move failed!");
			e.printStackTrace();
		}
		
		System.out.println("********************************");
	}
	
	/*
	 * A CLI-friendly version to call GET.
	 */
	public void getCLI(int number) {
		System.out.println("********************************");
		System.out.println("GETTING FILE:\n");
		
		if(number < 0 || number >= files.size()) {
			System.out.println("Error! Number not correct!");
		} else {
			String fileName = files.get(number);
			System.out.println("Getting: " + fileName);
			System.out.println("Downloading into: " + downloadLocation);
			get(fileName);
		}
		System.out.println("********************************");
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
				
				if(!isShepherd) {
					// I don't need to be a shepherd.
					System.out.println("Not becoming a shepherd. Existing shepherd IP Address: " + nm.ipAddress);
					myShepherd = nm;
					ping(false);
					return false;
					
				} else {
					// I'm a shepherd, so we need to compare Ip addresses.
					if(nm.compareIpAddresses(ipAddress) > 0) {
						// If I'm already a shepherd, and the other node has a lexicogrpahically
						// greater IP Address, then I resign.
						System.out.println("Resigning shepherd status. My IP: " + ipAddress + ", other ip: " + nm.ipAddress);
						myShepherd = nm;
						ping(false);
						return false;
					}
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
			ping(false);
			
			System.out.println("New shepherd set.");
		}
		
		System.out.println("My shepherd is: " + myShepherd.toString());
	}
	
	/*
	 * Return true if nodeId is associated with our recorded metadata for nm.
	 */
	public boolean authenticateNodeIdentity(NodeMetadata nm, int nodeId) {
		if(flock.containsKey(nm.toString())) {
			NodeMetadata recordedNode = flock.get(nm.toString());
			return recordedNode.nodeId == nodeId;
		} 
		
		return false;
	}
	
	/*
	 * Returns true if nm is allowed to propose.
	 * 
	 * Really only useful if this node is a shepherd, and is checking to see if
	 * it should bother downloading the proposed file for approval.
	 */
	public boolean nodeCanPropose(NodeMetadata nm) {
		if(numProposals.containsKey(nm.toString())) {
			return numProposals.get(nm.toString()) < CM_MAXIMUM_PROPOSED_FILES;
		}
		
		return true;
	}
	
	/*
	 * Notes that the specified node nm has proposed a file.
	 */
	public void noteNodeHasProposed(NodeMetadata nm) {
		System.out.println("Noting node has proposed: " + nm.toString());
		if(!numProposals.containsKey(nm.toString())) {
			System.out.println("Node has never before proposed.");
			numProposals.put(nm.toString(), 1);
		} else {
			int prevProposalNumber = numProposals.get(nm.toString());
			System.out.println("Node has previously proposed this many times: " + prevProposalNumber);
			numProposals.put(nm.toString(), prevProposalNumber + 1);
		}
		
		System.out.println(numProposals);
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
		
		client.close();
	}
	
	/*
	 * Asynchronously send a list of messages to node nm.
	 */
	public void asyncSend(NodeMetadata nm, List<Packet> data) {
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
		int count = 1;
		for(Packet packet : data) {
			System.out.println("Attempting to send packet " + count + "...");
			client.sendAsync(packet, new AsyncListener() {
				public void onCompletion(final boolean success) {
					System.out.println("Send status: " + success);
				}
			});
		}
		
		System.out.println("Success! Closing.");
		client.close();
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
		
		client.close();
		return connectStatus && sendStatus;
	}
	
	/*
	 * Verifies that a file we're asked to download is one we were expecting.
	 */
	public ExpectedFileMetadata validDownload(String fileName) {
		if(checkFileValidity(fileName) && requestedFiles.containsKey(fileName)) {
			return requestedFiles.get(fileName);
		}
		
		return null;
	}
	
	
	/*
	 * TODO: This method should do a checksum of the file we're supposed to download, 
	 * to be sure it's actually what we expect.
	 */
	public boolean checkFileValidity(String fileName) {
		return true;
	}
	
	/*
	 * Removes an ExpectedFileMetadata from our set of expected downloads.
	 */
	public void removeExpectedFile(String fileName) {
		requestedFiles.remove(fileName);
	}
	
	/*
	 * A quick check to see if the proposed file is valid.
	 */
	public boolean checkValidProposal(String fileName) {
		return fileName.endsWith(".pdf");
	}
	
	/*
	 * Checks that the String correctly verifies that it came from our shepherd.
	 */
	public boolean verifyShepherdOrigin(HashMap<String, String> data) {
		return Integer.parseInt(data.get("nodeId")) == nodeId;
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
	 * Parse a data string into IP Address, port, nodeId, and filename. 
	 * Reverses formatIdentifierWithAuth()
	 */
	public HashMap<String, String> parseIdentifierWithAuth(String data) {
		HashMap<String, String> parsedData = new HashMap<String, String>();

		String[] splitData = data.split("\n");
		parsedData.put("ipAddress", splitData[0]);
		parsedData.put("port", splitData[1]);
		parsedData.put("nodeId", splitData[2]);
		parsedData.put("fileName", splitData[3]);
		
		return parsedData;
	}
	
	/*
	 * Parse a data string into IP Address, port, filename, and nodeId 
	 * Reverses formatFileMandateHeader()
	 */
	public HashMap<String, String> parseFileMandateHeader(String data) {
		HashMap<String, String> parsedData = new HashMap<String, String>();

		String[] splitData = data.split("\n");
		parsedData.put("ipAddress", splitData[0]);
		parsedData.put("port", splitData[1]);
		parsedData.put("fileName", splitData[2]);
		parsedData.put("nodeId", splitData[3]);
		
		return parsedData;
	}
	
	/*
	 * Parse a data string node identifying information, and also a list of file names.
	 * Reverses formatFileMandateHeader()
	 */
	public HashMap<String, Object> parsePing(String data) {
		HashMap<String, Object> parsedData = new HashMap<String, Object>();

		String[] splitData = data.split("\n");
		parsedData.put("ipAddress", splitData[0]);
		parsedData.put("port", splitData[1]);
		parsedData.put("nodeId", splitData[2]);
		
		ArrayList<String> files = new ArrayList<String>();
		
		for(int i = 4; i < splitData.length; i++) {
			files.add(splitData[i]);
		}
		
		parsedData.put("files", files);
		return parsedData;
	}
	
	/*
	 * Parse a data string node identifying information, and also a list of file names.
	 * Reverses formatFileMandateHeader()
	 */
	public PingResponse parsePingResponse(String data) {
		Gson gson = new Gson();
		PingResponse response = gson.fromJson(data, PingResponse.class);

		return response;
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
	 * Produces a string that identifies this node, and also contains a file name.
	 */
	public String formatIdentifierWithAuth(String fileName) {
		return ipAddress + "\n" + port + "\n" + nodeId + "\n" + fileName;
	}
	
	/*
	 * Formats a String as part of a file mandate from a shepherd.
	 */
	public String formatFileMandateHeader(NodeMetadata nm, String fileName, int nodeId) {
		return nm.ipAddress + "\n" + nm.port + "\n" + fileName + "\n" + nodeId;
	}
	
	/*
	 * Formats a map containing two lists:
	 * One is a list of network files as strings.
	 * The other is a list of nodes in the flock (minus the shepherd).
	 * 
	 * nodeId is the id of the node who originated the ping.
	 */
	public String formatPingResponse(int nodeId) {		
		ArrayList<String> files = new ArrayList<String>();
		files.addAll(networkFiles.keySet());
		
		// Also add in whatever files are stored by this node specifically.
		for(FileMetadata fm : storedFiles) {
			files.add(fm.fileName);
		}
		
		ArrayList<String> peers = new ArrayList<String>();
		peers.addAll(flock.keySet());
		
		PingResponse response = new PingResponse(nodeId, files, peers);
		
		GsonBuilder builder = new GsonBuilder();
		Gson gson = builder.create();
		return gson.toJson(response);
	}
	
	/*
	 * Formats data we intend to put into a ping.
	 */
	public String formatPing() {
		StringBuilder sb = new StringBuilder();
		sb.append(ipAddress + "\n");
		sb.append(port + "\n");
		sb.append(nodeId + "\n");
		
		for(FileMetadata fm : storedFiles) {
			sb.append(fm.getFileName() + "\n");
		}
		
		return sb.toString();
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
	 * If we're a shepherd, updates our flock.
	 * 
	 * If we already knew about nm, updates information if relevant. If we didn't,
	 * then adds it to our known list of flock members.
	 */
	public void updateFlockMember(NodeMetadata nm) {
		System.out.println("\n\nUpdating flock member data...");
		String identifyingData = nm.toString();
		
		System.out.println("Identifying data: " + identifyingData);
		
		long timeToLive = System.currentTimeMillis() + Monitor.MAXIMUM_ADDED_TIME_BETWEEN_PINGS + Monitor.MINIMUM_TIME_BETWEEN_PINGS;
		System.out.println("Node's time to live: " + timeToLive);
		
		if(flock.containsKey(identifyingData)) {
			flock.get(identifyingData).updateTimeConsideredDead(timeToLive);
		} else {
			nm.updateTimeConsideredDead(timeToLive);
			System.out.println("Adding new node. Time to live: " + nm.timeConsideredDead);
			flock.put(identifyingData, nm);
		}
		
		System.out.println("Done updating flock");
	}
	
	/*
	 * Updates our knowledge of where files may be found given node and fileNames.
	 * 
	 * fileNames are the list of files that we're informed live at node.
	 * 
	 * live is a boolean flag that indicates whether the node is alive or dead.
	 * If it's alive, we update our knowledge accordingly. If it's dead, we look through
	 * our knowledge and remove that node as a holder of the relevant files. 
	 */
	public void updateNetworkFileLocations(NodeMetadata node, List<String> fileNames, boolean live) {
		System.out.println("Updating network file locations...");
		String nodeIdentifier = node.toString();
		
		System.out.println("Node identifier: " + nodeIdentifier);
		System.out.println("Old network file locations:" + networkFiles);
		
		for(String fileName : fileNames) {
			if(live) {
				// Add this node as a holder of the file.
				if(networkFiles.containsKey(fileName)) {
					ArrayList<String> holdingNodes = networkFiles.get(fileName);
					
					if(! holdingNodes.contains(nodeIdentifier))
						holdingNodes.add(nodeIdentifier);
				} else {
					ArrayList<String> holdingNodes = new ArrayList<String>();
					holdingNodes.add(nodeIdentifier);
					networkFiles.put(fileName, holdingNodes);
				}
			} else {
				// This is a dead node, so we remove it from the relevant places.
				if(networkFiles.containsKey(fileName)) {
					ArrayList<String> holdingNodes = networkFiles.get(fileName);
					holdingNodes.remove(nodeIdentifier);
					
					// TODO: Do we want to do this?
					if(holdingNodes.size() == 0) {
						networkFiles.remove(fileName);
					}
				} 
			}
		}
		
		System.out.println("New network file locations:" + networkFiles);
	}
	
	/*
	 * Request a file for download from the specified node nm.
	 */
	public void requestFile(NodeMetadata nm, String fileName, boolean mandated) {
		System.out.println("\n\nPreparing to request file...");
		
		System.out.println("Seeing if this file is something we're already waiting for...");
		
		// Update requestedFiles so we accept the download packet once it comes.
		if(requestedFiles.containsKey(fileName)) {
			System.out.println("Already waiting for file.");
			ExpectedFileMetadata efm = requestedFiles.get(fileName);
			efm.shepherdMandated = mandated;
			efm.personallyWanted = !mandated;
		} else {
			System.out.println("Recording that we're expecting this file.");
			ExpectedFileMetadata efm = new ExpectedFileMetadata(fileName, mandated, !mandated);
			requestedFiles.put(fileName, efm);
		}
		
		System.out.println("Node we're asking: " + nm.toString());
		System.out.println("File we're requesting: " + fileName);
		Packet fileRequestPacket = buildFileRequestPacket(fileName);
		
		System.out.println("Sending packet...");
		send(nm, fileRequestPacket);
		System.out.println("Packet sent.");
	}
	
	////////////////////////////////////////////////////////
	//
	// STORED FILE METHODS
	//

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
	
	/*
	 * Add some file metadata for some file that we have in storage.
	 */
	public void addFileMetadataToStorage(FileMetadata fm) {
		storedFiles.add(fm);
	}
	
	/*
	 * SHEPHERD METHOD.
	 * 
	 * Given all the nodes holding the file, choose one at random and return its identifier
	 * as a string.
	 */
	public String getRandomNodeHoldingFile(String fileName) {
		ArrayList<String> fileHolders = networkFiles.get(fileName);
		int randomIndex = new Random().nextInt(fileHolders.size());
		return fileHolders.get(randomIndex);
	}
	
	////////////////////////////////////////////////////////
	//
	// PACKET BUILDERS.
	//
	
	/*
	 * Builds a packet asking for a specific file.
	 * Contains data on both this node as well as the name of the file being requested.
	 */
	public Packet buildFileRequestPacket(String fileName) {
		return new PacketBuilder(Packet.PacketType.Request)
								.withID(CMNode.PACKET_REQUEST_FILE_ID)
								.withString(formatNodeIdentifierDataAndFile(fileName))
								.build();
	}
	
	/*
	 * Builds a ping packet. This packet contains this node's identifying data.
	 */
	public Packet buildPingPacket() {
		return new PacketBuilder(Packet.PacketType.Request)
								.withID(CMNode.PACKET_PING_REQUEST_ID)
								.withString(formatPing())
								.build();
	}
	
	/*
	 * Builds a file proposal packet. Contains information identifying the node,
	 * as well as the file data.
	 */
	public Packet buildFileProposalPacket(String fileName, byte[] file) {
		return new PacketBuilder(Packet.PacketType.Request)
								.withID(CMNode.PACKET_PROPOSE_FILE_ID)
								.withString(formatIdentifierWithAuth(fileName))
								.withBytes(file)
								.build();
	}
	
	/*
	 * Builds a file mandate packet. 
	 * 
	 * This is a packet sent from a shepherd to a node in its flock. This packet contains:
	 * 
	 * Who to download the file from (IP address and port)
	 * What to download
	 * Proof that I'm your shepherd.
	 */
	public Packet buildFileMandatePacket(NodeMetadata fileHolder, String fileName, int recipientNodeId) {
		return new PacketBuilder(Packet.PacketType.Request)
								.withID(CMNode.PACKET_MANDATE_DOWNLOAD_ID)
								.withString(formatFileMandateHeader(fileHolder, fileName, recipientNodeId))
								.build();
	}
	
	/*
	 * Builds a ping response packet.
	 * 
	 * Contains: 
	 * nodeId of the initial ping-er, to identify this as an authentic message.
	 * list of file names in the network.
	 * list of peers in the network.
	 */
	public Packet buildPingResponsePacket(int nodeId) {
		return new PacketBuilder(Packet.PacketType.Reply)
								.withID(CMNode.PACKET_PING_RESPONSE_ID)
								.withString(formatPingResponse(nodeId))
								.build();
	}

	////////////////////////////////////////////////////////
	//
	// MAIN METHOD
	//

	public static void main(String[] args) {
		CMNode me = new CMNode();
		me.cli();
		/*
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
		me.monitor();
		
		
		// Now, start up the normal CLI interface to request files, propose files, and such.
		System.out.println("\n\nBooting up workhorse...");
		
		// TODO: Code below is strictly for testing.
		FileMetadata testFile = new FileMetadata("Do Androids Dream of Electric Sheep by Philip Dick.pdf", 
				"C:\\Users\\Inanity\\collective_memory\\stored\\Do Androids Dream of Electric Sheep by Philip Dick.pdf");
		
		if(testFile.exists()) {
			me.addFileMetadataToStorage(testFile);
			System.out.println(me.storedFiles);
			
			while(true) {
				try {
					Thread.sleep(2000);
					System.out.println("\n\n\nFlock: \n" + me.flock);
					System.out.println("\n\nProposers: \n" + me.numProposals);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			
		} else {
			NodeMetadata myComp = CM_HARDCODED_NODES[0];
			me.requestFile(myComp, "Do Androids Dream of Electric Sheep by Philip Dick.pdf", false);
			
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			me.propose("Do Androids Dream of Electric Sheep by Philip Dick.pdf", me.downloadLocation + File.separator + "Do Androids Dream of Electric Sheep by Philip Dick.pdf");
		}
		*/
	}
}

/*
 * Monitor performs the monitoring phase of a node. This is periodic pings 
 * to the shepherd, or waiting for new nodes if you are a shepherd. 
 */
class Monitor implements Runnable {
	
	// Every so often, ping our shepherd if we're not a shepherd.
	public static final long MINIMUM_TIME_BETWEEN_PINGS = 60 * 1000;
	public static final long MAXIMUM_ADDED_TIME_BETWEEN_PINGS = 300 * 1000;
	
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
					// Add a number of milliseconds between 0-MAXIMUM_ADDED_TIME minutes randomly so we
					// don't get the chance to all gang up on our shepherd at once.
					// Also to reduce number of pings in general.
					Random rand = new Random();
					long randomAdditionalTime = (long)(rand.nextDouble() * MAXIMUM_ADDED_TIME_BETWEEN_PINGS);
					
					try {
						Thread.sleep(MINIMUM_TIME_BETWEEN_PINGS + randomAdditionalTime);
						node.waitingForPingResponse = true;
						node.receivedPingResponse = false;
						
						// We wait 30 seconds after we send our ping packet for a response.
						// Otherwise, we assume our shepherd is dead.
						long timeToWaitForResponse = 30 * 1000;
						
						node.send(node.myShepherd, node.buildPingPacket());
						
						// Wait for response.
						Thread.sleep(timeToWaitForResponse);
						
						// TODO: Finish this.
						/*
						 * We should have receivedPingResponse set by a packet sent back to us
						 * by our shepherd. If so, we should break out of this while loop.
						 * We should then see if 
						 */

						/*
						if(!node.receivedPingResponse) {
							break;
						}
						*/
						
					} catch (InterruptedException e) {
						e.printStackTrace();
						break;
					}
				}
			}
		}
	}
	
}
