import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;



/**
 *
 * 	Represents a chess board using a simple integer array.
 * 	Manages making and undoing a move, as well as castling and en passant rights.
 *  Contains implementation for Zobrist keys
 *  
 * 	@author Team Fairy Princess
 * 
 */

public class Board implements Definitions {
	
	/** A 12 x 12 byte matrix */
	byte[][] table;
	/** Side to move: uses WHITE or BLACK constants */
	byte toMove;
	/** Line (rank) position for white king */
	byte whiteKingLine;
	/** Column (file) position for white king */
	byte whiteKingCol;
	/** Line (rank) position for black king */
	byte blackKingLine;
	/** Column (file) position for black king */
	byte blackKingCol;
	
	/** Line and column of en passant-able pawn, -1 if unavailable */
	byte enPassantLine;
	byte enPassantCol;
	/** Manages castle rights: 0 (none), 1 (short), 2( long) or 3 (both) */
	byte whiteCastle;
	byte blackCastle;
	/** Previous rights stack */
	byte[] previousRights;
	int previousRightsIndex;
	
	/** Keeps track of number of pieces remaining in order to detect endgame */
	byte nWKnights, nBKnights;
	byte nWBishops, nBBishops;
	byte nWRooks, nBRooks;
	byte nWQueens, nBQueens;
	
	/** Zobrist keys: piece, line, col */
	long[][][] Zobrist_White;
	long[][][] Zobrist_Black;
	long Zobrist_Side;		// used for changing sides
	long Zobrist_Key;
	

	/**
	 *  Creates a new board with pieces on their initial positions.
	 *
	 */
	public Board() {
		byte i, j, k;
		
		table = new byte[12][12];
		toMove = WHITE;
		
		nWQueens = nBQueens = 1;
		nWRooks = nBRooks = 2;
		nWBishops = nBBishops = 2;
		nWKnights = nBKnights = 2;
		
		// Filling in the whole board with OUT_OF_BOUNDS
		for (i = 0; i < 12; ++i)
			for (j = 0; j < 12; ++j)
				table[i][j] = OUT_OF_BOUNDS;
		
		// Filling in the empty middle board
		for (i = LINE3; i <= LINE6; ++i)
			for (j = COLA; j <= COLH; ++j) {
				table[i][j] = EMPTY_SQUARE;
			}
		// Filling in the white pieces
		table[LINE1][COLA] = table[LINE1][COLH] = WHITE_ROOK;  
		table[LINE1][COLB] = table[LINE1][COLG] = WHITE_KNIGHT;
		table[LINE1][COLC] = table[LINE1][COLF] = WHITE_BISHOP;
		table[LINE1][COLD] = WHITE_QUEEN;
		table[LINE1][COLE] = WHITE_KING;
		for (j = COLA; j <= COLH; ++j) {
			table[LINE2][j] = WHITE_PAWN;
		}
		// Filling in the black pieces
		table[LINE8][COLA] = table[LINE8][COLH] = BLACK_ROOK;  
		table[LINE8][COLB] = table[LINE8][COLG] = BLACK_KNIGHT;
		table[LINE8][COLC] = table[LINE8][COLF] = BLACK_BISHOP;
		table[LINE8][COLD] = BLACK_QUEEN;
		table[LINE8][COLE] = BLACK_KING;
		for (j = COLA; j <= COLH; ++j) {
			table[LINE7][j] = BLACK_PAWN;
		}
		// after this, only the border remains out of bounds
		
		// Initializing kings positions
		whiteKingCol = COLE;
		whiteKingLine = LINE1;
		blackKingCol = COLE;
		blackKingLine = LINE8;
		
		whiteCastle = CASTLE_BOTH;
		blackCastle = CASTLE_BOTH;
		enPassantLine = enPassantCol = -1;
		
		previousRights = new byte[4 * 1024];
		previousRightsIndex = 0;
		
		// include padding
		// no castling rights or en passants
		Zobrist_White = new long[7][12][12];
		Zobrist_Black = new long[7][12][12];
		Random generator = new Random();
		Zobrist_Side = Math.abs(generator.nextLong());
		for (i = 1; i < 7; i++)
			for (j = 0; j < 12; j++)
				for (k = 0; k < 12; k++) {
					Zobrist_White[i][j][k] = Math.abs(generator.nextLong());
					Zobrist_Black[i][j][k] = Math.abs(generator.nextLong());
				}
		// initialize zobrist key
		Zobrist_Key = generateZobrist();

	}
	
	
	
