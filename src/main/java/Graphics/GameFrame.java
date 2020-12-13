package Graphics;

import MessageSerialize.SnakesProto;
import MessageSerialize.SnakesProto.GamePlayer;
import GameLogic.MainController;
import Net.Sender;
import View.Cell;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class GameFrame extends JFrame {

    private TablePanel scorePanel;

    public static class SnakeField extends JPanel {

        private final int widthCellNumber;
        private final int heightCellNumber;
        private final Cell[] cells;

        @Override
        public void paintComponent(Graphics graphics) {
            int width = this.getWidth(), height = this.getHeight();
            int cellSize = Math.min(width / widthCellNumber, height / heightCellNumber);
            int xSize = widthCellNumber * cellSize, ySize = heightCellNumber * cellSize;
            int startX = (width - xSize) / 2, startY = (height - ySize) / 2;
            for (int y = startY, it = 0; y < startY + ySize; y += cellSize) {
                for (int x = startX; x < startX + xSize && it < cells.length; x += cellSize, ++it) {
                    cells[it].paint(graphics, x, y, cellSize);
                }
            }
        }

        SnakeField(int width, int height, Cell[] cells) {
            super();
            widthCellNumber = width;
            heightCellNumber = height;
            this.cells = cells;
        }
    }

    private static class TablePanel extends JPanel {

        final JTable scoreTable;

        TablePanel(Dimension dimension) {
            super(new BorderLayout());
            setPreferredSize(dimension);

            TableModel tableModel = new DefaultTableModel() {{
                Object[] identifiers = new String[]{ "Place", "Name", "Score" };
                setColumnIdentifiers(identifiers);
            }
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };

            scoreTable = new JTable(tableModel);
            scoreTable.getTableHeader().setReorderingAllowed(false);
            scoreTable.setFocusable(false);
            add(new JScrollPane(scoreTable));
        }
    }

    public GameFrame(SnakesProto.GameConfig config, Cell[] cells,
                     ConcurrentHashMap<Integer, SnakesProto.Direction> nextDirection, int centerId) {
        super("Snake");
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                if (event.getKeyChar() == 'w' || event.getKeyChar() == 'ц' || event.getKeyCode() == KeyEvent.VK_UP) {
                    nextDirection.put(centerId, SnakesProto.Direction.UP);
                }
                if (event.getKeyChar() == 'a' || event.getKeyChar() == 'ф' || event.getKeyCode() == KeyEvent.VK_LEFT) {
                    nextDirection.put(centerId, SnakesProto.Direction.LEFT);
                }
                if (event.getKeyChar() == 's' || event.getKeyChar() == 'ы' || event.getKeyCode() == KeyEvent.VK_DOWN) {
                    nextDirection.put(centerId, SnakesProto.Direction.DOWN);
                }
                if (event.getKeyChar() == 'd' || event.getKeyChar() == 'в' || event.getKeyCode() == KeyEvent.VK_RIGHT) {
                    nextDirection.put(centerId, SnakesProto.Direction.RIGHT);
                }
            }
        });
        initGameFrame(config, cells, true);
    }

    public GameFrame(SnakesProto.GameConfig config, Cell[] cells, Sender sender,
                     InetSocketAddress masterAddress, int playerId) {
        super("Snake");
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                try {
                    sender.removeSteer();
                    if (event.getKeyChar() == 'w' || event.getKeyChar() == 'ц' || event.getKeyCode() == KeyEvent.VK_UP) {
                        sender.sendSteer(masterAddress, SnakesProto.Direction.UP, playerId);
                    }
                    if (event.getKeyChar() == 'a' || event.getKeyChar() == 'ф' || event.getKeyCode() == KeyEvent.VK_LEFT) {
                        sender.sendSteer(masterAddress, SnakesProto.Direction.LEFT, playerId);
                    }
                    if (event.getKeyChar() == 's' || event.getKeyChar() == 'ы' || event.getKeyCode() == KeyEvent.VK_DOWN) {
                        sender.sendSteer(masterAddress, SnakesProto.Direction.DOWN, playerId);
                    }
                    if (event.getKeyChar() == 'd' || event.getKeyChar() == 'в' || event.getKeyCode() == KeyEvent.VK_RIGHT) {
                        sender.sendSteer(masterAddress, SnakesProto.Direction.RIGHT, playerId);
                    }
                }
                catch (IOException exception) {
                    System.err.println(exception.getMessage() + " in KeyListener");
                }
            }
        });
        initGameFrame(config, cells, false);
    }

    public void initGameFrame(SnakesProto.GameConfig config, Cell[] cells, boolean master) {
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        setResizable(false);

        Dimension screenSize = getToolkit().getScreenSize();
        int width = screenSize.width * 3 / 4, height = screenSize.height * 3 / 4;
        setSize(width, height);

        SnakeField snakefield = new SnakeField(config.getWidth(), config.getHeight(), cells);
        snakefield.setPreferredSize(new Dimension(width * 7 / 10, height));
        add(snakefield, BorderLayout.WEST);

        scorePanel = new TablePanel(new Dimension(width * 12 / 43, height));
        JButton exit = new JButton("Exit");
        if (master) {
            exit.addActionListener(event -> {
                this.dispose();
                MainController.setMenuVisible(true);
                MainController.stopGameCenter();
            });
        }
        else {
            exit.addActionListener(event -> {
                this.dispose();
                MainController.setMenuVisible(true);
                MainController.stopConnectedNode();
            });
        }
        scorePanel.add(exit, BorderLayout.SOUTH);
        add(scorePanel, BorderLayout.EAST);

        //setAlwaysOnTop(true);
        setFocusable(true);
        validate();
        setVisible(true);
    }

    public void updateTable(Collection<GamePlayer.Builder> players) {
        ArrayList<GamePlayer.Builder> sortedPlayers = players.stream()
                .filter(p -> !p.getRole().equals(SnakesProto.NodeRole.VIEWER))
                .sorted(Comparator.comparingInt(GamePlayer.Builder::getScore))
                .collect(Collectors.toCollection(ArrayList::new));
        DefaultTableModel tableModel = (DefaultTableModel) scorePanel.scoreTable.getModel();
        tableModel.setNumRows(0);
        if (sortedPlayers.isEmpty()) {
            return;
        }
        int size = sortedPlayers.size();
        for (int it = size - 1; it >= 0; --it) {
            GamePlayer.Builder player = sortedPlayers.get(it);
            tableModel.addRow(new Object[]{size - it, player.getName(), player.getScore()});
        }
    }
}
