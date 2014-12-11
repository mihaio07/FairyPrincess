import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;
import java.util.TreeSet;

/**
 *
 * Represents a set of chess opening moves.
 * 
 * @author Team Fairy Princess
 * 
 */

public class OpeningBook {	
	
	/** SAN Strings are kept in a sorted set */
	private TreeSet<String> openingLines;
	
	
	/**
	 *	Creates an opening book from a simple ascii text file
	 *
	 *	@param String name of input file
	 */
	public OpeningBook(String filename) {
		openingLines = new TreeSet<String>();
		String current = "";
		try {
			BufferedReader in = new BufferedReader( new FileReader(filename) );
			while ( (current = in.readLine()) != null )
				openingLines.add(current);
			in.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	
	/**
	 *  Returns a SAN string with a move based on the opening book
	 *  
	 *  @param String History current moves (opening line)
	 *  @return a random available continuation move
	 *  
	 */
	public String getMove(String history) {
		String aux = "";
		String openingLine = "";
		int moveIndex;
		boolean stop = false;
		char side;
		int index;
		Random generator = new Random();
		ArrayList<String> candidates = new ArrayList<String>();
		ArrayList<String> favorableCandidates = new ArrayList<String>();
		Iterator<String> it = openingLines.iterator();
		
		// if first move, every line is a candidate
		if (history.compareTo("") == 0)
			candidates.addAll(openingLines);
		else {
			// match candidates
			while (it.hasNext()) {
				aux = it.next();
				if (aux.startsWith(history)) {
					candidates.add(aux);
					// a match was found
					// the first line that doesn't match will break the search
					stop = true;
				}
				else
					if (stop)
						break;
			}
		}
		
		if (candidates.size() == 0)
			return "";
		else {
			// find favorable opening lines
			// an odd number of moves means black to move
			if (history.compareTo("") == 0)
				moveIndex = 0;
			else
				moveIndex = history.split(" ").length;
			if ( moveIndex % 2 == 1 )
				side = 'b';
			else 
				side = 'w';
			it = candidates.iterator();
			while (it.hasNext()) {
				aux = it.next();
				if ( aux.charAt(aux.length()-2) == '#' ) {
					if ( aux.charAt(aux.length()-1) == side )
						favorableCandidates.add(aux);
				}
				else
					// because there are so few black favored openings
					// neutral openings are favorable for black
					if ( side == 'b' )
						favorableCandidates.add(aux);
			}
			if ( favorableCandidates.size() != 0 ) {
				index = generator.nextInt( favorableCandidates.size() );
				openingLine = favorableCandidates.get(index);
			}
			else {
				// if there are no favorable candidates
				// return a random neutral or unfavorable line
				index = generator.nextInt( candidates.size() );
				openingLine = candidates.get(index);
			}
			
			// update opening lines (the rest don't matter)
			openingLines.clear();
			openingLines.addAll(candidates);
			// convert the opening line into a simple move string
			String[] tokens = openingLine.split(" ");
			// the next move
			return tokens [ moveIndex ];
		}
	}
	
	
	/**
	 *	Sorts the strings of moves lexicographically
	 *  Reads from file and writes the sorted output to file
	 *  Not actually used during runtime
	 * 
	 *  @param String Name of input file
	 *	@param String Name of output file
	 * 
	 */
	public static void sortBook(String filenamein, String filenameout) {
		TreeSet<String> lines = new TreeSet<String>();
		String current = "";
		try {
			BufferedReader in = new BufferedReader( new FileReader(filenamein) );
			PrintWriter out = new PrintWriter(filenameout);
			while ( (current = in.readLine()) != null ) {
				lines.add(current);
			}
			Iterator<String> it = lines.iterator();
			while (it.hasNext())
				out.println(it.next());
			in.close();
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
}
