package sopra.steria.search;

import knight.clubbing.core.BBoard;
import knight.clubbing.core.BMove;
import knight.clubbing.movegen.MoveGenerator;
import sopra.steria.evaluation.Evaluator;
import sopra.steria.evaluation.GoodEvaluator;
import sopra.steria.ordering.MoveOrderer;
import sopra.steria.ordering.GoodOrderer;

import static sopra.steria.EngineConst.INF;
import static sopra.steria.EngineConst.MATE_SCORE;
import static sopra.steria.search.TranspositionTable.*;

public class Search {

    private volatile boolean stop;
    private long startTime;
    private SearchSetting setting;
    private long nodes;

    private final Evaluator evaluator;
    private final MoveOrderer moveOrderer;
    private BMove[][] killers;
    private final TranspositionTable tt;

    public Search() {
        this.evaluator = new GoodEvaluator();
        this.moveOrderer = new GoodOrderer();
        this.killers = new BMove[32][2];
        this.tt = new TranspositionTable();
    }

    public SearchResult bestMove(BBoard board, SearchSetting setting) {
        this.startTime = System.currentTimeMillis();
        this.setting = setting;
        this.stop = false;
        this.killers = new BMove[32][2]; // reset killers each search
        tt.clear();

        SearchResult bestResult = new SearchResult();
        bestResult.setScore(-INF);

        for (int depth = 1; depth <= setting.maxDepth(); depth++) {
            try {
                SearchResult result = searchDepth(board, depth);
                bestResult = result;

                checkStop();

                long elapsed = getTimeTakenMillis();
                String pv = result.getBestMove() != null ? result.getBestMove() : "";
                System.out.println("info depth " + depth + " score cp " + result.getScore() + " time " + elapsed + " pv " + pv);

                if (isDecisive(result)) break;
            } catch (SearchInterruptedException e) {
                break;
            }
        }

        bestResult.setTimeTakenMillis(getTimeTakenMillis());
        return bestResult;
    }

    private SearchResult searchDepth(BBoard board, int depth) {
        SearchResult bestResult = new SearchResult();
        bestResult.setScore(-INF);
        bestResult.setDepth(depth);
        int alpha = -INF;
        int beta = INF;
        this.nodes = 0;

        long rootHash = board.getState().getZobristKey();
        BMove[] moves = new MoveGenerator(board).generateMoves(false);

        // Use TT move from previous iteration for root ordering
        TranspositionTable.TTEntry rootEntry = tt.probe(rootHash);
        int ttMove = rootEntry != null ? rootEntry.bestMove : NO_MOVE;
        moveOrderer.orderMoves(moves, board, killers, 0, ttMove);

        int originalAlpha = alpha;
        int bestMovePacked = NO_MOVE;

        for (BMove move : moves) {
            checkStop();

            board.makeMove(move, true);
            int score = -negamax(board, depth - 1, -beta, -alpha, 1);
            board.undoMove(move, true);

            if (score > bestResult.getScore()) {
                bestResult.setScore(score);
                bestResult.setBestMove(move.getUci());
                bestMovePacked = encode(move);
            }

            alpha = Math.max(alpha, score);
        }

        // Store the root entry so the next ID iteration probes the right move first
        byte flag;
        if      (bestResult.getScore() <= originalAlpha) flag = UPPER_BOUND;
        else if (bestResult.getScore() >= beta)          flag = LOWER_BOUND;
        else                                             flag = EXACT;
        tt.store(rootHash, depth, scoreToTT(bestResult.getScore(), 0), flag, bestMovePacked);

        bestResult.setNodesSearched(this.nodes);
        return bestResult;
    }

