package sopra.steria.search;

import knight.clubbing.core.BBoard;
import knight.clubbing.core.BMove;
import knight.clubbing.core.BPiece;
import knight.clubbing.movegen.MoveGenerator;
import sopra.steria.evaluation.Evaluator;
import sopra.steria.evaluation.GoodEvaluator;
import sopra.steria.helpers.Helpers;
import sopra.steria.ordering.MoveOrderer;
import sopra.steria.ordering.GoodOrderer;

import static sopra.steria.EngineConst.INF;
import static sopra.steria.EngineConst.MATE_SCORE;

public class Search {

    private volatile boolean stop;
    private long startTime;
    private SearchSetting setting;
    private long nodes;

    private final Evaluator evaluator;
    private final MoveOrderer moveOrderer;
    private BMove[][] killers;

    public Search() {
        this.evaluator = new GoodEvaluator();
        this.moveOrderer = new GoodOrderer();
        this.killers = new BMove[256][2];
    }

    public SearchResult bestMove(BBoard board, SearchSetting setting) {
        this.startTime = System.currentTimeMillis();
        this.setting = setting;
        this.stop = false;

        clearKillers();
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

        BMove[] moves = new MoveGenerator(board).generateMoves(false);
        moveOrderer.orderMoves(moves, board, killers, 0);

        for (BMove move : moves) {
            checkStop();

            board.makeMove(move, true);
            int score = -negamax(board, depth - 1, -beta, -alpha, 1);
            board.undoMove(move, true);

            if (score > bestResult.getScore()) {
                bestResult.setScore(score);
                bestResult.setBestMove(move.getUci());
            }

            alpha = Math.max(alpha, score);
        }

        bestResult.setNodesSearched(this.nodes);
        return bestResult;
    }

    private int negamax(BBoard board, int depth, int alpha, int beta, int ply) {
        nodes++;

        if (isNthNode(1023))
            checkStop();

        if (depth <= 0) return quiescence(board, alpha, beta, ply);

        // Null Move Pruning
        if (depth >= 3 && !board.isInCheck() && Helpers.hasNonPawnMaterial(board)) {
            board.makeNullMove();
            int nullScore = -negamax(board, depth - 3, -beta, -beta + 1, ply + 1);
            board.undoNullMove();

            if (nullScore >= beta) return nullScore;
        }

        int bestScore = -INF;

        BMove[] nextMoves = new MoveGenerator(board).generateMoves(false);

        moveOrderer.orderMoves(nextMoves, board, killers, ply);

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

            bestScore = Math.max(bestScore, score);
            alpha = Math.max(alpha, score);

            if (alpha >= beta) {
                int capturedPiece = board.getPieceBoards()[move.targetSquare()];
                if (capturedPiece == BPiece.none) {
                    if (killers[ply][0] == null || !killers[ply][0].equals(move)) {
                        killers[ply][1] = killers[ply][0];
                        killers[ply][0] = move;
                    }
                }
                break;
            }
        }

        return bestScore;
    }

    // Safety margin for delta pruning: roughly the value of a queen
    private static final int DELTA_MARGIN = 1050;

    /**
     * Quiescence search — extends the search beyond the horizon by continuing
     * to search captures (and all moves when in check) until the position is "quiet".
     * This avoids mis-evaluating positions with hanging pieces.
     *
     * <p>Key behaviours:
     * <ul>
     *   <li><b>Stand-pat:</b> the side to move can always choose not to capture,
     *       so the static eval is a lower bound on the true score.</li>
     *   <li><b>Delta pruning:</b> if even capturing a queen cannot raise alpha, skip.</li>
     *   <li><b>Check handling:</b> when in check, all evasions are searched (not just
     *       captures) to avoid missing forced mates.</li>
     * </ul>
     */
    private int quiescence(BBoard board, int alpha, int beta, int ply) {
        nodes++;

        if (isNthNode(1023)) checkStop();

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
                capturesOnly ? 0 : Math.min(ply, killers.length - 1));

        // When in check there is no stand-pat baseline, so start from -INF
        int bestScore = inCheck ? -INF : alpha;

        for (BMove move : moves) {
            board.makeMove(move, true);
            int score = -quiescence(board, -beta, -alpha, ply + 1);
            board.undoMove(move, true);

            if (score > bestScore) bestScore = score;
            if (score >= beta) return beta;
            if (score > alpha) alpha = score;
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

    private void clearKillers() {
        for (int i = 0; i < killers.length; i++) {
            killers[i][0] = null;
            killers[i][1] = null;
        }
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

