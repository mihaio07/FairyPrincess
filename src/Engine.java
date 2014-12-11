import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.Stack;

/**
 *
 * 	Princess chess engine.
 * 	Generates a move based on a series of simple and advanced search algorithms.
 * 	Receives a SAN string and sends a SAN string
 *	Checks for checkmate and draw
 *  
 * 	@author Team Fairy Princess
 * 
 */

public class Engine implements Definitions, Evaluation {
	/** Opening book used by the engine */
	private OpeningBook book;
	/** History of moves played */
	private String history;
	/** History for three fold repetition */
	private Stack<Long> repetitionHistory;
	/** Number of moves played */
	int movesPlayed;
	/** Manages opening book usage */
	private boolean useOpeningBook;
	/** Global list of first moves from a position */
	private ArrayList<EvaluatedMove> bestMoves;	
    /** Number of nodes searched for current move */
    private int nodesSearched;

    
    /** Killer Moves */
    private Move[] primaryKillerMoves;
    private Move[] secondaryKillerMoves;
    
    /** History moves */
    // initial line, initial col, destination line, destination col
    private int historyMoves[][][][];
    // max history freq
    private int maxHistoryFreq;
	
    /** Hash table */
    TranspositionTable hashTable;
    
	/** Time available for current move */
	long timeForMove;
	/** Start of thinking time for current move */
	long startTime;
	/** Used for hard stop in alphaBeta */
	long timeCheckpoint;
	/** Global variable used for hard stops of the iterative deepening */
	private boolean stopThinking;
	
	
	/** Auxiliary class for Iterative Deepening algorithm */
	private class EvaluatedMove implements Comparable<EvaluatedMove> {
		Move m;
		int evaluation;
		
		EvaluatedMove(Move move, int score) {
			m = move;
			evaluation = score;
		}
		@Override
		public int compareTo(EvaluatedMove arg) {
			return arg.evaluation - evaluation;
		}
		@Override
		public String toString() {
			return m + " " + evaluation + "\n";
		}
	}
	
	
	/** Hash Entry: sizeof = 20 bytes */
	private class TranspositionEntry {
		long zobrist;
		Move move;
		byte depth;
		int eval;
		
		TranspositionEntry(long zobrist, Move move, byte depth, int eval) {
			this.zobrist = zobrist;
			this.move = move;
			this.depth = depth;
			this.eval = eval;
		}
	}
	
	
	/** Hash Table */
	private class TranspositionTable {
		int size;
		TranspositionEntry[] transpositions;
		
		TranspositionTable(int size) {
			this.size = size;
			transpositions = new TranspositionEntry[size];
		}
		
		// replace by depth
		void addTransposition(TranspositionEntry t) {
			int key = (int) (t.zobrist % size);
			if (transpositions[key] == null)
				transpositions[key] = t;
			else {
				// replace in case of collision
				if (transpositions[key].zobrist != t.zobrist)
					transpositions[key] = t;
				// replace only if new depth is greater
				else
					if (transpositions[key].depth <= t.depth)
						transpositions[key] = t;
			}
		}
		
		TranspositionEntry getEntry(long zobrist) {
			int key = (int) (zobrist % size);
			if (transpositions[key] != null)
				if (transpositions[key].zobrist == zobrist)
					return transpositions[key];
			
			return null;
		}

		void clear() {
			transpositions = new TranspositionEntry[size];
		}
	}
	
	
	/**
	 * 	Creates an engine object and associates an opening book
	 * 
	 *  @param String file containing the opening book
	 * 
	 */
	public Engine(String filename) {
		book = new OpeningBook(filename);
		history = "";
		repetitionHistory = new Stack<Long>();
		useOpeningBook = true;
		bestMoves = null;
		movesPlayed = 0;
		timeForMove = 0;
		startTime = 0;
		timeCheckpoint = 0;
		stopThinking = false;
        nodesSearched = 0;
        
        primaryKillerMoves = new Move[MAX_KILLERS];
        secondaryKillerMoves = new Move[MAX_KILLERS];
        historyMoves = new int[12][12][12][12];		// 8 x 8 + borders
        maxHistoryFreq = 0;
        
        hashTable = new TranspositionTable(HASH_SIZE);
	}
	
	
	/**
	 *  Tests whether current side is in mate or in stalemate
	 *  This method is only called when there are no valid moves
	 *  
	 *  @param Board the chessboard
	 *  @param int the ply where mate / stalemate is found
	 *  @return value of mate / stalemate
	 *  
	 */
	int mateCheck(Board board, int ply) {
		boolean control;
		// check if king is in check (or if a check situation was already present)
		if (board.toMove == WHITE) {
			//System.out.println("white king: " + whiteKingCol + whiteKingLine);
			control = board.isAttacked(board.whiteKingLine, board.whiteKingCol, BLACK);
			if (control) {
				// negative value for black; faster mate is more valuable
				return MATE_VALUE + ply * PAWN_VALUE / 10;
			}
		}
		else {
			control = board.isAttacked(board.blackKingLine, board.blackKingCol, WHITE);
			if (control) {
				// positive value for white; faster mate is more valuable
				return - (MATE_VALUE + ply * PAWN_VALUE / 10);
			}
		}
		// stalemate value is usually 0
		return STALEMATE_VALUE;
	}
	
	
	
