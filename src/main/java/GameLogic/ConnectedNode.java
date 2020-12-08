package GameLogic;

import MessageSerialize.SnakesProto.*;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.List;

public class ConnectedNode extends Thread {

    private final int id;
    private final GameConfig gameConfig;
    private final DatagramSocket socket;
    private int stateNum;
    private GameState actualState;
    private final InetSocketAddress masterAddress;
    private List<GamePlayer> players;
    private List<GameState.Snake> snakes;
    private List<GameState.Coord> food;

    public ConnectedNode(int id, GameConfig config, DatagramSocket datagramSocket,
                         InetSocketAddress masterAddress) {
        this.id = id;
        gameConfig = config;
        socket = datagramSocket;
        this.masterAddress = masterAddress;
    }

    @Override
    public void run() {
        int timeOut = 500;
        byte[] byteBuffer = new byte[1024];
        DatagramPacket packet = new DatagramPacket(byteBuffer, byteBuffer.length);
        try {
            socket.setSoTimeout(timeOut);
            while (!isInterrupted()) {
                try {
                    socket.receive(packet);
                    InetSocketAddress curAddress = new InetSocketAddress(packet.getAddress(), packet.getPort());
                    GameMessage message = GameMessage.parseFrom(Arrays.copyOf(packet.getData(), packet.getLength()));
                    if (message.getTypeCase().equals(GameMessage.TypeCase.STATE) && curAddress.equals(masterAddress)) {
                        parseGameStateMsg(message);
                    }
                }
                catch (com.google.protobuf.InvalidProtocolBufferException | SocketTimeoutException ignored) {}
            }
        }
        catch (IOException exception) {
            System.err.println(exception.getMessage());
        }
    }

    private void parseGameStateMsg(GameMessage message) {
        GameState gameState = message.getState().getState();
        players = gameState.getPlayers().getPlayersList();
        snakes = gameState.getSnakesList();
        food = gameState.getFoodsList();
    }
}
