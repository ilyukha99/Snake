package Net;

import GameLogic.GameCenter;
import GameLogic.MainController;
import MessageSerialize.SnakesProto.NodeRole;
import MessageSerialize.SnakesProto.GameMessage;

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;

public class Analyzer extends Thread {

    private final ArrayList<Long> receivedMessages = new ArrayList<>();
    private final DatagramSocket socket;
    private final GameCenter gameCenter;

    public Analyzer(GameCenter center, DatagramSocket datagramSocket) {
        socket = datagramSocket;
        gameCenter = center;
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
            socket.close();
        }
    }

    private void analyzeMessage(GameMessage message, InetAddress inetAddress, int port) throws IOException {
        if (message.getTypeCase() == GameMessage.TypeCase.JOIN) {
            GameMessage.JoinMsg joinMsg = message.getJoin();
            NodeRole role = joinMsg.getOnlyView() ? NodeRole.VIEWER : NodeRole.NORMAL;
            long msgSeq = message.getMsgSeq();
            int id = gameCenter.getNextPlayerId();
            if (role.equals(NodeRole.NORMAL)) {
                gameCenter.addNewSnake(id);
                if (id == -1) {
                    sendError(inetAddress, port);
                    return;
                }
            }
            sendAck(inetAddress, port, msgSeq);
            gameCenter.addNewPlayer(joinMsg.getName(), inetAddress.getHostAddress(), port, role, id);
        }
    }

    private void sendError(InetAddress address, int port) throws IOException {
        byte[] msg = GameMessage.newBuilder().setMsgSeq(-10)
                .setError(GameMessage.ErrorMsg.newBuilder().setErrorMessage("Lack of space on the field.").build())
                .build().toByteArray();
        socket.send(new DatagramPacket(msg, msg.length, new InetSocketAddress(address, port)));
    }

    private void sendAck(InetAddress address, int port, long msgSeq) throws IOException {
        byte[] msg = GameMessage.newBuilder().setAck(GameMessage.AckMsg.newBuilder().build())
                .setMsgSeq(msgSeq).build().toByteArray();
        socket.send(new DatagramPacket(msg, msg.length, new InetSocketAddress(address, port)));
    }
}