	/**
	 *  Makes the move on the board and can also change en passant and castling rights.
	 *  
	 *  @param Move Valid Move object
	 *  
	 */
	public void makeMove(Move move) {
		
		// save previous rights
		previousRights[previousRightsIndex++] = enPassantLine;
		previousRights[previousRightsIndex++] = enPassantCol;
		previousRights[previousRightsIndex++] = whiteCastle;
		previousRights[previousRightsIndex++] = blackCastle;
		
		// if move is a capture, update number of pieces
		if ( move.pieceCaptured != 0 ) {
			switch ( move.pieceCaptured ) {
				case WHITE_QUEEN: nWQueens--; break;
				case BLACK_QUEEN: nBQueens--; break;
				case WHITE_ROOK: nWRooks--; break;
				case BLACK_ROOK: nBRooks--; break;
				case WHITE_BISHOP: nWBishops--; break;
				case BLACK_BISHOP: nBBishops--; break;
				case WHITE_KNIGHT: nWKnights--; break;
				case BLACK_KNIGHT: nBKnights--; break;
			}
		}
		
		// update kings' positions
		if (move.pieceMoving == WHITE_KING) {
			whiteKingLine = move.destinationLine;
			whiteKingCol = move.destinationCol;
		}
		if (move.pieceMoving == BLACK_KING) {
			blackKingLine = move.destinationLine;
			blackKingCol = move.destinationCol;
		}
		
		// every move clears previous en passant rights
		enPassantLine = enPassantCol = -1;
		
		switch ( move.moveType ) {
			case ORDINARY_MOVE: {
				// set destination square and clear starting square
				table[ move.destinationLine ][ move.destinationCol ] = move.pieceMoving;
				table[ move.initialLine ][ move.initialCol ] = EMPTY_SQUARE;
				
				// set new en passant square if necessary
				if (move.pieceMoving == WHITE_PAWN)
					if (move.destinationLine == LINE4 && move.initialLine == LINE2) {
						enPassantLine = move.destinationLine;
						enPassantCol = move.destinationCol;
					}
				if (move.pieceMoving == BLACK_PAWN)
					if (move.destinationLine == LINE5 && move.initialLine == LINE7) {
						enPassantLine = move.destinationLine;
						enPassantCol = move.destinationCol;
					}
				
				break;
			}
			case SHORT_CASTLE: {
				// move king
				table[ move.destinationLine ][ move.destinationCol ] = move.pieceMoving;
				table[ move.initialLine ][ move.initialCol ] = EMPTY_SQUARE;
				// move rook
				if ( toMove == WHITE ) {
					table[ LINE1 ][ COLF ] = table[ LINE1 ][ COLH ];
					table[ LINE1 ][ COLH ] = EMPTY_SQUARE;
					// make further castling impossible
					whiteCastle = CASTLE_NONE;
				}
				else {
					table[ LINE8 ][ COLF ] = table[ LINE8 ][ COLH ];
					table[ LINE8 ][ COLH ] = EMPTY_SQUARE;
					// make further castling impossible
					blackCastle = CASTLE_NONE;
				}
				break;
			}
			case LONG_CASTLE: {
				// move king
				table[ move.destinationLine ][ move.destinationCol ] = move.pieceMoving;
				table[ move.initialLine ][ move.initialCol ] = EMPTY_SQUARE;
				// move rook
				if ( toMove == WHITE ) {
					table[ LINE1 ][ COLD ] = table[ LINE1 ][ COLA ];
					table[ LINE1 ][ COLA ] = EMPTY_SQUARE;
					// make further castling impossible
					whiteCastle = CASTLE_NONE;
				}
				else {
					table[ LINE8 ][ COLD ] = table[ LINE8 ][ COLA ];
					table[ LINE8 ][ COLA ] = EMPTY_SQUARE;
					// make further castling impossible
					blackCastle = CASTLE_NONE;
				}
				break;
			}
			case EN_PASSANT: {
				// move attacking pawn diagonally
				table[ move.destinationLine ][ move.destinationCol ] = move.pieceMoving;
				table[ move.initialLine ][ move.initialCol ] = EMPTY_SQUARE;
				// clear attacked pawn
				table[ move.initialLine ][ move.destinationCol ] = EMPTY_SQUARE;
				// clear en passant rights
				enPassantLine = enPassantCol = -1;
				break;	
			}
			case PROMOTION_QUEEN: {
				table[ move.destinationLine ][ move.destinationCol ] = (byte) (QUEEN * toMove);
				table[ move.initialLine ][ move.initialCol ] = EMPTY_SQUARE;
				break;
			}
			case PROMOTION_ROOK: {
				table[ move.destinationLine ][ move.destinationCol ] = (byte) (ROOK * toMove);
				table[ move.initialLine ][ move.initialCol ] = EMPTY_SQUARE;
				break;
			}
			case PROMOTION_BISHOP: {
				table[ move.destinationLine ][ move.destinationCol ] = (byte) (BISHOP * toMove);
				table[ move.initialLine ][ move.initialCol ] = EMPTY_SQUARE;
				break;
			}
			case PROMOTION_KNIGHT: {
				table[ move.destinationLine ][ move.destinationCol ] = (byte) (KNIGHT * toMove);
				table[ move.initialLine ][ move.initialCol ] = EMPTY_SQUARE;
				break;
			}
			default: break;					
		}
		
		// update castle rights if necessary
		if( whiteCastle != CASTLE_NONE || blackCastle != CASTLE_NONE )
		{
			// if the rooks / kings are missing from their initial positions
			// corresponding castle rights will be removed
			
			// kings
			if ( table[LINE1][COLE] != WHITE_KING )
				whiteCastle = CASTLE_NONE;
			if ( table[LINE8][COLE] != BLACK_KING )
				blackCastle = CASTLE_NONE;

			// white rooks
			if ( table[LINE1][COLA] != WHITE_ROOK ) {
				// remove long castle rights
				if ( whiteCastle == CASTLE_BOTH )
					whiteCastle = CASTLE_SHORT;
				else if (whiteCastle == CASTLE_LONG)
					whiteCastle = CASTLE_NONE;
			}
			if ( table[LINE1][COLH] != WHITE_ROOK ) {
				// remove short castle rights
				if ( whiteCastle == CASTLE_BOTH )
					whiteCastle = CASTLE_LONG;
				else if (whiteCastle == CASTLE_SHORT)
					whiteCastle = CASTLE_NONE;
			}
			
			// black rooks
			if ( table[LINE8][COLA] != BLACK_ROOK ) {
				// remove long castle rights
				if ( blackCastle == CASTLE_BOTH )
					blackCastle = CASTLE_SHORT;
				else if (blackCastle == CASTLE_LONG)
					blackCastle = CASTLE_NONE;
			}
			if ( table[LINE8][COLH] != BLACK_ROOK ) {
				// remove short castle rights
				if ( blackCastle == CASTLE_BOTH )
					blackCastle = CASTLE_LONG;
				else if (blackCastle == CASTLE_SHORT)
					blackCastle = CASTLE_NONE;
			}
		}	
		// change turn White <-> Black
		toMove *= -1;
		
		// update zobrist key for ordinary moves
		if (move.moveType == ORDINARY_MOVE) {
			// update side
			Zobrist_Key ^= Zobrist_Side;
			// remove piece moving
			// remove piece captured; if none (empty destination square), key is xored with 0
			// place piece moving
			if (move.pieceMoving < 0) {
				Zobrist_Key ^= Zobrist_Black[-move.pieceMoving][move.initialLine][move.initialCol];
				Zobrist_Key ^= Zobrist_White[move.pieceCaptured][move.destinationLine][move.destinationCol];
				Zobrist_Key ^= Zobrist_Black[-move.pieceMoving][move.destinationLine][move.destinationCol];							
			}
			else {
				Zobrist_Key ^= Zobrist_White[move.pieceMoving][move.initialLine][move.initialCol];
				Zobrist_Key ^= Zobrist_Black[-move.pieceCaptured][move.destinationLine][move.destinationCol];
				Zobrist_Key ^= Zobrist_White[move.pieceMoving][move.destinationLine][move.destinationCol];				
			}
		}

		// simpler is better for non-ordinary moves
		else
			Zobrist_Key = generateZobrist();
	
	}
	
	
	/**
	 *  Undoes the move on the board and can restore en passant and castling rights.
	 *  
	 *  @param Move Valid Move object
	 *  
	 */
	public void undoMove(Move move) {
		
		// restores previous en passant and castling rights (reverse order)
		blackCastle = previousRights[--previousRightsIndex];
		whiteCastle = previousRights[--previousRightsIndex];
		enPassantCol = previousRights[--previousRightsIndex];
		enPassantLine = previousRights[--previousRightsIndex];
		
		// if move was a capture, update number of pieces
		if ( move.pieceCaptured != 0 ) {
			switch ( move.pieceCaptured ) {
				case WHITE_QUEEN: nWQueens++; break;
				case BLACK_QUEEN: nBQueens++; break;
				case WHITE_ROOK: nWRooks++; break;
				case BLACK_ROOK: nBRooks++; break;
				case WHITE_BISHOP: nWBishops++; break;
				case BLACK_BISHOP: nBBishops++; break;
				case WHITE_KNIGHT: nWKnights++; break;
				case BLACK_KNIGHT: nBKnights++; break;
			}
		}
		
		// restores kings' positions if necessary
		if (move.pieceMoving == WHITE_KING) {
			whiteKingLine = move.initialLine;
			whiteKingCol = move.initialCol;
		}
		if (move.pieceMoving == BLACK_KING) {
			blackKingLine = move.initialLine;
			blackKingCol = move.initialCol;
		}
		
		switch ( move.moveType ) {
			case ORDINARY_MOVE: {
				// restore destination square and set starting square
				// if the move has been a capture, the captured piece will be restored
				table[ move.initialLine ][ move.initialCol ] = move.pieceMoving;
				table[ move.destinationLine ][ move.destinationCol ] = move.pieceCaptured;
				break;
			}
			case SHORT_CASTLE: {
				// restore king position
				table[ move.initialLine ][ move.initialCol ] = move.pieceMoving;
				table[ move.destinationLine ][ move.destinationCol ] = EMPTY_SQUARE;		
				// move rook
				// if black is at move => undo white castle
				if ( toMove == BLACK ) {
					table[ LINE1 ][ COLH ] = table[ LINE1 ][ COLF ];
					table[ LINE1 ][ COLF ] = EMPTY_SQUARE;
				}
				else {
					table[ LINE8 ][ COLH ] = table[ LINE8 ][ COLF ];
					table[ LINE8 ][ COLF ] = EMPTY_SQUARE;
				}
				break;
			}
			case LONG_CASTLE: {
				// restore king position
				table[ move.initialLine ][ move.initialCol ] = move.pieceMoving;
				table[ move.destinationLine ][ move.destinationCol ] = EMPTY_SQUARE;
				// move rook
				// if black is at move => undo white castle
				if ( toMove == BLACK ) {
					table[ LINE1 ][ COLA ] = table[ LINE1 ][ COLD ];
					table[ LINE1 ][ COLD ] = EMPTY_SQUARE;
				}
				else {
					table[ LINE8 ][ COLA ] = table[ LINE8 ][ COLD ];
					table[ LINE8 ][ COLD ] = EMPTY_SQUARE;
				}
				break;
			}
			case EN_PASSANT: {
				// restore pawn position
				table[ move.initialLine ][ move.initialCol ] = move.pieceMoving;
				table[ move.destinationLine ][ move.destinationCol ] = EMPTY_SQUARE;
				
				// restore attacked pawn
				table[ move.initialLine ][ move.destinationCol ] = move.pieceCaptured;
				break;	
			}
			case PROMOTION_QUEEN: {
				table[ move.initialLine ][ move.initialCol ] = move.pieceMoving;
				table[ move.destinationLine ][ move.destinationCol ] = move.pieceCaptured;
				break;
			}
			case PROMOTION_ROOK: {
				table[ move.initialLine ][ move.initialCol ] = move.pieceMoving;
				table[ move.destinationLine ][ move.destinationCol ] = move.pieceCaptured;
				break;
			}
			case PROMOTION_BISHOP: {
				table[ move.initialLine ][ move.initialCol ] = move.pieceMoving;
				table[ move.destinationLine ][ move.destinationCol ] = move.pieceCaptured;
				break;
			}
			case PROMOTION_KNIGHT: {
				table[ move.initialLine ][ move.initialCol ] = move.pieceMoving;
				table[ move.destinationLine ][ move.destinationCol ] = move.pieceCaptured;
				break;
			}
			default: break;					
		}
		
		// change turn White <-> Black
		toMove *= -1;
		
		// update zobrist key for ordinary moves
		if (move.moveType == ORDINARY_MOVE) {
			// update side
			Zobrist_Key ^= Zobrist_Side;
			// place piece moving
			// remove piece moving
			// place piece captured; if none (empty destination square), key is xored with 0
			if (move.pieceMoving < 0) {
				Zobrist_Key ^= Zobrist_Black[-move.pieceMoving][move.initialLine][move.initialCol];
				Zobrist_Key ^= Zobrist_Black[-move.pieceMoving][move.destinationLine][move.destinationCol];
				Zobrist_Key ^= Zobrist_White[move.pieceCaptured][move.destinationLine][move.destinationCol];			
			}
			else {
				Zobrist_Key ^= Zobrist_White[move.pieceMoving][move.initialLine][move.initialCol];
				Zobrist_Key ^= Zobrist_White[move.pieceMoving][move.destinationLine][move.destinationCol];
				Zobrist_Key ^= Zobrist_Black[-move.pieceCaptured][move.destinationLine][move.destinationCol];
			}
		}

		// simpler is better for non-ordinary moves
		else
			Zobrist_Key = generateZobrist();
	
	}
	
	
	
