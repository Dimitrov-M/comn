/**
 * 
 * @author Nicholas Swafford <s0827481 @ sms.ed.ac.uk>
 * @since 19/02/2012
 * 
 * This implementation of Sender3 allows packets to be received in whichever
 * order (in the case that an acknowledgment gets delayed whilst the next one
 * doesn't, it is irrelevant which one arrives first). The receptor listens for
 * all packets during a sending run, and checks whether the acknowledgments it
 * is looking for are received in the meantime. Once all acknowledgments time 
 * out or are received the buffer is cleared.
 *
 */

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Timer;
import java.util.TimerTask;

public class Sender3 {
	private static int PACKET_SIZE = 1024;
	private static int HEADER_SIZE = 5;
	private static int DATA_SIZE = PACKET_SIZE - HEADER_SIZE;
	private static int ACK_SIZE = 3;
	private static int TIMEOUT;
	private static int WINDOW;
	private static int PORT;
	private static int FILE_SIZE;
	private static DatagramSocket clientSocket;
	private static long start_time;
	private static long end_time;

	public static class Receptor implements Runnable {
		private ArrayList<byte[]> buffer;
		private int last_acknowledged;
		private boolean end;

		public Receptor() {
			this.buffer = new ArrayList<byte[]>();
			this.last_acknowledged = -1;
			this.end = false;
		}

		public void run() {
			byte[] ack_buffer = new byte[ACK_SIZE];
			do {
				try {
					ack_buffer = new byte[ACK_SIZE];
					DatagramPacket receivePacket = new DatagramPacket(
							ack_buffer, ack_buffer.length);
					clientSocket.receive(receivePacket);
				} catch (Exception e) {
				}

				if (Utilities.getPacketNum(ack_buffer) <= last_acknowledged) {
					continue;

				} else if (checkForPacket(ack_buffer) == false) {
					addPacket(ack_buffer);

				}

			} while (!end);
		}

		public synchronized void stop() {
			this.end = true;
		}

		/**
		 * Allows a synchronized addition to the buffer
		 * 
		 * @param packet
		 *            Packet to add to the buffer
		 */
		public synchronized void addPacket(byte[] packet) {
			buffer.add(packet);
		}

		/**
		 * Checks whether a given packet is contained within the buffer
		 * 
		 * @param packet
		 *            Packet you are looking for
		 * @return Boolean dependent on the packet's presence
		 */
		public synchronized boolean checkForPacket(byte[] packet) {
			if (buffer.contains(packet)) {
				return true;
			} else {
				return false;
			}
		}
		
		/**
		 * Verifies whether the buffer contains a particular packet given its
		 * number
		 * 
		 * @param number
		 *            The number of the packet you are searching for
		 * @return Boolean dependent on the packet's presence
		 */
		public synchronized boolean checkForPacketByNumber(int number) {
			for (byte[] x : buffer) {
				if (Utilities.getPacketNum(x) == number) {
					return true;
				}
			}
			return false;
		}

		// Clear buffer to avoid size issues
		public synchronized void clearBuffer() {
			buffer.clear();
		}
	}

	public static class IndividualPacketManager {
		private byte[] packet_data;
		private InetAddress ip;
		private Integer port;
		private Integer status;
		private long packet_timeout;

		/**
		 * The IndividualPacketManager class includes a variable that keeps
		 * track of the packet's current status. The following values represent
		 * which status the packet can find itself in:
		 * 
		 *  0	Packet has just been initialized and is ready for action
		 *  1	Packet has been acknowledged and can be discarded
		 * -1	Packet is currently waiting on acknowledgment
		 * -2	Acknowledgment for this packet has timed out
		 * -3	The packet has been killed by the user [invoked destroy()]
		 * 
		 */

		/**
		 * Constructor for IndividualPacketManager (IPM)
		 * 
		 * @param data
		 *            Packet for this particular IPM to manage
		 * @param ip
		 *            IP Address of the packet's destination
		 * @param port
		 *            Port number of the packet's destination
		 */
		public IndividualPacketManager(byte[] data, InetAddress ip, Integer port) {
			this.packet_data = data;
			this.ip = ip;
			this.port = port;
			this.status = 0;
		}

		/**
		 * Sends the packet through the given socket
		 * 
		 * @param socket
		 *            Socket to transfer the data through
		 */
		public void sendThroughSocket(DatagramSocket socket) {
			try {
				DatagramPacket packet = new DatagramPacket(packet_data,
						packet_data.length, ip, port);
				socket.send(packet);

			} catch (Exception e) {

			}
		}

		/**
		 * Set the time out for this particular packet's acknowledgment
		 * 
		 * @param timeout
		 *            Timeout in milliseconds
		 */
		public void setTimeout(int timeout) {
			status = -1;
			packet_timeout = System.currentTimeMillis() + timeout;
		}

		// Get the packet's current status
		public Integer getStatus() {
			return status;
		}

		// Set the packet's status
		public void setStatus(int status) {
			this.status = status;
		}

		// Get the packet's number
		public Integer getNumber() {
			return Utilities.getPacketNum(packet_data);
		}
		
		public long getTimeout() {
			return packet_timeout;
		}

		// Return the packet managed by this IPM
		public byte[] getData() {
			return this.packet_data;
		}

		// Destroy all data contained within this IPM
		public void destroy() {
			this.packet_data = null;
			this.ip = null;
			this.port = null;
			this.status = -3;
		}
		
		// Reset the IPM
		public void reset() {
			this.status = 0;
		}

	}

