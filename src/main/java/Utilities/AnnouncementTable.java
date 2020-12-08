package Utilities;

import MessageSerialize.SnakesProto.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AnnouncementTable extends JTable {
    private final ConcurrentHashMap<InetSocketAddress, Pair<GameMessage.AnnouncementMsg, Long>> gameMap;
    private final ArrayList<InetSocketAddress> indexedAddresses;

    public AnnouncementTable(ConcurrentHashMap<InetSocketAddress, Pair<GameMessage.AnnouncementMsg, Long>> games) {
        super();
        DefaultTableModel tableModel = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        indexedAddresses = new ArrayList<>();
        tableModel.setColumnIdentifiers(new String[]{ "Ведущий", "#", "Размер", "Еда" });
        setModel(tableModel);
        gameMap = games;
    }

    public void update() {
        DefaultTableModel model = (DefaultTableModel) getModel();
        int selectedRowNum = getSelectedRow();
        InetSocketAddress selectedAddress = null;
        if (selectedRowNum >= 0) {
            selectedAddress = indexedAddresses.get(selectedRowNum);
        }
        model.setNumRows(0);
        indexedAddresses.clear();

        for (Map.Entry<InetSocketAddress, Pair<GameMessage.AnnouncementMsg, Long>> info : gameMap.entrySet()) {
            GameMessage.AnnouncementMsg message = info.getValue().getFirst();
            indexedAddresses.add(info.getKey());
            GamePlayers players = message.getPlayers();

            GamePlayer master = null;
            for (GamePlayer player : players.getPlayersList()) {
                if (player.getRole().equals(NodeRole.MASTER)) {
                    master = player;
                }
            }

            if (master == null) {
                return;
            }
            GameConfig config = message.getConfig();

            Object[] row = new String[] {
                    master.getName(),
                    String.valueOf(players.getPlayersCount()),
                    String.valueOf(config.getWidth()).concat("x").concat(String.valueOf(config.getHeight())),
                    String.valueOf(config.getFoodStatic()).concat("+")
                            .concat(String.valueOf(config.getFoodPerPlayer())).concat("x")
            };
            model.addRow(row);
        }

        if (selectedRowNum < indexedAddresses.size() && selectedRowNum >= 0 && selectedAddress != null) {
            if (selectedAddress.equals(indexedAddresses.get(selectedRowNum))) {
                setRowSelectionInterval(selectedRowNum, selectedRowNum);
            }
        }
    }

    public Pair<InetSocketAddress, GameConfig> getAddressByIndex(int index) {
        InetSocketAddress address = indexedAddresses.get(index);
        return new Pair<>(address, gameMap.get(address).getFirst().getConfig());
    }
}
