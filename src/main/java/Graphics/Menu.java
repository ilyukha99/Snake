package Graphics;

import GameLogic.MainController;
import MessageSerialize.SnakesProto;
import MessageSerialize.SnakesProto.GameMessage;
import Utilities.AnnouncementTable;
import Utilities.Pair;

import javax.swing.*;
import java.awt.*;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class Menu extends JFrame {
    private CompletableFuture<Void> completableFuture = null;

    @Override
    public void dispose() {
        super.dispose();
        completableFuture.cancel(true);
    }

    public Menu(ConcurrentHashMap<InetSocketAddress, Pair<GameMessage.AnnouncementMsg, Long>> games) {
        super("Menu");
        setSize(600, 500);
        setLayout(new BorderLayout());
        setLocationByPlatform(true);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        JButton newGame = new JButton("Новая игра");
        newGame.setPreferredSize(new Dimension(290, 30));
        newGame.addActionListener(event -> new NewGameConfigs(this));

        AnnouncementTable gameTable = new AnnouncementTable(games);
        gameTable.setRowHeight(24);
        gameTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        completableFuture = CompletableFuture.runAsync(() -> {
            Thread.currentThread().setName("Table updater");
            long timeMark = System.currentTimeMillis();
            int timeout = 1000, deathTimeout = 3 * timeout;
            while (!completableFuture.isCancelled()) {
                gameTable.update();
                if (System.currentTimeMillis() - timeMark > deathTimeout) {
                    games.entrySet().removeIf(entry ->
                            System.currentTimeMillis() - entry.getValue().getSecond() > deathTimeout);
                    timeMark = System.currentTimeMillis();
                }
                try {
                    Thread.sleep(timeout);
                } catch (InterruptedException exc) {
                    return;
                }
            }
            System.out.println(Thread.currentThread().getName() + " terminated.");
        });

        JButton connectToGame = new JButton("Присоединиться");
        connectToGame.setPreferredSize(new Dimension(290, 30));
        connectToGame.addActionListener(event -> {
            int index = gameTable.getSelectedRow();
            System.err.println("Connecting to existing game using index = " + index + " of table");
            if (index < 0) {
                return;
            }
            Pair<InetSocketAddress, SnakesProto.GameConfig> info;

            try {
                info = gameTable.getAddressByIndex(index);
            }
            catch (NullPointerException exception) {
                return;
            }
            InetSocketAddress masterAddress = info.getFirst();
            SnakesProto.GameConfig gameConfig = info.getSecond();

            String[] options = new String[]{"Player", "Viewer"};
            String playerName = JOptionPane.showInputDialog(this, "Enter your name",
                    "Default");
            if (playerName == null || playerName.length() == 0) {
                return;
            }
            String playerType = (String) JOptionPane.showInputDialog(this, "Select player type:",
                    "Player type selection", JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
            if (playerType == null) {
                return;
            }

            String result = MainController.connectToGame(masterAddress, playerName,
                    playerType.equals("Viewer"), gameConfig, this);
            if (result != null) {
                JOptionPane.showConfirmDialog(this, result.concat(" ").concat(masterAddress.toString()),
                        "Connecting error", JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE);
            }
        });

        Box contents = new Box(BoxLayout.Y_AXIS);
        JScrollPane scrollPane = new JScrollPane(gameTable);
        scrollPane.setBackground(new Color(109, 99, 99)); //magic numbers
        scrollPane.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
        contents.add(scrollPane);

        JPanel buttons = new JPanel(new BorderLayout());
        buttons.add(newGame, BorderLayout.WEST);
        buttons.add(connectToGame, BorderLayout.EAST);

        getContentPane().add(contents);
        getContentPane().add(buttons, BorderLayout.SOUTH);
        setVisible(true);
    }
}