	public static void main(String argv[]) throws Exception {
		String hostname = argv[0];
		PORT = Integer.parseInt(argv[1]);
		String filepath = argv[2];
		TIMEOUT = Integer.parseInt(argv[3]);
		WINDOW = Integer.parseInt(argv[4]);
		FileInputStream inFromUser = new FileInputStream(filepath);
		clientSocket = new DatagramSocket();
		InetAddress IPAddress = InetAddress.getByName(hostname);

		Receptor receptor = new Receptor();
		Thread receiver = new Thread(receptor);
		receiver.start();

		int packet_number = 0;
		boolean eof_ack = false;
		boolean eof_reached = false;

		int retrans = 0;

		ArrayList<IndividualPacketManager> ipml = new ArrayList<IndividualPacketManager>();

		start_time = System.currentTimeMillis();
		FILE_SIZE = inFromUser.available();

		do {
			if (!eof_reached) {
				
				// Ensure there are only a WINDOW amount of packets buffered
				while (ipml.size() < WINDOW) {
					
					if (inFromUser.available() <= 0) {
						// This should only trigger if the file size is 0
						eof_reached = true;
						break;

					} else if (inFromUser.available() <= DATA_SIZE) {
						// Reached the end of the file
						byte[] data = new byte[inFromUser.available()];
						inFromUser.read(data);
						byte[] packet = preparePacket(packet_number, data, true);
						IndividualPacketManager ipm = new IndividualPacketManager(
								packet, IPAddress, PORT);
						ipml.add(ipm);
						eof_reached = true;
						packet_number++;
						break;

					} else {
						byte[] data = new byte[DATA_SIZE];
						inFromUser.read(data);
						byte[] packet = preparePacket(packet_number, data, false);
						IndividualPacketManager ipm = new IndividualPacketManager(
								packet, IPAddress, PORT);
						ipml.add(ipm);
						packet_number++;

					}
				}
			}


			ArrayList<IndividualPacketManager> to_clear = new ArrayList<IndividualPacketManager>();

			boolean resend_all = false;
			for (IndividualPacketManager ipm : ipml) {

				if (ipm.getStatus() == -1) {
					if (System.currentTimeMillis() > ipm.getTimeout()) {
						// Acknowledgment has timed out
						ipm.setStatus(-2);
						resend_all = true;

						// Since there are still some packets that might have
						// been
						// acknowledged the loop does not break at this point

					} else if (receptor.checkForPacketByNumber(ipm.getNumber())) {
						ipm.setStatus(1);
					} else {
						continue;
					}

				} else if (ipm.getStatus() == 1) {
					// Packet has been acknowledged and the IPM can be cleared
					to_clear.add(ipm);

				} else if (ipm.getStatus() == 0) {
					ipm.sendThroughSocket(clientSocket);
					ipm.setTimeout(TIMEOUT);
				}
			}

			// Clear all acknowledged packets from IPML
			for (IndividualPacketManager tc : to_clear) {
				ipml.remove(tc);
				tc.destroy();
				tc = null;
			}

			// Sort the collection by packet number
			sortIPMCollection(ipml);

			/**
			 * A packet has timed out and triggered resend_all, so all packets
			 * that did not receive an acknowledgment (status was waiting or
			 * timed out) are all sent at once.
			 * 
			 * TODO Find a way to implement this that excludes packets with
			 * status 0 that are caught within the resend all
			 */
			if (resend_all) {
				for (IndividualPacketManager ipm : ipml) {
					retrans++;
					ipm.reset();
					ipm.sendThroughSocket(clientSocket);
					ipm.setTimeout(TIMEOUT);
				}
			}

			if (eof_reached && ipml.isEmpty()) {
				eof_ack = true;
			}
		} while (!eof_ack);
		end_time = System.currentTimeMillis();

		System.out.println("Complete!");
		double transfer_time = (end_time*1.0 - start_time*1.0);
		System.out.println("Transfer time in millis: " + transfer_time);
		System.out.println("Transfer time in seconds: " + (transfer_time/1000.0));
		System.out.println("Throughput: " + ((FILE_SIZE/1024.0) / (transfer_time/1000.0) ));
		System.out.println("Retransmissions: " + retrans);
		

		receiver.interrupt();
		clientSocket.close();
	}

	/**
	 * Prepares a packet for transfer
	 * 
	 * @param packet_number
	 *            Number of the particular packet
	 * @param data
	 *            File data to transfer
	 * @param eof
	 *            Whether or not the packet is the end-of-file
	 * @return A packet in byte array form to be used in transfer
	 */
	public static byte[] preparePacket(int packet_number, byte[] data,
			boolean eof) {
		byte[] head = new byte[HEADER_SIZE];

		byte[] num = Utilities.intToBytes(packet_number);
		byte[] len = Utilities.intToBytes(data.length);

		head[0] = num[0];
		head[1] = num[1];

		if (eof == true) {
			head[2] = (byte) 1;
		} else {
			head[2] = (byte) 0;
		}

		head[3] = len[0];
		head[4] = len[1];

		return Utilities.concatArray(head, data);
	}

	/**
	 * Sorts a collection of IPMs in ascending order based on their packet
	 * number
	 * 
	 * @param unsorted
	 *            An ArrayList of unsorted IPMs
	 */
	public static void sortIPMCollection(
			ArrayList<IndividualPacketManager> unsorted) {
		Comparator<IndividualPacketManager> compare = new Comparator<IndividualPacketManager>() {
			public int compare(IndividualPacketManager o1,
					IndividualPacketManager o2) {
				return o1.getNumber().compareTo(o2.getNumber());
			}
		};
		Collections.sort(unsorted, compare);
	}

}

