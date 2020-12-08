package Graphics;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;

import MessageSerialize.SnakesProto.GameConfig;
import GameLogic.MainController;

public class NewGameConfigs extends JDialog {

    private final Border defaultBorder;
    private final Color myRed = new Color(212, 32, 32);
    private final String[] defaultSettings = new String[] { "", "40", "30", "1", "1", "1000", "0.1", "100", "800" };
    private final String[] labelNames = new String[] { "Name", "Width", "Height", "Food static", "Food per player",
            "State delay ms", "Dead food prob", "Ping delay ms", "Node timeout ms" };
    private String playerName;
    private GameConfig gameConfig;

    public NewGameConfigs(JFrame menu) {
        super(menu, "Configurations");
        setSize(600, 500);
        setLocationByPlatform(true);
        setLayout(new GridLayout(11,2,5,12));
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                menu.dispose();
            }
        });

        ArrayList<JLabel> labels = new ArrayList<>() {{
            for (String str : labelNames) {
                add(new JLabel(str, SwingConstants.CENTER));
            }
        }};

        ArrayList<JTextField> textFields = new ArrayList<>() {{
            for (String defaultSetting : defaultSettings) {
                JTextField field = new JTextField(defaultSetting);
                field.setBackground(Color.white);
                add(field);
            }
        }};

        for (int it = 0; it < 9; ++it) {
            this.add(labels.get(it));
            this.add(textFields.get(it));
        }

        JLabel status = new JLabel("%STATUS%", SwingConstants.CENTER);
        JTextField warningField = new JTextField("");
        warningField.setEditable(false);
        warningField.setForeground(Color.red);
        defaultBorder = warningField.getBorder();
        warningField.setFont(new Font("", Font.BOLD, 15));

        this.add(status);
        this.add(warningField);

        JButton ok = new JButton("Ok");
        ok.addActionListener(event -> {
            try {
                setDefaultSettings(textFields);
                validateAndBuildConfig(textFields);
                dispose();
                System.out.println("New game starts");
                MainController.startNewGame(gameConfig, playerName, menu);
            }
            catch (IllegalArgumentException exc) {
                warningField.setText(exc.getMessage());
            }
        });

        JButton back = new JButton("Back");
        back.addActionListener(event -> {
            menu.setVisible(true);
            dispose();
        });

        this.add(back);
        this.add(ok);

        menu.setVisible(false);
        setVisible(true);
    }

    private void setDefaultSettings(ArrayList<JTextField> textFields) {
        for (JTextField field : textFields) {
            if (!field.getBorder().equals(defaultBorder)) {
                field.setBorder(defaultBorder);
            }
        }
    }

    public void validateAndBuildConfig(ArrayList<JTextField> fields) {
        ArrayList<Integer> info = new ArrayList<>();
        for (JTextField field : fields) {
            String text = field.getText();
            if (text.isEmpty()) {
                field.setBorder(BorderFactory.createLineBorder(myRed, 2));
                throw new IllegalArgumentException("Empty field.");
            }
            try {
                info.add(Integer.parseInt(text));
            }
            catch (NumberFormatException exc) {
                info.add(-1);
            }
        }

        int width = info.get(1);
        if (width < 10 || width > 100) {
            fields.get(1).setBorder(BorderFactory.createLineBorder(myRed, 2));
            throw new IllegalArgumentException("Invalid width given.");
        }

        int height = info.get(2);
        if (height < 10 || height > 100) {
            fields.get(2).setBorder(BorderFactory.createLineBorder(myRed, 2));
            throw new IllegalArgumentException("Invalid height given.");
        }

        int foodStatic = info.get(3);
        if (foodStatic < 0 || foodStatic > 100) {
            fields.get(3).setBorder(BorderFactory.createLineBorder(myRed, 2));
            throw new IllegalArgumentException("Invalid static food amount given.");
        }

        float foodPerPlayer;
        try {
            foodPerPlayer = Float.parseFloat(fields.get(4).getText());
            if (foodPerPlayer < 0 || foodPerPlayer > 100) {
                throw new NumberFormatException();
            }
        }
        catch (NumberFormatException exc) {
            fields.get(4).setBorder(BorderFactory.createLineBorder(myRed, 2));
            throw new IllegalArgumentException("Invalid food per player amount given.");
        }

        int stateDelayMs = info.get(5);
        if (stateDelayMs < 1 || stateDelayMs > 10000) {
            fields.get(5).setBorder(BorderFactory.createLineBorder(myRed, 2));
            throw new IllegalArgumentException("Invalid state delay ms given.");
        }

        float deadFoodProb;
        try {
            deadFoodProb = Float.parseFloat(fields.get(6).getText());
            if (deadFoodProb < 0 || deadFoodProb > 1) {
                throw new NumberFormatException();
            }
        }
        catch (NumberFormatException exc) {
            fields.get(6).setBorder(BorderFactory.createLineBorder(myRed, 2));
            throw new IllegalArgumentException("Invalid dead food prob given.");
        }

        int pingDelayMs = info.get(7);
        if (pingDelayMs < 1 || pingDelayMs > 10000) {
            fields.get(7).setBorder(BorderFactory.createLineBorder(myRed, 2));
            throw new IllegalArgumentException("Invalid ping delay ms given.");
        }

        int nodeTimeoutMs = info.get(8);
        if (nodeTimeoutMs < 1 || nodeTimeoutMs > 10000) {
            fields.get(8).setBorder(BorderFactory.createLineBorder(myRed, 2));
            throw new IllegalArgumentException("Invalid node timeout ms given.");
        }

        playerName = fields.get(0).getText();
        gameConfig = GameConfig.newBuilder().setWidth(width).setHeight(height).setFoodStatic(foodStatic)
                .setFoodPerPlayer(foodPerPlayer).setStateDelayMs(stateDelayMs).setDeadFoodProb(deadFoodProb)
                .setPingDelayMs(pingDelayMs).setNodeTimeoutMs(nodeTimeoutMs).build();
    }
}
