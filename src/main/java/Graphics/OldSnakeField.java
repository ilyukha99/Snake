package Graphics;

import MessageSerialize.SnakesProto;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class OldSnakeField extends JFrame {

    private final int cellSize = 6;
    private ImageIcon appleIcon;
    private ArrayList<JLabel> fields;

    private class EmptyCell extends JLabel {
        EmptyCell() {
            setSize(cellSize, cellSize);
            setBorder(BorderFactory.createLineBorder(Color.black, 1));
        }
    }

    private class Food extends JLabel {
        Food() {
            setSize(cellSize, cellSize);
            if (appleIcon != null) {
                setIcon(appleIcon);
            }
            setBorder(BorderFactory.createLineBorder(Color.black, 1));
        }
    }

    public OldSnakeField(SnakesProto.GameConfig gameConfig, String name) {
        super("Snake");
        setLocationByPlatform(true);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        try {
            BufferedImage apple = ImageIO.read(new File("C:\\Users\\Ilya\\Labs\\Snake\\src\\main\\resources\\apple.jpg"));
            appleIcon = new ImageIcon(apple.getScaledInstance(cellSize, cellSize, Image.SCALE_SMOOTH));
        }
        catch (IOException exc) {
            appleIcon = null;
        }

        JPanel panel = new JPanel();
        int height = gameConfig.getHeight(), width = gameConfig.getWidth(), size = height * width;
        panel.setLayout(new GridLayout(height, width));

        setSize((cellSize + 2) * width, (cellSize + 2) * height);

        fields = new ArrayList<>(size);
        for (int it = 0; it < size; ++it) {
            EmptyCell field = new EmptyCell();
            fields.add(field);
            panel.add(field);
        }

        add(panel);
        validate();
        setVisible(true);
    }
}
