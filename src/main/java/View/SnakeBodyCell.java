package View;

import java.awt.*;

public class SnakeBodyCell implements Cell {

    private final int id;

    @Override
    public void paint(Graphics graphics, int x, int y, int cellSize) {
        graphics.setColor(GameColors.BACKGROUND.getColor());
        graphics.fillRect(x, y, cellSize, cellSize);
        graphics.setColor(GameColors.values()[id % 9].getColor());
        graphics.fillRoundRect(x, y, cellSize, cellSize, cellSize / 2, cellSize / 2);
        graphics.setColor(GameColors.LINES_COLOR.getColor());
        graphics.drawRect(x, y, cellSize, cellSize);
    }

    public SnakeBodyCell(int id) {
        this.id = id;
    }

    @Override
    public CellType getCellType() {
        return CellType.SNAKE_BODY;
    }

    public int getId() {
        return id;
    }
}
