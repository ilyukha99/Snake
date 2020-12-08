package View;

import MessageSerialize.SnakesProto.Direction;

import java.awt.*;

public class SnakeHeadCell implements Cell {

    private Direction direction;
    private final int id;

    public SnakeHeadCell(Direction direction, int id) {
        this.direction = direction;
        this.id = id;
    }

    @Override
    public void paint(Graphics graphics, int x, int y, int cellSize) {
        graphics.setColor(GameColors.BACKGROUND.getColor());
        graphics.fillRect(x, y, cellSize, cellSize);
        graphics.setColor(GameColors.values()[id % 9].getColor());
        graphics.fillRoundRect(x, y, cellSize, cellSize, cellSize / 2, cellSize / 2);
        graphics.setColor(GameColors.LINES_COLOR.getColor());
        graphics.drawRect(x, y, cellSize, cellSize);

        int space = cellSize / 6, diameter = cellSize / 4;
        graphics.setColor(GameColors.DARK.getColor());
        switch (direction) {
            case DOWN -> {
                graphics.fillOval(x + space, y + 4 * space, diameter, diameter);
                graphics.fillOval(x + 4 * space, y + 4 * space, diameter, diameter);
            }
            case UP -> {
                graphics.fillOval(x + space, y + space, diameter, diameter);
                graphics.fillOval(x + 4 * space, y + space, diameter, diameter);
            }
            case LEFT -> {
                graphics.fillOval(x + space, y + space, diameter, diameter);
                graphics.fillOval(x + space, y + 4 * space, diameter, diameter);
            }
            case RIGHT -> {
                graphics.fillOval(x + 4 * space, y + space, diameter, diameter);
                graphics.fillOval(x + 4 * space, y + 4 * space, diameter, diameter);
            }
        }
    }

    @Override
    public CellType getCellType() {
        return CellType.SNAKE_HEAD;
    }

    public int getId() {
        return id;
    }
}
