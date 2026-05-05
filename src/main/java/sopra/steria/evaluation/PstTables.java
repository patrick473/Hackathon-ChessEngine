package sopra.steria.evaluation;

import knight.clubbing.core.BPiece;

public class PstTables {

    // See https://www.chessprogramming.org/Simplified_Evaluation_Function at the PST part
    // Tables are rank-8-first (index 0 = a8, index 63 = h1).
    // White squares are mirrored (sq ^ 56) before lookup; Black uses raw sq.
    public static final int[][] PST = initializePst();

    private static int[][] initializePst() {
        int[][] pst = new int[7][64];

        pst[BPiece.pawn] = new int[] {
                //   a    b    c    d    e    f    g    h
                0,   0,   0,   0,   0,   0,   0,   0,  // rank 8
                50,  50,  50,  50,  50,  50,  50,  50,  // rank 7
                10,  10,  20,  30,  30,  20,  10,  10,  // rank 6
                5,   5,  10,  25,  25,  10,   5,   5,  // rank 5
                0,   0,   0,  20,  20,   0,   0,   0,  // rank 4
                5,  -5, -10,   0,   0, -10,  -5,   5,  // rank 3
                5,  10,  10, -20, -20,  10,  10,   5,  // rank 2
                0,   0,   0,   0,   0,   0,   0,   0   // rank 1
        };

        pst[BPiece.knight] = new int[] {
                //   a    b    c    d    e    f    g    h
                -50, -40, -30, -30, -30, -30, -40, -50,  // rank 8
                -40, -20,   0,   0,   0,   0, -20, -40,  // rank 7
                -30,   0,  10,  15,  15,  10,   0, -30,  // rank 6
                -30,   5,  15,  20,  20,  15,   5, -30,  // rank 5
                -30,   0,  15,  20,  20,  15,   0, -30,  // rank 4
                -30,   5,  10,  15,  15,  10,   5, -30,  // rank 3
                -40, -20,   0,   5,   5,   0, -20, -40,  // rank 2
                -50, -40, -30, -30, -30, -30, -40, -50   // rank 1
        };

        pst[BPiece.bishop] = new int[] {
                //   a    b    c    d    e    f    g    h
                -20, -10, -10, -10, -10, -10, -10, -20,  // rank 8
                -10,   0,   0,   0,   0,   0,   0, -10,  // rank 7
                -10,   0,   5,  10,  10,   5,   0, -10,  // rank 6
                -10,   5,   5,  10,  10,   5,   5, -10,  // rank 5
                -10,   0,  10,  10,  10,  10,   0, -10,  // rank 4
                -10,  10,  10,  10,  10,  10,  10, -10,  // rank 3
                -10,  15,   0,   0,   0,   0,  15, -10,  // rank 2
                -20, -10, -10, -10, -10, -10, -10, -20   // rank 1
        };

        pst[BPiece.rook] = new int[] {
                //   a    b    c    d    e    f    g    h
                0,   0,   0,   0,   0,   0,   0,   0,  // rank 8
                5,  10,  10,  10,  10,  10,  10,   5,  // rank 7
                -5,   0,   0,   0,   0,   0,   0,  -5,  // rank 6
                -5,   0,   0,   0,   0,   0,   0,  -5,  // rank 5
                -5,   0,   0,   0,   0,   0,   0,  -5,  // rank 4
                -5,   0,   0,   0,   0,   0,   0,  -5,  // rank 3
                -5,   0,   0,   0,   0,   0,   0,  -5,  // rank 2
                0,   0,   0,   5,   5,   0,   0,   0   // rank 1
        };

        pst[BPiece.queen] = new int[] {
                //   a    b    c    d    e    f    g    h
                -20, -10, -10,  -5,  -5, -10, -10, -20,  // rank 8
                -10,   0,   0,   0,   0,   0,   0, -10,  // rank 7
                -10,   0,   5,   5,   5,   5,   0, -10,  // rank 6
                -5,   0,   5,   5,   5,   5,   0,  -5,  // rank 5
                0,   0,   5,   5,   5,   5,   0,  -5,  // rank 4
                -10,   5,   5,   5,   5,   5,   0, -10,  // rank 3
                -10,   0,   5,   0,   0,   0,   0, -10,  // rank 2
                -20, -10, -10,  -5,  -5, -10, -10, -20   // rank 1
        };

        pst[BPiece.king] = new int[] {
                //   a    b    c    d    e    f    g    h
                -30, -40, -40, -50, -50, -40, -40, -30,  // rank 8
                -30, -40, -40, -50, -50, -40, -40, -30,  // rank 7
                -30, -40, -40, -50, -50, -40, -40, -30,  // rank 6
                -30, -40, -40, -50, -50, -40, -40, -30,  // rank 5
                -20, -30, -30, -40, -40, -30, -30, -20,  // rank 4
                -10, -20, -20, -20, -20, -20, -20, -10,  // rank 3
                20,  20,   0,   0,   0,   0,  20,  20,  // rank 2
                20,  30,  10,   0,   0,  10,  30,  20   // rank 1
        };

        return pst;
    }
}
