package sopra.steria.evaluation;

import knight.clubbing.core.BBoard;
import knight.clubbing.core.BPiece;
import sopra.steria.helpers.Helpers;

public class GoodEvaluator implements Evaluator {

    @Override
    public int evaluate(BBoard board) {
        int score = 0;
        int[] pieces = board.getPieceBoards(); // length 64, one entry per square

        for (int sq = 0; sq < 64; sq++) {
            int piece = pieces[sq];
            if (piece == BPiece.none) continue;

            boolean isWhite = BPiece.isWhite(piece);
            int type = BPiece.getPieceType(piece);

            // Tables are from Black's perspective (index 0 = a8).
            // White squares must be mirrored (sq ^ 56) so rank 1 maps to index 56+.
            // Black squares are used as-is.
            int mirroredIndex = isWhite ? (sq ^ 56) : sq;

            int materialValue = Helpers.pieceValue(type);
            score += (isWhite
                    ? PstTables.PST[type][mirroredIndex]
                    : -PstTables.PST[type][mirroredIndex])
                    * materialValue;
            score += materialValue;
        }

        // mobility bonus
        int mobilityScore = evaluatePieceActivity(board);
        score += mobilityScore;

        // Bishop pair bonus
        int bishopPairScore = evaluateBishopPair(board);
        score += bishopPairScore;

        // King safety
        int kingSafetyScore = evaluateKingSafety(board);
        score += kingSafetyScore;

        return board.isWhiteToMove() ? score : -score;
    }

    private int evaluatePieceActivity(BBoard board) {
        int[] pieces = board.getPieceBoards();
        int score = 0;

        for (int sq = 0; sq < 64; sq++) {
            int piece = pieces[sq];
            if (piece == BPiece.none) continue;

            boolean isWhite = BPiece.isWhite(piece);
            int type = BPiece.getPieceType(piece);
            int file = sq & 7;
            int rank = sq >> 3;

            int bonus = 0;

            // Center control bonus (d4, e4, d5, e5 = 27, 28, 35, 36)
            if ((file >= 3 && file <= 4) && (rank >= 3 && rank <= 4)) {
                bonus += switch (type) {
                    case BPiece.pawn -> 10;
                    case BPiece.knight -> 25;
                    case BPiece.bishop -> 15;
                    case BPiece.rook -> 10;
                    case BPiece.queen -> 20;
                    case BPiece.king -> 0;
                    default -> 0;
                };
            }

            // Starting square penalty (knights/bishops trapped)
            if (type == BPiece.knight && ((isWhite && sq == 1) || (!isWhite && sq == 62))) {
                bonus -= 15; // Knight on b1/g8 is trapped
            }
            if (type == BPiece.bishop && ((isWhite && (sq == 2 || sq == 5)) || (!isWhite && (sq == 57 || sq == 62)))) {
                bonus -= 10; // Bishop on starting square usually blocked
            }
            // Rank advancement bonus for minor pieces
            if (type == BPiece.knight || type == BPiece.bishop) {
                int rankDist = isWhite ? rank : 7 - rank;
                bonus += rankDist * 2; // Bonus for advanced pieces
            }

            score += isWhite ? bonus : -bonus;
        }
        return score;
    }

    private int evaluateBishopPair(BBoard board) {
        int[] pieces = board.getPieceBoards();
        int whiteBishops = 0;
        int blackBishops = 0;

        // Count bishops
        for (int sq = 0; sq < 64; sq++) {
            int piece = pieces[sq];
            if (piece == BPiece.none) continue;

            int type = BPiece.getPieceType(piece);
            if (type != BPiece.bishop) continue;

            if (BPiece.isWhite(piece)) {
                whiteBishops++;
            } else {
                blackBishops++;
            }
        }

        // Base bonus if side has both bishops
        int whiteBonus = whiteBishops >= 2 ? 50 : 0;
        int blackBonus = blackBishops >= 2 ? 50 : 0;

        // Scale by endgame material (more valuable when fewer pieces)
        int materialCount = countMaterial(board);
        float endgameScale = Math.max(0.5f, Math.min(1.0f, materialCount / 2000.0f));

        int whiteFinal = (int)(whiteBonus * endgameScale);
        int blackFinal = (int)(blackBonus * endgameScale);

        return whiteFinal - blackFinal;
    }

