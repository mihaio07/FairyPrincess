FairyPrincess
=============

Open source Java chess engine developed from scratch. Works through Winboard and has an approximate ELO rating of 1600 - 1800.


1. Introduction

FairyPrincess started in 2010 as an open source chess project. The resulted engine had a strength of about 1100 - 1200 elo points. Development continued under a closed source for another year and as of 2014, the updated source has been made available.
The main philosophy behind FairyPrincess was to create a robust chess engine that does not sacrifice readability or stability in favor of complex algorithms.
Currently, FairyPrincess has a rating of around 1700 elo points and implements (at a basic level at least) a wide variety of algorithms and heuristics.
As far as interactivity goes, FairyPrincess supports basic commands of the winboard protocol.


2. Architecture

The engine is written in Java, but does not have an exceedingly object orientated architecture. Primitive types have been prefered instead of objects wherever possible. Furthermore, most data structures have been designed with memory efficiency in mind.
The class hierarchy is simple:
	- Board. Contains the chess board representation using a simple 12 x 12 integer array. Manages making and undoing a move, as well as castling and en passant rights. It also contains the implementation for Zobrist keys.
	- Move. Represents a chess move by keeping track of the piece moving, source and destination squares, as well as the piece captured and the move type. Also contains methods for conversion between SAN (standard algebraic notation) and move objects.
	- OpeningBook. Simple class that manages the file acting as an opening book.
	- Main. Contains initializations and communication with the winboard protocol.
	- Definitions. Interface containing global constants.
	- Evaluation. Interface containing contants used in evaluation.
	- Engine. Main class of the engine. Contains all functions and data structures related to the search algorithm.

	
3. Board representation

The board is represented as a 8 x 8 integer array with a 2 square border, for easier move generation.
The pieces are represented as unsigned (for white) and signed (for black) bytes.
The castling and en passant rights are managed by a stack, which is updated every time a move is made or undone.


4. Move representation

A move consists of 7 bytes: source and destination ranks and files (4 bytes), piece moving, piece captured and move type. Moves can be of the following type: ordinary, promotion (to queen, to rook, etc.), en passant, long castle, short castle.
Two rather complex methods are used to obtain a move object from a SAN string and the other way around.


5. Move generation

Move generation is straightforward and modular and it does not use separate delta tables. In spite of that, it is not inefficient, as a minimum number of squares is scanned for each piece. This however leads to a certain redundancy of the move generation code.
In order to boost the speed of the quiescent search, there is a method that generates captures separately.
Since the move generation itself outputs valid moves, an isLegal method is provided to check the moves for legality. For performance purposes, this method is called inside the alphabeta algorithm, as opposed to inside the move generation.


6. Search

The core of the search is the ubiquitous alpha-beta algorithm. The other basic algorithms are: iterative deepening and quiescent search.
Other algorithms and heuristics include: move sorting, transposition tables, zobrist keys, killer moves, history bonus.



7. Static evaluation

As is the case with most of the FairyPrincess components, the static evaluation is rather simple and straightforward. Most of the evaluating takes place in one loop of the board, making the evaluation function lightweight and fast.
Only the most important features of a chess position are evaluated. These are, in order of importance:
	- material. FairyPrincess has standard piece weights (900, 500, 300, 300, 100).
	- positional. The engine has a few positional matrices, for each piece type and color. There are also additional matrices for the endgame. These matrices usually contain scores in the range of 1/20 – 1/2 pawns and cover basic chess positioning: control of the center, bishops on main diagonals, king in the corner of the board (during midgame), etc. They also play a large role in piece development and castling.
	- pawn structure. Pawn structure is one of the key points in chess evaluation, and FairyPrincess penalizes weak pawns, isolated pawns, double / triple pawns. A simple but quite effective passed pawn evaluation is also present.
	- trapped pieces. Trapped pieces can often make an engine that is ahead in material fall quickly behind. FairyPrincess has a few simple checks that manage to avoid the common patterns of trapped bishops, knights and rooks.


8. Opening book

FairyPrincess has a simple 134 line opening book from which a random line is chosen. The opening book can support preferential openings, by adding a '#b' or '#w' at the end of a line to mark it as white / black favored.