	/*********************************/
	/**** Move generation methods ****/
	/*********************************/
	
	
	/** Generates valid moves for the pawn at position (i,j) */
	ArrayList<Move> genPawnMoves(byte i, byte j) {
		ArrayList<Move> v = new ArrayList<Move>();
		byte line, col;
		
		// pawn cannot get to the last rank; promotion moves will be treated separately
		if ( ( i + toMove ) == LINE8 || ( i + toMove ) == LINE1 ) 
			return v;
		
		// test if pawn can move one position forward
		line = (byte) (i + toMove);
		col = j;
		if ( table[line][col] == EMPTY_SQUARE )
			v.add( new Move(table[i][j], i, j, line, col, table[line][col], ORDINARY_MOVE));
		
		// check for captures
		// NE direction
		line = (byte) (i + toMove);
		col = (byte) (j + 1);
		if ( table[line][col] != OUT_OF_BOUNDS )
			//if opposite color
			if ( table[line][col] * toMove < 0 )
				v.add( new Move(table[i][j], i, j, line, col, table[line][col], ORDINARY_MOVE));		
		// NW direction
		line = (byte) (i + toMove);
		col = (byte) (j - 1);
		if ( table[line][col] != OUT_OF_BOUNDS )
			// if opposite color
			if ( table[line][col] * toMove < 0 )
				v.add( new Move(table[i][j], i, j, line, col, table[line][col], ORDINARY_MOVE));
		
		// if first move, two square forward move is possible
		// only if a white pawn is on rank 2 or a black pawn is on rank 7
		if ( (toMove * i == LINE2) || ((- toMove) * i == LINE7) ) {
			line = (byte) (i + 2 * toMove);
			col = j;
			// if both first and second squares are empty
			if ( table[line][col] == EMPTY_SQUARE && table[line - toMove][col] == EMPTY_SQUARE )
				v.add( new Move(table[i][j], i, j, line, col, table[line][col], ORDINARY_MOVE));
		}
		return v;
	}
	
	
	/** Generates valid moves for the knight at position (i,j) */
	ArrayList<Move> genKnightMoves(byte i, byte j) {
		ArrayList<Move> v = new ArrayList<Move>();
		byte line, col;
		byte stop, capture;
		
		// NE1 position
		line = i; col = j;
		stop = 0; capture = 0;
		if ( table[line + 2][col + 1] != OUT_OF_BOUNDS ) {
			line += 2; col++;
			if ( table[line][col] != EMPTY_SQUARE )
				if ( table[line][col] * toMove > 0 ) //if same color
					stop = 1;
				else
					capture = table[line][col];
			if ( stop != 1 )
				v.add( new Move(table[i][j], i, j, line, col, capture, ORDINARY_MOVE)); 
		}
		// NE2 position
		line = i; col = j;
		stop = 0; capture = 0;
		if ( table[line + 1][col + 2] != OUT_OF_BOUNDS ) {
			line++; col += 2;
			if ( table[line][col] != EMPTY_SQUARE )
				if ( table[line][col] * toMove > 0 ) //if same color
					stop = 1;
				else
					capture = table[line][col];
			if ( stop != 1 )
				v.add( new Move(table[i][j], i, j, line, col, capture, ORDINARY_MOVE)); 
		}
		// SE2 position
		line = i; col = j;
		stop = 0; capture = 0;
		if ( table[line - 1][col + 2] != OUT_OF_BOUNDS ) {
			line--; col += 2;
			if ( table[line][col] != EMPTY_SQUARE )
				if ( table[line][col] * toMove > 0 ) //if same color
					stop = 1;
				else
					capture = table[line][col];
			if ( stop != 1 )
				v.add( new Move(table[i][j], i, j, line, col, capture, ORDINARY_MOVE)); 
		}
		// SE1 position
		line = i; col = j;
		stop = 0; capture = 0;
		if ( table[line - 2][col + 1] != OUT_OF_BOUNDS ) {
			line -= 2; col += 1;
			if ( table[line][col] != EMPTY_SQUARE )
				if ( table[line][col] * toMove > 0 ) //if same color
					stop = 1;
				else
					capture = table[line][col];
			if ( stop != 1 )
				v.add( new Move(table[i][j], i, j, line, col, capture, ORDINARY_MOVE)); 
		}
		// SW1 position
		line = i; col = j;
		stop = 0; capture = 0;
		if ( table[line - 2][col - 1] != OUT_OF_BOUNDS ) {
			line -= 2; col--;
			if ( table[line][col] != EMPTY_SQUARE )
				if ( table[line][col] * toMove > 0 ) //if same color
					stop = 1;
				else
					capture = table[line][col];
			if ( stop != 1 )
				v.add( new Move(table[i][j], i, j, line, col, capture, ORDINARY_MOVE)); 
		}
		// SW2 position
		line = i; col = j;
		stop = 0; capture = 0;
		if ( table[line - 1][col - 2] != OUT_OF_BOUNDS ) {
			line--; col -= 2;
			if ( table[line][col] != EMPTY_SQUARE )
				if ( table[line][col] * toMove > 0 ) //if same color
					stop = 1;
				else
					capture = table[line][col];
			if ( stop != 1 )
				v.add( new Move(table[i][j], i, j, line, col, capture, ORDINARY_MOVE)); 
		}
		// NW2 position
		line = i; col = j;
		stop = 0; capture = 0;
		if ( table[line + 1][col - 2] != OUT_OF_BOUNDS ) {
			line++; col -= 2;
			if ( table[line][col] != EMPTY_SQUARE )
				if ( table[line][col] * toMove > 0 ) //if same color
					stop = 1;
				else
					capture = table[line][col];
			if ( stop != 1 )
				v.add( new Move(table[i][j], i, j, line, col, capture, ORDINARY_MOVE)); 
		}
		// NW1 position
		line = i; col = j;
		stop = 0; capture = 0;
		if ( table[line + 2][col - 1] != OUT_OF_BOUNDS ) {
			line += 2; col--;
			if ( table[line][col] != EMPTY_SQUARE )
				if ( table[line][col] * toMove > 0 ) //if same color
					stop = 1;
				else
					capture = table[line][col];
			if ( stop != 1 )
				v.add( new Move(table[i][j], i, j, line, col, capture, ORDINARY_MOVE)); 
		}
		return v;
	}
	
	
	/** Generates valid moves for the bishop at position (i,j) */
	ArrayList<Move> genBishopMoves(byte i, byte j) {
		ArrayList<Move> v = new ArrayList<Move>();
		byte line, col;
		byte stop, capture;
		
		// NE direction
		line = i; col = j;
		stop = 0; capture = 0;
		while ( table[line + 1][col + 1] != OUT_OF_BOUNDS ) {
			line++; col++;
			if ( table[line][col] != EMPTY_SQUARE ) {
				if ( table[line][col] * toMove > 0 ) //if same color
					break;
				else {
					stop = 1;
					capture = table[line][col];
				}
			}
			v.add( new Move(table[i][j], i, j, line, col, capture, ORDINARY_MOVE));
			if ( stop == 1 )
				break;
		}		
		// SE direction
		line = i; col = j;
		stop = 0; capture = 0;
		while ( table[line - 1][col + 1] != OUT_OF_BOUNDS ) {
			line--; col++;
			if ( table[line][col] != EMPTY_SQUARE ) {
				if ( table[line][col] * toMove > 0 ) //if same color
					break;
				else {
					stop = 1;
					capture = table[line][col];
				}
			}
			v.add( new Move(table[i][j], i, j, line, col, capture, ORDINARY_MOVE));
			if ( stop == 1 )
				break;
		}
		// SW direction
		line = i; col = j;
		stop = 0; capture = 0;
		while ( table[line - 1][col - 1] != OUT_OF_BOUNDS ) {
			line--; col--;
			if ( table[line][col] != EMPTY_SQUARE ) {
				if ( table[line][col] * toMove > 0 ) //if same color
					break;
				else {
					stop = 1;
					capture = table[line][col];
				}
			}
			v.add( new Move(table[i][j], i, j, line, col, capture, ORDINARY_MOVE));
			if ( stop == 1 )
				break;
		}
		// NW direction
		line = i; col = j;
		stop = 0; capture = 0;
		while ( table[line + 1][col - 1] != OUT_OF_BOUNDS ) {
			line++; col--;
			if ( table[line][col] != EMPTY_SQUARE ) {
				if ( table[line][col] * toMove > 0 ) //if same color
					break;
				else {
					stop = 1;
					capture = table[line][col];
				}
			}
			v.add( new Move(table[i][j], i, j, line, col, capture, ORDINARY_MOVE));
			if ( stop == 1 )
				break;
		}	
		return v;
	}
	
	
	/** Generates valid moves for the rook at position (i,j) */
	ArrayList<Move> genRookMoves(byte i, byte j) {
		ArrayList<Move> v = new ArrayList<Move>();
		byte line, col;
		byte stop, capture;
			
		// E direction
		line = i; col = j;
		stop = 0; capture = 0;
		while ( table[line][col + 1] != OUT_OF_BOUNDS ) {
			col++;
			if ( table[line][col] != EMPTY_SQUARE ) {
				if ( table[line][col] * toMove > 0 ) //if same color
					break;
				else {
					stop = 1;
					capture = table[line][col];
				}
			}
			v.add( new Move(table[i][j], i, j, line, col, capture, ORDINARY_MOVE));
			if ( stop == 1 )
				break;
		}
		// S direction
		line = i; col = j;
		stop = 0; capture = 0;
		while ( table[line - 1][col] != OUT_OF_BOUNDS ) {
			line--;
			if ( table[line][col] != EMPTY_SQUARE ) {
				if ( table[line][col] * toMove > 0 ) //if same color
					break;
				else {
					stop = 1;
					capture = table[line][col];
				}
			}
			v.add( new Move(table[i][j], i, j, line, col, capture, ORDINARY_MOVE));
			if ( stop == 1 )
				break;
		}
		// W direction
		line = i; col = j;
		stop = 0; capture = 0;
		while ( table[line][col - 1] != OUT_OF_BOUNDS ) {
			col--;
			if ( table[line][col] != EMPTY_SQUARE ) {
				if ( table[line][col] * toMove > 0 ) //if same color
					break;
				else {
					stop = 1;
					capture = table[line][col];
				}
			}
			v.add( new Move(table[i][j], i, j, line, col, capture, ORDINARY_MOVE));
			if ( stop == 1 )
				break;
		}		
		// N direction
		line = i; col = j;
		stop = 0; capture = 0;
		while ( table[line + 1][col] != OUT_OF_BOUNDS ) {
			line++;
			if ( table[line][col] != EMPTY_SQUARE ) {
				if ( table[line][col] * toMove > 0 ) //if same colour
					break;
				else {
					stop = 1;
					capture = table[line][col];
				}
			}
			v.add( new Move(table[i][j], i, j, line, col, capture, ORDINARY_MOVE));
			if ( stop == 1 )
				break;
		}	
		return v;
	}
	
	
	/** Generates valid moves for the queen at position (i,j) */
	ArrayList<Move> genQueenMoves(byte i, byte j) {
		ArrayList<Move> v = new ArrayList<Move>();
		byte line, col;
		byte stop, capture;

		// NE direction
		line = i; col = j;
		stop = 0; capture = 0;
		while ( table[line + 1][col + 1] != OUT_OF_BOUNDS ) {
			line++; col++;
			if ( table[line][col] != EMPTY_SQUARE ) {
				if ( table[line][col] * toMove > 0 ) //if same color
					break;
				else {
					stop = 1;
					capture = table[line][col];
				}
			}
			v.add( new Move(table[i][j], i, j, line, col, capture, ORDINARY_MOVE));
			if ( stop == 1 )
				break;
		}		
		// E direction
		line = i; col = j;
		stop = 0; capture = 0;
		while ( table[line][col + 1] != OUT_OF_BOUNDS ) {
			col++;
			if ( table[line][col] != EMPTY_SQUARE ) {
				if ( table[line][col] * toMove > 0 ) //if same color
					break;
				else {
					stop = 1;
					capture = table[line][col];
				}
			}
			v.add( new Move(table[i][j], i, j, line, col, capture, ORDINARY_MOVE));
			if ( stop == 1 )
				break;
		}
		// SE direction
		line = i; col = j;
		stop = 0; capture = 0;
		while ( table[line - 1][col + 1] != OUT_OF_BOUNDS ) {
			line--; col++;
			if ( table[line][col] != EMPTY_SQUARE ) {
				if ( table[line][col] * toMove > 0 ) //if same color
					break;
				else {
					stop = 1;
					capture = table[line][col];
				}
			}
			v.add( new Move(table[i][j], i, j, line, col, capture, ORDINARY_MOVE));
			if ( stop == 1 )
				break;
		}

		// S direction
		line = i; col = j;
		stop = 0; capture = 0;
		while ( table[line - 1][col] != OUT_OF_BOUNDS ) {
			line--;
			if ( table[line][col] != EMPTY_SQUARE ) {
				if ( table[line][col] * toMove > 0 ) //if same color
					break;
				else {
					stop = 1;
					capture = table[line][col];
				}
			}
			v.add( new Move(table[i][j], i, j, line, col, capture, ORDINARY_MOVE));
			if ( stop == 1 )
				break;
		}
		// SW direction
		line = i; col = j;
		stop = 0; capture = 0;
		while ( table[line - 1][col - 1] != OUT_OF_BOUNDS ) {
			line--; col--;
			if ( table[line][col] != EMPTY_SQUARE ) {
				if ( table[line][col] * toMove > 0 ) //if same color
					break;
				else {
					stop = 1;
					capture = table[line][col];
				}
			}
			v.add( new Move(table[i][j], i, j, line, col, capture, ORDINARY_MOVE));
			if ( stop == 1 )
				break;
		}
		// W direction
		line = i; col = j;
		stop = 0; capture = 0;
		while ( table[line][col - 1] != OUT_OF_BOUNDS ) {
			col--;
			if ( table[line][col] != EMPTY_SQUARE ) {
				if ( table[line][col] * toMove > 0 ) //if same color
					break;
				else {
					stop = 1;
					capture = table[line][col];
				}
			}
			v.add( new Move(table[i][j], i, j, line, col, capture, ORDINARY_MOVE));
			if ( stop == 1 )
				break;
		}		
		// NW direction
		line = i; col = j;
		stop = 0; capture = 0;
		while ( table[line + 1][col - 1] != OUT_OF_BOUNDS ) {
			line++; col--;
			if ( table[line][col] != EMPTY_SQUARE ) {
				if ( table[line][col] * toMove > 0 ) //if same color
					break;
				else {
					stop = 1;
					capture = table[line][col];
				}
			}
			v.add( new Move(table[i][j], i, j, line, col, capture, ORDINARY_MOVE));
			if ( stop == 1 )
				break;
		}
		// N direction
		line = i; col = j;
		stop = 0; capture = 0;
		while ( table[line + 1][col] != OUT_OF_BOUNDS ) {
			line++;
			if ( table[line][col] != EMPTY_SQUARE ) {
				if ( table[line][col] * toMove > 0 ) //if same colour
					break;
				else {
					stop = 1;
					capture = table[line][col];
				}
			}
			v.add( new Move(table[i][j], i, j, line, col, capture, ORDINARY_MOVE));
			if ( stop == 1 )
				break;
		}	
		return v;
	}
	
	
	/** Generates valid moves for the king at position (i,j) */
	ArrayList<Move> genKingMoves(byte i, byte j) {
		ArrayList<Move> v = new ArrayList<Move>();
		byte line, col;
		byte stop, capture;
		
		// NE position
		line = i; col = j;
		stop = 0; capture = 0;
		if ( table[line + 1][col + 1] != OUT_OF_BOUNDS ){
			line++; col++;
			if ( table[line][col] != EMPTY_SQUARE )
				if ( table[line][col] * toMove > 0 ) //if same color
					stop = 1;
				else
					capture = table[line][col];
			if ( stop != 1 )
				v.add( new Move(table[i][j], i, j, line, col, capture, ORDINARY_MOVE)); 
		}
		// E position
		line = i; col = j;
		stop = 0; capture = 0;
		if ( table[line][col + 1] != OUT_OF_BOUNDS ){
			col++;
			if ( table[line][col] != EMPTY_SQUARE )
				if ( table[line][col] * toMove > 0 ) //if same color
					stop = 1;
				else
					capture = table[line][col];
			if ( stop != 1 )
				v.add( new Move(table[i][j], i, j, line, col, capture, ORDINARY_MOVE)); 
		}
		// SE position
		line = i; col = j;
		stop = 0; capture = 0;
		if ( table[line - 1][col + 1] != OUT_OF_BOUNDS ){
			line--; col++;
			if ( table[line][col] != EMPTY_SQUARE )
				if ( table[line][col] * toMove > 0 ) //if same color
					stop = 1;
				else
					capture = table[line][col];
			if ( stop != 1 )
				v.add( new Move(table[i][j], i, j, line, col, capture, ORDINARY_MOVE)); 
		}
		// S position
		line = i; col = j;
		stop = 0; capture = 0;
		if ( table[line - 1][col] != OUT_OF_BOUNDS ){
			line--;
			if ( table[line][col] != EMPTY_SQUARE )
				if ( table[line][col] * toMove > 0 ) //if same color
					stop = 1;
				else
					capture = table[line][col];
			if ( stop != 1 )
				v.add( new Move(table[i][j], i, j, line, col, capture, ORDINARY_MOVE)); 
		}
		// SW position
		line = i; col = j;
		stop = 0; capture = 0;
		if ( table[line - 1][col - 1] != OUT_OF_BOUNDS ){
			line--; col--;
			if ( table[line][col] != EMPTY_SQUARE )
				if ( table[line][col] * toMove > 0 ) //if same color
					stop = 1;
				else
					capture = table[line][col];
			if ( stop != 1 )
				v.add( new Move(table[i][j], i, j, line, col, capture, ORDINARY_MOVE)); 
		}
		// W position
		line = i; col = j;
		stop = 0; capture = 0;
		if ( table[line][col - 1] != OUT_OF_BOUNDS ){
			col--;
			if ( table[line][col] != EMPTY_SQUARE )
				if ( table[line][col] * toMove > 0 ) //if same color
					stop = 1;
				else
					capture = table[line][col];
			if ( stop != 1 )
				v.add( new Move(table[i][j], i, j, line, col, capture, ORDINARY_MOVE)); 
		}
		// NW position
		line = i; col = j;
		stop = 0; capture = 0;
		if ( table[line + 1][col - 1] != OUT_OF_BOUNDS ){
			line++; col--;
			if ( table[line][col] != EMPTY_SQUARE )
				if ( table[line][col] * toMove > 0 ) //if same color
					stop = 1;
				else
					capture = table[line][col];
			if ( stop != 1 )
				v.add( new Move(table[i][j], i, j, line, col, capture, ORDINARY_MOVE)); 
		}
		// N position
		line = i; col = j;
		stop = 0; capture = 0;
		if ( table[line + 1][col] != OUT_OF_BOUNDS ){
			line++;
			if ( table[line][col] != EMPTY_SQUARE )
				if ( table[line][col] * toMove > 0 ) //if same color
					stop = 1;
				else
					capture = table[line][col];
			if ( stop != 1 )
				v.add( new Move(table[i][j], i, j, line, col, capture, ORDINARY_MOVE)); 
		}
		return v;
	}
	
	
	/** Generates valid castle moves */
	ArrayList<Move> genCastleMoves() {
		ArrayList<Move> v = new ArrayList<Move>();
		
		if (toMove == WHITE) {
			if ( whiteCastle == CASTLE_SHORT || whiteCastle == CASTLE_BOTH )
				if (table[LINE1][COLF] == EMPTY_SQUARE && table[LINE1][COLG] == EMPTY_SQUARE)
					v.add(new Move(WHITE_KING, LINE1, COLE,	LINE1, COLG, EMPTY_SQUARE, SHORT_CASTLE));
			if ( whiteCastle == CASTLE_LONG || whiteCastle == CASTLE_BOTH)
				if (table[LINE1][COLD] == EMPTY_SQUARE && table[LINE1][COLC] == EMPTY_SQUARE
						&& table[LINE1][COLB] == EMPTY_SQUARE)
					v.add(new Move(WHITE_KING, LINE1, COLE,	LINE1, COLC, EMPTY_SQUARE, LONG_CASTLE));
		}
		else {
			if ( blackCastle == CASTLE_SHORT || blackCastle == CASTLE_BOTH )
				if (table[LINE8][COLF] == EMPTY_SQUARE && table[LINE8][COLG] == EMPTY_SQUARE)
					v.add(new Move(BLACK_KING, LINE8, COLE, LINE8, COLG, EMPTY_SQUARE, SHORT_CASTLE));
			if ( blackCastle == CASTLE_LONG || blackCastle == CASTLE_BOTH )
				if (table[LINE8][COLD] == EMPTY_SQUARE && table[LINE8][COLC] == EMPTY_SQUARE
						&& table[LINE8][COLB] == EMPTY_SQUARE)
					v.add(new Move(BLACK_KING, LINE8, COLE,	LINE8, COLC, EMPTY_SQUARE, LONG_CASTLE));
		}
		return v;
	}
	
	
	/** Generates valid en passant moves if available */
	ArrayList<Move> genEnPassantMoves() {
		ArrayList<Move> v = new ArrayList<Move>();
		byte line, col;  

		if (enPassantLine != -1 && enPassantCol != -1) {
			line = enPassantLine;
			col = enPassantCol;
			if ( table[line][col + 1] == PAWN * toMove )
				v.add(new Move( table[line][col + 1], line, (byte)(col + 1), 
						(byte)(line + toMove), col, table[line][col], EN_PASSANT));
			if ( table[line][col - 1] == PAWN * toMove )	
				v.add(new Move( table[line][col - 1], line, (byte)(col - 1), 
						(byte)(line + toMove), col, table[line][col], EN_PASSANT));
		}
		return v;
	}
	
	
	/** Generates valid promotion moves for the pawn at position (i,j) */
	ArrayList<Move> genPromotionMoves(byte i, byte j) {
		ArrayList<Move> v = new ArrayList<Move>();
		byte line, col;
		
		// check if promotion move
		if ( ( i + toMove ) != LINE8 && ( i + toMove ) != LINE1 )
			return v;
		
		// test if pawn can move one position forward
		line = (byte) (i + toMove);
		col = j;
		if ( table[line][col] == EMPTY_SQUARE ) {
			// add all 4 possible promotions
			v.add( new Move(table[i][j], i, j, line, col, table[line][col], PROMOTION_QUEEN));
			v.add( new Move(table[i][j], i, j, line, col, table[line][col], PROMOTION_ROOK));
			v.add( new Move(table[i][j], i, j, line, col, table[line][col], PROMOTION_BISHOP));
			v.add( new Move(table[i][j], i, j, line, col, table[line][col], PROMOTION_KNIGHT));
		}
		
		// check for diagonal promotion (with capture)
		// NE direction
		line = (byte) (i + toMove);
		col = (byte) (j + 1);
		if ( table[line][col] != OUT_OF_BOUNDS )
			//if opposite color
			if ( table[line][col] * toMove < 0 ) {
				v.add( new Move(table[i][j], i, j, line, col, table[line][col], PROMOTION_QUEEN));
				v.add( new Move(table[i][j], i, j, line, col, table[line][col], PROMOTION_ROOK));
				v.add( new Move(table[i][j], i, j, line, col, table[line][col], PROMOTION_BISHOP));
				v.add( new Move(table[i][j], i, j, line, col, table[line][col], PROMOTION_KNIGHT));
			}
						
		// NW direction
		line = (byte) (i + toMove);
		col = (byte) (j - 1);
		if ( table[line][col] != OUT_OF_BOUNDS )
			// if opposite color
			if ( table[line][col] * toMove < 0 ) {
				v.add( new Move(table[i][j], i, j, line, col, table[line][col], PROMOTION_QUEEN));
				v.add( new Move(table[i][j], i, j, line, col, table[line][col], PROMOTION_ROOK));
				v.add( new Move(table[i][j], i, j, line, col, table[line][col], PROMOTION_BISHOP));
				v.add( new Move(table[i][j], i, j, line, col, table[line][col], PROMOTION_KNIGHT));
			}
		
		return v;
	}

	
	
