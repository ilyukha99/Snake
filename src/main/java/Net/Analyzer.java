package Net;

import GameLogic.GameCenter;
import GameLogic.MainController;
import MessageSerialize.SnakesProto;
import MessageSerialize.SnakesProto.NodeRole;
import MessageSerialize.SnakesProto.GameMessage;

import java.io.IOException;
import java.net.*;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

public class Analyzer extends Thread {

    private final DatagramSocket socket;
    private final GameCenter gameCenter;
    private final Sender sender;
    private final ConcurrentHashMap<Integer, SnakesProto.Direction> nextDirections;

    public Analyzer(GameCenter center, DatagramSocket datagramSocket, Sender messageSender,
                    ConcurrentHashMap<Integer, SnakesProto.Direction> directions) {
        socket = datagramSocket;
        gameCenter = center;
        sender = messageSender;
        nextDirections = directions;
    }

    @Override
    public void run() {
        int timeOut = 500;
        byte[] buffer = new byte[1024];
        DatagramPacket recvPacket = new DatagramPacket(buffer, buffer.length);
        try {
            socket.setSoTimeout(timeOut);
            while (!isInterrupted()) {
                try {
                    socket.receive(recvPacket);
                    GameMessage message = GameMessage.parseFrom(Arrays.copyOf(recvPacket.getData(),
                            recvPacket.getLength()));
                    analyzeMessage(message, recvPacket.getAddress(), recvPacket.getPort());
                }
                catch (SocketTimeoutException | com.google.protobuf.InvalidProtocolBufferException ignored) {}
            }
        }
        catch (IOException exception) {
            System.err.println(exception.getMessage());
            MainController.stopGameCenter();
        }
        finally {
            if (!socket.isClosed()) {
                socket.close();
            }
        }
    }

    private void analyzeMessage(GameMessage message, InetAddress inetAddress, int port) throws IOException {
        InetSocketAddress address = new InetSocketAddress(inetAddress, port);
        switch (message.getTypeCase()) {
            case JOIN -> {
                GameMessage.JoinMsg joinMsg = message.getJoin();
                if (!gameCenter.checkJoin(inetAddress, port)) {
                    return;
                }
                NodeRole role = joinMsg.getOnlyView() ? NodeRole.VIEWER : NodeRole.NORMAL;
                int id = gameCenter.getNextPlayerId();
                if (role.equals(NodeRole.NORMAL)) {
                    boolean res = gameCenter.addNewSnake(id);
                    if (!res) {
                        sender.sendError(address, "Lack of space on the field.");
                        return;
                    }
                }
                sender.sendAck(address, message.getMsgSeq(), id, 0);
                gameCenter.addNewPlayer(joinMsg.getName(), inetAddress.getHostAddress(), port, role, id);
            }
            case STEER -> {
                GameMessage.SteerMsg steerMsg = message.getSteer();
                int id = message.getSenderId();
                if (!gameCenter.verifySteer(address, id)) {
                    return;
                }
                nextDirections.put(id, steerMsg.getDirection());
                sender.sendAck(address, message.getMsgSeq(), 0, 0);
                //System.out.println("Steer was changed from " + message.getSenderId() + " to " + steerMsg.getDirection().toString());
            }
            case ACK -> {
                sender.removeFromList(address, message.getMsgSeq());
                //System.out.println("Ack from " + message.getSenderId());
            }
            case PING -> {
                sender.sendAck(address, message.getMsgSeq(), 0, 0);
                //System.out.println("Ping from " + message.getSenderId());
            }
        }
    }
}
