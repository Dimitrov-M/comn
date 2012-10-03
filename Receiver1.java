/**
 * 
 * @author Nicholas Swafford <s0827481 @ sms.ed.ac.uk>
 * @since 19/02/2012
 * 
 */

import java.io.*;
import java.net.*;

public class Receiver1 {
	public static void main(String argv[]) throws Exception {
		int port = Integer.parseInt(argv[0]);
		String filename = argv[1];
		DatagramSocket serverSocket = new DatagramSocket(port);

		byte[] receiveData = new byte[1024];

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		boolean endOfFile = false;

		/* Continuously receive packets until end-of-file flag has been reached */
		do {
			DatagramPacket receivePacket = new DatagramPacket(receiveData,
					receiveData.length);
			serverSocket.receive(receivePacket);


			if (receiveData[2] == 1) {
				endOfFile = true;
				receiveData = Utilities.cleanUpEOF(receiveData);
			}

			baos.write(Utilities.getData(receiveData));

		} while (endOfFile == false);

		/* Once end-of-file flag has been reached write data to disk */
		FileOutputStream writeToFile = new FileOutputStream(filename);
		writeToFile.write(baos.toByteArray());
		serverSocket.close();
	}
}
