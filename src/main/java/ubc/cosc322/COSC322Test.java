package ubc.cosc322;

import java.util.Map;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import ygraph.ai.smartfox.games.amazons.AmazonsGameMessage;
import ygraph.ai.smartfox.games.BaseGameGUI;
import ygraph.ai.smartfox.games.GameClient;
import ygraph.ai.smartfox.games.GamePlayer;
import ygraph.ai.smartfox.games.GameMessage;

public class COSC322Test extends GamePlayer {

    private GameClient gameClient = null;
    private BaseGameGUI gamegui = null;
    private int[] gameBoard = null;
    private int myColor = 0;
    private AIPlayer ai = new AIPlayer();
    private Thread ponderThread = null;

    private static final long[][][] ZOBRIST = new long[11][11][4];
    private static final Random ZOBRIST_RANDOM = new Random(322);
    private static final long ZOBRIST_WHITE_TO_MOVE = new Random(999).nextLong();

    static {
        for (int r = 0; r < 11; r++)
            for (int c = 0; c < 11; c++)
                for (int piece = 0; piece < 4; piece++)
                    ZOBRIST[r][c][piece] = ZOBRIST_RANDOM.nextLong();
    }

    private String userName = null;
    private String passwd = null;

    private long hashBoard(int[] board) {
        long hash = 0L;
        for (int row = 1; row <= 10; row++)
            for (int col = 1; col <= 10; col++) {
                int piece = board[row * 11 + col];
                if (piece != 0) hash ^= ZOBRIST[row][col][piece];
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
        if (gamegui != null)
            gamegui.setRoomInformation(gameClient.getRoomList());
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean handleGameMessage(String messageType, Map<String, Object> msgDetails) {
        if (gamegui == null) return false;

        if (messageType.equals(GameMessage.GAME_STATE_BOARD)) {
            ArrayList<Integer> serverBoard = (ArrayList<Integer>) msgDetails.get(AmazonsGameMessage.GAME_STATE);
            gameBoard = new int[121];
            for (int i = 0; i < serverBoard.size(); i++)
                gameBoard[i] = serverBoard.get(i);
            gamegui.setGameState(serverBoard);
            printBoard(gameBoard);

        } else if (messageType.equals(GameMessage.GAME_ACTION_START)) {
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

            int fromIdx  = queenCurr.get(0) * 11 + queenCurr.get(1);
            int toIdx    = queenNext.get(0)  * 11 + queenNext.get(1);
            int arrowIdx = arrowPos.get(0)   * 11 + arrowPos.get(1);

            int movingPiece = gameBoard[fromIdx];

            if (movingPiece == 0) {
                System.out.println("Echo of our own move, ignoring.");
                return true;
            }

            gameBoard[fromIdx] = 0;
            gameBoard[toIdx] = movingPiece;
            gameBoard[arrowIdx] = 3;
            printBoard(gameBoard);

            if (myColor != 0 && movingPiece != myColor) {
                makeAndSendMove();
            }

        } else {
            System.out.println("Unhandled message: " + messageType);
        }

        return true;
    }

    private void startPondering(int[] board, int color) {
        ponderThread = new Thread(() -> ai.ponder(board, color));
        ponderThread.setDaemon(true);
        ponderThread.start();
        System.out.println("Pondering...");
    }

    private void stopPondering() {
        ai.stopPonder();
        if (ponderThread != null) {
            try { ponderThread.join(100); } catch (InterruptedException ignored) {}
            ponderThread = null;
        }
    }

    private void makeAndSendMove() {
        stopPondering();

        Move move = ai.chooseMove(cloneBoard(gameBoard), myColor);

        if (move == null) {
            System.out.println("No legal moves — I lost.");
            return;
        }

        gameBoard[move.fr * 11 + move.fc] = 0;
        gameBoard[move.tr * 11 + move.tc] = myColor;
        gameBoard[move.ar * 11 + move.ac] = 3;

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

        startPondering(cloneBoard(gameBoard), myColor);
    }

    private int[] getLegalMoves(int[] board, int row, int col) {
        int[] buf = new int[64];
        int count = 0;
        int[][] directions = {
            {1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}
        };
        for (int[] dir : directions) {
            int r = row + dir[0];
            int c = col + dir[1];
            while (r >= 1 && r <= 10 && c >= 1 && c <= 10) {
                if (board[r * 11 + c] == 0) {
                    buf[count++] = r * 11 + c;
                    r += dir[0];
                    c += dir[1];
                } else break;
            }
        }
        return Arrays.copyOf(buf, count);
    }

    private int[] getQueenPositions(int[] board, int color) {
        int[] buf = new int[4];
        int count = 0;
        for (int row = 1; row <= 10; row++)
            for (int col = 1; col <= 10; col++)
                if (board[row * 11 + col] == color)
                    buf[count++] = row * 11 + col;
        return buf;
    }

    private int[] cloneBoard(int[] board) {
        return board.clone();
    }

    private void printBoard(int[] board) {
        System.out.println("    1 2 3 4 5 6 7 8 9 10");
        for (int row = 10; row >= 1; row--) {
            System.out.printf("%2d  ", row);
            for (int col = 1; col <= 10; col++) {
                int val = board[row * 11 + col];
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

    static class TTEntry {
        static final int EXACT = 0, LOWER = 1, UPPER = 2;
        final int depth, score, flag;
        TTEntry(int depth, int score, int flag) {
            this.depth = depth; this.score = score; this.flag = flag;
        }
    }

    private static class ScoredMove {
        final Move move;
        final int score;
        ScoredMove(Move m, int s) { this.move = m; this.score = s; }
    }

    //Player class
    //------------
    private class AIPlayer {
        private final int[] territoryQueue = new int[121];
        private final int[] myDist = new int[121];
        private final int[] oppDist = new int[121];

        private static final int TT_SIZE = 1 << 20; 
        private final long[] ttKeys = new long[TT_SIZE];
        private final long[] ttData = new long[TT_SIZE]; 

        private int nodesExplored = 0;
        private int numArrows = 0;
        private volatile boolean pondering = false;

        // Directions for Queen/Arrow moves
        private final int[] dr = {-1, -1, -1, 0, 0, 1, 1, 1};
        private final int[] dc = {-1, 0, 1, -1, 1, -1, 0, 1};

        private int[] myQueens = new int[4];
        private int[] oppQueens = new int[4];

        private void initQueens(int[] board, int myColor, int oppColor) {
            int myCount = 0, oppCount = 0;
            for (int i = 12; i < 110; i++) {
                if (board[i] == myColor) myQueens[myCount++] = i;
                else if (board[i] == oppColor) oppQueens[oppCount++] = i;
            }
}

        void ponder(int[] board, int color) {
            pondering = true;
            long hash = hashBoard(board);
            if (color == 1) hash ^= ZOBRIST_WHITE_TO_MOVE;
            for (int depth = 1; depth <= 100 && pondering; depth++) {
                minimaxRoot(board, hash, depth, color, Long.MAX_VALUE);
            }
        }

        void stopPonder() { pondering = false; }

        Move chooseMove(int[] board, int color) {
            nodesExplored = 0;
            long hash = hashBoard(board);
            if (color == 1) hash ^= ZOBRIST_WHITE_TO_MOVE;
            
            Move bestMove = null;
            long timeLimit = System.currentTimeMillis() + 28000;

            for (int depth = 1; depth <= 100; depth++) {
                if (System.currentTimeMillis() >= timeLimit) break;
                Move candidate = minimaxRoot(board, hash, depth, color, timeLimit);
                if (candidate != null) {
                    bestMove = candidate;
                    System.out.println("Depth: " + depth + " | Nodes: " + nodesExplored);
                }
            }
            System.out.println("TT size: " + java.util.Arrays.stream(ttKeys).filter(k -> k != 0).count());
            return bestMove;
        }

        private Move minimaxRoot(int[] board, long hash, int depth, int color, long timeLimit) {
            int opponent = (color == 1) ? 2 : 1;
            List<ScoredMove> candidates = new ArrayList<>(2000);

            for (int qR = 1; qR <= 10; qR++) {
                for (int qC = 1; qC <= 10; qC++) {
                    int qIdx = qR * 11 + qC;
                    if (board[qIdx] != color) continue;

                    for (int i = 0; i < 8; i++) {
                        int tR = qR + dr[i], tC = qC + dc[i];
                        while (tR >= 1 && tR <= 10 && tC >= 1 && tC <= 10 && board[tR * 11 + tC] == 0) {
                            board[qIdx] = 0; board[tR * 11 + tC] = color;
                            for (int j = 0; j < 8; j++) {
                                int aR = tR + dr[j], aC = tC + dc[j];
                                while (aR >= 1 && aR <= 10 && aC >= 1 && aC <= 10 && board[aR * 11 + aC] == 0) {
                                    board[aR * 11 + aC] = 3;
                                    candidates.add(new ScoredMove(new Move(qR, qC, tR, tC, aR, aC), evaluate(board, color)));
                                    board[aR * 11 + aC] = 0;
                                    aR += dr[j]; aC += dc[j];
                                }
                            }
                            board[tR * 11 + tC] = 0; board[qIdx] = color;
                            tR += dr[i]; tC += dc[i];
                        }
                    }
                }
            }

            candidates.sort((a, b) -> b.score - a.score);

            Move bestMove = null;
            int alpha = Integer.MIN_VALUE + 1;

            for (ScoredMove sm : candidates) {
                if (!pondering && System.currentTimeMillis() >= timeLimit) break;
                Move m = sm.move;
                long nextHash = hash ^ ZOBRIST[m.fr][m.fc][color] ^ ZOBRIST[m.tr][m.tc][color] ^ ZOBRIST[m.ar][m.ac][3] ^ ZOBRIST_WHITE_TO_MOVE;
                
                board[m.fr * 11 + m.fc] = 0; board[m.tr * 11 + m.tc] = color; board[m.ar * 11 + m.ac] = 3;
                int score = -minimax(board, nextHash, depth - 1, opponent, color, Integer.MIN_VALUE + 1, -alpha, timeLimit);
                board[m.ar * 11 + m.ac] = 0; board[m.tr * 11 + m.tc] = 0; board[m.fr * 11 + m.fc] = color;

                if (score > alpha) {
                    alpha = score;
                    bestMove = m;
                }
            }
            return bestMove;
        }

        private int minimax(int[] board, long hash, int depth, int color, int opponent, int alpha, int beta, long timeLimit) {
            nodesExplored++;
            if (depth <= 0 || (!pondering && System.currentTimeMillis() >= timeLimit)) return evaluate(board, color);

            int ttIdx = (int)(hash & (TT_SIZE - 1));
            if (ttKeys[ttIdx] == hash) {
                long data = ttData[ttIdx];
                if ((int)(data >> 40) >= depth) {
                    int ttFlag = (int)((data >> 32) & 0xFF);
                    int ttScore = (int)data;
                    if (ttFlag == 0) return ttScore;
                    if (ttFlag == 1 && ttScore <= alpha) return alpha;
                    if (ttFlag == 2 && ttScore >= beta) return beta;
                }
            }

            int best = Integer.MIN_VALUE + 1;
            boolean moved = false;

            outer: for (int qR = 1; qR <= 10; qR++) {
                for (int qC = 1; qC <= 10; qC++) {
                    int qIdx = qR * 11 + qC;
                    if (board[qIdx] != color) continue;

                    for (int i = 0; i < 8; i++) {
                        int tR = qR + dr[i], tC = qC + dc[i];
                        while (tR >= 1 && tR <= 10 && tC >= 1 && tC <= 10 && board[tR * 11 + tC] == 0) {
                            board[qIdx] = 0; board[tR * 11 + tC] = color;
                            long queenHash = hash ^ ZOBRIST[qR][qC][color] ^ ZOBRIST[tR][tC][color];
                            
                            for (int j = 0; j < 8; j++) {
                                int aR = tR + dr[j], aC = tC + dc[j];
                                while (aR >= 1 && aR <= 10 && aC >= 1 && aC <= 10 && board[aR * 11 + aC] == 0) {
                                    board[aR * 11 + aC] = 3;
                                    moved = true;
                                    numArrows++;
                                    int score = -minimax(board, queenHash ^ ZOBRIST[aR][aC][3] ^ ZOBRIST_WHITE_TO_MOVE, depth - 1, opponent, color, -beta, -Math.max(alpha, best), timeLimit);
                                    numArrows--;
                                    board[aR * 11 + aC] = 0;
                                    
                                    if (score > best){
                                        best = score;
                                        if (best > alpha) alpha = best;
                                    } 
                                        
                                    if (best >= beta) {
                                        board[tR * 11 + tC] = 0; board[qIdx] = color;
                                        break outer;
                                    }
                                    aR += dr[j]; aC += dc[j];
                                }
                            }
                            board[tR * 11 + tC] = 0; board[qIdx] = color;
                            tR += dr[i]; tC += dc[i];
                        }
                    }
                }
            }

            if (!moved) return -10000 + (100 - depth);

            ttKeys[ttIdx] = hash;
            int flag = (best <= alpha) ? 1 : (best >= beta) ? 2 : 0;
            ttData[ttIdx] = ((long)depth << 40) | ((long)flag << 32) | (best & 0xFFFFFFFFL);
            return best;
        }

        private int evaluate(int[] board, int color) {
            int opp = (color == 1) ? 2 : 1;
            
            //switch to better heuristic after about 15 moves
            if (numArrows < 30) {
                 return countMovesFast(board, color) - countMovesFast(board, opp);
            }
             //scale since number is small normally
            return evaluateTerritory(board, color, opp) * 10;
            
        }


        private int evaluateTerritory(int[] board, int myColor, int opponent) {
            
            computeDistances(board, myColor, myDist, territoryQueue);
            computeDistances(board, opponent, oppDist, territoryQueue);
            
            int score = 0;
            for (int i = 12; i < 110; i++) {
                if (board[i] == 0) {
                    if (myDist[i] < oppDist[i]) score++;
                    else if (oppDist[i] < myDist[i]) score--;
                }
            }
            return score;
        }

        private int territoryToken = 1;
        private int[] visited = new int[121];

        private void computeDistances(int[] board, int color, int[] dist, int[] queue) {
            territoryToken++;
            int head = 0, tail = 0;
            
            // Initialize BFS with all 4 queen positions
            for (int i = 12; i < 110; i++) {
                if (board[i] == color) {
                    dist[i] = 0;
                    visited[i] = territoryToken; //mark visited
                    queue[tail++] = i;
                }
            }
            
            while (head < tail) {
                int curr = queue[head++];
                int d = dist[curr] + 1;
                
                int r = curr / 11, c = curr % 11;
                for (int i = 0; i < 8; i++) {
                    int nr = r + dr[i], nc = c + dc[i];
                    int next = nr * 11 + nc;
                    while (nr >= 1 && nr <= 10 && nc >= 1 && nc <= 10 && board[next] == 0) {
                        if (visited[next] != territoryToken) { // Only process if not seen in THIS run
                            dist[next] = d;
                            visited[next] = territoryToken;
                            queue[tail++] = next;
                        }
                        nr += dr[i]; nc += dc[i];
                        next = nr * 11 + nc;
                    }
                }
            }
        }

        private int countMovesFast(int[] board, int color) {
            int count = 0;
            for (int r = 1; r <= 10; r++) {
                for (int c = 1; c <= 10; c++) {
                    if (board[r * 11 + c] != color) continue;
                    for (int i = 0; i < 8; i++) {
                        int nr = r + dr[i], nc = c + dc[i];
                        while (nr >= 1 && nr <= 10 && nc >= 1 && nc <= 10 && board[nr * 11 + nc] == 0) {
                            count++; nr += dr[i]; nc += dc[i];
                        }
                    }
                }
            }
            return count;
        }
    }
    
}