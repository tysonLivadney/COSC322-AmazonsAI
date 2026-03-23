package ubc.cosc322;

import java.util.Map;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Random;

import ygraph.ai.smartfox.games.amazons.AmazonsGameMessage;
import ygraph.ai.smartfox.games.BaseGameGUI;
import ygraph.ai.smartfox.games.GameClient;
import ygraph.ai.smartfox.games.GamePlayer;
import ygraph.ai.smartfox.games.GameMessage;

public class COSC322Test extends GamePlayer {

    private GameClient gameClient = null;
    private BaseGameGUI gamegui = null;
    private ArrayList<Integer> gameBoard = null;
    private int myColor = 0; // 1 = white, 2 = black
    private AIPlayer ai = new AIPlayer();
    private static final long[][][] ZOBRIST = new long[11][11][4];
    private static final Random ZOBRIST_RANDOM = new Random(322);

     static {
        for (int r = 0; r < 11; r++) {
            for (int c = 0; c < 11; c++) {
                for (int piece = 0; piece < 4; piece++) {
                    ZOBRIST[r][c][piece] = ZOBRIST_RANDOM.nextLong();
                }
            }
        }
    }

    private String userName = null;
    private String passwd = null;

    private long hashBoard(ArrayList<Integer> board) {
        long hash = 0L;
        for (int row = 1; row <= 10; row++) {
            for (int col = 1; col <= 10; col++) {
                int piece = board.get(row * 11 + col);
                if (piece != 0) {
                    hash ^= ZOBRIST[row][col][piece];
                }
            }
        }
        return hash;
    }

    public static void main(String[] args) {
        COSC322Test player = new COSC322Test(args[0], "cosc322");

        if (player.getGameGUI() == null) {
            player.Go();
        } else {
            BaseGameGUI.sys_setup();
            java.awt.EventQueue.invokeLater(new Runnable() {
                public void run() {
                    player.Go();
                }
            });
        }
    }

    public COSC322Test(String userName, String passwd) {
        this.userName = userName;
        this.passwd = passwd;
        this.gamegui = new BaseGameGUI(this);
    }

