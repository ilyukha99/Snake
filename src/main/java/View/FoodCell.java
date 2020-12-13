package View;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.File;
import java.io.IOException;

public class FoodCell implements Cell {

    private static Image image;

    static {
        try {
            image = ImageIO.read(new File("C:\\Users\\Ilya\\Labs\\Snake\\apple.jpg"));
        }
        catch (IOException exc) {
            System.err.println(exc.getMessage());
        }
    }

    @Override
    public void paint(Graphics graphics, int x, int y, int size) {
        graphics.drawImage(image, x, y, size, size, (img, infoFlags, a, b, width, height) -> false);
        graphics.setColor(GameColors.LINES_COLOR.getColor());
        graphics.drawRect(x, y, size, size);
    }

    @Override
    public CellType getCellType() {
        return CellType.FOOD;
    }
}
