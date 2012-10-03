/**
 * 
 * @author Nicholas Swafford <s0827481 @ sms.ed.ac.uk>
 * @since 19/02/2012
 * 
 * Same threaded implementation as Sender3.
 * 
 */

import java.io.*;
import java.net.*;
import java.util.ArrayList;

public class Sender4 {
	private static int PACKET_SIZE = 1024;
	private static int HEADER_SIZE = 5;
	private static int DATA_SIZE = PACKET_SIZE - HEADER_SIZE;
	private static int FILE_SIZE;
	private static int ACK_SIZE = 3;
	private static int TIMEOUT;
	private static int WINDOW;
	private static int PORT;
	private static DatagramSocket clientSocket;
	private static int LAST_NUM = -5;

	public static class Receptor implements Runnable {
		private ArrayList<byte[]> buffer;
		private int last_acknowledged;
		private boolean end;

		public Receptor() {
			this.buffer = new ArrayList<byte[]>();
			this.last_acknowledged = 0;
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
				int ack_num = Utilities.getPacketNum(ack_buffer);

				if (checkForPacket(ack_buffer) == false) {
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

		public synchronized boolean isEnd() {
			return this.end;
		}

		public synchronized int getLastAck(){
			return this.last_acknowledged;
		}

		public synchronized void incrementLastAck(){
			this.last_acknowledged++;
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
			packet_timeout = System.currentTimeMillis() + timeout;
		}

		// Get the packet's current status
		public Integer getStatus() {
			return status;
		}
		
		// Return the time the packet will time out
		public long getTimeout(){
			return packet_timeout;
		}

		// Set the packet's status
		public void setStatus(int status) {
			this.status = status;
		}

		// Get the packet's number
		public Integer getNumber() {
			return Utilities.getPacketNum(packet_data);
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
			this.packet_timeout = 0;
		}
		
		// Reset the IPM
		public void reset() {
			this.status = 0;
			this.packet_timeout = 0;
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

		// Initialize acknowledgment receptor
		Receptor receptor = new Receptor();
		Thread receiver = new Thread(receptor);
		receiver.start();

		FILE_SIZE = inFromUser.available();
		int packet_number = 0;
		boolean eof_ack = false;
		boolean eof_reached = false;

		ArrayList<IndividualPacketManager> ipml = new ArrayList<IndividualPacketManager>();
		for(int x = 0; x < WINDOW; x++){
			ipml.add(null);
		}
		ipml.trimToSize();
		
		long start_time = System.currentTimeMillis();
		long retrans = 0;

		do {
			
			if (!eof_reached) {
				
				// Ensure there are only a WINDOW amount of packets buffered
				while (ipml.contains(null)) {
					
					int fni = ipml.indexOf(null);
					
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
						ipml.set(fni, ipm);
						eof_reached = true;
						LAST_NUM = packet_number;
						packet_number++;
						break;

					} else {
						byte[] data = new byte[DATA_SIZE];
						inFromUser.read(data);
						byte[] packet = preparePacket(packet_number, data, false);
						IndividualPacketManager ipm = new IndividualPacketManager(
								packet, IPAddress, PORT);
						ipml.set(fni, ipm);
						packet_number++;

					}
				}
			}

			/**
			 * Deal with each packet according to its current status. Please
			 * check the IPM class for details on status codes.
			 */
			for (int x = 0; x < WINDOW; x++){
			//for (IndividualPacketManager ipm : ipml) {

				if(ipml.get(x) == null) { 
					continue; 
				}
				
				if (ipml.get(x).getStatus() == -1) {
					// Check if the receptor has received an acknowledgment
					if (receptor.checkForPacketByNumber(ipml.get(x).getNumber())) {
						// Remove packet from list
						ipml.set(x, null);
						
					} else if (System.currentTimeMillis() > ipml.get(x).getTimeout()) {
						// Re-send packet
						ipml.get(x).sendThroughSocket(clientSocket);
						ipml.get(x).setStatus(-1);
						ipml.get(x).setTimeout(TIMEOUT);
						retrans++;
					}

				} else if (ipml.get(x).getStatus() == 0) {
					// Send virgin packet
					ipml.get(x).sendThroughSocket(clientSocket);
					ipml.get(x).setStatus(-1);
					ipml.get(x).setTimeout(TIMEOUT);

				}
			}

			if(receptor.checkForPacketByNumber(receptor.getLastAck())){
				receptor.incrementLastAck();
			}

			if (eof_reached && receptor.getLastAck() == LAST_NUM){
				eof_ack = true;
			}


		} while (!eof_ack);
		
		double transfer_time = System.currentTimeMillis()*1.0 - start_time*1.0;
		
		System.out.println("Complete!");
		System.out.println("Transfer time in millis: " + transfer_time);
		System.out.println("Transfer time in seconds: " + (transfer_time/1000.0));
		System.out.println("Throughput: " + ((FILE_SIZE/1024.0) / (transfer_time/1000.0) ));
		System.out.println("Retransmissions: " + retrans);
		
		receiver.interrupt();
		receiver.join();
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

}
