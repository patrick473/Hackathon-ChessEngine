package sopra.steria.ordering;

import knight.clubbing.core.BBoard;
import knight.clubbing.core.BMove;
import knight.clubbing.core.BPiece;
import sopra.steria.search.TranspositionTable;

import static sopra.steria.helpers.Helpers.pieceValue;
import static sopra.steria.helpers.Helpers.promotionPriority;
import static sopra.steria.helpers.Helpers.scoreCapture;


/**
 * ordering process:
 * 1. tt move
 * 2. promotions
 * 3. mvv lva
 * 4. killer moves
 * 5. quiet moves
 */
public class GoodOrderer implements MoveOrderer {

    @Override
    public void orderMoves(BMove[] moves, BBoard board, BMove[][] killers, int ply, int ttMove) {
        int[] scores = new int[moves.length];

        for (int i = 0; i < moves.length; i++) {
            scores[i] = score(moves[i], board);

            // TT move is searched first — highest possible priority
            if (ttMove != TranspositionTable.NO_MOVE
                    && TranspositionTable.encode(moves[i]) == ttMove) {
                scores[i] += 30_000;
            } else if (killers != null && ply >= 0) {
                if (moves[i].equals(killers[ply][0])) {
                    scores[i] += 10000;
                } else if (moves[i].equals(killers[ply][1])) {
                    scores[i] += 9000;
                }
            }
        }
        sortMovesByScore(moves, scores);
    }

    private int score(BMove move, BBoard board) {
        int[] pieceBoards = board.getPieceBoards();
        int attacker = BPiece.getPieceType(pieceBoards[move.startSquare()]);
        int victim = this.getVictimPieceType(move, board);

        int score = 0;

        if (move.isPromotion()) {
            score += 20000;
            score += promotionPriority(move);
        }
        if (victim != BPiece.none) {
            int attackerVal = pieceValue(attacker);
            int victimVal = pieceValue(victim);
            score += 10000;
            score += scoreCapture(victimVal, attackerVal);
        }
        return score;
    }

    private void sortMovesByScore(BMove[] moves, int[] scores) {
        for (int i = 0; i < moves.length - 1; i++) {
            for (int j = i + 1; j < moves.length; j++) {
                if (scores[j] > scores[i]) {
                    // Swap moves
                    BMove tempMove = moves[i];
                    moves[i] = moves[j];
                    moves[j] = tempMove;

                    // Swap scores
                    int tempScore = scores[i];
                    scores[i] = scores[j];
                    scores[j] = tempScore;
                }
            }
        }
    }

    private int getVictimPieceType(BMove move, BBoard board) {
        int[] pieceBoards = board.getPieceBoards();

        // check for en passant if so select square behind
        if (move.moveFlag() == BMove.enPassantCaptureFlag) {
            // Board is in pre-move state here.
            int capturedPawnSquare = board.isWhiteToMove()
                    ? move.targetSquare() - 8
                    : move.targetSquare() + 8;
            return BPiece.getPieceType(pieceBoards[capturedPawnSquare]);
        }

        return BPiece.getPieceType(pieceBoards[move.targetSquare()]);
    }
}