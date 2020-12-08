package Graphics;

import MessageSerialize.SnakesProto;
import MessageSerialize.SnakesProto.GamePlayer;
import GameLogic.MainController;
import View.Cell;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class GameFrame extends JFrame {

    private final SnakeField snakefield;
    private final TablePanel scorePanel;

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
            }};

            scoreTable = new JTable(tableModel);
            add(new JScrollPane(scoreTable));
        }
    }

    public GameFrame(SnakesProto.GameConfig config, Cell[] cells,
                     ConcurrentHashMap<Integer, SnakesProto.Direction> nextDirection) {
        super("Snake");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        setResizable(false);

        Dimension screenSize = getToolkit().getScreenSize();
        int width = screenSize.width * 3 / 4, height = screenSize.height * 3 / 4;
        setSize(width, height);

        snakefield = new SnakeField(config.getWidth(), config.getHeight(), cells);
        snakefield.setPreferredSize(new Dimension(width * 7 / 10, height));
        add(snakefield, BorderLayout.WEST);

        scorePanel = new TablePanel(new Dimension(width * 12 / 43, height));
        JButton exit = new JButton("Exit");
        exit.addActionListener(event -> {
            this.dispose();
            MainController.setMenuVisible();
            MainController.stopGameCenter();
        });
        scorePanel.add(exit, BorderLayout.SOUTH);
        add(scorePanel, BorderLayout.EAST);

        setFocusable(true);

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                if (event.getKeyChar() == 'w' || event.getKeyCode() == KeyEvent.VK_UP) {
                    nextDirection.put(0, SnakesProto.Direction.UP);
                }
                if (event.getKeyChar() == 'a' || event.getKeyCode() == KeyEvent.VK_LEFT) {
                    nextDirection.put(0, SnakesProto.Direction.LEFT);
                }
                if (event.getKeyChar() == 's' || event.getKeyCode() == KeyEvent.VK_DOWN) {
                    nextDirection.put(0, SnakesProto.Direction.DOWN);
                }
                if (event.getKeyChar() == 'd' || event.getKeyCode() == KeyEvent.VK_RIGHT) {
                    nextDirection.put(0, SnakesProto.Direction.RIGHT);
                }
            }
        });

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
        if (sortedPlayers.size() == 0) {
            return;
        }
        int size = sortedPlayers.size();
        for (int it = size - 1; it >= 0; --it) {
            GamePlayer.Builder player = sortedPlayers.get(it);
            tableModel.addRow(new Object[]{size - it, player.getName(), player.getScore()});
        }
    }
}
