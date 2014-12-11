import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;



public class Main implements Definitions {
	
	
	public static void main(String[] args) {
		
		// will read buffered winboard commands from stdin
		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
		// color of the pieces the engine will play
		byte engineColor = WHITE;
		// read from winboard
		long timeAvailable = 0;
		// the actual command from winboard to the engine or viceversa
		String command = null;
		// a board object
		Board board = new Board();
		// an engine object
		Engine engine = new Engine("book.dat");
		
		while (true) {
			
			try {
				// read a command from winboard
				command = reader.readLine();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			if ( command.compareTo("xboard") == 0 ) {
				try {
					// should read the protover command
					command = reader.readLine();
				} catch (IOException e) {
					e.printStackTrace();
				}
				if ( command.startsWith("protover") )
					// send features to winboard
					// SAN notation will be used
					// engine moves will be preceded by "usermove"
					System.out.println("feature san=1 usermove=1 done=1 myname=\"FairyPrincess1.0\"");
			}
			
			else if ( command.compareTo("new") == 0 ) {
				// initialize board
				board = new Board();
				// start engine
				engine = new Engine("book.dat");
				// engine starts with the black pieces
				engineColor = BLACK;
				// output a new line
				System.out.println("");
			}
			
			else if ( command.compareTo("white") == 0 ) {
				engineColor = WHITE;
			}
			
			else if ( command.compareTo("black") == 0 ) {
				engineColor = BLACK;
			}
			
			else if ( command.compareTo("force") == 0 )
				// do nothing
				// output a new line
				System.out.println("");
			
			else if ( command.compareTo("go") == 0 ) {
				if ( engineColor == board.toMove ) {
					command = engine.generateMove(board, timeAvailable);
					command = "move " + command;
					System.out.println( command );
				}
			}
			
			else if ( command.compareTo("quit") == 0 )
				// force exit
				System.exit(0);
			
			else if ( command.startsWith("usermove") ) {
				command = command.substring(9, command.length());
				// System.out.println(board);
				engine.receiveMove(command, board);
				command = engine.generateMove(board, timeAvailable);
				// System.out.println(board);
				if ( command.length() != 0 ) {
					command = "move " + command;
					System.out.println( command );
				}
				else
					System.out.println("resign");
			}
			else if ( command.startsWith("time") ) {
				timeAvailable = Long.parseLong( command.substring(5, command.length()) ) * 10;
			}
		}
	}
}

