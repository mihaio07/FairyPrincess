import java.util.ArrayList;
import java.util.Iterator;

 

/**
 *
 * 	Represents a chess move. 
 *  
 * 	@author Team Fairy Princess
 * 
 */

public class Move implements Definitions {
	
	// data is not private in order to avoid getter and setter overhead
	/** Type of piece moving including color */
	byte pieceMoving; 
	/** The square line (rank) the piece is moving from */
	byte initialLine; 
	/** The square column (file) the piece is moving from */
	byte initialCol;
	/** The square line (rank) the piece is moving to */
	byte destinationLine; 		
	/** The square column the piece is moving to */
	byte destinationCol;		
	/** Captured piece (0 if no capture) */
	byte pieceCaptured; 		
	/** Type of move: ordinary, castle, promotion, en passant */
	byte moveType;
	
	
	/**
	 *  Creates a new move depending on piece moving, source, destination and type.
	 *
	 *	@param byte Initial Line
	 *	@param byte Initial Col
	 *	@param byte Destination Line
	 *	@param byte Destination Col
	 *	@param byte Integer constant representing the piece that is moving.
	 *	@param byte Type of move: ordinary, long castle, short castle, en passant, promotion
	 *	@param byte Type of piece captured (0 if none)
	 * 	@param byte[] Rights on the board (en passant, castle) prior to move
	 *
	 */
	public Move( byte pieceMoving, byte initialLine, byte initialCol, byte destinationLine, 
					byte destinationCol, byte pieceCaptured, byte moveType) {
		this.initialLine = initialLine;
		this.initialCol = initialCol;
		this.destinationLine = destinationLine;
		this.destinationCol = destinationCol;
		this.pieceMoving = pieceMoving;
		this.moveType = moveType;
		this.pieceCaptured = pieceCaptured;
	}
	

	
	/**
	 *  Creates a new move based on a SAN String.
	 *  SAN -> Move conversion
	 *  
	 *  @param String SAN String.
	 *  @param Board The chessboard 
	 *  
	 */
	public Move(String s, Board board) {
		char c;
		
		// initializations
		initialLine = initialCol = destinationLine = destinationCol = 0;
		pieceMoving = pieceCaptured = 0;
		moveType = 0;
		
		// discard check ('+') or checkmate symbol ('#')
		if (s.charAt(s.length()-1) == '+' || s.charAt(s.length()-1) == '#')
			s = s.substring(0, s.length()-1);
		
		// check if it is a short castle
		if (s.compareTo("O-O") == 0) {
			if (board.toMove == WHITE) {
				pieceMoving = WHITE_KING;
				initialLine = LINE1;
				initialCol = COLE;
				destinationLine = LINE1;
				destinationCol = COLG;
				moveType = SHORT_CASTLE;
			}
			else {
				pieceMoving = BLACK_KING;
				initialLine = LINE8;
				initialCol = COLE;
				destinationLine = LINE8;
				destinationCol = COLG;
				moveType = SHORT_CASTLE;
			}
			return;
		}
		// check if it is a long castle
		if (s.compareTo("O-O-O") == 0) {
			if (board.toMove == WHITE) {
				pieceMoving = WHITE_KING;
				initialLine = LINE1;
				initialCol = COLE;
				destinationLine = LINE1;
				destinationCol = COLC;
				moveType = LONG_CASTLE;
			}
			else {
				pieceMoving = BLACK_KING;
				initialLine = LINE8;
				initialCol = COLE;
				destinationLine = LINE8;
				destinationCol = COLC;
				moveType = LONG_CASTLE;
			}
			return;
		}
		
		moveType = ORDINARY_MOVE;
		
		// check if it is a promotion
		if (s.contains("=")) {
			if ( s.charAt(s.length()-1) == 'Q')
				moveType = PROMOTION_QUEEN;
			if ( s.charAt(s.length()-1) == 'R')
				moveType = PROMOTION_ROOK;
			if ( s.charAt(s.length()-1) == 'B')
				moveType = PROMOTION_BISHOP;
			if ( s.charAt(s.length()-1) == 'N')
				moveType = PROMOTION_KNIGHT;
			// update string
			s = s.substring(0, s.length()-2);
		}
		
		// start analyzing the string from the end
		// first get the destination square
		destinationLine = codeLine( s.charAt(s.length()-1) );
		destinationCol = codeCol( s.charAt(s.length()-2) );
		
		// update string
		s = s.substring(0, s.length()-2);
		
		// if it is a simple pawn move
		if (s.length() == 0) {
			pieceMoving = (byte) (PAWN * board.toMove);
			fillInitialPosition(board);
			return;
		}
		
		// if it is a simple non pawn move
		c = s.charAt(s.length()-1);
		if ( Character.isUpperCase(c) ) {
			if ( board.toMove == BLACK )
				c = Character.toLowerCase(c);
			pieceMoving = codePiece(c);
			fillInitialPosition(board);
			return;
		}
		
		// check if it is a capture
		if ( s.charAt(s.length()-1) == 'x' ) {
			// if en passant
			if ( board.table[destinationLine][destinationCol] == EMPTY_SQUARE ) {
				pieceCaptured = board.table[initialLine][destinationCol];
				moveType = EN_PASSANT;
			}
			else
				pieceCaptured = board.table[destinationLine][destinationCol];
			// discard the 'x'
			s = s.substring(0, s.length()-1);
		}
		
		// unambiguous move
		if (s.length() == 1) {
			c = s.charAt(0);
			// if remaining character is an uppercase letter
			if ( Character.isUpperCase(c) ) {
				if ( board.toMove == BLACK )
					c = Character.toLowerCase(c);
				// the character represents a piece
				pieceMoving = codePiece(c);
			}
			else {
				pieceMoving = (byte) (PAWN * board.toMove);
				// the character represents the departing file for a pawn
				initialCol = codeCol(c);
			}
			fillInitialPosition(board);
			return;
		}
		
		// ambiguous move with rank / file disambiguation
		if ( s.length() == 2 ) {
			c = s.charAt(1);
			// file disambiguation
			if ( Character.isLetter(c) )
				initialCol = codeCol(c);
			// rank disambiguation
			else 
				initialLine = codeLine(c);
			c = s.charAt(0);
			// get piece code
			if ( board.toMove == BLACK )
				c = Character.toLowerCase(c);
			pieceMoving = codePiece(c);
			
			fillInitialPosition(board);
			return;
		}
		
		// double ambiguous move, both rank and file are specified
		// the remaining string is 3 characters long
		if ( s.length() == 3 ) {
			c = s.charAt(2);
			initialLine = codeLine(c);
			c = s.charAt(1);
			initialCol = codeCol(c);
			c = s.charAt(0);
			// get piece code
			if ( board.toMove == BLACK )
				c = Character.toLowerCase(c);
			pieceMoving = codePiece(c);
			// filling in the initial position is no longer required
			return;
		}
	}
	
	
	/**
	 *  Output current move in the form of a SAN String.
	 *  Move -> SAN conversion
	 *	
	 *	@param Board The chessboard.
	 *  @return String containing SAN move.
	 *  
	 */
	public String writeMove(Board board) {
		String notation = "";
		
		// return castle notations directly
		if (moveType == SHORT_CASTLE)
			return "O-O";
		if (moveType == LONG_CASTLE)
			return "O-O-O";
		
		// return promotions directly (pawn moves don't need disambiguation
		if (moveType == PROMOTION_QUEEN) {
			if (pieceCaptured != EMPTY_SQUARE)
				notation = notation + decodeCol(initialCol) + "x";
			notation = notation + decodeCol(destinationCol) + decodeLine(destinationLine);
			notation += "=Q";
			return notation;
		}
		if (moveType == PROMOTION_ROOK) {
			if (pieceCaptured != EMPTY_SQUARE)
				notation = notation + decodeCol(initialCol) + "x";
			notation = notation + decodeCol(destinationCol) + decodeLine(destinationLine);
			notation += "=R";
			return notation;
		}
		if (moveType == PROMOTION_BISHOP) {
			if (pieceCaptured != EMPTY_SQUARE)
				notation = notation + decodeCol(initialCol) + "x";
			notation = notation + decodeCol(destinationCol) + decodeLine(destinationLine);
			notation += "=B";
			return notation;
		}
		if (moveType == PROMOTION_KNIGHT) {
			if (pieceCaptured != EMPTY_SQUARE)
				notation = notation + decodeCol(initialCol) + "x";
			notation = notation + decodeCol(destinationCol) + decodeLine(destinationLine);
			notation += "=N";
			return notation;
		}
		
		// add piece symbol unless pawn
		if ( pieceMoving != WHITE_PAWN && pieceMoving != BLACK_PAWN )
			notation += Character.toUpperCase( decodePiece(pieceMoving) );
		
		// add x for capture; also in the case of en passant
		if ( pieceCaptured != 0 )
			notation += "x";
		
		// add destination column and line
		notation += decodeCol(destinationCol);
		notation += decodeLine(destinationLine);
		
		return disambiguate(notation, board);
	}
	
	
	/**
	 *	Disambiguates a partially correct generated SAN string
	 * 
	 * 	@param Board The chessboard
	 * 	@param String The pre-generated SAN string
	 * 
	 *	@return A new adjusted SAN string
	 * 
	 */
	private String disambiguate(String notation, Board board) {
		String aux = "";
		byte i, j;
		ArrayList<Move> moves = new ArrayList<Move>();
		ArrayList<Move> ambiguousMoves = new ArrayList<Move>(); 
		
		// pawn capture disambiguation
		// string begins with an 'x'
		if ( notation.charAt(0) == 'x' ) {
			// add file
			aux = aux + decodeCol(initialCol) + notation;
			return aux;
		}
		
		// find all the pieces of the same type and color on the board and generate their moves
		for (i = LINE1; i <= LINE8; ++i)
			for (j = COLA; j <= COLH; ++j)
				if ( pieceMoving == board.table[i][j] ) {
					switch ( Math.abs(pieceMoving) ) {
						case KNIGHT: moves.addAll(board.genKnightMoves(i,j)); break;
						case BISHOP: moves.addAll(board.genBishopMoves(i,j)); break;
						case ROOK: moves.addAll(board.genRookMoves(i,j)); break;
						case QUEEN: moves.addAll(board.genQueenMoves(i,j));
					}
				}
		
		// iterate through moves to find if there are other pieces that have the same destination
		// put them in a new arraylist (current piece will be excluded)
		// also exclude illegal moves
		Iterator<Move> it = moves.iterator();
		while (it.hasNext()) {
			Move m = it.next();
			// if it has the same destination but not the same initial square
			if ( (m.destinationCol == destinationCol && m.destinationLine == destinationLine) 
					&& ! ( m.initialCol == initialCol && m.initialLine == initialLine) 
						// test if it is legal
						&& board.isLegal(m) )
				ambiguousMoves.add(m);
		}

		if (ambiguousMoves.size() != 0) {
			// if there is a piece on the same file and a piece on the same rank
			// complete disambiguation is required; add file and rank
			if ( pieceOnSameFile(ambiguousMoves) && pieceOnSameRank(ambiguousMoves) )
				aux = aux + notation.charAt(0) + decodeCol(initialCol) +
						decodeLine(initialLine) + notation.substring(1, notation.length());
			else {
				// if there is a piece on the same file
				// disambiguation with rank is required
				if ( pieceOnSameFile(ambiguousMoves) )
					aux = aux + notation.charAt(0) + decodeLine(initialLine) + 
						notation.substring(1, notation.length());
				else {
					// file disambiguation will suffice
					// also covers the case of knights
					aux = aux + notation.charAt(0) + decodeCol(initialCol) + 
						notation.substring(1, notation.length());
				}
			}
			// return the new disambiguated string
			return aux;
		}
		else
			// there are no ambiguous moves
			// thus no disambiguation is required
			return notation;
	}
	
	
	/**
	 *	Fills in the initial square of a Move object based on the piece and destination 
	 *	Used in the SAN -> Move conversion	
	 *
	 *	@param Board The chessboard.
	 *
	 **/
	private void fillInitialPosition(Board board) {
		ArrayList<Move> moves = new ArrayList<Move>();
		Iterator<Move> it;
		byte i, j;
		Move m;
		
		// if there was no disambiguation
		// both rank and file must be found
		if (initialLine == 0 && initialCol == 0) {
			//generate moves for all the pieces of the correct type
			for ( i = LINE1; i <= LINE8; ++i )
				for ( j = COLA; j <= COLH; ++j)
					if ( board.table[i][j] == pieceMoving ) 
						switch ( Math.abs(pieceMoving) ) {
							case PAWN: {
										moves.addAll( board.genPawnMoves(i,j) );
										moves.addAll( board.genPromotionMoves(i,j) );
									} break;
							case KNIGHT: moves.addAll( board.genKnightMoves(i,j) ); break;
							case BISHOP: moves.addAll( board.genBishopMoves(i,j) ); break;
							case ROOK: moves.addAll( board.genRookMoves(i,j) ); break;
							case QUEEN: moves.addAll( board.genQueenMoves(i,j) ); break;
							case KING: moves.addAll( board.genKingMoves(i,j) ); break;
						}
			if ( Math.abs(pieceMoving) == PAWN )
				moves.addAll( board.genEnPassantMoves() );
			// find the correct piece
			it = moves.iterator();
			while ( it.hasNext() ) {
				m = it.next();
				// BUG FIX !!!
				if (board.isLegal(m))
				if ( (m.destinationCol == destinationCol) && (m.destinationLine == destinationLine) ) {
					initialCol = m.initialCol;
					initialLine = m.initialLine;
					// the moving piece has been found; no need to continue with the iteration
					break;
				}
			}			
		}
		else {
			// if file was provided in the SAN string
			if ( initialLine == 0 ) {	
				//generate moves for all the pieces of the correct type on the given file
				for ( i = LINE1; i <= LINE8; ++i )
					if ( board.table[i][initialCol] == pieceMoving ) 
						switch ( Math.abs(pieceMoving) ) {
							case PAWN: {
										moves.addAll( board.genPawnMoves(i,initialCol) );
										moves.addAll( board.genPromotionMoves(i,initialCol) );
									} break;
							case KNIGHT: moves.addAll( board.genKnightMoves(i,initialCol) ); break;
							case BISHOP: moves.addAll( board.genBishopMoves(i,initialCol) ); break;
							case ROOK: moves.addAll( board.genRookMoves(i,initialCol) ); break;
							case QUEEN: moves.addAll( board.genQueenMoves(i,initialCol) ); break;
							case KING: moves.addAll( board.genKingMoves(i,initialCol) ); break;
						}
				if ( Math.abs(pieceMoving) == PAWN )
					moves.addAll( board.genEnPassantMoves() );
				// find the correct piece
				it = moves.iterator();
				while ( it.hasNext() ) {
					m = it.next();
					if (board.isLegal(m))
					if ( (m.destinationCol == destinationCol) && (m.destinationLine == destinationLine) ) {
						initialLine = m.initialLine;
						break;
					}
				}
			}
			//if rank was provided in the SAN string
			else {
				//generate moves for all the pieces of the correct type on the give line
				for ( j = COLA; j <= COLH; ++j)
					if ( board.table[initialLine][j] == pieceMoving ) 
						switch ( Math.abs(pieceMoving) ) {
							case PAWN: {
										moves.addAll( board.genPawnMoves(initialLine,j) );
										moves.addAll( board.genPromotionMoves(initialLine,j) );
									} break;
							case KNIGHT: moves.addAll( board.genKnightMoves(initialLine,j) ); break;
							case BISHOP: moves.addAll( board.genBishopMoves(initialLine,j) ); break;
							case ROOK: moves.addAll( board.genRookMoves(initialLine,j) ); break;
							case QUEEN: moves.addAll( board.genQueenMoves(initialLine,j) ); break;
							case KING: moves.addAll( board.genKingMoves(initialLine,j) ); break;
						}
				if ( Math.abs(pieceMoving) == PAWN )
					moves.addAll( board.genEnPassantMoves() );
				//find the correct piece
				it = moves.iterator();
				while ( it.hasNext() ) {
					m = it.next();
					if (board.isLegal(m))
					if ( (m.destinationCol == destinationCol) && (m.destinationLine == destinationLine) ) {
						initialCol = m.initialCol;
						break;
					}
				}
			}
		}
	}
	
	
	
