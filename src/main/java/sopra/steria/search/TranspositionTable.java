package sopra.steria.search;

import knight.clubbing.core.BMove;

import static sopra.steria.EngineConst.MATE_SCORE;

/**
 * Fixed-size transposition table using parallel primitive arrays to avoid
 * per-entry object allocation and GC pressure. Clearing is O(1) via a
 * generation counter.
 *
 * Moves are stored as packed ints (see {@link #encodeMove}) to avoid String
 * allocation and slow equals-based comparison on the move-ordering hot path.
 * Use {@link #NO_MOVE} to indicate "no move stored".
 */
public class TranspositionTable {

    // ~1M entries; power-of-two so we can mask instead of mod
    private static final int SIZE = 1 << 20;
    private static final int MASK = SIZE - 1;

    private static final int MATE_BOUND = MATE_SCORE - 200;

    public static final byte EXACT = 0;
    public static final byte LOWER_BOUND = 1;
    public static final byte UPPER_BOUND = 2;

    /** Sentinel meaning "no best move stored". */
    public static final int NO_MOVE = 0;

    // Flat parallel arrays — no per-entry heap allocation
    private final long[] hashes    = new long[SIZE];
    private final int[]  depths    = new int[SIZE];
    private final int[]  scores    = new int[SIZE];
    private final byte[] flags     = new byte[SIZE];
    private final int[]  bestMoves = new int[SIZE];  // packed: from | to<<6 | promo<<12
    private final int[]  gens      = new int[SIZE];  // generation stamp

    private int currentGen = 0;

    /**
     * A view returned by probe — backed by the table arrays.
     * NOTE: This is a shared mutable instance. Copy fields to locals before
     * making any recursive call that might probe again, or the data will be
     * overwritten.
     */
    public static class TTEntry {
        public long hash;
        public int  depth;
        public int  score;
        public byte flag;
        public int  bestMove;  // packed; see encodeMove / decode helpers
    }

    // Reuse a single TTEntry object to avoid allocation on every probe
    private final TTEntry cached = new TTEntry();

    public TTEntry probe(long hash) {
        int idx = (int)(hash & MASK);
        if (gens[idx] != currentGen || hashes[idx] != hash) return null;
        cached.hash     = hashes[idx];
        cached.depth    = depths[idx];
        cached.score    = scores[idx];
        cached.flag     = flags[idx];
        cached.bestMove = bestMoves[idx];
        return cached;
    }

    public void store(long hash, int depth, int score, byte flag, int bestMove) {
        int idx = (int)(hash & MASK);
        // Keep the existing entry if it's from this search and deeper
        if (gens[idx] == currentGen && hashes[idx] == hash && depths[idx] > depth) {
            return;
        }
        hashes[idx]    = hash;
        depths[idx]    = depth;
        scores[idx]    = score;
        flags[idx]     = flag;
        bestMoves[idx] = bestMove;
        gens[idx]      = currentGen;
    }

    /** O(1) clear — just bump the generation counter. */
    public void clear() {
        currentGen++;
    }

    public static int scoreToTT(int score, int ply) {
        if (score >  MATE_BOUND) return score + ply;
        if (score < -MATE_BOUND) return score - ply;
        return score;
    }

    public static int scoreFromTT(int score, int ply) {
        if (score >  MATE_BOUND) return score - ply;
        if (score < -MATE_BOUND) return score + ply;
        return score;
    }

    // --- Move encoding ---------------------------------------------------
    // Layout: bits 0–5 = from (0–63), 6–11 = to (0–63), 12–14 = promotion piece type

    public static int encodeMove(int from, int to, int promoPieceType) {
        return (from & 0x3F) | ((to & 0x3F) << 6) | ((promoPieceType & 0x7) << 12);
    }

    /** Pack a BMove into a single int for TT storage and ordering comparison. */
    public static int encode(BMove m) {
        int promo = m.isPromotion() ? m.promotionPieceType() : 0;
        return encodeMove(m.startSquare(), m.targetSquare(), promo);
    }

    public static int moveFrom(int packed)  { return  packed        & 0x3F; }
    public static int moveTo(int packed)    { return (packed >>> 6) & 0x3F; }
    public static int movePromo(int packed) { return (packed >>> 12) & 0x7; }
}