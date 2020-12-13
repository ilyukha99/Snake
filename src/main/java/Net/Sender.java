package Net;

import GameLogic.GameCenter;
import MessageSerialize.SnakesProto.*;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class Sender extends Thread {

    private final DatagramSocket socket;
    private final ConcurrentHashMap<GameMessage, CopyOnWriteArrayList<InetSocketAddress>> controlMap;
    private final ConcurrentHashMap<InetSocketAddress, Long> pingTimeMap;
    private final GameCenter gameCenter;
    private final int ping_delay_ms;
    private final int node_timeout_ms;

    public Sender(GameCenter center, DatagramSocket datagramSocket, GameConfig config) {
        socket = datagramSocket;
        ping_delay_ms = config.getPingDelayMs();
        node_timeout_ms = config.getNodeTimeoutMs();
        controlMap = new ConcurrentHashMap<>(40, 0.8f, 2);
        pingTimeMap = new ConcurrentHashMap<>();
        gameCenter = center;
    }

    @Override
    public void run() {
        try {
            long timeMark = System.currentTimeMillis(), deathTimeMark = timeMark;
            Set<GameMessage> keys = controlMap.keySet();
            while (!isInterrupted()) {
                if (System.currentTimeMillis() - timeMark > ping_delay_ms) {
                    sendNotifications();
                    timeMark = System.currentTimeMillis();
                    if (System.currentTimeMillis() - deathTimeMark > node_timeout_ms) {
                        deathTimeMark = System.currentTimeMillis();
                        if (gameCenter != null) {
                            clearNodes(keys);
                        }
                        keys = controlMap.keySet();
                    }
                    sendPings();
                }
            }
        }
        catch (IOException exc) {
            System.err.println(exc.getMessage() + " in Sender");
        }
    }

    public void sendError(InetSocketAddress address, String message) throws IOException {
        long msgSeq = UUID.randomUUID().getMostSignificantBits();
        GameMessage msg = GameMessage.newBuilder().setMsgSeq(msgSeq)
                .setError(GameMessage.ErrorMsg.newBuilder().setErrorMessage(message).build()).build();
        byte[] bytes = msg.toByteArray();
        socket.send(new DatagramPacket(bytes, bytes.length, address));
        controlMap.put(msg, new CopyOnWriteArrayList<>(){{add(address);}});
    }

    public void sendAck(InetSocketAddress inetSocketAddress, long msgSeq, int receiverId, int senderId)
            throws IOException {
        byte[] msg = GameMessage.newBuilder().setAck(GameMessage.AckMsg.newBuilder().build())
                .setMsgSeq(msgSeq).setSenderId(senderId).setReceiverId(receiverId).build().toByteArray();
        socket.send(new DatagramPacket(msg, msg.length, inetSocketAddress));
        pingTimeMap.put(inetSocketAddress, System.currentTimeMillis());
    }

    public void sendSteer(InetSocketAddress inetSocketAddress, Direction direction, int id) throws IOException {
        GameMessage msg = GameMessage.newBuilder().setSteer(GameMessage.SteerMsg.newBuilder()
            .setDirection(direction)).setSenderId(id).setMsgSeq(UUID.randomUUID().getMostSignificantBits()).build();
        byte[] bytes = msg.toByteArray();
        socket.send(new DatagramPacket(bytes, bytes.length, inetSocketAddress));
        System.out.println("Steer message sent from id " + id + ", " + direction);
        controlMap.putIfAbsent(msg, new CopyOnWriteArrayList<>(){{add(inetSocketAddress);}});
        pingTimeMap.put(inetSocketAddress, System.currentTimeMillis());
    }

    public void distributeNewState(Set<InetSocketAddress> addresses, GameState state, int id)
            throws IOException {
        controlMap.entrySet().removeIf(entry -> entry.getKey().getTypeCase().equals(GameMessage.TypeCase.STATE));
        GameMessage message = GameMessage.newBuilder().setState(GameMessage.StateMsg.newBuilder().setState(state)
                .build()).setMsgSeq(UUID.randomUUID().getMostSignificantBits()).setSenderId(id).build();
        byte[] stateMsgBytes = message.toByteArray();
        DatagramPacket sendPacket = new DatagramPacket(stateMsgBytes, stateMsgBytes.length);
        CopyOnWriteArrayList<InetSocketAddress> addressesList = new CopyOnWriteArrayList<>();
        for (InetSocketAddress address : addresses) {
            addressesList.add(address);
            sendPacket.setSocketAddress(address);
            socket.send(sendPacket);
            pingTimeMap.put(address, System.currentTimeMillis());
        }
        controlMap.put(message, addressesList);
    }

    private void sendNotifications() throws IOException {
        for (Map.Entry<GameMessage, CopyOnWriteArrayList<InetSocketAddress>> entry : controlMap.entrySet()) {
            byte[] message = entry.getKey().toByteArray();
            CopyOnWriteArrayList<InetSocketAddress> addresses = entry.getValue();
            DatagramPacket packet = new DatagramPacket(message, message.length);
            for (InetSocketAddress address : addresses) {
                packet.setSocketAddress(address);
                socket.send(packet);
                pingTimeMap.put(address, System.currentTimeMillis());
            }
        }
    }

    public void removeFromList(InetSocketAddress address, long msgSeq) {
        for (Map.Entry<GameMessage, CopyOnWriteArrayList<InetSocketAddress>> entry : controlMap.entrySet()) {
            if (entry.getKey().getMsgSeq() == msgSeq) {
                entry.getValue().remove(address);
                return;
            }
        }
    }

    private void clearNodes(Set<GameMessage> keys) throws IOException {
        controlMap.entrySet().removeIf(entry -> entry.getValue().size() == 0);
        for (GameMessage message : keys) {
            CopyOnWriteArrayList<InetSocketAddress> addresses = controlMap.get(message);
            if (addresses == null) {
                continue;
            }
            for (InetSocketAddress address : addresses) {
                gameCenter.removePlayer(address);
            }
        }
    }

    private void sendPings() throws IOException {
        GameMessage ping = GameMessage.newBuilder().setPing(GameMessage.PingMsg.newBuilder().build())
                .setMsgSeq(UUID.randomUUID().getMostSignificantBits()).build();
        byte[] bytes = ping.toByteArray();
        DatagramPacket sendPacket = new DatagramPacket(bytes, bytes.length);
        CopyOnWriteArrayList<InetSocketAddress> list = new CopyOnWriteArrayList<>();
        InetSocketAddress address;
        long mark;
        for (Map.Entry<InetSocketAddress, Long> entry : pingTimeMap.entrySet()) {
            mark = System.currentTimeMillis();
            if (mark - entry.getValue() > ping_delay_ms) {
                address = entry.getKey();
                sendPacket.setSocketAddress(address);
                socket.send(sendPacket);
                list.add(address);
                pingTimeMap.put(address, System.currentTimeMillis());
            }
        }
        controlMap.entrySet().removeIf(e -> e.getKey().getTypeCase().equals(GameMessage.TypeCase.PING));
        controlMap.put(ping, list);
    }

    public void sendRoleChange(GamePlayer.Builder player, NodeRole receiverRole, NodeRole senderRole)
            throws IOException {
        GameMessage message = GameMessage.newBuilder().setRoleChange(GameMessage.RoleChangeMsg.newBuilder()
                .setReceiverRole(receiverRole).setSenderRole(senderRole).build()).build();
        InetSocketAddress address = new InetSocketAddress(player.getIpAddress(), player.getPort());
        byte[] bytes = message.toByteArray();
        DatagramPacket packet = new DatagramPacket(bytes, bytes.length, address);
        socket.send(packet);
        controlMap.put(message, new CopyOnWriteArrayList<>(){{add(address);}});
    }

    public void removeSteer() {
        controlMap.entrySet().removeIf(entry -> entry.getKey().getTypeCase().equals(GameMessage.TypeCase.STEER));
    }
}