	/**
	 * Generates all possible valid moves for the current side (might not be legal)
	 * 
	 * @return a list of valid moves
	 * 
	 */
	public ArrayList<Move> generateMoves() {
		byte i, j;
		// optimize array initial capacity
		ArrayList<Move> validMoves = new ArrayList<Move>(50);
		
		if (toMove == WHITE) {
			for (i = LINE1; i <= LINE8; ++i)
				for (j = COLA; j <= COLH; ++j)
					if ( table[i][j] <= 0 )
						continue;
					else
						switch (table[i][j]) {
							case WHITE_PAWN: {
								validMoves.addAll(genPawnMoves(i,j));
								validMoves.addAll(genPromotionMoves(i,j));
								break;
							}
							case WHITE_KNIGHT: validMoves.addAll(genKnightMoves(i,j)); break;
							case WHITE_BISHOP: validMoves.addAll(genBishopMoves(i,j)); break;
							case WHITE_ROOK: validMoves.addAll(genRookMoves(i,j)); break;
							case WHITE_QUEEN: validMoves.addAll(genQueenMoves(i,j)); break;
							case WHITE_KING: validMoves.addAll(genKingMoves(i,j)); break;
						}
		}
		else {
			for (i = LINE1; i <= LINE8; ++i)
				for (j = COLA; j <= COLH; ++j)
					if ( table[i][j] >= 0 )
						continue;
					else
						switch (table[i][j]) {
							case BLACK_PAWN: {
								validMoves.addAll(genPawnMoves(i,j));
								validMoves.addAll(genPromotionMoves(i,j));
								break;
							}
							case BLACK_KNIGHT: validMoves.addAll(genKnightMoves(i,j)); break;
							case BLACK_BISHOP: validMoves.addAll(genBishopMoves(i,j)); break;
							case BLACK_ROOK: validMoves.addAll(genRookMoves(i,j)); break;
							case BLACK_QUEEN: validMoves.addAll(genQueenMoves(i,j)); break;
							case BLACK_KING: validMoves.addAll(genKingMoves(i,j)); break;
						}
		}
		validMoves.addAll(genEnPassantMoves());
		validMoves.addAll(genCastleMoves());
		
		return validMoves;
	}
	
	
	
	
	/** Generates valid captures for the pawn at position (i,j) */
	ArrayList<Move> genPawnCaptures(byte i, byte j) {
		ArrayList<Move> v = new ArrayList<Move>();
		byte line, col;
		
		// pawn cannot get to the last rank; promotion moves will be treated separately
		if ( ( i + toMove ) == LINE8 || ( i + toMove ) == LINE1 ) 
			return v;
		
		// check for captures
		// NE direction
		line = (byte) (i + toMove);
		col = (byte) (j + 1);
		if ( table[line][col] != OUT_OF_BOUNDS )
			//if opposite color
			if ( table[line][col] * toMove < 0 )
				v.add( new Move(table[i][j], i, j, line, col, table[line][col], ORDINARY_MOVE));		
		// NW direction
		line = (byte) (i + toMove);
		col = (byte) (j - 1);
		if ( table[line][col] != OUT_OF_BOUNDS )
			// if opposite color
			if ( table[line][col] * toMove < 0 )
				v.add( new Move(table[i][j], i, j, line, col, table[line][col], ORDINARY_MOVE));
		
		return v;
	}
	
	
	/** Generates valid captures for the knight at position (i,j) */
	ArrayList<Move> genKnightCaptures(byte i, byte j) {
		ArrayList<Move> v = new ArrayList<Move>();
		byte line, col;
		
		// NE1 position
		line = i; col = j;
		if ( table[line + 2][col + 1] != OUT_OF_BOUNDS ) {
			line += 2; col++;
			if ( table[line][col] * toMove < 0 ) // if opposite color
				v.add( new Move(table[i][j], i, j, line, col, table[line][col], ORDINARY_MOVE)); 
		}
		// NE2 position
		line = i; col = j;
		if ( table[line + 1][col + 2] != OUT_OF_BOUNDS ) {
			line++; col += 2;
			if ( table[line][col] * toMove < 0 ) // if opposite color
				v.add( new Move(table[i][j], i, j, line, col, table[line][col], ORDINARY_MOVE)); 
		}
		// SE2 position
		line = i; col = j;
		if ( table[line - 1][col + 2] != OUT_OF_BOUNDS ) {
			line--; col += 2;
			if ( table[line][col] * toMove < 0 ) // if opposite color
				v.add( new Move(table[i][j], i, j, line, col, table[line][col], ORDINARY_MOVE)); 
		}
		// SE1 position
		line = i; col = j;
		if ( table[line - 2][col + 1] != OUT_OF_BOUNDS ) {
			line -= 2; col += 1;
			if ( table[line][col] * toMove < 0 ) // if opposite color
				v.add( new Move(table[i][j], i, j, line, col, table[line][col], ORDINARY_MOVE)); 
		}
		// SW1 position
		line = i; col = j;
		if ( table[line - 2][col - 1] != OUT_OF_BOUNDS ) {
			line -= 2; col--;
			if ( table[line][col] * toMove < 0 ) //if opposite color
				v.add( new Move(table[i][j], i, j, line, col, table[line][col], ORDINARY_MOVE)); 
		}
		// SW2 position
		line = i; col = j;
		if ( table[line - 1][col - 2] != OUT_OF_BOUNDS ) {
			line--; col -= 2;
			if ( table[line][col] * toMove < 0 ) // if opposite color
				v.add( new Move(table[i][j], i, j, line, col, table[line][col], ORDINARY_MOVE)); 
		}
		// NW2 position
		line = i; col = j;
		if ( table[line + 1][col - 2] != OUT_OF_BOUNDS ) {
			line++; col -= 2;
			if ( table[line][col] * toMove < 0 ) // if opposite color
				v.add( new Move(table[i][j], i, j, line, col, table[line][col], ORDINARY_MOVE)); 
		}
		// NW1 position
		line = i; col = j;
		if ( table[line + 2][col - 1] != OUT_OF_BOUNDS ) {
			line += 2; col--;
			if ( table[line][col] * toMove < 0 ) // if opposite color
				v.add( new Move(table[i][j], i, j, line, col, table[line][col], ORDINARY_MOVE)); 
		}
		return v;
	}
	
	
	/** Generates valid captures for the bishop at position (i,j) */
	ArrayList<Move> genBishopCaptures(byte i, byte j) {
		ArrayList<Move> v = new ArrayList<Move>();
		byte line, col;
		
		// NE direction
		line = i; col = j;
		while ( table[line + 1][col + 1] != OUT_OF_BOUNDS ) {
			line++; col++;
			if ( table[line][col] != EMPTY_SQUARE ) {
				if ( table[line][col] * toMove < 0 ) // if opposite color
					v.add( new Move(table[i][j], i, j, line, col, table[line][col], ORDINARY_MOVE));
				break;
			}
		}		
		// SE direction
		line = i; col = j;
		while ( table[line - 1][col + 1] != OUT_OF_BOUNDS ) {
			line--; col++;
			if ( table[line][col] != EMPTY_SQUARE ) {
				if ( table[line][col] * toMove < 0 ) // if opposite color
					v.add( new Move(table[i][j], i, j, line, col, table[line][col], ORDINARY_MOVE));
				break;
			}
		}
		// SW direction
		line = i; col = j;
		while ( table[line - 1][col - 1] != OUT_OF_BOUNDS ) {
			line--; col--;
			if ( table[line][col] != EMPTY_SQUARE ) {
				if ( table[line][col] * toMove < 0 ) // if opposite color
					v.add( new Move(table[i][j], i, j, line, col, table[line][col], ORDINARY_MOVE));
				break;
			}
		}
		// NW direction
		line = i; col = j;
		while ( table[line + 1][col - 1] != OUT_OF_BOUNDS ) {
			line++; col--;
			if ( table[line][col] != EMPTY_SQUARE ) {
				if ( table[line][col] * toMove < 0 ) // if opposite color
					v.add( new Move(table[i][j], i, j, line, col, table[line][col], ORDINARY_MOVE));
				break;
			}
		}
		return v;
	}
	
	
	/** Generates valid captures for the rook at position (i,j) */
	ArrayList<Move> genRookCaptures(byte i, byte j) {
		ArrayList<Move> v = new ArrayList<Move>();
		byte line, col;
			
		// E direction
		line = i; col = j;
		while ( table[line][col + 1] != OUT_OF_BOUNDS ) {
			col++;
			if ( table[line][col] != EMPTY_SQUARE ) {
				if ( table[line][col] * toMove < 0 ) // if opposite colour
					v.add( new Move(table[i][j], i, j, line, col, table[line][col], ORDINARY_MOVE));
				break;
			}
		}
		// S direction
		line = i; col = j;
		while ( table[line - 1][col] != OUT_OF_BOUNDS ) {
			line--;
			if ( table[line][col] != EMPTY_SQUARE ) {
				if ( table[line][col] * toMove < 0 ) // if opposite colour
					v.add( new Move(table[i][j], i, j, line, col, table[line][col], ORDINARY_MOVE));
				break;
			}
		}
		// W direction
		line = i; col = j;
		while ( table[line][col - 1] != OUT_OF_BOUNDS ) {
			col--;
			if ( table[line][col] != EMPTY_SQUARE ) {
				if ( table[line][col] * toMove < 0 ) // if opposite colour
					v.add( new Move(table[i][j], i, j, line, col, table[line][col], ORDINARY_MOVE));
				break;
			}
		}

		// N direction
		line = i; col = j;
		while ( table[line + 1][col] != OUT_OF_BOUNDS ) {
			line++;
			if ( table[line][col] != EMPTY_SQUARE ) {
				if ( table[line][col] * toMove < 0 ) // if opposite colour
					v.add( new Move(table[i][j], i, j, line, col, table[line][col], ORDINARY_MOVE));
				break;
			}
		}
		return v;
	}
	
	
	/** Generates valid captures for the queen at position (i,j) */
	ArrayList<Move> genQueenCaptures(byte i, byte j) {
		ArrayList<Move> v = new ArrayList<Move>();
		byte line, col;

		// NE direction
		line = i; col = j;
		while ( table[line + 1][col + 1] != OUT_OF_BOUNDS ) {
			line++; col++;
			if ( table[line][col] != EMPTY_SQUARE ) {
				if ( table[line][col] * toMove < 0 ) // if opposite color
					v.add( new Move(table[i][j], i, j, line, col, table[line][col], ORDINARY_MOVE));
				break;
			}
		}		
		// E direction
		line = i; col = j;
		while ( table[line][col + 1] != OUT_OF_BOUNDS ) {
			col++;
			if ( table[line][col] != EMPTY_SQUARE ) {
				if ( table[line][col] * toMove < 0 ) // if opposite color
					v.add( new Move(table[i][j], i, j, line, col, table[line][col], ORDINARY_MOVE));
				break;
			}
		}
		// SE direction
		line = i; col = j;
		while ( table[line - 1][col + 1] != OUT_OF_BOUNDS ) {
			line--; col++;
			if ( table[line][col] != EMPTY_SQUARE ) {
				if ( table[line][col] * toMove < 0 ) // if opposite color
					v.add( new Move(table[i][j], i, j, line, col, table[line][col], ORDINARY_MOVE));
				break;
			}
		}
		// S direction
		line = i; col = j;
		while ( table[line - 1][col] != OUT_OF_BOUNDS ) {
			line--;
			if ( table[line][col] != EMPTY_SQUARE ) {
				if ( table[line][col] * toMove < 0 ) // if opposite color
					v.add( new Move(table[i][j], i, j, line, col, table[line][col], ORDINARY_MOVE));
				break;
			}
		}
		// SW direction
		line = i; col = j;
		while ( table[line - 1][col - 1] != OUT_OF_BOUNDS ) {
			line--; col--;
			if ( table[line][col] != EMPTY_SQUARE ) {
				if ( table[line][col] * toMove < 0 ) // if opposite color
					v.add( new Move(table[i][j], i, j, line, col, table[line][col], ORDINARY_MOVE));
				break;
			}
		}
		// W direction
		line = i; col = j;
		while ( table[line][col - 1] != OUT_OF_BOUNDS ) {
			col--;
			if ( table[line][col] != EMPTY_SQUARE ) {
				if ( table[line][col] * toMove < 0 ) // if opposite color
					v.add( new Move(table[i][j], i, j, line, col, table[line][col], ORDINARY_MOVE));
				break;
			}
		}		
		// NW direction
		line = i; col = j;
		while ( table[line + 1][col - 1] != OUT_OF_BOUNDS ) {
			line++; col--;
			if ( table[line][col] != EMPTY_SQUARE ) {
				if ( table[line][col] * toMove < 0 ) // if opposite color
					v.add( new Move(table[i][j], i, j, line, col, table[line][col], ORDINARY_MOVE));
				break;
			}
		}
		// N direction
		line = i; col = j;
		while ( table[line + 1][col] != OUT_OF_BOUNDS ) {
			line++;
			if ( table[line][col] != EMPTY_SQUARE ) {
				if ( table[line][col] * toMove < 0 ) // if opposite color
					v.add( new Move(table[i][j], i, j, line, col, table[line][col], ORDINARY_MOVE));
				break;
			}
		}	
		return v;
	}
	
	
	/** Generates valid captures for the king at position (i,j) */
	ArrayList<Move> genKingCaptures(byte i, byte j) {
		ArrayList<Move> v = new ArrayList<Move>();
		byte line, col;
		
		// NE position
		line = i; col = j;
		if ( table[line + 1][col + 1] != OUT_OF_BOUNDS ){
			line++; col++;
			if ( table[line][col] * toMove < 0 ) // if opposite color
				v.add( new Move(table[i][j], i, j, line, col, table[line][col], ORDINARY_MOVE)); 
		}
		// E position
		line = i; col = j;
		if ( table[line][col + 1] != OUT_OF_BOUNDS ){
			col++;
			if ( table[line][col] * toMove < 0 ) // if opposite color
				v.add( new Move(table[i][j], i, j, line, col, table[line][col], ORDINARY_MOVE)); 
		}
		// SE position
		line = i; col = j;
		if ( table[line - 1][col + 1] != OUT_OF_BOUNDS ){
			line--; col++;
			if ( table[line][col] * toMove < 0 ) // if opposite color
				v.add( new Move(table[i][j], i, j, line, col, table[line][col], ORDINARY_MOVE)); 
		}
		// S position
		line = i; col = j;
		if ( table[line - 1][col] != OUT_OF_BOUNDS ){
			line--;
			if ( table[line][col] * toMove < 0 ) // if opposite color
				v.add( new Move(table[i][j], i, j, line, col, table[line][col], ORDINARY_MOVE)); 
		}
		// SW position
		line = i; col = j;
		if ( table[line - 1][col - 1] != OUT_OF_BOUNDS ){
			line--; col--;
			if ( table[line][col] * toMove < 0 ) // if opposite color
				v.add( new Move(table[i][j], i, j, line, col, table[line][col], ORDINARY_MOVE)); 
		}
		// W position
		line = i; col = j;
		if ( table[line][col - 1] != OUT_OF_BOUNDS ){
			col--;
			if ( table[line][col] * toMove < 0 ) // if opposite color
				v.add( new Move(table[i][j], i, j, line, col, table[line][col], ORDINARY_MOVE)); 
		}
		// NW position
		line = i; col = j;		
		if ( table[line + 1][col - 1] != OUT_OF_BOUNDS ){
			line++; col--;
			if ( table[line][col] * toMove < 0 ) // if opposite color
				v.add( new Move(table[i][j], i, j, line, col, table[line][col], ORDINARY_MOVE)); 
		}
		// N position
		line = i; col = j;
		if ( table[line + 1][col] != OUT_OF_BOUNDS ){
			line++;
			if ( table[line][col] * toMove < 0 ) // if opposite color
				v.add( new Move(table[i][j], i, j, line, col, table[line][col], ORDINARY_MOVE)); 
		}
		return v;
	}
	
	
	/** Generates quiescent promotion moves for the pawn at position (i,j) */
	/** Only promotions to queen (normal or by capture) are generated */
	ArrayList<Move> genQuisPromotions(byte i, byte j) {
		ArrayList<Move> v = new ArrayList<Move>();
		byte line, col;
		
		// check if promotion move
		if ( ( i + toMove ) != LINE8 && ( i + toMove ) != LINE1 )
			return v;
		
		// test if pawn can move one position forward
		line = (byte) (i + toMove);
		col = j;
		if ( table[line][col] == EMPTY_SQUARE )
			v.add( new Move(table[i][j], i, j, line, col, table[line][col], PROMOTION_QUEEN));
		
		// check for diagonal promotion (with capture)
		// NE direction
		line = (byte) (i + toMove);
		col = (byte) (j + 1);
		if ( table[line][col] != OUT_OF_BOUNDS )
			//if opposite color
			if ( table[line][col] * toMove < 0 )
				v.add( new Move(table[i][j], i, j, line, col, table[line][col], PROMOTION_QUEEN));
						
		// NW direction
		line = (byte) (i + toMove);
		col = (byte) (j - 1);
		if ( table[line][col] != OUT_OF_BOUNDS )
			// if opposite color
			if ( table[line][col] * toMove < 0 )
				v.add( new Move(table[i][j], i, j, line, col, table[line][col], PROMOTION_QUEEN));

		return v;
	}
	
	
	/**
	 * Generates all possible valid captures for the current side (might not be legal)
	 * Used in quiescence search to improve speed
	 * 
	 * @return a list of valid captures
	 * 
	 */
	public ArrayList<Move> generateCaptures() {
		byte i, j;
		// optimize array initial capacity
		ArrayList<Move> validCaptures = new ArrayList<Move>(50);
		
		if ( toMove == WHITE ) {
			for (i = LINE1; i <= LINE8; ++i)
				for (j = COLA; j <= COLH; ++j)
					if ( table[i][j] <= 0 )
						continue;
					else
						switch (table[i][j]) {
							case WHITE_PAWN: {
								validCaptures.addAll(genPawnCaptures(i,j));
								validCaptures.addAll(genQuisPromotions(i,j));
								break;
							}
							case WHITE_KNIGHT: validCaptures.addAll(genKnightCaptures(i,j)); break;
							case WHITE_BISHOP: validCaptures.addAll(genBishopCaptures(i,j)); break;
							case WHITE_ROOK: validCaptures.addAll(genRookCaptures(i,j)); break;
							case WHITE_QUEEN: validCaptures.addAll(genQueenCaptures(i,j)); break;
							case WHITE_KING: validCaptures.addAll(genKingCaptures(i,j)); break;
						}
		}
		else {
			for (i = LINE1; i <= LINE8; ++i)
				for (j = COLA; j <= COLH; ++j)
					if ( table[i][j] >= 0 )
						continue;
					else
						switch (table[i][j]) {
							case BLACK_PAWN: {
								validCaptures.addAll(genPawnCaptures(i,j));
								validCaptures.addAll(genQuisPromotions(i,j));
								break;
							}
							case BLACK_KNIGHT: validCaptures.addAll(genKnightCaptures(i,j)); break;
							case BLACK_BISHOP: validCaptures.addAll(genBishopCaptures(i,j)); break;
							case BLACK_ROOK: validCaptures.addAll(genRookCaptures(i,j)); break;
							case BLACK_QUEEN: validCaptures.addAll(genQueenCaptures(i,j)); break;
							case BLACK_KING: validCaptures.addAll(genKingCaptures(i,j)); break;
						}
		}
		validCaptures.addAll(genEnPassantMoves());
		
		return validCaptures;
	}
	
	
	
