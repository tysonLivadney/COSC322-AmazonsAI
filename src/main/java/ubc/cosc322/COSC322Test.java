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

    public static void main(String[] args) {
        COSC322Test player = new COSC322Test(args[0], "cosc322");
        player.connect();

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
        if (gamegui != null) {
            gamegui.setRoomInformation(gameClient.getRoomList());
        }
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
            Move myMove = ai.chooseMove(gameBoard, myColor);
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

            if(piece != myColor){
                if (ai == null) {
                    ai = new AIPlayer();
                }
                Move myMove = ai.chooseMove(gameBoard, myColor);
                if (myMove != null){
                     sendMove(myMove);
                }
                else System.out.println("No legal moves available - I lose");
            }

            // find and print my queens
            ArrayList<int[]> myQueens = getQueenPositions(gameBoard, myColor);
            for (int[] q : myQueens) {
                System.out.println("My queen at: " + q[0] + ", " + q[1]);
            }
        }

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

        int from = m.fr * 11 + m.fc;
        int to = m.tr * 11 + m.tc;
        int arr = m.ar * 11 + m.ac;

        gameBoard.set(from, 0);
        gameBoard.set(to, myColor);
        gameBoard.set(arr, 3);

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
        Move chooseMove(ArrayList<Integer> board, int myColor) {

            ArrayList<int[]> myQueens = getQueenPositions(board, myColor);
            java.util.Collections.shuffle(myQueens);

            for (int[] queen : myQueens) {
                ArrayList<int[]> moves = getLegalMoves(board,queen[0], queen[1]);
                if (moves.isEmpty()) continue;

                int[] newPos = moves.get((int)(Math.random() * moves.size()));

                int from = queen[0] * 11 + queen[1];
                int to = newPos[0] * 11 + newPos[1];

                board.set(from, 0);
                board.set(to, myColor);

                ArrayList<int[]> arrows = getLegalMoves(board, newPos[0], newPos[1]);

                board.set(from, myColor);
                board.set(to, 0);

                if (arrows.isEmpty()) continue;

                int[] arrowPos = arrows.get((int)(Math.random() * arrows.size()));

                return new Move(queen[0], queen[1], newPos[0], newPos[1], arrowPos[0], arrowPos[1]);
            }

            return null;
        }
    }
    // next to add: iterative deepening, ai pruning 
}