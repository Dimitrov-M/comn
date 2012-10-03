/**
 * 
 * @author Nicholas Swafford <s0827481 @ sms.ed.ac.uk>
 * @since 19/02/2012
 * 
 */

import java.io.*;
import java.net.*;

public class Sender2 {
	private static int PACKET_SIZE = 1024;
	private static int HEADER_SIZE = 5;

	public static void main(String argv[]) throws Exception {
		String hostname = argv[0];
		Integer port = Integer.parseInt(argv[1]);
		String filepath = argv[2];
		int timeout = Integer.parseInt(argv[3]);

		FileInputStream inFromUser = new FileInputStream(filepath);
		DatagramSocket clientSocket = new DatagramSocket();
		InetAddress IPAddress = InetAddress.getByName(hostname);

		/**
		 * For this question I decided to read the full file into memory for
		 * simplicity. Of course, this is highly discouraged in real world
		 * applications.
		 */
		
		byte[] fullFileByteArray = new byte[inFromUser.available()];
		inFromUser.read(fullFileByteArray, 0, inFromUser.available());


		// Initialize variables for use in file transfer
		int FILE_SIZE = fullFileByteArray.length;
		int bytesSEN = 0;
		int bytesREM = FILE_SIZE;
		int tailSIZE = PACKET_SIZE - HEADER_SIZE;
		int numOfPackets = (int) Math.ceil(FILE_SIZE / (double) tailSIZE) - 1;
		int packetNUM = 0;
		boolean endOfFile = false;

		long start_time = System.currentTimeMillis();
		long end_time = System.currentTimeMillis();
		long retrans = 0;

		// Transfer loop
		do {
			// Artificial 10ms delay due to transfer speed issues
			//Thread.sleep(10);

			if (packetNUM == numOfPackets) {
				tailSIZE = bytesREM;
				endOfFile = true;
			}

			byte[] tail = new byte[tailSIZE];
			byte[] num = Utilities.intToBytes(packetNUM);
			byte[] eof = new byte[1];
			if (endOfFile == false) {
				eof[0] = (byte) 0;
			} else {
				eof[0] = (byte) 1;
			}
			byte[] size = Utilities.intToBytes(tailSIZE);

			System.arraycopy(fullFileByteArray, bytesSEN, tail, 0, tailSIZE);

			byte[] sendData = Utilities.concatArray(
					Utilities.concatArray(Utilities.concatArray(num, eof), size), tail);
			int a = 0;
			do {
				a = sendAndWait(sendData, packetNUM, timeout, IPAddress, port,
						clientSocket);
				if(a != 1){
					retrans++;
				}
			} while (a != 1);

			if(endOfFile == true){
				end_time = System.currentTimeMillis();
			}

			bytesSEN += tailSIZE;
			bytesREM = fullFileByteArray.length - bytesSEN;
			packetNUM += 1;
			//System.out.println("SENT DATAGRAM ");

		} while (endOfFile == false);
		clientSocket.close();

		double transfer_time = (end_time*1.0 - start_time*1.0);
		System.out.println("Retransmissions: " + retrans);
		System.out.println("Transfer time in millis: " + transfer_time);
		System.out.println("Transfer time in seconds: " + (transfer_time/1000.0));
		System.out.println("Throughput: " + ((FILE_SIZE/1024.0) / (transfer_time/1000.0) ));
	}

	/**
	 * Send a packet to a host machine and await acknowledgment of that packet's
	 * reception before transmitting the next one. (Stop-and-Wait)
	 * 
	 * @param b
	 *            A byte array of data to send to the host machine.
	 * @param num
	 *            The packet number of the particular datagram.
	 * @param t
	 *            The timeout period (in ms) before retransmission.
	 * @param ip
	 *            The IP address of the host machine.
	 * @param p
	 *            The port number of the host machine.
	 * @param d
	 *            The sender's datagram socket for packet transmission and
	 *            retrieval.
	 * @return An integer describing the success or failure of the transmission.
	 *         Returns 0 if the timeout is reached, 1 if the correct packet has
	 *         been acknowledged, and -1 if an acknowledgment for a previous
	 *         packet was received.
	 * @throws IOException
	 */
	public static int sendAndWait(byte[] b, int num, int t, InetAddress ip,
			Integer p, DatagramSocket d) throws IOException {
		int ack = 0;
		byte[] receiveAck = new byte[3];
		try {
			// Send packet
			DatagramPacket packet = new DatagramPacket(b, b.length, ip, p);
			d.send(packet);

			// Wait for acknowledgment
			DatagramPacket receivePacket = new DatagramPacket(receiveAck,
					receiveAck.length);
			d.setSoTimeout(t);
			d.receive(receivePacket);
			if (Utilities.bytesToInt(receiveAck[0], receiveAck[1]) == num) {
				ack = (int) receiveAck[2];
			} else {
				ack = -1;
			}

		} catch (SocketException e) {
			// Socket timed out
			ack = 0;

		} catch (IOException e) {
			ack = 0;

		}

		return ack;
	}
}
