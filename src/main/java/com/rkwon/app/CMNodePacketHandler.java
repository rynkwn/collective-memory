package com.rkwon.app;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
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
		
		host.asyncSend(newNode, setShepherdRequest);
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
		// TODO: Finish this.
		
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
				
				host.asyncSend(requestingNode, filePacket);
				success = true;
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

		HashMap<String, String> parsedData = host.parseNodeIdentifierAndFileNameData(reader.readString());
		byte[] data = reader.readBytes();
		
		System.out.println("Parsed data is: " + parsedData);
		
		NodeMetadata proposer = new NodeMetadata(parsedData);
		String fileName = parsedData.get("fileName");
		
		if(host.isShepherd && host.nodeCanPropose(proposer) && host.checkValidProposal(fileName)) {
			System.out.println("Valid proposal. Downloading into " + CMNode.CM_PROPOSE_DIRECTORY);
			
			host.noteNodeHasProposed(proposer);
			Files.write(Paths.get(CMNode.CM_PROPOSE_DIRECTORY + File.separator + fileName), data);
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
		} else {
			System.out.println("This person is not our shepherd. Ignoring mandate.");
		}
	}
}