	/**
	 * Generates all possible valid moves for the current side (might not be legal)
	 * NOT USED
	 * 
	 * @return a list of valid non capture moves
	 * 
	 */
	public ArrayList<Move> generateNonCaptures() {
		byte i, j;
		// optimize array initial capacity
		ArrayList<Move> validMoves = new ArrayList<Move>(50);
		
		for (i = LINE1; i <= LINE8; ++i)
			for (j = COLA; j <= COLH; ++j)
				if ( table[i][j] != EMPTY_SQUARE )
					switch (table[i][j] * toMove) {
						case PAWN: {
							validMoves.addAll(genPawnMoves(i,j));
							validMoves.addAll(genPromotionMoves(i,j));
							break;
						}
						case KNIGHT: validMoves.addAll(genKnightMoves(i,j)); break;
						case BISHOP: validMoves.addAll(genBishopMoves(i,j)); break;
						case ROOK: validMoves.addAll(genRookMoves(i,j)); break;
						case QUEEN: validMoves.addAll(genQueenMoves(i,j)); break;
						case KING: validMoves.addAll(genKingMoves(i,j)); break;
					}
		validMoves.addAll(genEnPassantMoves());
		validMoves.addAll(genCastleMoves());

		return validMoves;
	}
	
	
	
	/*********************************/
	/** END Move generation methods **/
	/*********************************/
	
	
	
