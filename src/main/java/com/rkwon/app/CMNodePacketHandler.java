package com.rkwon.app;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
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
	
	public static final short PACKET_ID = CMNode.PACKET_JOIN_DIRECT_REQUEST_ID;
	
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
		
		host.send(newNode, setShepherdRequest);
	}
}


/*
 * Responds to a request for me to set my shepherd handler.
 */
class CMNodeSetShepherdHandler implements PacketHandler {
	
	public static final short PACKET_ID = CMNode.PACKET_SHEPHERD_SET_REQUEST_ID;
	
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
	
	public static final short PACKET_ID = CMNode.PACKET_JOIN_REPLY_ID;
	
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


/*
 * Responds to a file download request.
 * 
 * NOTE: The way requests go is:
 * 
 * node -> requests file from -> shepherd
 * 
 * The shepherd then either responds with a file download offer, or, if 
 * the file isn't held locally, forwards the request to a node that _does_
 * have the file, who then offers the file download to the original node.
 */
class CMNodeRequestFileHandler implements PacketHandler {
	
	public static final short PACKET_ID = CMNode.PACKET_REQUEST_FILE_ID;
	
	// The host who's receiving the responses.
	public CMNode host;
	
	public CMNodeRequestFileHandler(CMNode host) {
		this.host = host;
	}
	
	/*
	 * Client c is the sender
	 */
	public void handlePacket(final Packet p, final Client c) throws IOException {
		System.out.println("\n\nReceiving file request...");
		PacketReader reader = new PacketReader(p);
		
		// Data should be delimited by "-"
		String data = reader.readString();
		
		// From the data:
		// 1) Determine where the file is, and where to send it to (IP Address and Port).
		// We can solve 2 by making sure that the file we're given is just a filename.
		
		// 2) Determine that the file is a file we should share. E.g., in storage.
		// 3) Convert the file to bytes.
		// 4) Send the file.
		HashMap<String, String> parsedData = host.parseNodeIdentifierAndFileNameData(data);
		
		System.out.println("Parsed data is: " + parsedData);
		
		NodeMetadata requestingNode = new NodeMetadata(parsedData);
		String fileName = parsedData.get("fileName");
		
		System.out.println("Requested file is: " + fileName);
		
		// Determine if the file is among the files we should be storing.
		
		FileMetadata fm = host.getMetadataForFile(fileName);
		boolean success = false;
		
		// If we found file metadata for the specified file.
		if(fm != null) {
			
			byte[] fileRepresentation = fm.convertFileToByteArray();
			if(fileRepresentation != null) {
				// If we're able to get the byte representation:
				Packet filePacket = new PacketBuilder(Packet.PacketType.Reply)
												.withID(CMNode.PACKET_DOWNLOAD_FILE_ID)
												.withString(fm.fileName)
												.withBytes(fileRepresentation)
												.build();
				
				host.send(requestingNode, filePacket);
				success = true;
			}
		} else {
			if(host.isShepherd) {
				// We don't have the file locally stored.
				// So, as a shepherd, we choose a random node in the network
				// that we believe holds the file, and forward the request there.
				String identifier = host.getRandomNodeHoldingFile(fileName);
				NodeMetadata nodeHoldingFile = new NodeMetadata(host.parseNodeIdentifierData(identifier));
				
				host.send(nodeHoldingFile, p);
			}
		}
		
		if(!success) {
			// TODO: Probably send a failure packet? We should probably handle failures in more of these places.
		}
	}
}

/*
 * Receives a file to download.
 */
class CMNodeFileDownloadHandler implements PacketHandler {
	
	public static final short PACKET_ID = CMNode.PACKET_DOWNLOAD_FILE_ID;
	
	// The host who's receiving the responses.
	public CMNode host;
	
	public CMNodeFileDownloadHandler(CMNode host) {
		this.host = host;
	}
	
