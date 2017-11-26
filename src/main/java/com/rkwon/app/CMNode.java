package com.rkwon.app;

import nl.pvdberg.pnet.client.*;
import nl.pvdberg.pnet.client.util.*;
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

    public static final int CM_MULTICAST_RECEIVE_PORT = 4768;


    public static final int CM_PERSONAL_STANDARD_PORT = 51325; // Basically a random number.


    ////////////////////
    //
    // Attributes
    //

    public String IPAddress;
    public int port;
    
    // Message openings.
    public Server server;
    public Client client;


    // IP Address.
    // Port
    // List of other Nodes (their IP Addresses.)
    // HashSet of files + file metadata. A "file descriptor ;D ;D ;D" object.

    // Receiving socket (TCP)
    //


    // NOTES TO SELF:
    // https://github.com/PvdBerg1998/PNet#creating-a-server
    // For TCP Connections.


    // Whether or not this node will welcome new nodes.
    public boolean welcomeNewNodes = true;

    /* Perform initial setup and attribute creation.
     *
     */
    public CMNode() {
        try {
            IPAddress = CMNode.getIP();
            port = CM_PERSONAL_STANDARD_PORT; // TODO: We should try other ports if this one cannot be bound to.
            
            // Start server to receive future packets.
            // TODO: Make sure the server is asynchronous, otherwise we'll have problems!
            server = new PlainServer();
            server.start(port);
            
            // Start client to send future packets.
            // Client is made asynchronous.
            client = new AsyncClient(new PlainClient());
        } catch(Exception e) {
            System.out.println("CM Node creation failed!");
        }
    }


    ////////////////////////////////////////////////////////
    //
    // JOIN PHASE
    //

    /* Join the CM network.
     *
     * We do this by connecting to a specific multicast address
     * and announcing our presence. We announce by sending a message
     * CM_KEYWORD-IP ADDRESS-PORT
     * delimited by '-'
     */
    public void join() {
    	byte[] buf = new byte[CM_MULTICAST_BUFFER_SIZE];

    	// Insert the keyword to distinguish our traffic from random people.
    	CMNode.stringToBytes(CM_KEYWORD + "-", buf, 0);

    	// Determine offset to insert data. We add 1 to account for the "-"
    	int ipDataOffset = CM_KEYWORD_LENGTH + 1;

    	try {
    	    // Get our IP Address.
    	    String myIPAddress = CMNode.getIP();
    	    CMNode.stringToBytes(myIPAddress + "-", buf, ipDataOffset);

            // Again, we add 1 to account for the extra "-"
    	    int portDataOffset = ipDataOffset + myIPAddress.length() + 1;
    	    // TODO: Include the port we can be reached at.

    	    // Send the data.
    	    DatagramSocket socket = new DatagramSocket(4445); // Host port doesn't matter here.
    	    InetAddress group = InetAddress.getByName(CM_MULTICAST_MEETUP_ADDRESS);
    	    DatagramPacket packet = new DatagramPacket(buf, buf.length, group, CM_MULTICAST_RECEIVE_PORT);
    	    socket.send(packet);

    	    socket.close();
    	} catch(Exception e) {
    	    System.out.println("We can't join! Error:");
    	    e.printStackTrace();
    	}
    }

    public void acceptJoinRequest(String inviteeIPAddress, int port) {

    }

    /* Sits at the CM multicast address and responds to node join requests when they come in.
     *
     */
    private void receiveNewNodes() {
    	try {
    	    MulticastSocket socket = new MulticastSocket(CM_MULTICAST_RECEIVE_PORT);
    	    InetAddress meetupAddress = InetAddress.getByName(CM_MULTICAST_MEETUP_ADDRESS);
    	    socket.joinGroup(meetupAddress);

            byte[] buf = new byte[CM_MULTICAST_BUFFER_SIZE];

            while(welcomeNewNodes) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                String keyword = new String(packet.getData(), 0, CM_KEYWORD_LENGTH);

                if(keyword.equals(CM_KEYWORD)) {

                    // We add 1 to get rid of "CM_KEYWORD-" and capture only "IP ADDRESS-PORT"
                    String payload = new String(packet.getData(), CM_KEYWORD_LENGTH + 1, packet.getLength());

                    // joinerData[0] contains IP Address as string
                    // joinerData[1] contains port number as string.
                    String[] joinerData = payload.split("-");
                    acceptJoinRequest(joinerData[0], Integer.parseInt(joinerData[1]));
                }
            }

            // We're no longer welcoming new nodes.
            socket.leaveGroup(meetupAddress);
            socket.close();
    	} catch(IOException e) {
    	    e.printStackTrace();
    	}
    }

    ////////////////////////////////////////////////////////
    //
    // UTILITY METHODS
    //

    // Taken from:
    // http://stackoverflow.com/questions/2939218/getting-the-external-ip-address-in-java
    public static String getIP() throws Exception {
        URL whatismyip = new URL("http://checkip.amazonaws.com");
        BufferedReader in = null;
        try {
            in = new BufferedReader(new InputStreamReader( whatismyip.openStream()));
            String ip = in.readLine();
            return ip;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /* Write String in byte representation to a byte array in place.
     *
     * If there's insufficient space to store the entire string, we write
     * as much as we can.
     */
    public static void stringToBytes(String str, byte[] ar, int startIndex) {
    	byte[] byteRep = str.getBytes();

    	for(int strIndex = 0, i = startIndex; i < ar.length && strIndex < byteRep.length; strIndex++, i++) {
    	    ar[i] = byteRep[strIndex];
    	}
    }
    

    ////////////////////////////////////////////////////////
    //
    // MAIN METHOD
    //
    
    public static void main(String[] args) {

    }
}
