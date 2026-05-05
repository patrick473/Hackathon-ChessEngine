package sopra.steria.ordering;

import knight.clubbing.core.BBoard;
import knight.clubbing.core.BBoardHelper;
import knight.clubbing.core.BMove;
import knight.clubbing.core.BPiece;

public class BadMoveOrderer implements MoveOrderer {

    @Override
    public void orderMoves(BMove[] moves, BBoard board, BMove[][] killers, int ply, int ttMove) {
        int[] scores = new int[moves.length];

        for (int i = 0; i < moves.length; i++) {
            scores[i] = score(moves[i], board);
        }

        sortMovesByScore(moves, scores);
    }

    private int score(BMove move, BBoard board) {
        int score = 0;

        int movingPiece = board.getPieceBoards()[move.startSquare()];

        int rank = BBoardHelper.rankIndex(move.startSquare());

        // Oooh me move knight!
        if (BPiece.getPieceType(movingPiece) == BPiece.knight)
            score += 500;

        // Me want to move forward!
        if (board.isWhiteToMove() && rank == 5 || !board.isWhiteToMove() && rank == 2)
            score += 250;

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
}