	/**
	 *  Tests whether the current position has appeared 2 times (2 fold repetition)
	 *  
	 *  @param Long the current position zobrist 
	 *  @return true or false
	 *  
	 */
	boolean repetitionCheck(Long zobrist) {
        if (movesPlayed < REPETITION_MOVES)
			return false;
		
        // 2 fold repetition
        if (repetitionHistory.search(zobrist) != -1)
            return true;

		return false;
	}

	
	// history bonus for ordinary moves
	private int historyBonus(int x) {
		// for [c..d] -> [e..f]
		float c, d, e, f, a, b;
		c = 0;
		d = maxHistoryFreq;
		e = 0;
		f = MAX_HISTORY_BONUS;
		a = (f - e) / (d - c);
		b = e - a * c;
		return Math.round(a * x + b);
	}
	
	
	/** Returns the score for a move 
	 * 
	 *  Killer moves, MVV/LVA captures, non-captures
	 * 
	 * */
	private int getMoveScore(Move m1, int ply, long zobrist) {
		int score = 0;
		byte type = m1.moveType;
		byte piece = m1.pieceMoving;
		byte capture = m1.pieceCaptured;
		TranspositionEntry te;
		
		// treat hash move separately
		te = hashTable.getEntry(zobrist);
		if (te != null)
			if (te.move.equals(m1))
				return HASH_SCORE;
		
		// treat killer moves separately
		if ( primaryKillerMoves[ply] != null )
			if ( primaryKillerMoves[ply].equals(m1) )
				return PRIMARY_KILLER_SCORE;
		if ( secondaryKillerMoves[ply] != null )
			if ( secondaryKillerMoves[ply].equals(m1) )
				return SECONDARY_KILLER_SCORE;
		
		if (capture < 0)
			capture = (byte) -capture;
		if (piece < 0)
			piece = (byte) -piece;
		
		if ( type == ORDINARY_MOVE ) {
			// if no piece captured
			if ( capture == EMPTY_SQUARE ) {
				score = ORDINARY_PIECE_SCORES[piece];
				score += historyBonus(historyMoves[m1.initialLine][m1.initialCol]
				                           		  [m1.destinationLine][m1.destinationCol]);
			}
			else
				score = CAPTURE_SCORES[capture][piece];
		}
		else {
			switch ( type ) {
				case PROMOTION_QUEEN: { score = PROMOTION_QUEEN_SCORE; break; }
				case LONG_CASTLE: { score = LONG_CASTLE_SCORE; break; }
				case SHORT_CASTLE: { score = SHORT_CASTLE_SCORE; break; }
				case EN_PASSANT: { score = EN_PASSANT_SCORE; break; }
				default: score = 0;
			}
		}
		
		return score;
	}
	
	
	
	/**
	 * 	Sorts all the generated moves in a descending order according to their value.
	 *  Based on but not restricted to the MVV LVA sorting
	 * 	Improves the alphabeta algorithm.
	 *  !!! Might not keep same size of move array
	 *  !!! Killer moves and hash move are removed because they are treated separately
	 * 
	 * @param ArrayList<Move> a list of all legal moves
	 * @param ply the current ply
	 * 
	 */
	void sortMovesHeuristic(ArrayList<Move> moves, int ply, long zobrist) {
		
		ArrayList<EvaluatedMove> sortedMoves = new ArrayList<EvaluatedMove>(50);
		Move aux;
		EvaluatedMove auxe;
		Iterator<Move> it;
		Iterator<EvaluatedMove> ite;
		
		it = moves.iterator();
		while (it.hasNext()) {
			aux = it.next();
			sortedMoves.add(new EvaluatedMove(aux, getMoveScore(aux, ply, zobrist)));
		}
		
		moves.clear();
		Collections.sort(sortedMoves);
		
		ite = sortedMoves.iterator();
		while (ite.hasNext()) {
			auxe = ite.next();
			// hash and killer moves are treated separately
			if ( auxe.evaluation != PRIMARY_KILLER_SCORE && 
					auxe.evaluation != SECONDARY_KILLER_SCORE &&
					auxe.evaluation != HASH_SCORE)
			moves.add(auxe.m);
		}
		
	}
	
	
	/**
	 * 	Sorts all the generated moves in a descending order according to their value.
	 *  Based on but not restricted to the MVV LVA sorting
	 * 	Used exclusively in the QS search
	 *  Does not remove killers and hash move
	 * 
	 * @param ArrayList<Move> a list of all legal moves
	 * @param ply the current ply
	 * 
	 */
	void sortQuisMovesHeuristic(ArrayList<Move> moves, int ply, long zobrist) {
		
		ArrayList<EvaluatedMove> sortedMoves = new ArrayList<EvaluatedMove>(50);
		Move aux;
		EvaluatedMove auxe;
		Iterator<Move> it;
		Iterator<EvaluatedMove> ite;
		
		it = moves.iterator();
		while (it.hasNext()) {
			aux = it.next();
			sortedMoves.add(new EvaluatedMove(aux, getMoveScore(aux, ply, zobrist)));
		}
		
		moves.clear();
		Collections.sort(sortedMoves);
		
		ite = sortedMoves.iterator();
		while (ite.hasNext()) {
			auxe = ite.next();
			moves.add(auxe.m);
		}
		
	}
	
	
	
