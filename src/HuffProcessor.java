import java.util.*;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){

		int[] counts = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root);

		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHeader(root, out);

		in.reset();
		writeCompressedBits(codings, in, out);
		out.close();
	}

	/**
	 * Determines the frequency of every 8 bit character in the file being compressed
	 *
	 * @param in
	 * 			  Buffered bit stream of the file to be compressed
	 * @return
	 * 			  an int array of the frequencies
	 */

	private int[] readForCounts(BitInputStream in) {
    int[] freq = new int[ALPH_SIZE+1];
    int value = in.readBits(BITS_PER_WORD);
    while(value != -1) {
            freq[value] ++;
            value = in.readBits(BITS_PER_WORD);
        }
    freq[PSEUDO_EOF] = 1;
    return freq;
    }

	/**
	 * Uses the frequencies of the 8 bit characters to create a Huffman Tree with each frequency used as myWeight
	 * It is a greedy algorithm meaning the lowest two frequencies/weights are chosen to create a tree
	 *
	 * @param freq
	 * 				an array of frequencies used to construct the tree. This array is created by readForCounts
	 * @return
	 * 				The tree used to compress the bitstream
	 */

	private HuffNode makeTreeFromCounts(int[] freq) {
        PriorityQueue<HuffNode> pq = new PriorityQueue<>();

        for (int i = 0; i < freq.length; i++) {
            if (freq[i] > 0) {
                pq.add(new HuffNode(i, freq[i], null, null));
            }
        }
		while (pq.size() > 1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			HuffNode t = new HuffNode(0, left.myWeight + right.myWeight, left, right);
			pq.add(t);
		}
            HuffNode root = pq.remove();
            return root;
	}

	/**
	 *	Encodes the input stream characters using a Huffman Tree
	 *
	 * @param root
	 * 				Is the Huffman tree used to encode the bitstream. This tree is created by makeTreeFromCounts
	 * @return
	 * 				An array of the encodings by calling codingHelper
	 */

	private String[] makeCodingsFromTree(HuffNode root) {
        String[] encodings = new String[ALPH_SIZE + 1];
        codingHelper(root,"",encodings);
		return encodings;
    }

	/**
	 * Fills a String array with the proper root to leaf path
	 *
	 * @param root
	 * 					The Huffman Tree used to encode the bitstream
	 * @param path
	 * 					The root to leaf path for the bitstream. The recursive function creates this path from an empty string
	 * @param encodings
	 * 					An array of empty strings to be filled by the function
	 */

	private void codingHelper(HuffNode root, String path, String[] encodings) {
        if (root == null) {
            return;
        }
        if (root.myLeft == null && root.myRight == null) {
            encodings[root.myValue] = path;
        } else {
            if (root.myLeft != null) {
                codingHelper(root.myLeft, path + "0", encodings);
            }
            if (root.myRight != null) {
                codingHelper(root.myRight, path + "1", encodings);
            }
        }
        return;
    }

	/**
	 * Write the magic number and the tree to the beginning/header of the compressed file
	 *
	 * @param root
	 * 				Huffman Tree used for the encoding
	 * @param out
	 * 				Buffered bit stream writing to the output file
	 */

	private void writeHeader(HuffNode root, BitOutputStream out) {
    	if (root.myLeft != null || root.myRight != null) {
    		out.writeBits(1, 0);
    		writeHeader(root.myLeft, out);
    		writeHeader(root.myRight, out);
		} else if (root.myRight == null && root.myRight == null) {
    		out.writeBits(1, 1);
    		out.writeBits(BITS_PER_WORD + 1, root.myValue);
		}
	}

	/**
	 * Read the file again and write the encoding for each eight-bit chunk until reaching PSEUDO_EOF
	 *
	 * @param encoding
	 * 					The string of encodings created by makeCodingsFromTree
	 * @param in
	 * 					Buffered bit stream of the file to be compressed
	 * @param out
	 * 					Buffered bit stream writing to the output file
	 */

	private void writeCompressedBits (String[] encoding, BitInputStream in, BitOutputStream out) {
		int val = in.readBits(BITS_PER_WORD);
		while (val != -1) {
			String code = encoding[val];
			out.writeBits(code.length(), Integer.parseInt(code, 2));
			val = in.readBits(BITS_PER_WORD);
		}
		String code = encoding[PSEUDO_EOF];
		out.writeBits(code.length(), Integer.parseInt(code, 2));
	}


	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out) {

		int magic = in.readBits(BITS_PER_INT);
		if (magic != HUFF_TREE) {
			throw new HuffException("invalid magic number" + magic);
		}
		HuffNode root = readTree(in);
		HuffNode current = root;
		while (true) {
			int bits = in.readBits(1);
			if (bits == -1) {
				throw new HuffException("bad input, no PSEUDO_EOF");
			} else {
				if (bits == 0) {
					current = current.myLeft;
				} else {
					current = current.myRight;
				}
				if (current.myLeft == null && current.myRight == null){
					if (current.myValue == PSEUDO_EOF) {
						break;
					} else {
						out.writeBits(8, current.myValue);
						current = root;
					}
				}
			}
		}

		out.close();
	}

	/**
	 * Reads the bit stream to traverse the root to leaf paths and determine which HuffNode is returned
	 *
	 * @param in
	 *			  Buffered bit stream of the file to be decompressed
	 * @return
	 *			  Returns the Huffnode that is a result of the root to leaf path
	 */

	private HuffNode readTree(BitInputStream in) {
		int bit = in.readBits(1);
		if (bit == -1) {
			throw new HuffException("invalid bit number" + bit);
		}
		if (bit == 0) {
			HuffNode left = readTree(in);
			HuffNode right = readTree(in);
			return new HuffNode(0, 0, left, right);
		} else {
			int value = in.readBits(9);
			return new HuffNode(value, 0, null, null);
		}
	}
}