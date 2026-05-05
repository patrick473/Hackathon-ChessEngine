package sopra.steria.helpers;

import knight.clubbing.core.BBoard;
import knight.clubbing.core.BMove;
import knight.clubbing.core.BPiece;

public class Helpers {


    public static int pieceValue(int piece) {
        return switch (piece) {
            case BPiece.pawn -> 100;
            case BPiece.knight, BPiece.bishop -> 350;
            case BPiece.rook -> 525;
            case BPiece.queen -> 1000;
            default -> 0; // King is not considered in MVV-LVA
        };
    }

    /**
     *
     * @param victimValue captured piece
     * @param attackerValue attacking piece
     * Score is weighted based on that victim value is more important
     * @return move score
     */
    public static int scoreCapture(int victimValue, int attackerValue) {
        return victimValue * 10 - attackerValue; // Higher score for capturing more valuable pieces
    }

    public static int promotionPriority(BMove move) {
        return switch (move.promotionPieceType()) {
            case BPiece.queen -> 400;
            case BPiece.knight -> 300; // usually more tactical than rook/bishop underpromotions
            case BPiece.rook -> 200;
            case BPiece.bishop -> 100;
            default -> 0;
        };
    }

    public static boolean hasNonPawnMaterial(final BBoard board) {
        int colorIndex = board.moveColorIndex();
        long colorBitboard = board.getColorBitboard(colorIndex);

        // Check for knights, bishops, rooks, or queens
        for (int pieceType = BPiece.knight; pieceType <= BPiece.queen; pieceType++) {
            int piece = BPiece.makePiece(pieceType, board.moveColor());
            if ((board.getBitboard(piece) & colorBitboard) != 0L) {
                return true;
            }
        }

        return false;
    }
}