	/**
	 *  Tests whether a square on the board is attacked.
	 *
	 *	@param byte line on the board.
	 * 	@param byte column on the board.
	 *  @param byte color of the attacker (white or black)
	 *  @return True if attacked, false otherwise.
	 *  
	 */
	public boolean isAttacked(byte i, byte j, byte attacker) {
		byte line, col;
		
		// Search for possible attacking pieces in all 8 directions
		
		// NE direction; possible attackers: Queen, Bishop
		line = i; col = j;
		while ( true ) {
			line++; col++;
			if ( table[line][col] == OUT_OF_BOUNDS )
				break;
			if ( table[line][col] != EMPTY_SQUARE )
				if ( (table[line][col] * attacker == QUEEN) || (table[line][col] * attacker == BISHOP) )
					return true;
				else
					break;
		}		
		// SE direction; possible attackers: Queen, Bishop
		line = i; col = j;
		while ( true ) {
			line--; col++;
			if ( table[line][col] == OUT_OF_BOUNDS )
				break;
			if ( table[line][col] != EMPTY_SQUARE )
				if ( (table[line][col] * attacker == QUEEN) || (table[line][col] * attacker == BISHOP) )
					return true;
				else
					break;
		}
		// SW direction; possible attackers: Queen, Bishop
		line = i; col = j;
		while ( true ) {
			line--; col--;
			if ( table[line][col] == OUT_OF_BOUNDS )
				break;
			if ( table[line][col] != EMPTY_SQUARE )
				if ( (table[line][col] * attacker == QUEEN) || (table[line][col] * attacker == BISHOP) )
					return true;
				else
					break;
		}
		// NW direction; possible attackers: Queen, Bishop
		line = i; col = j;
		while ( true ) {
			line++; col--;
			if ( table[line][col] == OUT_OF_BOUNDS )
				break;
			if ( table[line][col] != EMPTY_SQUARE )
				if ( (table[line][col] * attacker == QUEEN) || (table[line][col] * attacker == BISHOP) )
					return true;
				else
					break;
		}
		
		// N direction; possible attackers: Queen, Rook
		line = i; col = j;
		while ( true ) {
			line++;
			if ( table[line][col] == OUT_OF_BOUNDS )
				break;
			if ( table[line][col] != EMPTY_SQUARE )
				if ( (table[line][col] * attacker == QUEEN) || (table[line][col] * attacker == ROOK) )
					return true;
				else
					break;			
		}
		// E direction; possible attackers: Queen, Rook
		line = i; col = j;
		while ( true ) {
			col++;
			if ( table[line][col] == OUT_OF_BOUNDS )
				break;
			if ( table[line][col] != EMPTY_SQUARE )
				if ( (table[line][col] * attacker == QUEEN) || (table[line][col] * attacker == ROOK) )
					return true;
				else
					break;
		}
		// S direction; possible attackers: Queen, Rook
		line = i; col = j;
		while ( true ) {
			line--;
			if ( table[line][col] == OUT_OF_BOUNDS )
				break;
			if ( table[line][col] != EMPTY_SQUARE )
				if ( (table[line][col] * attacker == QUEEN) || (table[line][col] * attacker == ROOK) )
					return true;
				else
					break;
		}
		// W direction; possible attackers: Queen, Rook
		line = i; col = j;
		while ( true ) {
			col--;
			if ( table[line][col] == OUT_OF_BOUNDS )
				break;
			if ( table[line][col] != EMPTY_SQUARE )
				if ( (table[line][col] * attacker == QUEEN) || (table[line][col] * attacker == ROOK) )
					return true;
				else
					break;
		}
		
		//check for Knight attacks
		if ( (table[i + 2][j + 1] * attacker == KNIGHT)
			|| (table[i + 1][j + 2] * attacker == KNIGHT)
			|| (table[i - 2][j + 1] * attacker == KNIGHT)
			|| (table[i - 1][j + 2] * attacker == KNIGHT)
			|| (table[i + 2][j - 1] * attacker == KNIGHT)
			|| (table[i + 1][j - 2] * attacker == KNIGHT)
			|| (table[i - 2][j - 1] * attacker == KNIGHT)
			|| (table[i - 1][j - 2] * attacker == KNIGHT) )
			return true;
		
		//check for King attack
		if ( (table[i + 1][j + 1] * attacker == KING)
			|| (table[i][j + 1] * attacker == KING)
			|| (table[i - 1][j + 1] * attacker == KING)
			|| (table[i - 1][j] * attacker == KING)
			|| (table[i - 1][j - 1] * attacker == KING)
			|| (table[i][j - 1] * attacker == KING)
			|| (table[i + 1 ][j - 1] * attacker == KING)
			|| (table[i + 1][j] * attacker == KING) )
			return true;	
		
		//check for Pawn attack
		if ( (table[i - attacker][j + 1] * attacker == PAWN)
			|| (table[i - attacker][j - 1] * attacker == PAWN) )
			return true;
		
		return false;
	}
	
	
	/** Filters moves, keeping only legal ones */
	public void filterLegal(ArrayList<Move> moves) {
		Move aux;
		Iterator<Move> it = moves.iterator();
		
		while (it.hasNext()) {
			aux = it.next();
			if (! isLegal(aux))
				it.remove();
		}
	}
	
	
	/** Test if a move exists and is legal 
	 *  Used for hash and killer moves */
	public boolean moveExists(Move m) {
		
		if ( toMove * m.pieceMoving < 0 )
			return false;
		if ( table[m.initialLine][m.initialCol] != m.pieceMoving )
			return false;
		if ( table[m.destinationLine][m.destinationCol] != m.pieceCaptured &&
				m.moveType != EN_PASSANT )
			return false;
		
		ArrayList<Move> testMoves = new ArrayList<Move>();
		
		switch (Math.abs(m.pieceMoving)) {
			case PAWN: {
				testMoves.addAll(genPawnMoves(m.initialLine, m.initialCol));
				// add promotions and en passants
				if (m.moveType != ORDINARY_MOVE) {
					testMoves.addAll(genPromotionMoves(m.initialLine, m.initialCol));
					testMoves.addAll(genEnPassantMoves());
				}
				break;
			}
			case KNIGHT: {
				testMoves.addAll(genKnightMoves(m.initialLine, m.initialCol));
				break;
			}
			case BISHOP: {
				testMoves.addAll(genBishopMoves(m.initialLine, m.initialCol));
				break;
			}
			case ROOK: {
				testMoves.addAll(genRookMoves(m.initialLine, m.initialCol));
				break;
			}
			case QUEEN: {
				testMoves.addAll(genQueenMoves(m.initialLine, m.initialCol));
				break;
			}
			case KING: {
				testMoves.addAll(genKingMoves(m.initialLine, m.initialCol));
				if (m.moveType != ORDINARY_MOVE) {
					testMoves.addAll(genCastleMoves());
				}
				break;
			}
		}
		Iterator<Move> it = testMoves.iterator();
		Move aux;
		while (it.hasNext()) {
			aux = it.next();
			if (aux.equals(m))
				if (isLegal(aux))
					return true;
		}
		return false;
	}
	
	
	
