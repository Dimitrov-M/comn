/**
 * 
 * @author Nicholas Swafford <s0827481 @ sms.ed.ac.uk>
 * @since 19/02/2012
 * 
 */

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.zip.CRC32;

public class Utilities {
	/**
	 * Converts an integer into an array of two bytes
	 * 
	 * Courtesy of user MichaelTague, 15 November 2007. Available at:
	 * http://snippets.dzone.com/posts/show/93
	 * 
	 * @param i
	 *            Integer for conversion
	 * @return A byte array representation of the integer
	 */
	public static byte[] intToBytes(int i) {
		return new byte[] {
				// (byte) (value >>> 24),
				// (byte) (value >>> 16),
				(byte) (i >>> 8), (byte) i };
	}

	/**
	 * Converts two bytes into an integer
	 * 
	 * @param b1
	 *            The first byte in the array
	 * @param b0
	 *            The second byte in the array
	 * @return The integer value of both bytes
	 */
	public static int bytesToInt(byte b1, byte b0) {
		return (b1 << 8) + (b0 & 0xFF);
	}

	/**
	 * Retrieves the file data of a packet
	 * 
	 * @param input
	 *            A packet in byte array form
	 * @return The file data of the packet, assuming it had a 5 byte sized
	 *         header
	 */
	public static byte[] getData(byte[] input) {
		byte[] output = new byte[input.length - 5];
		for (int i = 5; i < input.length; i++) {
			output[i - 5] = input[i];
		}
		return output;
	}

	/**
	 * Retrieves the packet number for a given packet
	 * 
	 * @param input
	 *            The packet in byte array form
	 * @return The packet number for the input
	 */
	public static int getPacketNum(byte[] input) {
		return bytesToInt(input[0], input[1]);
	}

	/**
	 * Checks whether or not a packet is the end of file
	 * 
	 * @param input
	 *            A packet in byte array form
	 * @return
	 */
	public static boolean isEOF(byte[] input) {
		if ((int) input[2] == 1) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Concatenates two byte arrays into a new byte array
	 * 
	 * @param h
	 *            Serves as the head of the output array
	 * @param t
	 *            Serves as the tail of the output array
	 * @return A concatenated byte array consisting of both inputs
	 */
	public static byte[] concatArray(byte[] h, byte[] t) {
		byte[] output = new byte[h.length + t.length];
		System.arraycopy(h, 0, output, 0, h.length);
		System.arraycopy(t, 0, output, h.length, t.length);
		return output;
	}

	/**
	 * Adjusts the packet size to the actual size of the data transmitted
	 * 
	 * @param b
	 *            The packet in byte array form
	 * @return A new and adjusted copy of the packet
	 */
	public static byte[] cleanUpEOF(byte[] b) {
		/* Size of the data plus the size of the header */
		int size = bytesToInt(b[3], b[4]) + 5;
		byte[] output = new byte[size];
		
		for (int x = 0; x < size; x++) {
			output[x] = b[x];
			
		}
		return output;
	}

	/**
	 * Sort an ArrayList of datagram packets according to their packet number
	 * 
	 * @param unsorted
	 *            An unsorted ArrayList of packets in byte array form
	 * @return Sorted version of the input
	 */
	public static ArrayList<byte[]> sortyByPacketNumber(
			ArrayList<byte[]> unsorted) {
		ArrayList<byte[]> output = unsorted;
		Comparator<byte[]> compare = new Comparator<byte[]>() {
			public int compare(byte[] o1, byte[] o2) {
				Integer o1_num = bytesToInt(o1[0], o1[1]);
				Integer o2_num = bytesToInt(o2[0], o2[1]);
				return o1_num.compareTo(o2_num);
			}
		};
		Collections.sort(output, compare);
		return output;
	}

	/**
	 * Create and return an acknowledgement packet
	 * 
	 * @param packet_number
	 *            Packet number you wish to acknowledge
	 * @param src_ip
	 *            InetAddress of the original sender
	 * @param src_port
	 *            Port number of the original sender
	 * @return DatagramPacket object ready for transfer
	 */
	public static DatagramPacket createAckPacket(int packet_number,
			InetAddress src_ip, int src_port) {
		byte[] confirm = { (byte) 1 };
		byte[] ack = Utilities.concatArray(Utilities.intToBytes(packet_number),
				confirm);
		DatagramPacket ack_packet = new DatagramPacket(ack, ack.length, src_ip,
				src_port);
		return ack_packet;
	}
	
	/**
	 * A debugging tool to check data integrity prior to and after transfer.
	 * Could also be used for data integrity checks if the feature were
	 * implemented.
	 * 
	 * @param b
	 *            A packet in byte array form
	 * @return A checksum of the data in the given packet
	 */
	public static long getChecksum(byte[] b) {
		CRC32 check = new CRC32();
		check.update(b);
		return check.getValue();
	}
}
