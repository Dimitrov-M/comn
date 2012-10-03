/**
 * 
 * @author Nicholas Swafford <s0827481 @ sms.ed.ac.uk>
 * @since 19/02/2012
 * 
 * I am aware that the implementation of this receiver could have easily been
 * done without an additional thread. I did not put the PacketReceptor class
 * in its own file because Receiver4 is the only program that uses this particular
 * strain of receptor.
 *
 */

import java.io.*;
import java.net.*;
import java.util.ArrayList;

public class Receiver4 {

	private static int WINDOW;
	private static int PORT;
	private static String FILENAME;
	private static DatagramSocket serverSocket;

	public static class PacketReceptor implements Runnable {
		private ArrayList<byte[]> buffer;
		private ArrayList<byte[]> write_buffer;
		private int start_window;
		private int end_window;
		private boolean cont = true;

		public PacketReceptor(ArrayList<byte[]> buffer, int start_window,
				int end_window) throws Exception {

			this.buffer = buffer;
			this.write_buffer = new ArrayList<byte[]>();
			if (start_window > end_window) {
				throw new Exception();
			} else {
				this.start_window = start_window;
				this.end_window = end_window;
			}
		}

		public void run() {
			byte[] packet_buffer;
			do {
				packet_buffer = new byte[1024];
				try {
					DatagramPacket packet = new DatagramPacket(packet_buffer,
							packet_buffer.length);
					serverSocket.receive(packet);

					int packet_number = Utilities.getPacketNum(packet_buffer);
					InetAddress src_ip = packet.getAddress();
					int src_port = packet.getPort();

					if (Utilities.isEOF(packet_buffer)) {
						packet_buffer = Utilities.cleanUpEOF(packet_buffer);
					}

					/**
					 * Packet management is carried out within this thread. It
					 * might have been more efficient to implement it in the
					 * main program.
					 */
					if (packet_number < start_window) {
						// Send acknowledgment for previously received packets
						serverSocket.send(Utilities.createAckPacket(
								packet_number, src_ip, src_port));

					} else if (packet_number > end_window) {
						// Discard packets beyond the current window
						continue;

					} else if (packet_number == start_window) {
						// Accept the packet and add it to the write buffer
						synchronized (write_buffer) {
							write_buffer.add(packet_buffer);
						}

						adjustWindowBy(1);
						serverSocket.send(Utilities.createAckPacket(
								packet_number, src_ip, src_port));

						// Add any packets in the window buffer that are next in
						// line to the write buffer
						boolean check = false;
						do {
							byte[] write = popPacket(start_window);
							if (write != null) {
								write_buffer.add(write);
								adjustWindowBy(1);
								check = true;
							} else {
								check = false;
							}
						} while (check);

					} else if (checkForPacket(packet_number) == true) {
						// Filter out duplicate packets
						continue;

					} else {
						// Packet is within the window but not next in line
						// Accept the packet and add it to the window buffer
						addPacket(packet_buffer);
						serverSocket.send(Utilities.createAckPacket(
								packet_number, src_ip, src_port));

					}

				} catch (Exception e) {
				}

			} while (cont);
		}

		/**
		 * Synchronizes adding packets to the window buffer
		 * 
		 * @param packet
		 */
		public synchronized void addPacket(byte[] packet) {
			this.buffer.add(packet);
		}

		/**
		 * Pops the packet with the given packet number if it exists in the
		 * window buffer
		 * 
		 * @param number
		 *            The packet number of the packet you want to pop
		 * @return The popped packet if it exists, null otherwise
		 */
		public synchronized byte[] popPacket(int number) {
			if (checkForPacket(number) == true) {
				byte[] packet = getByNumber(number);
				this.buffer.remove(packet);
				return packet;
			} else {
				return null;
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
		public synchronized boolean checkForPacket(int number) {
			for (byte[] b : buffer) {
				if (Utilities.getPacketNum(b) == number) {
					return true;
				}
			}
			return false;
		}

		/**
		 * Returns the first packet that has the given packet number
		 * 
		 * @param number
		 *            The number of the packet you are searching for
		 * @return The first packet to match the given number, otherwise null
		 */
		public synchronized byte[] getByNumber(int number) {
			for (byte[] b : buffer) {
				if (Utilities.getPacketNum(b) == number) {
					return b;
				}
			}
			return null;
		}

		/**
		 * Pops the write buffer to alleviate the receptor thread and to write
		 * the data to file. The write buffer is cleared after this method is
		 * called.
		 * 
		 * @return Copy of the write buffer
		 */
		public synchronized ArrayList<byte[]> popWriteBuffer() {
			ArrayList<byte[]> output = new ArrayList<byte[]>();
			synchronized (write_buffer) {
				for (byte[] x : write_buffer) {
					output.add(x);
				}
				write_buffer.clear();
			}
			return output;
		}

		// Adjust current window by the specified value
		public synchronized void adjustWindowBy(int val) {
			this.start_window += val;
			this.end_window += val;

		}

		// Returns the buffer so that the file can be written to memory
		public synchronized ArrayList<byte[]> returnBuffer() {
			return this.buffer;
		}

		// Clear buffer to avoid size issues
		public synchronized void clearBuffer() {
			buffer.clear();
		}
		
		// Ends the do-while loop in run method
		public synchronized void stop(){
			this.cont = false;
		}

		// Get the value for the start of the window
		public synchronized int getSOW(){
			return this.start_window;
		}
	}

	public static void main(String argv[]) throws Exception {

		PORT = Integer.parseInt(argv[0]);
		FILENAME = argv[1];
		WINDOW = Integer.parseInt(argv[2]);
		serverSocket = new DatagramSocket(PORT);

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		boolean eof = false;

		ArrayList<byte[]> packet_buffer = new ArrayList<byte[]>();
		PacketReceptor receptor = new PacketReceptor(packet_buffer, 0, WINDOW);
		Thread receiver = new Thread(receptor);
		receiver.start();

		/**
		 * Checks to the write buffer in the receptor thread can be managed
		 * through the introduction of a thread sleep within this loop
		 */
		ArrayList<byte[]> write_to_baos = new ArrayList<byte[]>();
		do {

			/**
			 * Pop the write buffer from the receptor thread so that the main
			 * thread can write the data to memory
			 */

			write_to_baos = receptor.popWriteBuffer();

			for (byte[] x : write_to_baos) {
				//IF WINDOW IS LARGER THAN NUM OF EOF
				if(Utilities.isEOF(x)){
					if(receptor.getSOW() > Utilities.getPacketNum(x)){
						eof = true;
					}
				}
				baos.write(Utilities.getData(x));
			}
			
		} while (eof == false);

		receptor.stop();

		FileOutputStream writeToFile = new FileOutputStream(FILENAME);
		System.out.println("Transmission complete! Writing to file...");
		writeToFile.write(baos.toByteArray());
	}
}