	/**
	 *  Evaluates current position on the board depending on material and positioning
	 *  Returns a positive score for white, negative score for black
	 *  Must be fast and simple
	 *
	 *  @param Board The board containing position to be evaluated
	 *  @return int The evaluation of the position
	 *
	 */

	int evaluatePosition(Board board) {

		// the sum of white pieces values - the sum of black pieces values
		// if black has the advantage, the score will be negative
		int materialAdvantage = 0;
		// positional advantage calculated with evaluation matrixes
		int positionalAdvantage = 0;
		byte i, j, piece, k;
		boolean endgame;
		
		// number of pawns for each file
		byte whitePawns[] = new byte[12];
		byte blackPawns[] = new byte[12];
		
		for (i = 0; i < 12; i++) {
			whitePawns[i] = 0;
			blackPawns[i] = 0;
		}
		
		endgame = board.isEndgame();
		
		// loop through the board
		for (i = LINE1; i <= LINE8; ++i)
			for (j = COLA; j <= COLH; ++j) {
				if (board.table[i][j] != EMPTY_SQUARE) {
					piece = board.table[i][j];

					// king value is not added to score
					// white pieces: values are added to the score
					// black pieces: values are subtracted from the score
					if ( piece != WHITE_KING && piece != BLACK_KING )
						if (piece > 0)
							materialAdvantage += pieceValue[piece];
						else
							materialAdvantage -= pieceValue[- piece];
					
					switch (piece) {
						
						case WHITE_KNIGHT: {
							// for endgame, change the evaluation matrix
							if (! endgame)
								positionalAdvantage += W_KNIGHT_POS[i][j];
							else
								positionalAdvantage += KNIGHT_POS_END[i][j];
							break;
						}
						
						case WHITE_BISHOP: {
							// for endgame, change the evaluation matrix
							if ( ! endgame)
								positionalAdvantage += W_BISHOP_POS[i][j];
							else
								positionalAdvantage += BISHOP_POS_END[i][j];
							break;
						}
						
						case WHITE_ROOK: {
							// for endgame, change the evaluation matrix
							if ( ! endgame )
								positionalAdvantage += W_ROOK_POS[i][j];
							else
								positionalAdvantage += ROOK_POS_END[i][j];
							// check if rook is on open or semiopen file
							boolean open = true, semiopen = true;
							for (k = LINE1; k < LINE8; k++) {
								if ( board.table[k][j] != EMPTY_SQUARE && k != i )
									open = false;
								if ( board.table[k][j] > 0 && k != i )
									semiopen = false;
							}
							if ( open == true )
								positionalAdvantage += 20;
							else if ( semiopen == true )
								positionalAdvantage += 15;
							break;
						}
						
						case WHITE_QUEEN: {
							if ( ! endgame )
								positionalAdvantage += W_QUEEN_POS[i][j];
							else	
								positionalAdvantage += QUEEN_POS_END[i][j];
							break;
						}
						
						case WHITE_PAWN: {
							whitePawns[j]++;
							if (! endgame)
								positionalAdvantage += W_PAWN_POS[i][j];
							else {
								positionalAdvantage += W_PAWN_POS_END[i][j];
								// if passed pawn is blocked by pawns, or by enemy king 
								// remove half of the bonus
								for (k = i; k <= LINE8; k++) {
									if ( board.table[k][j] == BLACK_PAWN || 
											board.table[k][j-1] == BLACK_PAWN || 
											board.table[k][j+1] == BLACK_PAWN ||
											board.table[k][j] == BLACK_KING || 
											board.table[k][j-1] == BLACK_KING || 
											board.table[k][j+1] == BLACK_KING ) {
										positionalAdvantage += - W_PAWN_POS_END[i][j] / 2;
										break;
									}
								}
							}
							// test if it is weak pawn (not defended by another pawn)
							if ( board.table[i-1][j-1] != WHITE_PAWN && 
									board.table[i-1][j+1] != WHITE_PAWN )
								positionalAdvantage += -15;
							break;
						}
						
						case WHITE_KING: {
							// for endgame, change the evaluation matrix
							if (! endgame)
								positionalAdvantage += W_KING_POS[i][j];
							else
								positionalAdvantage += KING_POS_END[i][j];
							break;
						}

						
						case BLACK_KNIGHT: {
							// for endgame, change the evaluation matrix
							if (! endgame)
								positionalAdvantage -= B_KNIGHT_POS[i][j];
							else
								positionalAdvantage -= KNIGHT_POS_END[i][j];
							break;
						}
						
						case BLACK_BISHOP: {
							// for endgame, change the evaluation matrix
							if (! endgame)
								positionalAdvantage -= B_BISHOP_POS[i][j];
							else
								positionalAdvantage -= BISHOP_POS_END[i][j];
							break;
						}
						
						case BLACK_ROOK: {
							// for endgame, change the evaluation matrix
							if ( ! endgame )
								positionalAdvantage -= B_ROOK_POS[i][j];
							else
								positionalAdvantage -= ROOK_POS_END[i][j];
							// check if rook is on open or semiopen file
							boolean open = true, semiopen = true;
							for (k = LINE1; k < LINE8; k++) {
								if ( board.table[k][j] != EMPTY_SQUARE && k != i )
									open = false;
								if ( board.table[k][j] < 0 && k != i )
									semiopen = false;
							}
							if ( open == true )
								positionalAdvantage -= 20;
							else if ( semiopen == true )
								positionalAdvantage -= 15;
							break;
						}
						
						case BLACK_QUEEN: {
							if ( ! endgame )
								positionalAdvantage -= B_QUEEN_POS[i][j];
							else	
								positionalAdvantage -= QUEEN_POS_END[i][j];
							break;
						}
						
						case BLACK_PAWN: {
							blackPawns[j]++;
							if (! endgame)
								positionalAdvantage -= B_PAWN_POS[i][j];
							else {
								positionalAdvantage -= B_PAWN_POS_END[i][j];
								// if passed pawn is blocked by other pawns or by king 
								// remove half of the bonus
								for (k = i; k > LINE1; k--) {
									if ( board.table[k][j] == WHITE_PAWN || 
											board.table[k][j-1] == WHITE_PAWN || 
											board.table[k][j+1] == WHITE_PAWN ||
											board.table[k][j] == WHITE_KING || 
											board.table[k][j-1] == WHITE_KING || 
											board.table[k][j+1] == WHITE_KING) {
										positionalAdvantage -= - B_PAWN_POS_END[i][j] / 2;
										break;
									}
								}
							}
							// test if it is weak pawn
							if ( board.table[i+1][j-1] != BLACK_PAWN && 
									board.table[i+1][j+1] != BLACK_PAWN )
								positionalAdvantage -= -15;
							break;
						}
						
						case BLACK_KING: {
							// for endgame, change the evaluation matrix
							if (! endgame)
								positionalAdvantage -= B_KING_POS[i][j];
							else
								positionalAdvantage -= KING_POS_END[i][j];
							break;
						}
					}
				}
			}
		
		// check for isolated pawns and double (triple) pawns
		for (i = COLA; i <= COLH; i++)
		{
			// isolated white pawn
			if ( whitePawns[i] >=1 )
				if ( whitePawns[i-1] == 0 && whitePawns[i+1] == 0 )
					positionalAdvantage += -20;
			// isolated black pawn
			if ( blackPawns[i] >=1 )
				if ( blackPawns[i-1] == 0 && blackPawns[i+1] == 0 )
					positionalAdvantage -= -20;
				
			// double pawns
			if (whitePawns[i] >= 2)
				positionalAdvantage += -25 * (whitePawns[i] - 1);
			if (blackPawns[i] >= 2)
				positionalAdvantage -= -25 * (blackPawns[i] - 1);
		}
		
		// bishop pair bonus
		if ( board.nWBishops == 2 )
			positionalAdvantage += 50;
		if ( board.nBBishops == 2 )
			positionalAdvantage -= 50;
		
		
		// trapped pieces (if not endgame)
		if (! endgame ) {
		
			// white pieces
			
			// knights
			if ( board.table[LINE7][COLA] == WHITE_KNIGHT &&
					board.table[LINE7][COLB] == BLACK_PAWN && 
					board.table[LINE6][COLC] == BLACK_PAWN )
				positionalAdvantage += -100;
			if ( board.table[LINE7][COLH] == WHITE_KNIGHT &&
					board.table[LINE7][COLG] == BLACK_PAWN && 
					board.table[LINE6][COLF] == BLACK_PAWN )
				positionalAdvantage += -100;
			if ( board.table[LINE8][COLA] == WHITE_KNIGHT &&
					(board.table[LINE7][COLA] == BLACK_PAWN || 
					board.table[LINE7][COLC] == BLACK_PAWN) )
				positionalAdvantage += -50;
			if ( board.table[LINE8][COLH] == WHITE_KNIGHT &&
					(board.table[LINE7][COLH] == BLACK_PAWN || 
					board.table[LINE7][COLF] == BLACK_PAWN) )
				positionalAdvantage += -50;
		
			// bishops
			if ( board.table[LINE7][COLA] == WHITE_BISHOP &&
					board.table[LINE6][COLB] == BLACK_PAWN )
				positionalAdvantage += -100;
			if ( board.table[LINE8][COLB] == WHITE_BISHOP &&
					board.table[LINE7][COLC] == BLACK_PAWN )
				positionalAdvantage += -100;
			if ( board.table[LINE7][COLH] == WHITE_BISHOP &&
					board.table[LINE6][COLG] == BLACK_PAWN )
				positionalAdvantage += -100;
			if ( board.table[LINE8][COLG] == WHITE_BISHOP &&
					board.table[LINE7][COLF] == BLACK_PAWN )
				positionalAdvantage += -100;
			if ( board.table[LINE6][COLA] == WHITE_BISHOP &&
					board.table[LINE5][COLB] == BLACK_PAWN )
				positionalAdvantage += -100;
			if ( board.table[LINE6][COLH] == WHITE_BISHOP &&
					board.table[LINE5][COLG] == BLACK_PAWN )
				positionalAdvantage += -100;
			if ( board.table[LINE1][COLC] == WHITE_BISHOP && 
					board.table[LINE2][COLB] == WHITE_PAWN &&
					board.table[LINE2][COLD] == WHITE_PAWN )
				positionalAdvantage += -50;
			if ( board.table[LINE1][COLF] == WHITE_BISHOP && 
					board.table[LINE2][COLE] == WHITE_PAWN &&
					board.table[LINE2][COLG] == WHITE_PAWN )
				positionalAdvantage += -50;
			

			// rooks (blocked by king)
			if ( (board.table[LINE1][COLG] == WHITE_ROOK || board.table[LINE1][COLH] == WHITE_ROOK)
					&& (board.table[LINE1][COLG] == WHITE_KING || board.table[LINE1][COLF] == WHITE_KING) )
				positionalAdvantage += -50;
			if ( (board.table[LINE1][COLB] == WHITE_ROOK || board.table[LINE1][COLA] == WHITE_ROOK)
					&& (board.table[LINE1][COLB] == WHITE_KING || board.table[LINE1][COLC] == WHITE_KING) )
				positionalAdvantage += -50;
			
			// trapped black pieces
		
			// knights
			if ( board.table[LINE2][COLA] == BLACK_KNIGHT &&
					board.table[LINE2][COLB] == WHITE_PAWN && 
					board.table[LINE3][COLC] == WHITE_PAWN )
				positionalAdvantage -= -100;
			if ( board.table[LINE2][COLH] == BLACK_KNIGHT &&
					board.table[LINE2][COLG] == WHITE_PAWN && 
					board.table[LINE3][COLF] == WHITE_PAWN )
				positionalAdvantage -= -100;
			if ( board.table[LINE1][COLA] == BLACK_KNIGHT &&
					(board.table[LINE2][COLA] == WHITE_PAWN || 
					board.table[LINE2][COLC] == WHITE_PAWN) )
				positionalAdvantage -= -50;
			if ( board.table[LINE1][COLH] == BLACK_KNIGHT &&
					(board.table[LINE2][COLH] == WHITE_PAWN || 
					board.table[LINE2][COLF] == WHITE_PAWN) )
				positionalAdvantage -= -50;
		
			// bishops
			if ( board.table[LINE2][COLA] == BLACK_BISHOP &&
					board.table[LINE3][COLB] == WHITE_PAWN )
				positionalAdvantage -= -100;
			if ( board.table[LINE1][COLB] == BLACK_BISHOP &&
					board.table[LINE2][COLC] == WHITE_PAWN )
				positionalAdvantage -= -100;
			if ( board.table[LINE2][COLH] == BLACK_BISHOP &&
					board.table[LINE3][COLG] == WHITE_PAWN )
				positionalAdvantage -= -100;
			if ( board.table[LINE1][COLG] == BLACK_BISHOP &&
					board.table[LINE2][COLF] == WHITE_PAWN )
				positionalAdvantage -= -100;
			if ( board.table[LINE3][COLA] == BLACK_BISHOP &&
					board.table[LINE4][COLB] == WHITE_PAWN )
				positionalAdvantage -= -100;
			if ( board.table[LINE3][COLH] == BLACK_BISHOP &&
					board.table[LINE4][COLG] == WHITE_PAWN )
				positionalAdvantage -= -100;
			if ( board.table[LINE8][COLC] == BLACK_BISHOP && 
					board.table[LINE7][COLB] == BLACK_PAWN &&
					board.table[LINE7][COLD] == BLACK_PAWN )
				positionalAdvantage -= -50;
			if ( board.table[LINE8][COLF] == BLACK_BISHOP && 
					board.table[LINE7][COLE] == BLACK_PAWN &&
					board.table[LINE7][COLG] == BLACK_PAWN )
				positionalAdvantage -= -50;
		
			// rooks (blocked by king)
			if ( (board.table[LINE8][COLG] == BLACK_ROOK || board.table[LINE8][COLH] == BLACK_ROOK)
					&& (board.table[LINE8][COLG] == BLACK_KING || board.table[LINE8][COLF] == BLACK_KING) )
				positionalAdvantage -= -50;
			if ( (board.table[LINE8][COLB] == BLACK_ROOK || board.table[LINE8][COLA] == BLACK_ROOK)
					&& (board.table[LINE8][COLB] == BLACK_KING || board.table[LINE8][COLC] == BLACK_KING) )
				positionalAdvantage -= -50;
		}
		
		// if return value is positive => white is ahead, else black is ahead
		return materialAdvantage + positionalAdvantage;
	}			
	
	
	
