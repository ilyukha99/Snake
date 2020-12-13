package Net;

import MessageSerialize.SnakesProto.GameMessage;
import Utilities.Pair;

import java.io.IOException;
import java.net.*;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.concurrent.ConcurrentHashMap;

public class GameListener implements Runnable {

    private MulticastSocket receiveSocket;
    private final int groupPort = 9192;
    //map's entry contains ip of game's master, AnnouncementMsg and time msg was received
    public final ConcurrentHashMap<InetSocketAddress, Pair<GameMessage.AnnouncementMsg, Long>>
            availableGames = new ConcurrentHashMap<>(5, 0.85f, 1);

    @Override
    public void run() {
        try {
            Thread.currentThread().setName("GameListener");
            String groupAddress = "239.192.0.4";
            InetSocketAddress groupInetSocketAddress = new InetSocketAddress(groupAddress, groupPort);
            NetworkInterface networkInterface = getLocalNetworkInterface(groupInetSocketAddress);
            receiveSocket = new MulticastSocket(groupPort);
            receiveSocket.joinGroup(groupInetSocketAddress, networkInterface);

            long timeMark;
            byte[] receiveBuffer = new byte[1024];
            DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            while (!Thread.currentThread().isInterrupted()) {
                receiveSocket.receive(receivePacket);
                timeMark = System.currentTimeMillis();

                GameMessage message = GameMessage.parseFrom(Arrays.copyOf(receivePacket.getData(),
                        receivePacket.getLength()));
                if (message.hasAnnouncement()) {
                    availableGames.put(new InetSocketAddress(receivePacket.getAddress(), receivePacket.getPort()),
                            new Pair<>(message.getAnnouncement(), timeMark));
                }
            }
        }
        catch (com.google.protobuf.InvalidProtocolBufferException ignored) {}
        catch (IOException exc) {
            System.err.println(exc.getMessage());
            System.exit(1);
        }
        finally {
            if (!receiveSocket.isClosed()) {
                receiveSocket.close();
            }
        }
    }

    private NetworkInterface getLocalNetworkInterface(InetSocketAddress address) throws IOException {
        Enumeration<NetworkInterface> enumeration = NetworkInterface.getNetworkInterfaces();
        MulticastSocket testSocket = new MulticastSocket(groupPort);
        while (enumeration.hasMoreElements()) {
            NetworkInterface inter = enumeration.nextElement();
            if (inter.supportsMulticast() && !inter.isLoopback() && inter.isUp()) {
                try {
                    testSocket.joinGroup(address, inter);
                    testSocket.leaveGroup(address, inter);
                    if (!testSocket.isClosed()) {
                        testSocket.close();
                    }
                    return inter;
                }
                catch (IOException ignored) {}
            }
        }
        throw new IOException("No network interface found.");
    }

    public ConcurrentHashMap<InetSocketAddress, Pair<GameMessage.AnnouncementMsg, Long>> getAvailableGames() {
        return availableGames;
    }
}
