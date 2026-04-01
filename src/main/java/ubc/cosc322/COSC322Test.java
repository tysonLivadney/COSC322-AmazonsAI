package ubc.cosc322;

import java.util.Map;
import java.util.ArrayList;
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
                myColor = 1;
            } else if (userName.equals(whitePlayer)) {
                myColor = 2;
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

    // -------------------------------------------------------------------------
    // Move / ScoredMove
    // -------------------------------------------------------------------------
    private static class Move {
        final int fr, fc, tr, tc, ar, ac;
        Move(int fr, int fc, int tr, int tc, int ar, int ac) {
            this.fr=fr; this.fc=fc; this.tr=tr; this.tc=tc; this.ar=ar; this.ac=ac;
        }
        @Override
        public String toString() {
            return "("+fr+","+fc+") -> ("+tr+","+tc+") arrow ("+ar+","+ac+")";
        }
    }

    private static class ScoredMove {
        final Move move;
        final int score;
        ScoredMove(Move m, int s) { this.move=m; this.score=s; }
    }

    // =========================================================================
    // AIPlayer
    // =========================================================================
    private class AIPlayer {

        // killers[depth] = { k1_tr, k1_tc, k2_tr, k2_tc }
        private final int[][] killers = new int[101][4];
        // history[fromFlat][toFlat]
        private final int[][] history = new int[121][121];

        private static final int TT_SIZE = 1 << 21; // 2M entries
        private final long[] ttKeys  = new long[TT_SIZE];
        private final long[] ttData  = new long[TT_SIZE]; // [depth(8)|flag(8)|score(32) as signed]

        private int nodesExplored = 0;
        private int gameMoveCount = 0;
        private volatile boolean pondering = false;
        private volatile long timeLimit;

        private final int[] dr = {-1,-1,-1, 0, 0, 1, 1, 1};
        private final int[] dc = {-1, 0, 1,-1, 1,-1, 0, 1};

        // TT flag constants
        private static final int FLAG_EXACT = 0;
        private static final int FLAG_UPPER = 1; // score is an upper bound (alpha node)
        private static final int FLAG_LOWER = 2; // score is a lower bound (beta cutoff)

        void ponder(int[] board, int color) {
            pondering = true;
            long hash = hashBoard(board);
            if (color == 1) hash ^= ZOBRIST_WHITE_TO_MOVE;
            int prevScore = 0;
            for (int depth = 1; depth <= 100 && pondering; depth++) {
                prevScore = minimaxRootAspiration(board, hash, depth, color, prevScore, Long.MAX_VALUE);
            }
        }

        void stopPonder() { pondering = false; }

        Move chooseMove(int[] board, int color) {
            nodesExplored = 0;
            gameMoveCount++;
            for (int[] row : killers) java.util.Arrays.fill(row, 0);
            for (int[] row : history)  java.util.Arrays.fill(row, 0);

            long hash = hashBoard(board);
            if (color == 1) hash ^= ZOBRIST_WHITE_TO_MOVE;

            timeLimit = System.currentTimeMillis() + 29_000;

            Move bestMove = null;
            int prevScore = 0;

            for (int depth = 1; depth <= 100; depth++) {
                if (System.currentTimeMillis() >= timeLimit) break;
                Object[] result = minimaxRootAspirationFull(board, hash, depth, color, prevScore);
                if (result != null) {
                    bestMove  = (Move) result[0];
                    prevScore = (int)  result[1];
                    System.out.println("Depth: " + depth + " | Score: " + prevScore + " | Nodes: " + nodesExplored);
                }
            }
            return bestMove;
        }

        // Returns [bestMove, bestScore], or null if time expired before any move tried
        private Object[] minimaxRootAspirationFull(int[] board, long hash, int depth, int color, int prevScore) {
            int opponent = (color == 1) ? 2 : 1;
            int WINDOW = 80;
            int alpha = prevScore - WINDOW, beta = prevScore + WINDOW;

            while (true) {
                Object[] res = minimaxRootSearch(board, hash, depth, color, opponent, alpha, beta);
                if (res == null) return null; // time expired
                int score = (int) res[1];
                if (score <= alpha) {
                    alpha = Integer.MIN_VALUE + 1;
                } else if (score >= beta) {
                    beta = Integer.MAX_VALUE - 1;
                } else {
                    return res;
                }
            }
        }

        // Only used by ponder (no move return needed)
        private int minimaxRootAspiration(int[] board, long hash, int depth, int color, int prevScore, long tl) {
            timeLimit = tl;
            int WINDOW = 80;
            int alpha = prevScore - WINDOW, beta = prevScore + WINDOW;
            while (true) {
                Object[] res = minimaxRootSearch(board, hash, depth, color, (color==1)?2:1, alpha, beta);
                if (res == null) return prevScore;
                int score = (int) res[1];
                if (score <= alpha) alpha = Integer.MIN_VALUE + 1;
                else if (score >= beta) beta = Integer.MAX_VALUE - 1;
                else return score;
            }
        }

        private Object[] minimaxRootSearch(int[] board, long hash, int depth, int color, int opponent, int alpha, int beta) {
            // Build and score root moves
            List<ScoredMove> candidates = new ArrayList<>(2000);

            for (int qR = 1; qR <= 10; qR++) {
                for (int qC = 1; qC <= 10; qC++) {
                    int qIdx = qR * 11 + qC;
                    if (board[qIdx] != color) continue;

                    for (int i = 0; i < 8; i++) {
                        int tR = qR + dr[i], tC = qC + dc[i];
                        while (tR>=1&&tR<=10&&tC>=1&&tC<=10&&board[tR*11+tC]==0) {
                            board[qIdx]=0; board[tR*11+tC]=color;

                            // Queen-move order: mobility after move
                            int qMob = queenMobility(board, tR, tC);
                            int fromFlat = qR*11+qC, toFlat = tR*11+tC;
                            int baseScore = qMob
                                + history[fromFlat][toFlat]
                                + (tR==killers[1][0]&&tC==killers[1][1] ? 5000 : 0)
                                + (tR==killers[1][2]&&tC==killers[1][3] ? 3000 : 0);

                            for (int j = 0; j < 8; j++) {
                                int aR = tR + dr[j], aC = tC + dc[j];
                                while (aR>=1&&aR<=10&&aC>=1&&aC<=10&&board[aR*11+aC]==0) {
                                    board[aR*11+aC]=3;
                                    // Arrow ordering: prefer arrows that block opponent most (reduce opp mobility)
                                    int arrowScore = baseScore - queenMobility(board, tR, tC);
                                    candidates.add(new ScoredMove(new Move(qR,qC,tR,tC,aR,aC), arrowScore));
                                    board[aR*11+aC]=0;
                                    aR+=dr[j]; aC+=dc[j];
                                }
                            }
                            board[tR*11+tC]=0; board[qIdx]=color;
                            tR+=dr[i]; tC+=dc[i];
                        }
                    }
                }
            }

            if (candidates.isEmpty()) return null;
            candidates.sort((a, b) -> b.score - a.score);

            Move bestMove = candidates.get(0).move;
            int bestScore = Integer.MIN_VALUE + 1;

            for (ScoredMove sm : candidates) {
                if (!pondering && System.currentTimeMillis() >= timeLimit) {
                    if (bestMove == null) return null;
                    break;
                }
                Move m = sm.move;
                long nextHash = hash
                    ^ ZOBRIST[m.fr][m.fc][color]
                    ^ ZOBRIST[m.tr][m.tc][color]
                    ^ ZOBRIST[m.ar][m.ac][3]
                    ^ ZOBRIST_WHITE_TO_MOVE;

                board[m.fr*11+m.fc]=0; board[m.tr*11+m.tc]=color; board[m.ar*11+m.ac]=3;
                int score = -minimax(board, nextHash, depth-1, opponent, color,
                                     Integer.MIN_VALUE+1, -Math.max(alpha, bestScore));
                board[m.ar*11+m.ac]=0; board[m.tr*11+m.tc]=0; board[m.fr*11+m.fc]=color;

                if (score > bestScore) {
                    bestScore = score;
                    bestMove  = m;
                }
                if (bestScore >= beta) break;
            }
            return new Object[]{ bestMove, bestScore };
        }

        // -beta is passed as alpha, so the call signature is (board, hash, depth, color, opponent, alpha, beta)
        // We use fail-soft negamax
        private int minimax(int[] board, long hash, int depth, int color,
                            int opponent, int alpha, int beta) {
            nodesExplored++;

            if (!pondering && System.currentTimeMillis() >= timeLimit)
                return evaluate(board, color);

            // TT lookup
            int ttIdx = (int)(hash & (TT_SIZE - 1));
            if (ttKeys[ttIdx] == hash) {
                long data   = ttData[ttIdx];
                int ttDepth = (int)((data >> 40) & 0xFF);
                if (ttDepth >= depth) {
                    int ttFlag  = (int)((data >> 32) & 0xFF);
                    int ttScore = (int)(data & 0xFFFFFFFFL); // sign-extended below
                    ttScore = (int)(data); // correctly sign-extend 32-bit
                    if (ttFlag == FLAG_EXACT)               return ttScore;
                    if (ttFlag == FLAG_LOWER && ttScore >= beta)  return ttScore;
                    if (ttFlag == FLAG_UPPER && ttScore <= alpha) return ttScore;
                }
            }

            if (depth <= 0) return evaluate(board, color);

            // Generate moves into flat arrays for cache efficiency
            int[] moves  = new int[6 * 3000];
            int[] scores = new int[3000];
            int moveCount = 0;

            for (int qR = 1; qR <= 10; qR++) {
                for (int qC = 1; qC <= 10; qC++) {
                    int qIdx = qR * 11 + qC;
                    if (board[qIdx] != color) continue;
                    for (int i = 0; i < 8; i++) {
                        int tR = qR + dr[i], tC = qC + dc[i];
                        while (tR>=1&&tR<=10&&tC>=1&&tC<=10&&board[tR*11+tC]==0) {
                            board[qIdx]=0; board[tR*11+tC]=color;

                            int qMob = queenMobility(board, tR, tC);
                            int fromFlat = qR*11+qC, toFlat = tR*11+tC;
                            int baseScore = qMob
                                + (depth >= 3 ? history[fromFlat][toFlat] : 0)
                                + (depth >= 3 && tR==killers[depth][0]&&tC==killers[depth][1] ? 5000 : 0)
                                + (depth >= 3 && tR==killers[depth][2]&&tC==killers[depth][3] ? 3000 : 0);

                            for (int j = 0; j < 8; j++) {
                                int aR = tR + dr[j], aC = tC + dc[j];
                                while (aR>=1&&aR<=10&&aC>=1&&aC<=10&&board[aR*11+aC]==0) {
                                    board[aR*11+aC]=3;
                                    if (moveCount < 3000) {
                                        int s = moveCount * 6;
                                        moves[s]=qR; moves[s+1]=qC;
                                        moves[s+2]=tR; moves[s+3]=tC;
                                        moves[s+4]=aR; moves[s+5]=aC;
                                        // Arrow score: keep own mobility high after arrow
                                        scores[moveCount] = baseScore - queenMobility(board, tR, tC);
                                        moveCount++;
                                    }
                                    board[aR*11+aC]=0;
                                    aR+=dr[j]; aC+=dc[j];
                                }
                            }
                            board[tR*11+tC]=0; board[qIdx]=color;
                            tR+=dr[i]; tC+=dc[i];
                        }
                    }
                }
            }

            if (moveCount == 0) return -10_000 + (100 - depth);

            // Insertion sort descending by score
            for (int i = 1; i < moveCount; i++) {
                int key = scores[i];
                int ki  = i * 6;
                int m0=moves[ki],m1=moves[ki+1],m2=moves[ki+2],
                    m3=moves[ki+3],m4=moves[ki+4],m5=moves[ki+5];
                int j = i - 1;
                while (j >= 0 && scores[j] < key) {
                    scores[j+1] = scores[j];
                    int ji=j*6, ji1=(j+1)*6;
                    moves[ji1]=moves[ji]; moves[ji1+1]=moves[ji+1]; moves[ji1+2]=moves[ji+2];
                    moves[ji1+3]=moves[ji+3]; moves[ji1+4]=moves[ji+4]; moves[ji1+5]=moves[ji+5];
                    j--;
                }
                scores[j+1] = key;
                int ji1=(j+1)*6;
                moves[ji1]=m0; moves[ji1+1]=m1; moves[ji1+2]=m2;
                moves[ji1+3]=m3; moves[ji1+4]=m4; moves[ji1+5]=m5;
            }

            int best     = Integer.MIN_VALUE + 1;
            int origAlpha = alpha;
            int bestTR = -1, bestTC = -1, bestFR = -1, bestFC = -1;

            for (int idx = 0; idx < moveCount; idx++) {
                if (!pondering && System.currentTimeMillis() >= timeLimit) break;
                int s  = idx * 6;
                int fr=moves[s],fc=moves[s+1],tr=moves[s+2],tc=moves[s+3],ar=moves[s+4],ac=moves[s+5];

                board[fr*11+fc]=0; board[tr*11+tc]=color; board[ar*11+ac]=3;
                long nextHash = hash ^ ZOBRIST[fr][fc][color] ^ ZOBRIST[tr][tc][color]
                                     ^ ZOBRIST[ar][ac][3] ^ ZOBRIST_WHITE_TO_MOVE;

                int score;
                if (idx == 0) {
                    // Full window for first move
                    score = -minimax(board, nextHash, depth-1, opponent, color, -beta, -alpha);
                } else {
                    // PVS: null window search, then re-search if it improves
                    score = -minimax(board, nextHash, depth-1, opponent, color, -alpha-1, -alpha);
                    if (score > alpha && score < beta) {
                        score = -minimax(board, nextHash, depth-1, opponent, color, -beta, -alpha);
                    }
                }

                board[ar*11+ac]=0; board[tr*11+tc]=0; board[fr*11+fc]=color;

                if (score > best) {
                    best   = score;
                    bestFR = fr; bestFC = fc;
                    bestTR = tr; bestTC = tc;
                    if (best > alpha) alpha = best;
                }
                if (best >= beta) {
                    if (depth >= 3) {
                        killers[depth][2] = killers[depth][0];
                        killers[depth][3] = killers[depth][1];
                        killers[depth][0] = tr;
                        killers[depth][1] = tc;
                        int fromFlat = fr*11+fc, toFlat = tr*11+tc;
                        history[fromFlat][toFlat] = Math.min(
                            history[fromFlat][toFlat] + depth*depth, 9000);
                    }
                    break;
                }
            }

            // TT store
            int flag = (best <= origAlpha) ? FLAG_UPPER
                     : (best >= beta)      ? FLAG_LOWER
                     :                       FLAG_EXACT;
            ttKeys[ttIdx] = hash;
            ttData[ttIdx] = ((long)Math.min(depth, 0xFF) << 40)
                          | ((long)(flag & 0xFF) << 32)
                          | (best & 0xFFFFFFFFL);
            return best;
        }

        // ---------------------------------------------------------------
        // Evaluation
        // ---------------------------------------------------------------

        private int evaluate(int[] board, int color) {
            int opp = (color == 1) ? 2 : 1;

            if (gameMoveCount < 6) {
                // Opening: pure mobility
                return countMovesFast(board, color) - countMovesFast(board, opp);
            }

            // Territory via queen-distance BFS (correct sliding moves)
            int[] myDist  = computeDistances(board, color);
            int[] oppDist = computeDistances(board, opp);

            int territory = 0;
            int myMobility = 0, oppMobility = 0;

            for (int r = 1; r <= 10; r++) {
                for (int c = 1; c <= 10; c++) {
                    int i = r * 11 + c;
                    if (board[i] == 0) {
                        int md = myDist[i];
                        int od = oppDist[i];
                        if (md == 0 && od == 0) continue; // unreachable by both
                        if (md < od) {
                            territory += (od == Integer.MAX_VALUE/2) ? 2 : 1;
                        } else if (od < md) {
                            territory -= (md == Integer.MAX_VALUE/2) ? 2 : 1;
                        }
                        // Mobility contribution: reachable in 1 move
                        if (md == 1) myMobility++;
                        if (od == 1) oppMobility++;
                    }
                }
            }

            // Weighted combination: territory is primary, mobility secondary
            int mobilityWeight = Math.max(1, 10 - gameMoveCount / 4);
            return territory * 10 + (myMobility - oppMobility) * mobilityWeight;
        }

        // BFS using queen-slide distances (each square on a ray costs 1 queen move)
        // Returns distance[i] = min queen moves from any piece of 'color' to square i
        private int[] computeDistances(int[] board, int color) {
            final int INF = Integer.MAX_VALUE / 2;
            int[] dist    = new int[121];
            int[] queue   = new int[121];
            java.util.Arrays.fill(dist, INF);
            int head = 0, tail = 0;

            // Seed with queen positions (distance 0)
            for (int r = 1; r <= 10; r++) {
                for (int c = 1; c <= 10; c++) {
                    int i = r * 11 + c;
                    if (board[i] == color) {
                        dist[i] = 0;
                        queue[tail++] = i;
                    }
                }
            }

            // BFS: one queen move = distance 1 from source queen
            // Each square along a ray from a reached square has the same BFS level
            while (head < tail) {
                int curr = queue[head++];
                int d    = dist[curr] + 1; // next BFS level
                int r    = curr / 11, c = curr % 11;

                // From curr, slide in all 8 directions — each square on ray costs 1 more move
                for (int i = 0; i < 8; i++) {
                    int nr = r + dr[i], nc = c + dc[i];
                    while (nr>=1&&nr<=10&&nc>=1&&nc<=10) {
                        int next = nr*11+nc;
                        if (board[next] != 0) break; // blocked
                        if (d < dist[next]) {
                            dist[next] = d;
                            queue[tail++] = next;
                        }
                        nr+=dr[i]; nc+=dc[i];
                        // Squares further along this ray cost the SAME move count from curr
                        // They're all reachable in one queen move from curr, so don't increment d here.
                        // However, from those squares they can reach others in d+1.
                        // To handle this properly: each square on the same ray gets distance d,
                        // but we don't re-add them with d again—we let BFS propagate naturally.
                        // Actually correct: all squares on one ray from curr = d moves from source.
                        // We only need dist[next] = d for all squares on that ray (same queen move).
                    }
                }
            }
            return dist;
        }

        private int queenMobility(int[] board, int r, int c) {
            int count = 0;
            for (int i = 0; i < 8; i++) {
                int nr = r + dr[i], nc = c + dc[i];
                while (nr>=1&&nr<=10&&nc>=1&&nc<=10&&board[nr*11+nc]==0) {
                    count++; nr+=dr[i]; nc+=dc[i];
                }
            }
            return count;
        }

        private int countMovesFast(int[] board, int color) {
            int count = 0;
            for (int r = 1; r <= 10; r++) {
                for (int c = 1; c <= 10; c++) {
                    if (board[r*11+c] != color) continue;
                    for (int i = 0; i < 8; i++) {
                        int nr = r+dr[i], nc = c+dc[i];
                        while (nr>=1&&nr<=10&&nc>=1&&nc<=10&&board[nr*11+nc]==0) {
                            count++; nr+=dr[i]; nc+=dc[i];
                        }
                    }
                }
            }
            return count;
        }
    }
}