	/**
	 *	Negamax algorithm with alphabeta prunning
	 *
	 *  @param int alpha
	 *  @param int beta
	 *  @param Board the board
	 *  @param int current ply
	 *  @param Move the grandfather of all moves (level 1 in tree) on the current branch
	 * 
	 *  @return final evaluation
	 *
	 */
	int alphaBeta(int alpha, int beta, int ply, Board board, Move firstMove) {

		ArrayList<Move> moves;
		int numMoves, eval;
		Move aux;
		Move hashMove = null;
		boolean mateCheckFlag = true;
		
		// update checkpoint
		timeCheckpoint--;
		
		// test for hard stop
		if (timeCheckpoint == 0) {
			if ( (movesPlayed > MIDGAME_MOVES) && System.currentTimeMillis() - startTime >= timeForMove )
				stopThinking = true;
			timeCheckpoint = TIME_CHECK_INTERVAL;
		}
		
		// hard stop
		if (stopThinking)
			return 0;
		
		// update nodes searched
		nodesSearched++;
		
		// if this is the first alphaBeta recursive call
		// the moves are taken from the bestMoves list
		if ( firstMove == null ) {
			moves = new ArrayList<Move>();
			Iterator<EvaluatedMove> it = bestMoves.iterator();
			while (it.hasNext()) {
				moves.add(it.next().m);
			}
			numMoves = moves.size();
			
			// loop through all available moves
			for (int i = 0; i < numMoves; ++i) {
				aux = moves.get(i);
			
				board.makeMove(aux);
				
				if (repetitionCheck(board.Zobrist_Key))
					// take contempt factor into consideration
					eval = CONTEMPT_FACTOR;	
				else {
					// first, add zobrist to repetition history
					repetitionHistory.push(board.Zobrist_Key);
					// this is where the firstMove variable is initialized
					// with the current first level move
					eval = - alphaBeta(-beta, -alpha, ply-1, board, aux);
					// remove zobrist from repetition history
					repetitionHistory.pop();
				}
				
				board.undoMove(aux);
				
				// beta cutoff
				if ( eval >= beta ) {
					// never gets here?
					return beta;
				}
				if ( eval > alpha) {
					alpha = eval;
					// since it is the first alpha beta call
					// update the score for current move
					bestMoves.get(i).evaluation = alpha; 
				}
			}
		}
		
		else {

			// QUIESCENT SEARCH
			if ( ply == 0 )
				return quiescentSearch(alpha, beta, board);
			
			else {
				
				// PRE-MOVE GENERATION PHASE
				// TEST KILLER MOVES AND HASH MOVE
				
				moves = new ArrayList<Move>();
				
				// hash move
				TranspositionEntry te = hashTable.getEntry(board.Zobrist_Key);
				if (te != null)
					if ( board.moveExists(te.move) ) {
						if ( te.depth >= ply )
							return te.eval;
						else
							moves.add(te.move);
					}
				
				// killer moves
				if (primaryKillerMoves[ply] != null)
					if ( board.moveExists(primaryKillerMoves[ply]) )
						moves.add(primaryKillerMoves[ply]);
				if (secondaryKillerMoves[ply] != null)
					if ( board.moveExists(secondaryKillerMoves[ply]) )
						moves.add(secondaryKillerMoves[ply]);
				numMoves = moves.size();
				if (numMoves > 0)
					mateCheckFlag = false;

				for (int i = 0; i < numMoves; ++i) {
					aux = moves.get(i);
					
					board.makeMove(aux);
					
					if (repetitionCheck(board.Zobrist_Key))
						// take contempt factor into consideration
						eval = CONTEMPT_FACTOR;	
					else {
						// first, add zobrist to repetition history
						repetitionHistory.push(board.Zobrist_Key);
						// this is where the firstMove variable is initialized
						// with the current first level move
						eval = - alphaBeta(-beta, -alpha, ply-1, board, aux);
						// remove zobrist from repetition history
						repetitionHistory.pop();
					}

					board.undoMove(aux);
					
					// beta cutoff
					if ( eval >= beta ) {

						// add killer move for current ply
						if ( primaryKillerMoves[ply] != null ) {
							// if new killer
							if ( ! primaryKillerMoves[ply].equals(aux) ) {
								// move primary killer down; add new primary killer
								secondaryKillerMoves[ply] = primaryKillerMoves[ply];
								primaryKillerMoves[ply] = aux;
							}
						}
						// no move exists for current ply 
						// add current move as primary killer
						else 
							primaryKillerMoves[ply] = aux;
						
						// add history move
						historyMoves[aux.initialLine][aux.initialCol]
						            [aux.destinationLine][aux.destinationCol]++;
						int freq = historyMoves[aux.initialLine][aux.initialCol]
						                       [aux.destinationLine][aux.destinationCol];
						// new max
						if (freq > maxHistoryFreq)
							maxHistoryFreq = freq;
						
						return beta;
					}
					
					if ( eval > alpha) {
						alpha = eval;
						hashMove = moves.get(i);
					}
				}
				
				// MOVE GENERATION
				
				// generate the valid moves for current position
				// follow normal alpha beta algorithm pattern
				moves = board.generateMoves();
				// for mate check
				int nLegalMoves = 0;

				// apply the move sorting
				sortMovesHeuristic(moves, ply, board.Zobrist_Key);
				numMoves = moves.size();
			
				// loop through all available moves
				for (int i = 0; i < numMoves; ++i) {
					aux = moves.get(i);
					
					// check if legal
					if (! board.isLegal(aux))
						continue;
					
					nLegalMoves++;
					board.makeMove(aux);
					
					if (repetitionCheck(board.Zobrist_Key))
						// take contempt factor into consideration
						eval = CONTEMPT_FACTOR;	
					else {
						// first, add zobrist to repetition history
						repetitionHistory.push(board.Zobrist_Key);
						// this is where the firstMove variable is initialized
						// with the current first level move
						eval = - alphaBeta(-beta, -alpha, ply-1, board, aux);
						// remove zobrist from repetition history
						repetitionHistory.pop();
					}

					board.undoMove(aux);
					
					// beta cutoff
					if ( eval >= beta ) {

						// add killer move for current ply
						if ( primaryKillerMoves[ply] != null ) {
							// if new killer
							if ( ! primaryKillerMoves[ply].equals(aux) ) {
								// move primary killer down; add new primary killer
								secondaryKillerMoves[ply] = primaryKillerMoves[ply];
								primaryKillerMoves[ply] = aux;
							}
						}
						// no move exists for current ply 
						// add current move as primary killer
						else 
							primaryKillerMoves[ply] = aux;
						
						// add history move
						historyMoves[aux.initialLine][aux.initialCol]
						            [aux.destinationLine][aux.destinationCol]++;
						int freq = historyMoves[aux.initialLine][aux.initialCol]
						                       [aux.destinationLine][aux.destinationCol];
						// new max
						if (freq > maxHistoryFreq)
							maxHistoryFreq = freq;
						
						return beta;
					}
					
					if ( eval > alpha) {
						alpha = eval;
						hashMove = moves.get(i);
					}
				}
				
				// if there are no legal moves => return mate / stalemate value
				if ( nLegalMoves == 0 && mateCheckFlag )
					return - board.toMove * mateCheck(board, ply);
			}
		}
		
		// add HASH_EXACT transposition
		if (hashMove != null)
			hashTable.addTransposition(new TranspositionEntry(
					board.Zobrist_Key, hashMove, (byte)ply, alpha));
			
		return alpha;
	}
	
	
	
