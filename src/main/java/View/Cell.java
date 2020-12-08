package View;

import java.awt.*;

public interface Cell {
    void paint(Graphics graphics, int x, int y, int size);
    CellType getCellType();
}