	/*
	 * Client c is the sender
	 * 
	 * 1) Check that this is a file we requested, or that a shepherd has mandated we hold.
	 * 2) If so, then download into the appropriate place!
	 */
	public void handlePacket(final Packet p, final Client c) throws IOException {
		System.out.println("\n\nReceiving file download...");
		PacketReader reader = new PacketReader(p);
		
		// Read off file name.
		String fileName = reader.readString();
		byte[] data = reader.readBytes();
		
		System.out.println("Filename: " + fileName);
		System.out.println("Checking that this is a file we were expecting...");
		ExpectedFileMetadata expectedFileData = host.validDownload(fileName);
		
		if(expectedFileData != null) {
			// If this was in fact a file we were expecting.
			System.out.println("This was a file we were expecting!");
			
			if(expectedFileData.shepherdMandated) {
				// Download into storage.
				System.out.println("Shepherd mandated download, downloading into " + CMNode.CM_STORAGE_DIRECTORY);
				Files.write(Paths.get(CMNode.CM_STORAGE_DIRECTORY + File.separator + fileName), data);
				
				// Note that we're now storing this file.
				FileMetadata fm = new FileMetadata(fileName, CMNode.CM_STORAGE_DIRECTORY + File.separator + fileName);
				host.addStoredFile(fm);
			}
			
			if(expectedFileData.personallyWanted) {
				// Download into target directory.
				System.out.println("Personally desired download, downloading into " + host.downloadLocation);
				Files.write(Paths.get(host.downloadLocation + File.separator + fileName), data);
			}
			
			// Remove ExpectedFileData object from host.
			System.out.println("Removing file from list of expected downloads.");
			host.removeExpectedFile(fileName);
		}
	}
}

/*
 * Downloads a file proposed by another node for the shepherd to consider.
 * 
 * If we see too many proposed files by a node, we reject it automatically
 * without downloading it.
 * 
 * Downloaded files go into the Shepherd's CM_BASE_DIR, where they need to be accepted
 * or rejected. Rejected/accepted files are both removed (though accepted files are moved 
 * to the shepherd's Storage directory.)
 */
class CMNodeProposalHandler implements PacketHandler {
	
	public static final short PACKET_ID = CMNode.PACKET_PROPOSE_FILE_ID;
	
	// The host who's receiving the responses.
	public CMNode host;
	
	public CMNodeProposalHandler(CMNode host) {
		this.host = host;
	}
	
	/*
	 * Client c is the sender
	 * 
	 * 1) Make sure the node hasn't already exceeded his/her proposal limit.
	 * Also make sure that we're a shepherd.
	 * 2) If he/she hasn't, increment the number of proposals made by that node
	 * and download the file.
	 */
	public void handlePacket(final Packet p, final Client c) throws IOException {
		System.out.println("\n\nReceiving file proposal...");
		PacketReader reader = new PacketReader(p);

		HashMap<String, String> parsedData = host.parseIdentifierWithAuth(reader.readString());
		byte[] data = reader.readBytes();
		
		System.out.println("Parsed data is: " + parsedData);
		
		NodeMetadata proposer = new NodeMetadata(parsedData);
		int nodeId = Integer.parseInt(parsedData.get("nodeId"));
		String fileName = parsedData.get("fileName");
		
		if(host.isShepherd &&
				host.authenticateNodeIdentity(proposer, nodeId) &&
				host.nodeCanPropose(proposer) && 
				host.checkValidProposal(fileName)) {
			System.out.println("Valid proposal. Downloading into " + CMNode.CM_PROPOSE_DIRECTORY);
			String downloadPath = CMNode.CM_PROPOSE_DIRECTORY + File.separator + fileName;
			host.addProposedFile(fileName);
			System.out.println("Full filepath: " + downloadPath);
			
			host.noteNodeHasProposed(proposer);
			Files.write(Paths.get(downloadPath), data);
		} else {
			System.out.println("Invalid proposal. Ignoring.");
		}
	}
}

/*
 * Handles a file mandate from our shepherd.
 */
class CMNodeFileMandateHandler implements PacketHandler {
	
	public static final short PACKET_ID = CMNode.PACKET_MANDATE_DOWNLOAD_ID;
	
	// The host who's receiving the responses.
	public CMNode host;
	
	public CMNodeFileMandateHandler(CMNode host) {
		this.host = host;
	}
	
	/*
	 * Client c is the sender
	 * 
	 * 1) Make sure the person sending us the mandate is _our_ shepherd.
	 * 2) If so, request the file from the specified IP address and port.
	 */
	public void handlePacket(final Packet p, final Client c) throws IOException {
		System.out.println("\n\nReceiving file mandate...");
		PacketReader reader = new PacketReader(p);

		HashMap<String, String> parsedData = host.parseFileMandateHeader(reader.readString());
		
		System.out.println("Parsed data is: " + parsedData);
		
		System.out.println("Checking for verification that this is our shepherd...");
		if(host.verifyShepherdOrigin(parsedData)) {
			System.out.println("Verified to come from shepherd. Creating request for mandated file...");
			
			NodeMetadata fileHolder = new NodeMetadata(parsedData);
			String fileName = parsedData.get("fileName");
			host.requestFile(fileHolder, fileName, true);
			
			// Ping our host to let them know we've downloaded.
			host.ping(false);
		} else {
			System.out.println("This person is not our shepherd. Ignoring mandate.");
		}
	}
}

/*
 * Handles a ping as a shepherd. If we're not a shepherd, ignores the ping.
 */
class CMNodePingHandler implements PacketHandler {
	
