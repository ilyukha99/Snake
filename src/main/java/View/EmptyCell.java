package View;

import java.awt.*;

public class EmptyCell implements Cell {

    @Override
    public void paint(Graphics graphics, int x, int y, int cellSize) {
        graphics.setColor(GameColors.BACKGROUND.getColor());
        graphics.fillRect(x, y, cellSize, cellSize);
        graphics.setColor(GameColors.LINES_COLOR.getColor());
        graphics.drawRect(x, y, cellSize, cellSize);
    }

    @Override
    public CellType getCellType() {
        return CellType.EMPTY;
    }
}
