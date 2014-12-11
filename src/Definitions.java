
/**
 * 
 * Set of constants used by the chess engine and board.
 * Contains the actual Piece implementation as integer numbers.
 *  
 * @author Team Fairy Princess
 * 
 */

public interface Definitions {

	
	// Constants for pieces
	public static final byte WHITE_KING = 1;
	public static final byte WHITE_QUEEN = 2;
	public static final byte WHITE_ROOK = 3;
	public static final byte WHITE_BISHOP = 4;
	public static final byte WHITE_KNIGHT = 5;
	public static final byte WHITE_PAWN = 6;

	public static final byte BLACK_KING = -1;
	public static final byte BLACK_QUEEN = -2;
	public static final byte BLACK_ROOK = -3;
	public static final byte BLACK_BISHOP = -4;
	public static final byte BLACK_KNIGHT = -5;
	public static final byte BLACK_PAWN = -6;

	public static final byte EMPTY_SQUARE = 0;
	
	// unitary treatment of pieces, regardless of color
	public static final byte KING = 1;
	public static final byte QUEEN = 2;
	public static final byte ROOK = 3;
	public static final byte BISHOP = 4;
	public static final byte KNIGHT = 5;
	public static final byte PAWN = 6;

	// Constants for pieces
	
	
	// Constants for board adjustment
	public static final byte LINE1 = 2;
	public static final byte LINE2 = 3;
	public static final byte LINE3 = 4;
	public static final byte LINE4 = 5;
	public static final byte LINE5 = 6;
	public static final byte LINE6 = 7;
	public static final byte LINE7 = 8;
	public static final byte LINE8 = 9;
	public static final byte COLA  = 2;
	public static final byte COLB  = 3;
	public static final byte COLC  = 4;
	public static final byte COLD  = 5;
	public static final byte COLE  = 6;
	public static final byte COLF  = 7;
	public static final byte COLG  = 8;
	public static final byte COLH  = 9;
	public static final byte OUT_OF_BOUNDS = 99;
	// Constants for board adjustment
	
	
	// Side to move
	public static final byte WHITE = 1;
	public static final byte BLACK = -1;
	// Side to move
	
	
	// Constants for type of move
	public static final byte ORDINARY_MOVE = 0;
	public static final byte SHORT_CASTLE = 1;
	public static final byte LONG_CASTLE = 2;
	public static final byte EN_PASSANT = 3;
	public static final byte PROMOTION_QUEEN = 4;
	public static final byte PROMOTION_ROOK = 5;
	public static final byte PROMOTION_BISHOP = 6;
	public static final byte PROMOTION_KNIGHT = 7;
	// Constants for type of move
	
	
	// Constans for castle and en passant availability
	public static final byte CASTLE_NONE = 0;
	public static final byte CASTLE_SHORT = 1;
	public static final byte CASTLE_LONG = 2;
	public static final byte CASTLE_BOTH = 3;
	// Constans for castle and en passant availability
	
	
	// Evaluation constants
	// queen was initially 900
	public static final int QUEEN_VALUE = 950;
	public static final int ROOK_VALUE = 500;
	public static final int BISHOP_VALUE = 300;
	public static final int KNIGHT_VALUE = 300;
	public static final int PAWN_VALUE = 100;
	public static final int MATE_VALUE = 100000;
	public static final int STALEMATE_VALUE = 0;
	public static final int[] pieceValue = { 0, MATE_VALUE, 
				QUEEN_VALUE, ROOK_VALUE, BISHOP_VALUE, KNIGHT_VALUE, PAWN_VALUE };
	// Evaluation constants
	
	// AlphaBeta constants
	public static final int INF = 2000000;
	public static final long TIME_CHECK_INTERVAL = 5000;
	public static final int CONTEMPT_FACTOR = -50;
	// 10000 miliseconds = 10 seconds
	public static final long TIME_SAFETY = 2000;
	public static final int TIME_CONTROL_MOVES = 40;
	public static final float EXTRA_TIME = (float) 0.25;
	public static final int MIDGAME_MOVES = 15;
    public static final int REPETITION_MOVES = 20;
	// aspiration window
	public static final int ASPIRATION_WINDOW = 30;
	
	// maximum number of killer moves stored (maximum number of plies reached)
	public static final int MAX_KILLERS = 50;
	
	// max 2000000 moves ~= 38 MB
	// in practice, the hash holds 20000 - 30000
	public static final int HASH_SIZE = 2000003;
    // hash is cleared every 4 moves (8 half moves)
	// to avoid zobrist collisions
	public static final int HASH_CHECKPOINT = 4;
	
}
 