	/***************************/
	/**** Auxiliary methods ****/
	/***************************/
	
	
	/** Converts a piece from character to byte */
	public static byte codePiece(char c) {
		final byte[] symbolW = { '-', 'K', 'Q', 'R', 'B', 'N', 'P' };
		final byte[] symbolB = { '-', 'k', 'q', 'r', 'b', 'n', 'p' };
		byte i;
		
		if (c == '-')
			return 0;
		else {
			if (Character.isUpperCase(c)) {
				for (i = 1; i <= 6; ++i)
					if (c == symbolW[i])
						return i;
			}
			else
				for (i = 1; i <= 6; ++i)
					if (c == symbolB[i])
						return (byte) -i;
		}
		return 0;
	}
	
	/** Converts a piece from byte to character */
	static char decodePiece(byte p) {
		final byte[] symbolW = { '-', 'K', 'Q', 'R', 'B', 'N', 'P' };
		final byte[] symbolB = { '-', 'k', 'q', 'r', 'b', 'n', 'p' };
		
		if (p < 0)
			return (char) symbolB[-p];
		else
			return (char) symbolW[p];
	}
	
	/** Converts the line (rank) from character to byte */
	static byte codeLine(char c) {
		return (byte) (c - '1' + 2);	
	}
	
	/** Converts the column (file) from character to byte */
	static byte codeCol(char c) {
		return (byte) (c - 'a' + 2);		
	}
	