    @Override
    public void onLogin() {
        System.out.println("Logged in as: " + userName);
        if (gamegui != null) {
            gamegui.setRoomInformation(gameClient.getRoomList());
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean handleGameMessage(String messageType, Map<String, Object> msgDetails) {
        if (gamegui == null) return false;

        if (messageType.equals(GameMessage.GAME_STATE_BOARD)) {
            ArrayList<Integer> serverBoard = (ArrayList<Integer>) msgDetails.get(AmazonsGameMessage.GAME_STATE);
            gameBoard = new ArrayList<>(serverBoard);
            gamegui.setGameState(serverBoard);
            printBoard(gameBoard);

        } else if (messageType.equals(GameMessage.GAME_ACTION_START)) {
            //gameBoard = new ArrayList<>((ArrayList<Integer>) msgDetails.get(AmazonsGameMessage.GAME_STATE));

            String blackPlayer = (String) msgDetails.get(AmazonsGameMessage.PLAYER_BLACK);
            String whitePlayer = (String) msgDetails.get(AmazonsGameMessage.PLAYER_WHITE);

            if (userName.equals(blackPlayer)) {
                myColor = 2;
            } else if (userName.equals(whitePlayer)) {
                myColor = 1;
            } else {
                myColor = 0;
            }

            System.out.println("I am: " + (myColor == 1 ? "White" : myColor == 2 ? "Black" : "Spectator"));

            if (myColor == 1) {
                makeAndSendMove();
            }

        } else if (messageType.equals(GameMessage.GAME_ACTION_MOVE)) {
            gamegui.updateGameState(msgDetails);

            ArrayList<Integer> queenCurr = (ArrayList<Integer>) msgDetails.get(AmazonsGameMessage.QUEEN_POS_CURR);
            ArrayList<Integer> queenNext = (ArrayList<Integer>) msgDetails.get(AmazonsGameMessage.QUEEN_POS_NEXT);
            ArrayList<Integer> arrowPos  = (ArrayList<Integer>) msgDetails.get(AmazonsGameMessage.ARROW_POS);

            int fromRow = queenCurr.get(0), fromCol = queenCurr.get(1);
            int toRow   = queenNext.get(0), toCol   = queenNext.get(1);
            int arrRow  = arrowPos.get(0),  arrCol  = arrowPos.get(1);

            int fromIdx  = fromRow * 11 + fromCol;
            int toIdx    = toRow   * 11 + toCol;
            int arrowIdx = arrRow  * 11 + arrCol;

            int movingPiece = gameBoard.get(fromIdx);

            if (movingPiece == 0) {
                System.out.println("Echo of our own move, ignoring.");
                return true;
            }

            gameBoard.set(fromIdx, 0);
            gameBoard.set(toIdx, movingPiece);
            gameBoard.set(arrowIdx, 3);
            printBoard(gameBoard);

            if (myColor != 0 && movingPiece != myColor) {
                makeAndSendMove();
            }

        } else {
            System.out.println("Unhandled message: " + messageType);
        }

        return true;
    }

    private void makeAndSendMove() {
        Move move = ai.chooseMove(cloneBoard(gameBoard), myColor);

        if (move == null) {
            System.out.println("No legal moves — I lost.");
            return;
        }

        gameBoard.set(move.fr * 11 + move.fc, 0);
        gameBoard.set(move.tr * 11 + move.tc, myColor);
        gameBoard.set(move.ar * 11 + move.ac, 3);

        ArrayList<Integer> qCurr = new ArrayList<>();
        qCurr.add(move.fr);
        qCurr.add(move.fc);

        ArrayList<Integer> qNext = new ArrayList<>();
        qNext.add(move.tr);
        qNext.add(move.tc);

        ArrayList<Integer> arrow = new ArrayList<>();
        arrow.add(move.ar);
        arrow.add(move.ac);

        gameClient.sendMoveMessage(qCurr, qNext, arrow);
        gamegui.updateGameState(qCurr, qNext, arrow);
        System.out.println("Sent: " + move);
        printBoard(gameBoard);
    }

    private ArrayList<int[]> getLegalMoves(ArrayList<Integer> board, int row, int col) {
        ArrayList<int[]> moves = new ArrayList<>();
        int[][] directions = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
        };

        for (int[] dir : directions) {
            int r = row + dir[0];
            int c = col + dir[1];
            while (r >= 1 && r <= 10 && c >= 1 && c <= 10) {
                if (board.get(r * 11 + c) == 0) {
                    moves.add(new int[]{r, c});
                    r += dir[0];
                    c += dir[1];
                } else {
                    break;
                }
            }
        }
        return moves;
    }

    private ArrayList<int[]> getQueenPositions(ArrayList<Integer> board, int color) {
        ArrayList<int[]> queens = new ArrayList<>();
        for (int row = 1; row <= 10; row++) {
            for (int col = 1; col <= 10; col++) {
                if (board.get(row * 11 + col) == color) {
                    queens.add(new int[]{row, col});
                }
            }
        }
        return queens;
    }

    private ArrayList<Integer> cloneBoard(ArrayList<Integer> board) {
        return new ArrayList<>(board);
    }

    private void printBoard(ArrayList<Integer> board) {
        System.out.println("    1 2 3 4 5 6 7 8 9 10");
        for (int row = 10; row >= 1; row--) {
            System.out.printf("%2d  ", row);
            for (int col = 1; col <= 10; col++) {
                int val = board.get(row * 11 + col);
                char c = val == 0 ? '.' : val == 1 ? 'B' : val == 2 ? 'W' : val == 3 ? 'X' : '?';
                System.out.print(c + " ");
            }
            System.out.println();
        }
        System.out.println("--------------------------");
    }

    @Override public String userName() { return userName; }
    @Override public GameClient getGameClient() { return this.gameClient; }
    @Override public BaseGameGUI getGameGUI() { return this.gamegui; }
    @Override public void connect() { gameClient = new GameClient(userName, passwd, this); }

    // ------------------------------------------------------------------------

    private static class Move {
        final int fr, fc, tr, tc, ar, ac;

        Move(int fr, int fc, int tr, int tc, int ar, int ac) {
            this.fr = fr; this.fc = fc;
            this.tr = tr; this.tc = tc;
            this.ar = ar; this.ac = ac;
        }

        @Override
        public String toString() {
            return "(" + fr + "," + fc + ") -> (" + tr + "," + tc + ") arrow (" + ar + "," + ac + ")";
        }
    }

    private static class TTEntry {
        final int depth;
        final int score;

        TTEntry(int depth, int score) {
            this.depth = depth;
            this.score = score;
        }
    }

    // ------------------------------------------------------------------------

    private class AIPlayer {

        private final HashMap<Long, TTEntry> transTable = new HashMap<>();
        
        Move chooseMove(ArrayList<Integer> board, int color) {
            
            Move bestMove = null;
            long timeLimit = System.currentTimeMillis() + 5000;
            transTable.clear();
            for (int depth = 1; depth <= 10; depth++) {
                if (System.currentTimeMillis() >= timeLimit) break;
                Move candidate = minimaxRoot(board, depth, color, timeLimit);
                if (candidate == null) break;
                bestMove = candidate;
                System.out.println("Depth " + depth + " best: " + bestMove);
            }

            return bestMove;
        }

        private Move minimaxRoot(ArrayList<Integer> board, int depth, int color, long timeLimit) {
            Move bestMove = null;
            int bestScore = Integer.MIN_VALUE;
            int opponent = (color == 1) ? 2 : 1;

            for (int[] queen : getQueenPositions(board, color)) {
                for (int[] newPos : getLegalMoves(board, queen[0], queen[1])) {
                    if (System.currentTimeMillis() >= timeLimit) return bestMove;

                    int from = queen[0] * 11 + queen[1];
                    int to   = newPos[0] * 11 + newPos[1];
                    board.set(from, 0);
                    board.set(to, color);

                    for (int[] arrow : getLegalMoves(board, newPos[0], newPos[1])) {
                        int arr = arrow[0] * 11 + arrow[1];
                        board.set(arr, 3);

                        int score = minimax(board, depth - 1, false, color, opponent,
                                            Integer.MIN_VALUE, Integer.MAX_VALUE, timeLimit);

                        board.set(arr, 0);

                        if (score > bestScore) {
                            bestScore = score;
                            bestMove = new Move(queen[0], queen[1], newPos[0], newPos[1], arrow[0], arrow[1]);
                        }
                    }

                    board.set(to, 0);
                    board.set(from, color);
                }
            }

            return bestMove;
        }

        private int minimax(ArrayList<Integer> board, int depth, boolean isMaximizing,
                            int myColor, int opponent, int alpha, int beta, long timeLimit) {

            if (System.currentTimeMillis() >= timeLimit || depth == 0) {
                return evaluate(board, myColor);
            }

            int current = isMaximizing ? myColor : opponent;

            long hash = hashBoard(board)
          ^ (isMaximizing ? 1234567L : 7654321L)
          ^ (current == 1 ? 1111111L : 2222222L);
            TTEntry cached = transTable.get(hash);
            if (cached != null && cached.depth >= depth) {
                return cached.score;
            }

            
            boolean hadMove = false;
            int best = isMaximizing ? Integer.MIN_VALUE : Integer.MAX_VALUE;

            for (int[] queen : getQueenPositions(board, current)) {
                for (int[] newPos : getLegalMoves(board, queen[0], queen[1])) {
                    int from = queen[0] * 11 + queen[1];
                    int to   = newPos[0] * 11 + newPos[1];
                    board.set(from, 0);
                    board.set(to, current);

                    boolean pruned = false;
                    for (int[] arrow : getLegalMoves(board, newPos[0], newPos[1])) {
                        int arr = arrow[0] * 11 + arrow[1];
                        board.set(arr, 3);
                        hadMove = true;

                        int score = minimax(board, depth - 1, !isMaximizing,
                                            myColor, opponent, alpha, beta, timeLimit);

                        board.set(arr, 0);

                        if (isMaximizing) {
                            if (score > best) best = score;
                            if (best > alpha) alpha = best;
                        } else {
                            if (score < best) best = score;
                            if (best < beta) beta = best;
                        }

                        if (beta <= alpha) {
                            pruned = true;
                            break;
                        }
                    }

                    board.set(to, 0);
                    board.set(from, current);

                    if (pruned) break;
                }

                if (beta <= alpha) break;
            }

            if (!hadMove) {
                best = isMaximizing ? -10000 : 10000;
            }

            transTable.put(hash, new TTEntry(depth, best));
            return best;
        }

        private int evaluate(ArrayList<Integer> board, int myColor) {
        int opponent = (myColor == 1) ? 2 : 1;
        return evaluateTerritory(board, myColor, opponent);
        }

        private int evaluateTerritory(ArrayList<Integer> board, int myColor, int opponent) {
            int[][] myDist  = queenDistanceMap(board, myColor);
            int[][] oppDist = queenDistanceMap(board, opponent);
            int score = 0;

            for (int row = 1; row <= 10; row++) {
                for (int col = 1; col <= 10; col++) {
                    if (board.get(row * 11 + col) != 0) continue;
                    int md = myDist[row][col];
                    int od = oppDist[row][col];
                    if (md == Integer.MAX_VALUE && od == Integer.MAX_VALUE) continue;
                    if (md < od) score++;
                    else if (od < md) score--;
                }
            }

            return score;
        }

        private int[][] queenDistanceMap(ArrayList<Integer> board, int color) {
            int[][] dist = new int[11][11];
            for (int r = 0; r < 11; r++)
                for (int c = 0; c < 11; c++)
                    dist[r][c] = Integer.MAX_VALUE;

            ArrayDeque<int[]> queue = new ArrayDeque<>();
            for (int[] queen : getQueenPositions(board, color)) {
                dist[queen[0]][queen[1]] = 0;
                queue.add(queen);
            }

            while (!queue.isEmpty()) {
                int[] cur = queue.poll();
                int nextDist = dist[cur[0]][cur[1]] + 1;
                for (int[] move : getLegalMoves(board, cur[0], cur[1])) {
                    if (dist[move[0]][move[1]] > nextDist) {
                        dist[move[0]][move[1]] = nextDist;
                        queue.add(move);
                    }
                }
            }

            return dist;
        }
    }
}