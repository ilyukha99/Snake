package View;

import java.awt.*;

public enum GameColors {

    LIGHT_GREEN(new Color(0xFFE1F8A8)),
    YELLOW(new Color(0xFFF6F6A4)),
    LIGHT_ORANGE(new Color(0xFFF6DDA8)),
    ORANGE(new Color(0xFFF6CBA8)),
    LIGHT_PINK(new Color(0xFFF6BEAC)),
    PINK(new Color(0xFFF5A9A5)),
    RASPBERRY(new Color(0xFFF3A0BC)),
    LIGHT_PURPLE(new Color(0xFFF5A5F0)),
    PURPLE(new Color(0xFFC5A8F5)),

    GREEN(new Color(27, 147, 42)),
    DARK(new Color(21, 19, 19)),
    BACKGROUND(new Color(0x4A4D4A)),
    LINES_COLOR(new Color(0x636963));

    private final Color color;

    GameColors(Color color) {
        this.color = color;
    }

    public Color getColor() {
        return color;
    }
}
