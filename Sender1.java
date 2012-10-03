/**
 * 
 * @author Nicholas Swafford <s0827481 @ sms.ed.ac.uk>
 * @since 19/02/2012
 * 
 */

import java.io.*;
import java.net.*;

public class Sender1 {
	private static int PACKET_SIZE = 1024;
	private static int HEADER_SIZE = 5;

	public static void main(String argv[]) throws Exception {
		String hostname = argv[0];
		Integer port = Integer.parseInt(argv[1]);
		String filepath = argv[2];

		InetAddress IP_ADDRESS = InetAddress.getByName(hostname);
		DatagramSocket clientSocket = new DatagramSocket();

		FileInputStream inFromUser = new FileInputStream(filepath);

		/* Initialize variables for use in file transfer */
		int FILE_SIZE = inFromUser.available();
		int tail_size;
		if (FILE_SIZE < PACKET_SIZE - HEADER_SIZE) {
			tail_size = FILE_SIZE;
		} else {
			tail_size = PACKET_SIZE - HEADER_SIZE;
		}
		int num_of_packets = (int) Math.ceil(FILE_SIZE / (double) tail_size);
		int packet_num = 0;
		boolean eof = false;

		/* Transfer loop */
		do {
			/* Artificial 10ms delay due to transfer speed issues */
			Thread.sleep(100);

			/* Set end-of-file (eof) flag if this is the last packet */
			if (packet_num == num_of_packets - 1) {
				tail_size = inFromUser.available();
				eof = true;
			}

			/* Prepare packet */
			byte[] tail = new byte[tail_size];
			byte[] eof_arr = new byte[1];
			if (eof == false) {
				eof_arr[0] = (byte) 0;
			} else {
				eof_arr[0] = (byte) 1;
			}
			byte[] size = Utilities.intToBytes(tail_size);

			byte[] head = Utilities.concatArray(
					Utilities.intToBytes(packet_num), Utilities.concatArray(eof_arr, size));

			inFromUser.read(tail);
			byte[] sendData = Utilities.concatArray(head, tail);

			/* Send packet */
			DatagramPacket sendPacket = new DatagramPacket(sendData,
					sendData.length, IP_ADDRESS, port);
			clientSocket.send(sendPacket);

			packet_num += 1;

		} while (eof == false);
		clientSocket.close();
	}
}
