package ubc.cosc322;

import java.util.Map;
import java.util.ArrayList;
import java.lang.reflect.Array;
import java.util.ArrayDeque;

import ygraph.ai.smartfox.games.amazons.AmazonsGameMessage;
import ygraph.ai.smartfox.games.BaseGameGUI;
import ygraph.ai.smartfox.games.GameClient;
import ygraph.ai.smartfox.games.GamePlayer;
import ygraph.ai.smartfox.games.GameMessage;

public class COSC322Test extends GamePlayer {

    private GameClient gameClient = null;
    private BaseGameGUI gamegui = null;
    private ArrayList<Integer> gameBoard = null;
    private int myColor = 0; // 2 = white, 1 = black
    private AIPlayer ai = new AIPlayer();

    private String userName = null;
    private String passwd = null;

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
            System.out.println("Board initialized. Size=" + gameBoard.size());
            printBoard(gameBoard);

        } else if (messageType.equals(GameMessage.GAME_ACTION_START)) {
            String blackPlayer = (String) msgDetails.get(AmazonsGameMessage.PLAYER_BLACK);
            String whitePlayer = (String) msgDetails.get(AmazonsGameMessage.PLAYER_WHITE);
            System.out.println("Black: " + blackPlayer);
            System.out.println("White: " + whitePlayer);
            System.out.println("I am: " + userName);

            if (userName.equals(blackPlayer)) {
                myColor = 1;
            } else if (userName.equals(whitePlayer)) {
                myColor = 2;
            } else {
                myColor = 0;
                System.out.println("Not a player in this game — spectating only.");
            }
            System.out.println("My color: " + (myColor == 1 ? "White" : myColor == 2 ? "Black" : "Spectator"));

            if (myColor == 1) {
                System.out.println("I am White — making first move.");
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

            System.out.println("MOVE MSG: from(" + fromRow + "," + fromCol + ")"
                + " to(" + toRow + "," + toCol + ")"
                + " arrow(" + arrRow + "," + arrCol + ")");
            System.out.println("  board[from]=" + gameBoard.get(fromIdx)
                + " board[to]=" + gameBoard.get(toIdx)
                + " board[arrow]=" + gameBoard.get(arrowIdx)
                + "  myColor=" + myColor);

            int movingPiece = gameBoard.get(fromIdx);

            if (movingPiece == 0) {
                System.out.println("Source square already empty — echo of our own move. Ignoring.");
            } else if (movingPiece != 1 && movingPiece != 2) {
                System.out.println("WARNING: unexpected piece value " + movingPiece + " at source — skipping.");
            } else {
                gameBoard.set(fromIdx, 0);
                gameBoard.set(toIdx, movingPiece);
                gameBoard.set(arrowIdx, 3);
                printBoard(gameBoard);

                if (myColor != 0 && movingPiece != myColor) {
                    System.out.println("Opponent (" + movingPiece + ") moved. My turn.");
                    makeAndSendMove();
                } else if (myColor == 0) {
                    System.out.println("Spectating — not sending a move.");
                } else {
                    System.out.println("WARNING: received move for my own piece but board wasn't pre-applied.");
                }
            }

        } else {
            System.out.println("Unhandled message type: " + messageType);
        }

        return true;
    }

    /**
     * Ask the AI for a move, apply it to our board, and send it to the server.
     */
    private void makeAndSendMove() {
        if (gameBoard == null) {
            System.out.println("ERROR: gameBoard is null, cannot move.");
            return;
        }

        Move myMove = ai.chooseMove(cloneBoard(gameBoard), myColor);

        if (myMove == null) {
            System.out.println("==========================");
            System.out.println("No legal moves — I LOST.");
            System.out.println("==========================");
            return;
        }

        // Snapshot board BEFORE we apply the move, so we can validate against it
        ArrayList<Integer> boardBeforeMove = cloneBoard(gameBoard);

        // Apply move to our own board
        gameBoard.set(myMove.fr * 11 + myMove.fc, 0);
        gameBoard.set(myMove.tr * 11 + myMove.tc, myColor);
        gameBoard.set(myMove.ar * 11 + myMove.ac, 3);

        // Send to server
        ArrayList<Integer> qCurr = new ArrayList<>();
        qCurr.add(myMove.fr);
        qCurr.add(myMove.fc);

        ArrayList<Integer> qNext = new ArrayList<>();
        qNext.add(myMove.tr);
        qNext.add(myMove.tc);

        ArrayList<Integer> arrow = new ArrayList<>();
        arrow.add(myMove.ar);
        arrow.add(myMove.ac);

        validateMove(myMove, boardBeforeMove);
        gameClient.sendMoveMessage(qCurr, qNext, arrow);
        System.out.println("Sent move: " + myMove);
        printBoard(gameBoard);
    }

    // -----------------------------------------------------------------------
    // Board helpers
    // -----------------------------------------------------------------------

    /**
     * Validates a move against the board state BEFORE the move was applied.
     * Logs exactly which constraint is violated, if any.
     */
    private void validateMove(Move m, ArrayList<Integer> board) {
        int piece = board.get(m.fr * 11 + m.fc);

        // 1. Is there our piece at the source?
        if (piece != myColor) {
            System.out.println("INVALID MOVE: no " + myColor + " at source (" + m.fr + "," + m.fc + "), found " + piece);
            return;
        }

        // 2. Is the queen path clear (no jumping)?
        if (!isQueenPathClear(board, m.fr, m.fc, m.tr, m.tc)) {
            System.out.println("INVALID MOVE: queen path blocked from (" + m.fr + "," + m.fc + ") to (" + m.tr + "," + m.tc + ")");
            return;
        }

        // 3. Simulate queen move, then check arrow path
        ArrayList<Integer> afterQueenMove = cloneBoard(board);
        afterQueenMove.set(m.fr * 11 + m.fc, 0);
        afterQueenMove.set(m.tr * 11 + m.tc, myColor);

        if (!isQueenPathClear(afterQueenMove, m.tr, m.tc, m.ar, m.ac)) {
            System.out.println("INVALID MOVE: arrow path blocked from (" + m.tr + "," + m.tc + ") to (" + m.ar + "," + m.ac + ")");
            return;
        }

        System.out.println("Move validated OK.");
    }

    /**
     * Returns true if a queen can move from (r1,c1) to (r2,c2) on the given board —
     * i.e. same row/col/diagonal, and all squares in between are empty.
     */
    private boolean isQueenPathClear(ArrayList<Integer> board, int r1, int c1, int r2, int c2) {
        int dr = Integer.signum(r2 - r1);
        int dc = Integer.signum(c2 - c1);

        // Must be in a straight line (row, col, or diagonal)
        if (dr == 0 && dc == 0) return false; // same square
        if (dr != 0 && dc != 0 && Math.abs(r2 - r1) != Math.abs(c2 - c1)) return false; // not diagonal

        int r = r1 + dr;
        int c = c1 + dc;
        while (r != r2 || c != c2) {
            if (board.get(r * 11 + c) != 0) return false; // blocked
            r += dr;
            c += dc;
        }
        // destination itself must be empty
        return board.get(r2 * 11 + c2) == 0;
    }

    /**
     * Return all squares reachable in queen-moves from (row, col) on board.
     * Board is 11-wide (column 0 unused); valid squares are rows/cols 1..10.
     */
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
        if (board == null) { System.out.println("Board is null"); return; }

        System.out.println("    1 2 3 4 5 6 7 8 9 10");
        for (int row = 10; row >= 1; row--) {
            System.out.printf("%2d  ", row);
            for (int col = 1; col <= 10; col++) {
                int val = board.get(row * 11 + col);
                // 1=Black, 2=White in the server's encoding (matches GUI display)
                char c = val == 0 ? '.' : val == 1 ? 'B' : val == 2 ? 'W' : val == 3 ? 'X' : '?';
                System.out.print(c + " ");
            }
            System.out.println();
        }
        System.out.println("--------------------------");
    }

    // -----------------------------------------------------------------------
    // GamePlayer interface
    // -----------------------------------------------------------------------

    @Override
    public String userName() { return userName; }

    @Override
    public GameClient getGameClient() { return this.gameClient; }

    @Override
    public BaseGameGUI getGameGUI() { return this.gamegui; }

    @Override
    public void connect() {
        gameClient = new GameClient(userName, passwd, this);
    }

    // -----------------------------------------------------------------------
    // Move record
    // -----------------------------------------------------------------------

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

    // -----------------------------------------------------------------------
    // AI — minimax with alpha-beta pruning + iterative deepening
    // -----------------------------------------------------------------------

    private class AIPlayer {

        Move chooseMove(ArrayList<Integer> boardCopy, int color) {
            Move bestMove = null;
            long startTime = System.currentTimeMillis();
            long timeLimit = startTime + 30000; // 5-second budget

            for (int depth = 1; depth <= 10; depth++) {
                if (System.currentTimeMillis() >= timeLimit) break;

                Move candidate = minimaxRoot(boardCopy, depth, color, timeLimit);
                if (candidate == null) break; // timed out before finishing depth 1
                bestMove = candidate;
                System.out.println("Completed depth " + depth + " — best so far: " + bestMove);
            }

            return bestMove;
        }

        private Move minimaxRoot(ArrayList<Integer> board, int depth, int color, long timeLimit) {
            Move bestMove = null;
            int bestScore = Integer.MIN_VALUE;
            int opponent = (color == 1) ? 2 : 1;

            for (int[] queen : getQueenPositions(board, color)) {
                for (int[] newPos : getLegalMoves(board, queen[0], queen[1])) {
                    if (System.currentTimeMillis() >= timeLimit) return bestMove; // return best found so far

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

            if (System.currentTimeMillis() >= timeLimit) return evaluate(board, myColor);
            if (depth == 0) return evaluate(board, myColor);

            int current = isMaximizing ? myColor : opponent;
            boolean hadMove = false;
            int best = isMaximizing ? Integer.MIN_VALUE : Integer.MAX_VALUE;

            outer:
            for (int[] queen : getQueenPositions(board, current)) {
                for (int[] newPos : getLegalMoves(board, queen[0], queen[1])) {
                    int from = queen[0] * 11 + queen[1];
                    int to   = newPos[0] * 11 + newPos[1];
                    board.set(from, 0);
                    board.set(to, current);

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
                            board.set(to, 0);
                            board.set(from, current);
                            break outer; // prune
                        }
                    }

                    board.set(to, 0);
                    board.set(from, current);
                }
            }

            // No moves at all = that side has lost
            if (!hadMove) {
                return isMaximizing ? -10000 : 10000;
            }

            return best;
        }

        /**
         * Heuristic: difference in total queen-reachable squares (mobility).
         */
        private int evaluate(ArrayList<Integer> board, int myColor) {
            int opponent = (myColor == 1) ? 2 : 1;

            int myMobility = countTotalMoves(board, myColor);
            int oppMobility = countTotalMoves(board, opponent);
            int mobilityScore = myMobility - oppMobility;
            int territoryScore = evaluateTerritory(board, myColor, opponent);
            int trappedScore = countTrappedQueens(board, opponent) - countTrappedQueens(board, myColor);
            int localFreedomScore = localFreedom(board, myColor) - localFreedom(board, opponent);
            return 4 * mobilityScore+ 6 * territoryScore + 8 * trappedScore + 2 * localFreedomScore;
        }

        private int countTotalMoves(ArrayList<Integer> board, int color) {
            int count = 0;
            for (int[] queen : getQueenPositions(board, color)) {
                count += getLegalMoves(board, queen[0], queen[1]).size();
            }
            return count;
        }

        private int countTrappedQueens(ArrayList<Integer> board, int color) {
            int trapped = 0;
            for (int[] queen : getQueenPositions(board, color)) {
                int mobility = getLegalMoves(board, queen[0], queen[1]).size();

                if (mobility <= 2) {
                    trapped += 2;   // very trapped
                } else if (mobility <= 5) {
                    trapped += 1;   // somewhat trapped
                }
            }
        return trapped;
        }

        private int localFreedom(ArrayList<Integer> board, int color) {
            int freedom = 0;
            int[][] dirs = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
            };
            for (int[] queen : getQueenPositions(board, color)) {
                int r = queen[0];
                int c = queen[1];

                for (int[] d : dirs) {
                    int nr = r + d[0];
                    int nc = c + d[1];

                    if (nr >= 1 && nr <= 10 && nc >= 1 && nc <= 10) {
                        if (board.get(nr * 11 + nc) == 0) {
                            freedom++;
                        }
                    }
                }
            }
            return freedom;
        }

        private int evaluateTerritory(ArrayList<Integer> board, int myColor, int opponent) {
            int[][] myDist = queenDistanceMap(board, myColor);
            int[][] oppDist = queenDistanceMap(board, opponent);

            int score = 0;

            for (int row = 1; row <= 10; row++) {
                for (int col = 1; col <= 10; col++) {
                    if (board.get(row * 11 + col) != 0) continue; // only score empty squares

                    int md = myDist[row][col];
                    int od = oppDist[row][col];

                    if (md == Integer.MAX_VALUE && od == Integer.MAX_VALUE) {
                        continue;
                    } else if (md < od) {
                        score += 1;
                    } else if (od < md) {
                        score -= 1;
                    }
                    // tie = 0
                }
            }

            return score;
        }

        private int[][] queenDistanceMap(ArrayList<Integer> board, int color) {
            int[][] dist = new int[11][11];

            for (int r = 0; r < 11; r++) {
                for (int c = 0; c < 11; c++) {
                    dist[r][c] = Integer.MAX_VALUE;
                }
            }

            ArrayDeque<int[]> queue = new ArrayDeque<>();

            for (int[] queen : getQueenPositions(board, color)) {
                dist[queen[0]][queen[1]] = 0;
                queue.add(new int[]{queen[0], queen[1]});
            }

            while (!queue.isEmpty()) {
                int[] cur = queue.poll();
                int row = cur[0];
                int col = cur[1];
                int nextDist = dist[row][col] + 1;

                for (int[] move : getLegalMoves(board, row, col)) {
                    int nr = move[0];
                    int nc = move[1];

                    if (dist[nr][nc] > nextDist) {
                        dist[nr][nc] = nextDist;
                        queue.add(new int[]{nr, nc});
                    }
                }
            }

            return dist;
        }
    }
}