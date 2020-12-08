package GameLogic;

import Graphics.Menu;
import MessageSerialize.SnakesProto;
import MessageSerialize.SnakesProto.GameMessage;
import Net.GameListener;
import View.Cell;

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

    public static void startListener() {
        SwingUtilities.invokeLater(()-> new Menu(gameListener.getAvailableGames()));
        gameListener.run();
    }

    public static void startNewGame(SnakesProto.GameConfig config, String name, JFrame menu) {
        mainMenu = menu;
        Cell[] cells = new Cell[config.getWidth() * config.getHeight()];
        ConcurrentHashMap<Integer, SnakesProto.Direction> nextDirection
                = new ConcurrentHashMap<>(10, 0.81f,1);
        gameCenter = new GameCenter(config, name, cells, nextDirection);
        gameCenter.start();
    }

    public static void setMenuVisible() {
        mainMenu.setVisible(true);
    }

    public static void stopGameCenter() {
        gameCenter.interrupt();
    }

    public static String connectToGame(InetSocketAddress masterAddress, String name, boolean onlyView,
            SnakesProto.GameConfig config) {
        DatagramSocket socket;
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
                System.out.println("Sent to " + masterAddress);
                try {
                    socket.receive(recvPacket);
                    GameMessage message = GameMessage.parseFrom(Arrays.copyOf(recvPacket.getData(),
                            recvPacket.getLength()));
                    if (message.hasAck() && message.getMsgSeq() == msgSeq) {
                        int myId = message.getReceiverId();
                        new ConnectedNode(myId, config, socket, masterAddress).start();
                        return null;
                    }
                    if (message.hasError()) {
                        return message.getError().getErrorMessage();
                    }
                }
                catch (SocketTimeoutException | com.google.protobuf.InvalidProtocolBufferException ignored) {}
            }
        }
        catch (IOException exc) {
            System.err.println(exc.getMessage());
        }
        return "Can not connect to the host";
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