	public static final short PACKET_ID = CMNode.PACKET_PING_REQUEST_ID;
	
	// The host who's receiving the responses.
	public CMNode host;
	
	public CMNodePingHandler(CMNode host) {
		this.host = host;
	}
	
	/*
	 * Client c is the sender
	 * 
	 * A ping contains identifying information and the files which are
	 * stored by the sending node. We incorporate this information if we're a shepherd,
	 * and we respond to the ping with a list of files in the network.
	 */
	public void handlePacket(final Packet p, final Client c) throws IOException {
		System.out.println("\n\nReceiving ping...");
		PacketReader reader = new PacketReader(p);

		HashMap<String, Object> parsedData = host.parsePing(reader.readString());
		
		System.out.println("Parsed data is: " + parsedData);
		
		if(host.isShepherd) {
			String ipAddress = (String) parsedData.get("ipAddress");
			int port = Integer.parseInt( (String) parsedData.get("port")); //TODO: Ugly. Not sure if other ways are safe though. Look at again later.
			int nodeId = Integer.parseInt( (String) parsedData.get("nodeId"));
			ArrayList<String> fileNames = (ArrayList<String>) parsedData.get("files");
			
			NodeMetadata node = new NodeMetadata(ipAddress, port, nodeId);
			
			// Update what filesNames we have replicated.
			host.updateFlockMember(node);
			host.updateNetworkFileLocations(node, fileNames, true);
			
			Packet response = host.buildPingResponsePacket(nodeId);
			host.send(node, response);
		} else {
			System.out.println("We're not a shepherd. Ignoring ping!");
		}
	}
}

/*
 * Handles a ping response. We should get:
 * A list of all files in the network
 * A list of all living nodes in the network.
 */
class CMNodePingResponseHandler implements PacketHandler {
	
	public static final short PACKET_ID = CMNode.PACKET_PING_RESPONSE_ID;
	
	// The host who's receiving the responses.
	public CMNode host;
	
	public CMNodePingResponseHandler(CMNode host) {
		this.host = host;
	}
	
	/*
	 * Client c is the sender
	 */
	public void handlePacket(final Packet p, final Client c) throws IOException {
		System.out.println("\n\nReceiving ping response...");
		
		PacketReader reader = new PacketReader(p);
		
		PingResponse response = host.parsePingResponse(reader.readString());

		System.out.println("Verifying that this response has our node id.");
		if(response.nodeId == host.nodeId) {
			System.out.println("Verified!");
			
			ArrayList<String> files = response.fileNames;
			ArrayList<String> peers = response.peers;
			
			System.out.println("Discovered resources:");
			System.out.println("Files: " + files);
			System.out.println("Peers: " + peers);
			
			host.files = files;
			host.peers = peers;
			host.receivedPingResponse = true;
		}
	}
}

/*
 * Inform a node that the shepherd appears to be dead.
 * 
 * That node then responds with the node he/she thinks should be shepherd.
 * If he/she already has a shepherd, she checks that the shepherd
 * is alive, and returns that information.
 */
class CMNodeInformShepherdDeath implements PacketHandler {
	
	public static final short PACKET_ID = CMNode.PACKET_INFORM_SHEPHERD_DEATH_REQUEST_ID;
	
	// The host who's receiving the responses.
	public CMNode host;
	
	public CMNodeInformShepherdDeath(CMNode host) {
		this.host = host;
	}
	
	/*
	 * Client c is the sender
	 */
	public void handlePacket(final Packet p, final Client c) throws IOException {
		System.out.println("\n\n Receiving nomination...");
		
		PacketReader reader = new PacketReader(p);
		
		HashMap<String, String> parsedData = host.parseNodeIdentifierData(reader.readString());
		
		NodeMetadata sender = new NodeMetadata(parsedData);
		
		// See if our shepherd is dead.
	}
}