    private int evaluateKingSafety(BBoard board) {
        int[] pieces = board.getPieceBoards();
        int score = 0;

        // Evaluate white king safety
        int whiteKingSquare = board.getKingSquare(BBoard.whiteIndex);
        int whiteKingSafety = evaluateKingSafetyForSide(pieces, whiteKingSquare, true);
        score += whiteKingSafety;

        // Evaluate black king safety
        int blackKingSquare = board.getKingSquare(BBoard.blackIndex);
        int blackKingSafety = evaluateKingSafetyForSide(pieces, blackKingSquare, false);
        score -= blackKingSafety;

        return score;
    }

    private int evaluateKingSafetyForSide(int[] pieces, int kingSquare, boolean isWhite) {
        int safety = 0;
        int kingFile = kingSquare & 7;
        int kingRank = kingSquare >> 3;

        // Pawn shelter bonus (pawns near king)
        // Kingside castling typically f/g/h files
        // Queenside castling typically a/b/c files
        if (kingFile >= 5) {
            // King on kingside (f-h files) - check f, g, h pawns
            safety += evaluatePawnShelter(pieces, kingSquare, isWhite, 5, 6, 7);
        } else if (kingFile <= 2) {
            // King on queenside (a-c files) - check a, b, c pawns
            safety += evaluatePawnShelter(pieces, kingSquare, isWhite, 0, 1, 2);
        }

        // Penalize exposed king (open files around king)
        int openFiles = 0;
        for (int f = Math.max(0, kingFile - 1); f <= Math.min(7, kingFile + 1); f++) {
            boolean fileOpen = true;
            // Check if file has pawns
            for (int r = 0; r < 8; r++) {
                int sq = (r << 3) | f;
                int piece = pieces[sq];
                if (piece != BPiece.none && BPiece.getPieceType(piece) == BPiece.pawn) {
                    fileOpen = false;
                    break;
                }
            }
            if (fileOpen) openFiles++;
        }
        safety -= openFiles * 15; // Penalty for each open file near king

        return safety;
    }
    private int evaluatePawnShelter(int[] pieces, int kingSquare, boolean isWhite, int f1, int f2, int f3) {
        int shelter = 0;
        int kingRank = kingSquare >> 3;

        // Check for pawns 1-2 ranks in front of king
        for (int file : new int[]{f1, f2, f3}) {
            for (int rankOffset = 1; rankOffset <= 2; rankOffset++) {
                int shelterRank = isWhite ? kingRank + rankOffset : kingRank - rankOffset;
                if (shelterRank < 0 || shelterRank > 7) continue;

                int shelterSquare = (shelterRank << 3) | file;
                int piece = pieces[shelterSquare];

                if (piece != BPiece.none && BPiece.getPieceType(piece) == BPiece.pawn) {
                    boolean pawnIsWhite = BPiece.isWhite(piece);
                    if (pawnIsWhite == isWhite) {
                        // Bonus for friendly pawn shelter
                        shelter += rankOffset == 1 ? 20 : 10; // Closer pawn = better shelter
                    }
                }
            }
        }

        return shelter;
    }
    private int countMaterial(BBoard board) {
        int[] pieces = board.getPieceBoards();
        int total = 0;

        for (int sq = 0; sq < 64; sq++) {
            int piece = pieces[sq];
            if (piece == BPiece.none) continue;

            int type = BPiece.getPieceType(piece);
            total += switch (type) {
                case BPiece.pawn -> 100;
                case BPiece.knight -> 320;
                case BPiece.bishop -> 330;
                case BPiece.rook -> 500;
                case BPiece.queen -> 900;
                default -> 0;
            };
        }

        return total;
    }
}
