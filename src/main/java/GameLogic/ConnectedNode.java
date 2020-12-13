package GameLogic;

import Graphics.GameFrame;
import MessageSerialize.SnakesProto.*;
import Net.Sender;
import Utilities.CellModifier;
import View.Cell;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ConnectedNode extends Thread {

    private final int id;
    private final GameConfig gameConfig;
    private final DatagramSocket socket;
    private int gameStateNumber;
    private final InetSocketAddress masterAddress;
    private List<GamePlayer> players;
    private List<GameState.Snake> snakes;
    private List<GameState.Coord> food;
    private final Sender sender;
    private final GameFrame gameFrame;
    private final CellModifier cellModifier;
    private final int width;
    private final int height;

    public ConnectedNode(int id, GameConfig config, DatagramSocket datagramSocket,
                         InetSocketAddress masterAddress) {
        MainController.setMenuVisible(false);
        this.id = id;
        gameConfig = config;
        socket = datagramSocket;
        this.masterAddress = masterAddress;
        Cell[] cells = new Cell[config.getWidth() * config.getHeight()];
        sender = new Sender(null, socket, gameConfig);
        gameFrame = new GameFrame(config, cells, sender, masterAddress, id);
        cellModifier = new CellModifier(gameConfig.getWidth(), gameConfig.getHeight(), 5, cells);
        width = gameConfig.getWidth();
        height = gameConfig.getHeight();
    }

    @Override
    public void run() {
        int timeOut = gameConfig.getStateDelayMs() / 4;
        byte[] byteBuffer = new byte[1024];
        DatagramPacket packet = new DatagramPacket(byteBuffer, byteBuffer.length);
        sender.start();
        try {
            socket.setSoTimeout(timeOut);
            while (!isInterrupted()) {
                try {
                    socket.receive(packet);
                    InetSocketAddress curAddress = new InetSocketAddress(packet.getAddress(), packet.getPort());
                    GameMessage message = GameMessage.parseFrom(Arrays.copyOf(packet.getData(), packet.getLength()));
                    if (curAddress.equals(masterAddress)) {
                        switch (message.getTypeCase()) {
                            case STATE -> {
                                if (!parseGameStateMsg(message)) {
                                    continue;
                                }
                                fullCellsRefill();
                                gameFrame.repaint();
                                gameFrame.updateTable(players.stream().map(GamePlayer::toBuilder).collect(Collectors.toList()));
                                sender.sendAck(masterAddress, message.getMsgSeq(), 0, id);
                            }
                            case ACK -> sender.removeFromList(masterAddress, message.getMsgSeq());
                            case PING -> sender.sendAck(masterAddress, message.getMsgSeq(), 0, id);
                        }
                    }
                }
                catch (com.google.protobuf.InvalidProtocolBufferException | SocketTimeoutException ignored) {}
            }
        }
        catch (IOException exception) {
            System.err.println(exception.getMessage());
        }
        finally {
            sender.interrupt();
            socket.close();
        }
    }

    private boolean parseGameStateMsg(GameMessage message) {
        GameState gameState = message.getState().getState();
        if (gameState.getStateOrder() <= gameStateNumber) {
            return false;
        }
        gameStateNumber = gameState.getStateOrder();
        players = gameState.getPlayers().getPlayersList();
        snakes = gameState.getSnakesList();
        food = gameState.getFoodsList();
        return true;
    }

    private void fullCellsRefill() {
        cellModifier.setEmpty(); //or cellModifier.init();
        for (GameState.Coord coordinate : food) {
            cellModifier.setFood(coordinate.getY() * width + coordinate.getX());
        }

        int keyCoordsAmount, playerId, module;
        for (GameState.Snake snake : snakes) {
            List<GameState.Coord> coords = snake.getPointsList();
            GameState.Coord head = coords.get(0);
            playerId = snake.getPlayerId();
            int x = head.getX(), y = head.getY(), keyX, keyY;
            cellModifier.setSnakeHead(y * width + x,
                    snake.getHeadDirection(), playerId);
            keyCoordsAmount = coords.size();
            for (int k = 1; k < keyCoordsAmount; ++k) {
                GameState.Coord keyCoordinate = coords.get(k);
                keyX = keyCoordinate.getX();
                keyY = keyCoordinate.getY();
                module = Math.abs((keyX == 0) ? keyY : keyX);
                for (int it = 1; it <= module; ++it) {
                    if (keyX < 0) {
                        x = (x - 1 + width) % width;
                    } else if (keyX > 0) {
                        x = (x + 1) % width;
                    } else if (keyY < 0) {
                        y = (y - 1 + height) % height;
                    } else if (keyY > 0) {
                        y = (y + 1) % height;
                    }
                    cellModifier.setSnakeBody(y * width + x, playerId);
                }
            }
        }
    }
}