	/**
	 *  Quiescent search (only considers captures)
	 *  Has no fixed maximum depth
	 *
	 *	@param int alpha value
	 *  @param int beta value  
	 *  @param Board the board
	 *  @param ArrayList<Move> initial Moves
	 *  
	 *  @return evaluation
	 *  
	 */
	int quiescentSearch(int alpha, int beta, Board board) {

		ArrayList<Move> quisMoves;
		int numMoves, eval;
		Move aux;

		// update nodes searched
        nodesSearched++;
        
		// evaluate current position first and check for cut offs
		eval = board.toMove * evaluatePosition(board);
		if(eval >= beta)
			return beta;
		if(eval > alpha) {
			alpha = eval;
		}

		// optimize array initial capacity
		quisMoves = new ArrayList<Move>(50);
		
		// generate all captures
		quisMoves = board.generateCaptures();
		
		// sort new list of moves
		// sorting heuristic works fine despite moves being only captures
		// no ply is available so a dummy value is set
		sortQuisMovesHeuristic(quisMoves, 0, board.Zobrist_Key);
		numMoves = quisMoves.size();
		
		// loop through moves
		for (int i = 0; i < numMoves; ++i) {
			aux = quisMoves.get(i);
			
			// check if legal
			if (! board.isLegal(aux))
				continue;
		
			board.makeMove(aux);
			eval = - quiescentSearch(-beta, -alpha, board);
			board.undoMove(aux);
			
			// beta cutoff
			if ( eval >= beta ) {
				return beta;
			}
			if ( eval > alpha) {
				alpha = eval;
				// NOTHING EXTRA HERE?
			}
		}
		
		return alpha;
	}
	
	
	
