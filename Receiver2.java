/**
 * 
 * @author Nicholas Swafford <s0827481 @ sms.ed.ac.uk>
 * @since 19/02/2012
 * 
 */

import java.io.*;
import java.net.*;

public class Receiver2 {
	public static void main(String argv[]) throws Exception {

		int port = Integer.parseInt(argv[0]);
		String filename = argv[1];
		DatagramSocket serverSocket = new DatagramSocket(port);

		byte[] receiveData = new byte[1024];
		byte[] sendData = new byte[3];

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		boolean eof = false;

		int expected_number = 0;
		do {
			DatagramPacket receivePacket = new DatagramPacket(receiveData,
					receiveData.length);
			serverSocket.receive(receivePacket);
			InetAddress src_ip = receivePacket.getAddress();
			int src_port = receivePacket.getPort();
			int packet_number = Utilities.bytesToInt(receiveData[0],
					receiveData[1]);

			if (receiveData[2] == 1) {
				eof = true;
				receiveData = Utilities.cleanUpEOF(receiveData);
			}

			if (packet_number == expected_number) {

				// Send acknowledgment back to sender
				sendData[2] = (byte) 1;
				sendData[0] = receiveData[0];
				sendData[1] = receiveData[1];
				DatagramPacket packet = new DatagramPacket(sendData,
						sendData.length, src_ip, src_port);
				serverSocket.send(packet);

				// Write data to stream
				//System.out.println("Writing now! " + expected_number);
				baos.write(Utilities.getData(receiveData));
				expected_number++;

				// Make sure the last ACK was received
				if (eof == true){
					for(int x = 0; x < 20; x++){
						serverSocket.send(packet);
					}
				}
				
			} else if (packet_number < expected_number) {
				// Re-send old acknowledgment in case of packet loss
				sendData[2] = (byte) 1;
				sendData[0] = receiveData[0];
				sendData[1] = receiveData[1];
				DatagramPacket packet = new DatagramPacket(sendData,
						sendData.length, src_ip, src_port);
				serverSocket.send(packet);

			} else {
				// Ignore packets that ahead of the expected packet number
				continue;
			}
			
		} while (eof == false);

		FileOutputStream writeToFile = new FileOutputStream(filename);
		System.out.println("Writing to file!");
		writeToFile.write(baos.toByteArray());
	}
}
