package sopra.steria;

import knight.clubbing.core.BBoard;
import knight.clubbing.core.BMove;
import knight.clubbing.movegen.MoveGenerator;
import knight.clubbing.movegen.PrecomputedMoveData;
import knight.clubbing.movegen.magic.PrecomputedMagics;
import sopra.steria.ordering.BadMoveOrderer;
import sopra.steria.ordering.GoodOrderer;
import sopra.steria.search.Search;
import sopra.steria.search.SearchResult;
import sopra.steria.search.SearchSetting;
import sopra.steria.search.TranspositionTable;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.Scanner;
import java.util.logging.Logger;

import static sopra.steria.EngineConst.DEFAULT_DEPTH;

public class Uci {

    public static final String IN = "in: ";
    public static final String OUT = "out: ";
    public static final String COMMAND_LOG = "command.log";
    public static final String BOARD_LOG = "board.log";
    private final Logger logger = Logger.getLogger(getClass().getName());

    private BBoard board;
    private Thread searchThread;
    private Search search;
    private boolean enableLogging;
    private static volatile boolean engineInitialized;

    public Uci(boolean enableLogging) {
        this.enableLogging = enableLogging;
    }

    private static void initEngine() {
        PrecomputedMoveData.getInstance();
        @SuppressWarnings("unused")
        var unused = PrecomputedMagics.ROOK_MAGICS;
    }

    private static synchronized void ensureEngineInitialized() {
        if (engineInitialized) {
            return;
        }
        initEngine();
        engineInitialized = true;
    }

    public Uci() {
        this(true);
    }

    protected BBoard getBoard() {
        return board;
    }

    public void run() {
        Scanner scanner = new Scanner(System.in);

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (line == null || line.isEmpty()) continue;
            handleCommand(line.trim());
        }
    }

    protected void handleCommand(String line) {
        logText(IN + line, COMMAND_LOG);

        switch (line) {
            case "uci": {
                sendCommand("id name Hackathon Chess Engine");
                sendCommand("id author John Doe");
                sendCommand("uciok");
                break;
            }
            case "isready": {
                ensureEngineInitialized();
                sendCommand("readyok");
                break;
            }
            case "stop": {
                if (searchThread != null) searchThread.interrupt();
                break;
            }
            case "quit" : {
                if (searchThread != null) searchThread.interrupt();
                System.exit(0);
                break;
            }
            default: {
                if (line.startsWith("position")) {
                    handlePosition(line);
                } else if (line.startsWith("go")) {
                    handleGo(line);
                }
                break;
            }
        }
    }

    private void sendCommand(String line) {
        System.out.println(line);
        logText(OUT + line, COMMAND_LOG);
    }

    private void logText(String text, String location) {
        if (!enableLogging) return;

        try (PrintWriter log = new PrintWriter(new FileWriter(location, true))) {
            log.println(text);
        } catch (IOException e) {
            logger.warning("Failed to write log to '" + location + "': " + e.getMessage());
        }
    }

    protected void handlePosition(String line) {
        String[] parts = line.split(" ");
        int index = 1;

        if (parts[index].equals("startpos")) {
            board = new BBoard();
            index = 2;
        } else if (parts[index].equals("fen")) {
            StringBuilder fen = new StringBuilder();
            for (int i = 2; i < 8; i++) fen.append(parts[i]).append(" ");
            board = new BBoard(fen.toString().trim());
            index = 8;
        }

        if (index < parts.length && parts[index].equals("moves")) {
            for (int i = index + 1; i < parts.length; i++) {
                try {
                    BMove move = BMove.fromUci(parts[i], board);
                    board.makeMove(move, false);
                } catch (Exception e) {
                    logger.warning("Invalid move in position command: " + parts[i]);
                    logger.warning(e.getMessage());
                }

            }
        }
    }

    protected void handleGo(String line) {
        ensureEngineInitialized();
        if (search == null) search = new Search();
        int wtime = -1, btime = -1, winc = 0, binc = 0, depthInput = -1;
        boolean whiteToMove = board.isWhiteToMove();

        String[] parts = line.split(" ");
        for (int i = 0; i < parts.length; i++) {
            switch (parts[i]) {
                case "wtime":
                    if (i + 1 < parts.length) wtime = Integer.parseInt(parts[++i]);
                    break;
                case "btime":
                    if (i + 1 < parts.length) btime = Integer.parseInt(parts[++i]);
                    break;
                case "winc":
                    if (i + 1 < parts.length) winc = Integer.parseInt(parts[++i]);
                    break;
                case "binc":
                    if (i + 1 < parts.length) binc = Integer.parseInt(parts[++i]);
                    break;
                case "depth":
                    if (i + 1 < parts.length) depthInput = Integer.parseInt(parts[++i]);
                    break;
            }
        }

        int time = whiteToMove ? wtime : btime;
        int inc = whiteToMove ? winc : binc;
        int depth = depthInput > 0 ? depthInput : DEFAULT_DEPTH;

        searchThread = new Thread(() -> {
            String move = "";
            SearchResult resultMove = null;
            try {
                int moveTime;
                if (time > 0) {
                    moveTime = Math.min(time / 30 + inc, time / 2);
                    moveTime = Math.max(10, Math.min(moveTime, time - 10));
                } else {
                    moveTime = 60000; // 60 seconds default
                }
                SearchResult result = search.bestMove(board, new SearchSetting(depth, moveTime));
                resultMove = result;
                move = result.getBestMove();
            } catch (Throwable t) {
                t.printStackTrace();
                try (PrintWriter log = new PrintWriter(new FileWriter("engine_crash.log", true))) {
                    t.printStackTrace(log);
                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append("FEN: ").append(board.exportFen()).append("\n");
                    stringBuilder.append("Time: ").append(Instant.now()).append("\n");
                    stringBuilder.append("------\n");
                    String string = stringBuilder.toString();
                    log.println(string);
                } catch (IOException e) {
                    logger.warning("Failed to write engine crash log: " + e.getMessage());
                }
            } finally {
                if (move != null && !move.isEmpty()) {
                    sendCommand("info depth " + resultMove.getDepth() + " score cp " + resultMove.getScore() + " time " + resultMove.getTimeTakenMillis());
                    sendCommand("bestmove " + move);
                } else {
                    BMove[] someMoves = new MoveGenerator(board).generateMoves(false);
                    if (someMoves.length > 0) {
                        GoodOrderer goodMoveOrderer = new GoodOrderer();
                        goodMoveOrderer.orderMoves(someMoves, board, null, 0, TranspositionTable.NO_MOVE);
                        sendCommand("bestmove " + someMoves[0].getUci());
                    } else {
                        sendCommand("bestmove 0000");
                    }
                }
            }
        });


        searchThread.start();
    }
}
