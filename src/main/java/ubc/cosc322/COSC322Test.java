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
            gameBoard.set(from, 0);
            gameBoard.set(to, piece);
            gameBoard.set(arrow, 3);

            // find and print my queens
            ArrayList<int[]> myQueens = getQueenPositions(myColor);
            for (int[] q : myQueens) {
                System.out.println("My queen at: " + q[0] + ", " + q[1]);
            }
            makeRandomMove();
        }

        return true;
    }

    private ArrayList<int[]> getLegalMoves(int row, int col) {
        ArrayList<int[]> moves = new ArrayList<>();
        int[][] directions = {
            {-1, 0}, {1, 0}, {0, -1}, {0, 1},   // up, down, left, right
            {-1, -1}, {-1, 1}, {1, -1}, {1, 1}   // diagonals
        };

        for (int[] dir : directions) {
            int r = row + dir[0];
            int c = col + dir[1];
            while (r >= 1 && r <= 10 && c >= 1 && c <= 10) {
                if (gameBoard.get(r * 11 + c) == 0) {
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

    private void makeRandomMove() {
        ArrayList<int[]> myQueens = getQueenPositions(myColor);
        
        // shuffle queens so we pick randomly
        java.util.Collections.shuffle(myQueens);
        
        for (int[] queen : myQueens) {
            ArrayList<int[]> moves = getLegalMoves(queen[0], queen[1]);
            if (moves.isEmpty()) continue;
            
            // pick random move
            int[] newPos = moves.get((int)(Math.random() * moves.size()));
            
            // temporarily move queen to find legal arrow shots
            int from = queen[0] * 11 + queen[1];
            int to = newPos[0] * 11 + newPos[1];
            gameBoard.set(from, 0);
            gameBoard.set(to, myColor);
            
            ArrayList<int[]> arrows = getLegalMoves(newPos[0], newPos[1]);
            
            // undo temporary move
            gameBoard.set(from, myColor);
            gameBoard.set(to, 0);
            
            if (arrows.isEmpty()) continue;
            
            // pick random arrow
            int[] arrowPos = arrows.get((int)(Math.random() * arrows.size()));
            
            // build arraylists for sendMoveMessage
            ArrayList<Integer> qCurr = new ArrayList<>();
            qCurr.add(queen[0]); qCurr.add(queen[1]);
            
            ArrayList<Integer> qNext = new ArrayList<>();
            qNext.add(newPos[0]); qNext.add(newPos[1]);
            
            ArrayList<Integer> arrow = new ArrayList<>();
            arrow.add(arrowPos[0]); arrow.add(arrowPos[1]);
            
            // update our board with final move
            gameBoard.set(from, 0);
            gameBoard.set(to, myColor);
            gameBoard.set(arrowPos[0] * 11 + arrowPos[1], 3);
            
            gameClient.sendMoveMessage(qCurr, qNext, arrow);
            System.out.println("Sent move: " + queen[0] + "," + queen[1] + " -> " + newPos[0] + "," + newPos[1] + " arrow: " + arrowPos[0] + "," + arrowPos[1]);
            return;
        }
    
     System.out.println("No legal moves available - I lose");
    }

    private ArrayList<int[]> getQueenPositions(int color) {
        ArrayList<int[]> queens = new ArrayList<>();
        for (int row = 1; row <= 10; row++) {
            for (int col = 1; col <= 10; col++) {
                if (gameBoard.get(row * 11 + col) == color) {
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
}