	/**
	 *  Iterative Deepening heuristic
	 *  Calls the alphaBeta repeatedly and sets the bestMoves global variable
	 *	Implements time management
	 *
	 *  @param Board The board containing position to be evaluated
	 *  @return the best move
	 *
	 */
	Move iterativeDeepening(Board board) {
		int i, eval, oldEval;
		Move aux;
		int alpha, beta;
		EvaluatedMove bestMove = null;
		
		// initialize thinking time
		startTime = System.currentTimeMillis();
		
		// initialize stopThinking to false
		stopThinking = false;
		
		// initialize killer moves
		primaryKillerMoves = new Move[MAX_KILLERS];
		secondaryKillerMoves = new Move[MAX_KILLERS];
		
		// initialize history moves
		historyMoves = new int[12][12][12][12];		// 8 x 8 + borders
        maxHistoryFreq = 0;

		// reset global variable
		bestMoves = new ArrayList<EvaluatedMove>();
		ArrayList<Move> moves = board.generateMoves();
		board.filterLegal(moves);
		int numMoves = moves.size();
		// if there are no moves available return null
		if ( numMoves == 0 )
			return null;
		
		// fill in bestMoves list with initial evaluations
		for (i = 0; i < numMoves; ++i) {
			aux = moves.get(i);
			board.makeMove(aux);
			eval = - board.toMove * evaluatePosition(board);
			board.undoMove(aux);
			bestMoves.add( new EvaluatedMove(aux, eval) );
		}
		
		// sort best moves by initial evaluation score
		Collections.sort(bestMoves);
		bestMove = bestMoves.get(0);
		oldEval = bestMove.evaluation;
		
		alpha = - INF;
		beta = + INF;
		
		// iterative deepening loop
		for (i = 2; ; i++) {
			
			nodesSearched = 0;
			
			// initialize evaluations with -INF
			// only move order is important; score doesn't matter
			Iterator<EvaluatedMove> it = bestMoves.iterator();
			while ( it.hasNext() ) {
				it.next().evaluation = -INF;
			}
			
			// initialize hard stop counter before alphaBeta
			timeCheckpoint = TIME_CHECK_INTERVAL;
			
			// call alphabeta and get best move for current iteration
			eval = alphaBeta( alpha, beta, i, board, null );
			
			// if hard stop return bestMove from previous level
			if (stopThinking) {
				System.out.println(i - 1 + " " + oldEval + " "
                                    + (System.currentTimeMillis() - startTime) / 10 + " "
                                    + nodesSearched + "    " + bestMove.m.toString());
				return bestMove.m;
			}
			
			// if alphabeta result is outside aspiration window
			// call alphabeta again with - INF and INF
			if ( eval <= alpha || eval >= beta ) {
				/*
				System.out.println(i + " " + eval + " "
                        + (System.currentTimeMillis() - startTime) / 10 + " "
                        + nodesSearched + "    " + bestMove.m.toString() 
                        + "   qply=" + qPly + "    AW");
                */
				nodesSearched = 0;
				eval = alphaBeta( - INF, INF, i, board, null);
			}
			
			// set aspiration window around the value of previous evaluation
			alpha = eval - ASPIRATION_WINDOW;
			beta = eval + ASPIRATION_WINDOW;
			
			// if hard stop return bestMove from previous level
			if (stopThinking) {
				System.out.println(i - 1 + " " + oldEval + " "
                                    + (System.currentTimeMillis() - startTime) / 10 + " "
                                    + nodesSearched + "    " + bestMove.m.toString());
				return bestMove.m;
			}
			
			// no hard stop, so alphabeta completed successfully
			// sort moves by score from current level
			Collections.sort(bestMoves);
			// the highest evaluated best move is the best move
			bestMove = bestMoves.get(0);
			
			// soft stop if there is not enough estimated time for another iteration
			if (System.currentTimeMillis() - startTime >= timeForMove / 3) {
				System.out.println(i + " " + bestMove.evaluation + " "
                                    + (System.currentTimeMillis() - startTime) / 10 + " "
                                    + nodesSearched + "    " + bestMove.m.toString());
				return bestMove.m;
			}
			
			oldEval = bestMove.evaluation;

			System.out.println(i + " " + bestMove.evaluation + " "
	                + (System.currentTimeMillis() - startTime) / 10 + " "
	                + nodesSearched + "    " + bestMove.m.toString());

			// return if mate found
			if (bestMove.evaluation >= MATE_VALUE || bestMove.evaluation <= - MATE_VALUE)
				return bestMove.m;
		}
	}
	
	
	
