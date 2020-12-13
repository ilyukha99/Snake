package GameLogic;

import Graphics.Menu;
import MessageSerialize.SnakesProto;
import MessageSerialize.SnakesProto.GameMessage;
import Net.GameListener;

import javax.swing.*;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MainController {

    private static final GameListener gameListener = new GameListener();
    private static JFrame mainMenu;
    private static GameCenter gameCenter;
    private static ConnectedNode connectedNode;

    public static void startListener() {
        SwingUtilities.invokeLater(()-> new Menu(gameListener.getAvailableGames()));
        gameListener.run();
    }

    public static void startNewGame(SnakesProto.GameConfig config, String name, JFrame menu) {
        mainMenu = menu;
        ConcurrentHashMap<Integer, SnakesProto.Direction> nextDirections
                = new ConcurrentHashMap<>(10, 0.81f,1);
        gameCenter = new GameCenter(config, name, nextDirections);
        gameCenter.start();
    }

    public static void setMenuVisible(boolean flag) {
        mainMenu.setVisible(flag);
    }

    public static void stopGameCenter() {
        gameCenter.interrupt();
    }

    public static String connectToGame(InetSocketAddress masterAddress, String name, boolean onlyView,
            SnakesProto.GameConfig config, Menu menu) {
        mainMenu = menu;
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket();
            long msgSeq = UUID.randomUUID().getMostSignificantBits();
            byte[] bytes = GameMessage.newBuilder()
                    .setJoin(GameMessage.JoinMsg.newBuilder().setName(name).setOnlyView(onlyView)
                    .setPlayerType(SnakesProto.PlayerType.HUMAN).build())
                    .setMsgSeq(msgSeq).build().toByteArray();

            int interval = 1500;
            byte[] recvBuffer = new byte[100];
            DatagramPacket sendPacket, recvPacket = new DatagramPacket(recvBuffer, recvBuffer.length);
            socket.setSoTimeout(interval);
            for (int it = 0; it < 3; ++it) {
                sendPacket = new DatagramPacket(bytes, bytes.length, masterAddress);
                socket.send(sendPacket); //sending join message
                try {
                    socket.receive(recvPacket);
                    GameMessage message = GameMessage.parseFrom(Arrays.copyOf(recvPacket.getData(),
                            recvPacket.getLength()));
                    if (message.hasAck() && message.getMsgSeq() == msgSeq) {
                        int myId = message.getReceiverId();
                        connectedNode = new ConnectedNode(myId, config, socket, masterAddress);
                        connectedNode.start();
                        return null;
                    }
                    if (message.hasError()) {
                        socket.close();
                        return message.getError().getErrorMessage();
                    }
                }
                catch (SocketTimeoutException | com.google.protobuf.InvalidProtocolBufferException ignored) {}
            }
        }
        catch (IOException exc) {
            System.err.println(exc.getMessage());
            if (socket != null) {
                if (!socket.isClosed()) {
                    socket.close();
                }
            }
        }
        return "Can not connect to the host";
    }

    public static void stopConnectedNode() {
        connectedNode.interrupt();
    }
}

//    public static MainController getInstance() {
//        MainController localInstance = instance;
//        if (localInstance == null) {
//            synchronized (MainController.class) {
//                localInstance = instance;
//                if (localInstance == null) {
//                    instance = localInstance = new MainController();
//                }
//            }
//        }
//        return localInstance;
//    }
