package Net;

import MessageSerialize.SnakesProto.GameMessage;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Sender extends Thread {

    private final DatagramSocket socket;
    private final ConcurrentHashMap<GameMessage, ArrayList<InetSocketAddress>> controlMap = new ConcurrentHashMap<>();

    public Sender(DatagramSocket datagramSocket) {
        socket = datagramSocket;
    }

    @Override
    public void run() {

    }

    public void sendError(InetAddress address, int port, String message) throws IOException {
        long msgSeq = UUID.randomUUID().getMostSignificantBits();
        GameMessage msg = GameMessage.newBuilder().setMsgSeq(msgSeq)
                .setError(GameMessage.ErrorMsg.newBuilder().setErrorMessage(message).build())
                .build();
        byte[] bytes = msg.toByteArray();
        socket.send(new DatagramPacket(bytes, bytes.length, new InetSocketAddress(address, port)));
        controlMap.put(msg, new ArrayList<>(){{add(new InetSocketAddress(address, port));}});
    }

//    private void sendAck(InetAddress address, int port, long msgSeq) throws IOException {
//        byte[] msg = GameMessage.newBuilder().setAck(GameMessage.AckMsg.newBuilder().build())
//                .setMsgSeq(msgSeq).setReceiverId().build().toByteArray();
//        socket.send(new DatagramPacket(msg, msg.length, new InetSocketAddress(address, port)));
//    }
}
