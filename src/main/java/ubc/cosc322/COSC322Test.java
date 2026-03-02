package ubc.cosc322;

import java.util.Map;
import java.util.ArrayList;

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
    private int lastMoverColor = 0;
    private AIPlayer ai;

    private String userName = null;
    private String passwd = null;

    /**
     * Convert display coordinates (1,1 = bottom-left) to board array index.
     * Board is 11 columns wide (0-10), where column 0 is unused.
     */
    private int coordToIndex(int row, int col) {
        int internalRow = 11 - row;  // flip row: 1->10, 10->1
        return internalRow * 11 + col;
    }

    /**
     * Convert board array index to display coordinates (1,1 = bottom-left).
     */
    private int[] indexToCoord(int index) {
        int internalRow = index / 11;
        int col = index % 11;
        int row = 11 - internalRow;  // flip back: 10->1, 1->10
        return new int[]{row, col};
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
    System.out.println("Logged in as: " + userName);  // is this printing?
    if (gamegui != null) {
        gamegui.setRoomInformation(gameClient.getRoomList());
        System.out.println("Rooms: " + gameClient.getRoomList());
    }
}

private void printBoard(ArrayList<Integer> board) {
    if (board == null) {
        System.out.println("Board is null");
        return;
    }

    System.out.println("    1 2 3 4 5 6 7 8 9 10");

    // Print from row 10 (top) to row 1 (bottom) - (1,1) is bottom-left
    for (int row = 10; row >= 1; row--) {
        if (row < 10) System.out.print(" " + row + "  ");
        else System.out.print(row + "  ");

        for (int col = 1; col <= 10; col++) {
            int val = board.get(row * 11 + col);

            char c;
            if (val == 0) c = '.';   // empty
            else if (val == 1) c = 'W'; // white
            else if (val == 2) c = 'B'; // black
            else if (val == 3) c = 'X'; // arrow
            else c = '?'; // unexpected

            System.out.print(c + " ");
        }
        System.out.println();
    }
    System.out.println("--------------------------");
}

    @SuppressWarnings("unchecked")
    @Override
    public boolean handleGameMessage(String messageType, Map<String, Object> msgDetails) {
        if (gamegui == null) return false;

        if (messageType.equals(GameMessage.GAME_STATE_BOARD)) {
            gameBoard = (ArrayList<Integer>) msgDetails.get(AmazonsGameMessage.GAME_STATE);
            gamegui.setGameState(gameBoard);
            if (ai == null) ai = new AIPlayer();

        } else if (messageType.equals(GameMessage.GAME_ACTION_START)) {
            String blackPlayer = (String) msgDetails.get(AmazonsGameMessage.PLAYER_BLACK);
            String whitePlayer = (String) msgDetails.get(AmazonsGameMessage.PLAYER_WHITE);
            System.out.println("Black: " + blackPlayer);
            System.out.println("White: " + whitePlayer);
            System.out.println("I am: " + userName);

            if (userName.equals(blackPlayer)) {
                myColor = 2;
            } else {
                myColor = 1;
            }
            System.out.println("My color: " + (myColor == 2 ? "Black" : "White"));
            if (myColor == 1 && gameBoard != null) {
                if (ai == null) ai = new AIPlayer(); 
                Move myMove = ai.chooseMove(cloneBoard(gameBoard), myColor);
                if (myMove != null) sendMove(myMove);
            }
        } else if (messageType.equals(GameMessage.GAME_ACTION_MOVE)) {
            gamegui.updateGameState(msgDetails);

            // update internal board
            ArrayList<Integer> queenCurr = (ArrayList<Integer>) msgDetails.get(AmazonsGameMessage.QUEEN_POS_CURR);
            ArrayList<Integer> queenNext = (ArrayList<Integer>) msgDetails.get(AmazonsGameMessage.QUEEN_POS_NEXT);
            ArrayList<Integer> arrowPos = (ArrayList<Integer>) msgDetails.get(AmazonsGameMessage.ARROW_POS);

            int from = queenCurr.get(0) * 11 + queenCurr.get(1);
            int to = queenNext.get(0) * 11 + queenNext.get(1);
            int arrow = arrowPos.get(0) * 11 + arrowPos.get(1);

            int piece = gameBoard.get(from);
            lastMoverColor = piece;
            gameBoard.set(from, 0);
            gameBoard.set(to, piece);
            gameBoard.set(arrow, 3);

            if (ai == null) {
                ai = new AIPlayer();
            }
            Move myMove = ai.chooseMove(cloneBoard(gameBoard), myColor);
            if (myMove != null) {
                sendMove(myMove);
            } else System.out.println("No legal moves available - I lose");

            // find and print my queens
            ArrayList<int[]> myQueens = getQueenPositions(gameBoard, myColor);
            for (int[] q : myQueens) {
                System.out.println("My queen at: " + q[0] + ", " + q[1]);
            }
        }
        printBoard(gameBoard);
        return true;
    }

    private ArrayList<int[]> getLegalMoves(ArrayList<Integer> board, int row, int col) {
        ArrayList<int[]> moves = new ArrayList<>();
        int[][] directions = {
            {-1, 0}, {1, 0}, {0, -1}, {0, 1},   // up, down, left, right
            {-1, -1}, {-1, 1}, {1, -1}, {1, 1}   // diagonals
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

    private void sendMove(Move m) {
        ArrayList<Integer> qCurr = new ArrayList<>();
        qCurr.add(m.fr); qCurr.add(m.fc);
        ArrayList<Integer> qNext = new ArrayList<>();
        qNext.add(m.tr); qNext.add(m.tc);
        ArrayList<Integer> arrow = new ArrayList<>();
        arrow.add(m.ar); arrow.add(m.ac);

        // apply our move to the board here (and only here)
        gameBoard.set(m.fr * 11 + m.fc, 0);
        gameBoard.set(m.tr * 11 + m.tc, myColor);
        gameBoard.set(m.ar * 11 + m.ac, 3);

        gameClient.sendMoveMessage(qCurr, qNext, arrow);
        System.out.println("Sent move: " + m);
    }

    private ArrayList<int[]> getQueenPositions(ArrayList<Integer> board,int color) {
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

    @Override
    public String userName() {
        return userName;
    }

    @Override
    public GameClient getGameClient() {
        return this.gameClient;
    }

    @Override
    public BaseGameGUI getGameGUI() {
        return this.gamegui;
    }

    @Override
    public void connect() {
        gameClient = new GameClient(userName, passwd, this);
    }

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

    private class AIPlayer {
        Move chooseMove(ArrayList<Integer> boardCopy, int myColor) { //call minimax with iterative deepening
            Move bestMove = null;
            long startTime = System.currentTimeMillis();
            long timeLimit = startTime + 5000;

            System.out.println("Starting iterative deepening...");
            for (int depth = 1; depth <= 10; depth++) {
                if (System.currentTimeMillis() >= timeLimit) break;
                Move candidate = minimaxRoot(boardCopy, depth, myColor, startTime, timeLimit);
                if (candidate == null) break;
                bestMove = candidate;
                System.out.println("Completed depth: " + depth);
            }
            System.out.println("Best move chosen: " + bestMove);
            return bestMove;
        }

        private Move minimaxRoot(ArrayList<Integer> board, int depth, int myColor, long startTime, long timeLimit) {
            if (System.currentTimeMillis() >= timeLimit) return null;

            int opponent = (myColor == 1) ? 2 : 1;
            Move bestMove = null;
            int bestScore = Integer.MIN_VALUE;

            for (int[] queen : getQueenPositions(board, myColor)) {
                for (int[] newPos : getLegalMoves(board, queen[0], queen[1])) {
                    int from = queen[0] * 11 + queen[1];
                    int to = newPos[0] * 11 + newPos[1];
                    board.set(from, 0);
                    board.set(to, myColor);

                    for (int[] arrow : getLegalMoves(board, newPos[0], newPos[1])) {
                        int arr = arrow[0] * 11 + arrow[1];
                        board.set(arr, 3);

                        int score = minimax(board, depth - 1, false, myColor, opponent, Integer.MIN_VALUE, Integer.MAX_VALUE, startTime, timeLimit);

                        board.set(arr, 0); 
                        if (score > bestScore) {
                            bestScore = score;
                            bestMove = new Move(queen[0], queen[1], newPos[0], newPos[1], arrow[0], arrow[1]);
                        }
                    }

                    board.set(to, 0); // undo move
                    board.set(from, myColor);
                }
            }
            return bestMove;
        }

        private int minimax(ArrayList<Integer> board, int depth, boolean isMaximizing, int myColor, int opponent, int alpha, int beta, long startTime, long timeLimit) {
            if (System.currentTimeMillis() > timeLimit) return 0; // will choose best move from previous level, return 0

            if (depth == 0) return evaluate(board, myColor); //base case: evaluate board


            int current = isMaximizing ? myColor : opponent;
            int best = isMaximizing ? Integer.MIN_VALUE : Integer.MAX_VALUE;
            int score;

            for (int[] queen : getQueenPositions(board, current)) {
                for (int[] newPos : getLegalMoves(board, queen[0], queen[1])) {
                    int from = queen[0] * 11 + queen[1];
                    int to   = newPos[0] * 11 + newPos[1];
                    board.set(from, 0);
                    board.set(to, current);

                    for (int[] arrow : getLegalMoves(board, newPos[0], newPos[1])) {
                        int arr = arrow[0] * 11 + arrow[1];
                        board.set(arr, 3);

                        score = minimax(board, depth - 1, !isMaximizing, myColor, opponent, alpha, beta, startTime, timeLimit);

                        if (isMaximizing) {
                            best = Math.max(best, score);
                            alpha = Math.max(alpha, best);
                        } else {
                            best = Math.min(best, score);
                            beta = Math.min(beta, best);
                        }

                        if (beta <= alpha) {
                            board.set(arr, 0);
                            return best;
                        }

                        board.set(arr, 0);
                    }

                    board.set(to, 0);
                    board.set(from, current);
                }
            }

            // no moves = loss
            if (best == Integer.MIN_VALUE || best == Integer.MAX_VALUE) {
                return isMaximizing ? -1000 : 1000;
            }
            return best;
        }
        private int evaluate(ArrayList<Integer> board, int myColor) {
            int opponent = (myColor == 1) ? 2 : 1;
            int myMoves = countTotalMoves(board, myColor);
            int opMoves = countTotalMoves(board, opponent);
            return myMoves - opMoves;
        }

        private int countTotalMoves(ArrayList<Integer> board, int color) {
            int count = 0;
            for (int[] queen : getQueenPositions(board, color)) {
                count += getLegalMoves(board, queen[0], queen[1]).size();
            }
            return count;
        }
    }
    // next to add: iterative deepening, ai pruning 
}