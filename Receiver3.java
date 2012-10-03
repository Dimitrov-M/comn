/**
 * 
 * @author Nicholas Swafford <s0827481 @ sms.ed.ac.uk>
 * @since 19/02/2012
 * 
 */

import java.io.*;
import java.net.*;

public class Receiver3 {

	private static int PORT;
	private static String FILENAME;
	private static DatagramSocket SERVER_SOCKET;

	public static void main(String argv[]) throws Exception {

		PORT = Integer.parseInt(argv[0]);
		FILENAME = argv[1];
		SERVER_SOCKET = new DatagramSocket(PORT);

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		int expected_number = 0;
		boolean eof = false;

		do {
			byte[] packet_buffer = new byte[1024];
			try {
				DatagramPacket packet = new DatagramPacket(packet_buffer,
						packet_buffer.length);
				SERVER_SOCKET.receive(packet);

				int packet_number = Utilities.getPacketNum(packet_buffer);
				InetAddress src_ip = packet.getAddress();
				int src_port = packet.getPort();

				// Clean up end-of-file packet and set eof flag
				if (Utilities.isEOF(packet_buffer)) {
						packet_buffer = Utilities.cleanUpEOF(packet_buffer);
						eof = true;
					}

				if (packet_number == expected_number) {
					baos.write(Utilities.getData(packet_buffer));
					SERVER_SOCKET.send(Utilities.createAckPacket(packet_number,
							src_ip, src_port));
					
					// Make sure last packet is received
					if (eof == true){
						for(int x = 0; x < 20; x++){
							SERVER_SOCKET.send(Utilities.createAckPacket(packet_number,src_ip, src_port));
						}
					}

					expected_number++;

				} else if (packet_number < expected_number) {
					// Re-send acknowledgment for previously accepted packet
					SERVER_SOCKET.send(Utilities.createAckPacket(packet_number,
							src_ip, src_port));
					eof = false;

				} else {
					// Re-send acknowledgment for previous expected packet
					SERVER_SOCKET.send(Utilities.createAckPacket(expected_number - 1,
							src_ip, src_port));
					eof = false;

				}


			} catch (Exception e) {

			}

		} while (eof == false);

		FileOutputStream writeToFile = new FileOutputStream(FILENAME);
		System.out.println("WRITING TO FILE!");
		writeToFile.write(baos.toByteArray());

		byte[] packet_buffer = new byte[1024];
		do {
			DatagramPacket packet = new DatagramPacket(packet_buffer,
						packet_buffer.length);
			SERVER_SOCKET.receive(packet);

			int packet_number = Utilities.getPacketNum(packet_buffer);
			InetAddress src_ip = packet.getAddress();
			int src_port = packet.getPort();

			SERVER_SOCKET.send(Utilities.createAckPacket(packet_number,
							src_ip, src_port));
		} while (true);

	}
}