	/** Converts the line (rank) from byte to character */
	static char decodeLine(byte c) {
		return (char) (c - 2 + '1');	
	}
	
	/** Converts the column (file) from byte to character */
	static char decodeCol(byte c) {
		return (char) (c - 2 + 'a');		
	}
	
	/** Checks if there is an identical piece on the same file */
	private boolean pieceOnSameFile(ArrayList<Move> moves) {
		Iterator<Move> it = moves.iterator();
		while (it.hasNext())
			if (it.next().initialCol == initialCol)
				return true;
		return false;
	}

	/** Checks if there is an identical piece on the same rank */
	private boolean pieceOnSameRank(ArrayList<Move> moves) {
		Iterator<Move> it = moves.iterator();
		while (it.hasNext())
			if (it.next().initialLine == initialLine)
				return true;
		return false;
	}
	
	/** Returns a string containing a move object */
	public String toString() {
		String s = "";
		s += Character.toUpperCase(decodePiece(pieceMoving)) + "" + decodeCol(initialCol) + decodeLine(initialLine) +
				"" + decodeCol(destinationCol) + decodeLine(destinationLine);
		return s;
	}
	
	
	/** Equals method to check if two moves are similar */
	public boolean equals(Move move) {
		
		if ( pieceMoving != move.pieceMoving || moveType != move.moveType || 
			initialCol != move.initialCol || initialLine != move.initialLine || 
			destinationCol != move.destinationCol || destinationLine != move.destinationLine ||
			pieceCaptured != move.pieceCaptured )
				return false;
		
		return true;
	}
	
}