    private int negamax(BBoard board, int depth, int alpha, int beta, int ply) {
        nodes++;

        if (isNthNode(1023))
            checkStop();

        if (depth <= 0) return quiescence(board, alpha, beta, ply);

        // --- Transposition table probe ---
        long hash = board.getState().getZobristKey();
        TranspositionTable.TTEntry entry = tt.probe(hash);
        int ttMove = NO_MOVE;

        if (entry != null) {
            ttMove = entry.bestMove;
            if (entry.depth >= depth) {
                int ttScore = scoreFromTT(entry.score, ply);
                if (entry.flag == EXACT) return ttScore;
                if (entry.flag == LOWER_BOUND) alpha = Math.max(alpha, ttScore);
                if (entry.flag == UPPER_BOUND) beta  = Math.min(beta,  ttScore);
                if (alpha >= beta) return ttScore;
            }
        }

        int originalAlpha = alpha;
        int bestScore = -INF;
        int bestMovePacked = NO_MOVE;

        BMove[] nextMoves = new MoveGenerator(board).generateMoves(false);
        moveOrderer.orderMoves(nextMoves, board, killers, ply, ttMove);

        if (nextMoves.length == 0) {
            if (board.isInCheck())
                return -MATE_SCORE + ply;
            else
                return 0;
        }

        if (board.isDrawByRepetition()) {
            return 0;
        }

        for (BMove move : nextMoves) {
            board.makeMove(move, true);
            int score = -negamax(board, depth - 1, -beta, -alpha, ply + 1);
            board.undoMove(move, true);

            if (score > bestScore) {
                bestScore = score;
                bestMovePacked = encode(move);
            }
            alpha = Math.max(alpha, score);

            if (alpha >= beta) {
                if (killers[ply][0] == null || !killers[ply][0].equals(move)) {
                    killers[ply][1] = killers[ply][0];
                    killers[ply][0] = move;
                }
                break;
            }
        }

        // --- Transposition table store ---
        byte flag;
        if      (bestScore <= originalAlpha) flag = UPPER_BOUND;
        else if (bestScore >= beta)          flag = LOWER_BOUND;
        else                                 flag = EXACT;

        tt.store(hash, depth, scoreToTT(bestScore, ply), flag, bestMovePacked);

        return bestScore;
    }

    // Safety margin for delta pruning: roughly the value of a queen
    private static final int DELTA_MARGIN = 1050;

    /**
     * Quiescence search — extends the search beyond the horizon by continuing
     * to search captures (and all moves when in check) until the position is "quiet".
     * This avoids mis-evaluating positions with hanging pieces.
     */
    private int quiescence(BBoard board, int alpha, int beta, int ply) {
        nodes++;

        if (isNthNode(1023)) checkStop();

        // Hard limit to prevent explosion in perpetual-check lines
        if (ply >= 64) return evaluator.evaluate(board);

        boolean inCheck = board.isInCheck();

        if (!inCheck) {
            int standPat = evaluator.evaluate(board);
            if (standPat >= beta) return beta;              // stand-pat beta cutoff
            alpha = Math.max(alpha, standPat);              // stand-pat raises alpha
            if (standPat + DELTA_MARGIN < alpha) return alpha; // delta prune
        }

        // In check: generate all evasions; otherwise only captures
        boolean capturesOnly = !inCheck;
        BMove[] moves = new MoveGenerator(board).generateMoves(capturesOnly);

        if (inCheck && moves.length == 0) return -MATE_SCORE + ply; // checkmate

        moveOrderer.orderMoves(moves, board, capturesOnly ? null : killers,
                capturesOnly ? 0 : Math.min(ply, killers.length - 1), NO_MOVE);

        // When in check there is no stand-pat baseline, so start from -INF
        int bestScore = inCheck ? -INF : alpha;

        for (BMove move : moves) {
            board.makeMove(move, true);
            int score = -quiescence(board, -beta, -alpha, ply + 1);
            board.undoMove(move, true);

            if (score > bestScore) {
                bestScore = score;
                if (score > alpha) alpha = score;
            }
            if (bestScore >= beta) return bestScore;
        }

        return bestScore;
    }

    private boolean isNthNode(int n) {
        return (nodes & n) == 0;
    }

    private boolean isDecisive(SearchResult result) {
        return Math.abs(result.getScore()) >= MATE_SCORE - result.getDepth();
    }

    private long getTimeTakenMillis() {
        return System.currentTimeMillis() - startTime;
    }

    private void checkStop() {
        if (stop) throw new SearchInterruptedException();
        if (Thread.currentThread().isInterrupted()) {
            stop = true;
            throw new SearchInterruptedException();
        }

        if (setting.timeLimit() > 0 && getTimeTakenMillis() >= setting.timeLimit()) {
            stop = true;
            throw new SearchInterruptedException();
        }
    }
}