	/** Checks if the move is legal (king is not in check or in danger of discovery check) */
	public boolean isLegal(Move move) {
		boolean control;
		
		// treat castles separately
		if (move.moveType == SHORT_CASTLE) {
			if (toMove == WHITE) {
				control = isAttacked(whiteKingLine, whiteKingCol, BLACK);
				control = control | isAttacked(LINE1, COLF, BLACK);
				control = control | isAttacked(LINE1, COLG, BLACK);
				return ! control;
			}
			else {
				control = isAttacked(blackKingLine, blackKingCol, WHITE);
				control = control | isAttacked(LINE8, COLF, WHITE);
				control = control | isAttacked(LINE8, COLG, WHITE);
				return ! control;
			}
		}
		if (move.moveType == LONG_CASTLE) {
			if (toMove == WHITE) {
				control = isAttacked(whiteKingLine, whiteKingCol, BLACK);
				// control = control | isAttacked(LINE1, COLB, BLACK);
				control = control | isAttacked(LINE1, COLC, BLACK);
				control = control | isAttacked(LINE1, COLD, BLACK);
				return ! control;
			}
			else {
				control = isAttacked(blackKingLine, blackKingCol, WHITE);
				// control = control | isAttacked(LINE8, COLB, WHITE);
				control = control | isAttacked(LINE8, COLC, WHITE);
				control = control | isAttacked(LINE8, COLD, WHITE);
				return ! control;
			}
		}
		
		// simulate making the move
		makeMove(move);
		
		// check if king is in check
		// the king might be in a check position due to the simulated move or
		// the king was already in check and the simulated move did nothing to change that
		// making it illegal
		if (toMove == WHITE)
			control = isAttacked(blackKingLine, blackKingCol, WHITE);
		else
			control = isAttacked(whiteKingLine, whiteKingCol, BLACK);
		
		// take back the move
		undoMove(move);
		
		return ! control;
	}
	

	
	/** Checks if the board is in an endgame position */
	public boolean isEndgame() {
		int phase = 0;
		
		phase += nWKnights;
		phase += nBKnights;
		phase += nWBishops;
		phase += nBBishops;
		phase += nWRooks * 2;
		phase += nBRooks * 2;
		phase += nWQueens * 4;
		phase += nBQueens * 4;
		
		if ( phase <= 8 )
			return true;
		
		return false;		
	}
	
	
	
	/** Generates a String version of the board for repetition detection 
	 * NOT USED */
	public byte[] generateShort() {
		byte[] s = new byte[64];
		int i, j, c = 0;
		
		for (i = LINE1; i <= LINE8; ++i)
			for (j = COLA; j <= COLH; ++j)
				s[c++] = table[i][j];

		return s;
	}
	
	
	/** Generate Zobrist key from scratch */
	public long generateZobrist() {
		long zobristKey = 0L;
		byte i, j, piece;
		
		for (i = LINE1; i <= LINE8; i++)
			for (j = COLA; j <= COLH; j++) {
					piece = table[i][j];
					if ( piece < 0 )
						zobristKey ^= Zobrist_Black[-piece][i][j];
					else
						zobristKey ^= Zobrist_White[piece][i][j];
				}
		// xor side if black to move
		if (toMove == BLACK)
			zobristKey ^= Zobrist_Side;
		
		return zobristKey;
	}
	
}

