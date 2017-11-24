import java.util.*;
import java.net.*;
import java.io.*;

public class CMNode {

    public static final String CM_KEYWORD = "COLLECTIVE_MEMORY"; // 17 characters/bytes
    public static final String CM_MULTICAST_MEETUP_ADDRESS = "230.0.0.1";

    public static final int CM_MULTICAST_RECEIVE_PORT = 4768;

    // IP Address.
    // Port
    // List of other Nodes (their IP Addresses.)
    // HashSet of files + file metadata. A "file descriptor ;D ;D ;D" object.

    // Receiving socket (TCP)
    //

    /*
     *
     */
    public CMNode() {
	
    }


    ////////////////////////////////////////////////////////
    //
    // JOIN PHASE
    //

    /* Join the CM network.
     *
     * We do this by connecting to a specific multicast address
     * and announcing our presence.
     */
    public void join() {
	byte[] buf = new byte[256];

	// Insert the keyword to distinguish our traffic from random people.
	CMNode.stringToBytes(CM_KEYWORD, buf, 0);

	// Determine offset to insert data:
	int ipDataOffset = CM_KEYWORD.length();

	try {
	    // Get our IP Address.
	    String myIPAddress = CMNode.getIP();
	    CMNode.stringToBytes(myIPAddress, buf, ipDataOffset);

	    int portDataOffset = ipDataOffset + myIPAddress.length();
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

    /* Sits at the CM multicast address and responds to node join requests when they come in.
     *
     */
    private void receiveNewNodes() {
	try {
	    MulticastSocket socket = new MulticastSocket(CM_MULTICAST_RECEIVE_PORT);
	    InetAddress meetupAddress = InetAddress.getByName(CM_MULTICAST_MEETUP_ADDRESS);
	    socket.joinGroup(meetupAddress);
		
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