	/**
	 *
	 * 	Generates a move on the board using the alpha beta algorithm
	 * 
	 *	@param board the chessboard
	 *	@param long total time remaining in miliseconds
	 * 	@return The move in SAN or an empty string
	 * 
	 */
	
	public String generateMove(Board board, long timeAvailable) {
		String moveSAN = "";
		Move move;
		byte i, j;
		
		// clear hash every HASH_CHECKPOINT moves
		if (movesPlayed % HASH_CHECKPOINT == 0)
            hashTable.clear();
		
		// set time for move
		// 40 moves per increment
		timeForMove = (timeAvailable - TIME_SAFETY) / 
						(TIME_CONTROL_MOVES - movesPlayed % TIME_CONTROL_MOVES);

		// award an extra 1/2 time for first 1 - 20 moves
		if (movesPlayed <= MIDGAME_MOVES)
			timeForMove += timeForMove * EXTRA_TIME;
		
		// increment moves played
		movesPlayed++;
		
		// if opening book is turned on
		if (useOpeningBook) {
			// try to get a move from the book
			moveSAN = book.getMove(history);
		}
		
		// if a move was found in the opening book
		if (moveSAN.length() != 0 && ! moveSAN.contains("#") ) {
			// add it to history
			history += moveSAN + " ";
			
			// create a move object from the SAN string
			move = new Move(moveSAN, board);
			// make the move on the board
			board.makeMove(move);
			
			// return the SAN string
			return moveSAN;
		}
		
		// if the opening book did not return a move
		else {
			// turn off opening book
			useOpeningBook = false;
			// also there is no point in updating history since book is turned off
			
			// call the iterative deepening algorithm
			move = iterativeDeepening(board);
 
			// if the algorithm hasn't returned a move?
			if (move != null) {
				moveSAN = move.writeMove(board);
				// and also make it on the board
				board.makeMove(move);
				
				// add move to history
                if (movesPlayed >= REPETITION_MOVES)
                    repetitionHistory.push(board.Zobrist_Key);
			
				// append '#' if mate or '+' if check to the SAN string
				// check to see if opponent is in check or mate
				// generate opponent moves (toMove has been changed) by makeMove
		
				ArrayList<Move> moves = board.generateMoves();
				board.filterLegal(moves);
				if ( board.toMove == BLACK ) {
					i = board.blackKingLine;
					j = board.blackKingCol;
				}
				else {
					i = board.whiteKingLine;
					j = board.whiteKingCol;
				}
				
				if ( moves.size() == 0 ) {
					// if opponent has no legal moves and his king is attacked
					// he is clearly mated
					if ( board.isAttacked(i, j, (byte) (board.toMove * -1)) )
						moveSAN += "#";
				}
				else
					if ( board.isAttacked(i, j, (byte) (board.toMove * -1)) )
						moveSAN += "+";
			}
			// if the engine has no valid move, an empty string will be returned
			return moveSAN;
		}
	}

	
	
	/**
	 * 	Receives a SAN string and makes the corresponding move on the board
	 * 
	 * 	@param board the chessboard
	 * 	@param moveSan a SAN string
	 *
	 */
	public void receiveMove(String moveSAN, Board board) {
		
		// if the opening book is turned on
		// add received move to history
		if (useOpeningBook)
			history += moveSAN + " ";
		
		// create a move from the SAN string
		Move move = new Move(moveSAN, board);
		// make the move on the board
		board.makeMove(move);
	}
}
