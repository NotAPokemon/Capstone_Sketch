package dev.korgi.utils;

import java.util.ArrayList;

import dev.korgi.game.ui.Screen;
import dev.korgi.json.JSONObject;
import processing.core.PApplet;
import processing.core.PFont;

public class StyleConstants {

    public static final int BG_DARK = 0xFF0D0F14;
    public static final int BG_CARD = 0xFF1C2130;
    public static final int BG_INPUT = 0xFF161B26;
    public static final int ACCENT = 0xFF4F8EF7;
    public static final int ACCENT_DIM = 0xFF2A4F9A;
    public static final int TEXT_PRIMARY = 0xFFE8EAF0;
    public static final int TEXT_PRIMARY_HOVER = 0xFFB9D2FF;
    public static final int TEXT_DIM = 0xFF7A8099;
    public static final int TEXT_LABEL = 0xFF4F5A72;
    public static final int DANGER = 0xFFE05C5C;
    public static final int SUCCESS_DIM = 0xFF2A7044;
    public static final int SUCCESS = 0xFF4FD17A;
    public static final int BORDER = 0xFF252B3B;
    public static final int RED = 0xFFFF0000;
    public static final int WARN = 0xFFE9D502;

    private static final ArrayList<PFont> fonts = new ArrayList<>();

    public static int getFontId(PFont font) {
        int id = fonts.indexOf(font);
        if (id == -1) {
            id = fonts.size();
            fonts.add(font);
        }
        return id;
    }

    public static PFont getFont(int index) {
        return fonts.get(index);
    }

    public static void addPanelStyles(JSONObject stylesheet) {

        Screen screen = Screen.getInstance();
        stylesheet.set("shadow", new JSONObject()
                .set("bg", 0x3C000000)
                .set("borderRadius", 12f));

        stylesheet.set("card", new JSONObject()
                .set("bg", BG_CARD)
                .set("borderRadius", 10f));

        stylesheet.set("card-border", new JSONObject()
                .set("borderColor", BORDER)
                .set("borderSize", 1f)
                .set("borderRadius", 10f));

        stylesheet.set("label", new JSONObject()
                .set("bg", StyleConstants.TEXT_LABEL)
                .set("txtAlignX", PApplet.CENTER)
                .set("txtAlignY", PApplet.CENTER)
                .set("font", screen.fontMono10));

        stylesheet.set("heading", new JSONObject()
                .set("bg", StyleConstants.TEXT_PRIMARY)
                .set("txtAlignX", PApplet.CENTER)
                .set("txtAlignY", PApplet.CENTER)
                .set("font", screen.fontSans22));

        stylesheet.set("divider", new JSONObject()
                .set("borderColor", StyleConstants.BORDER)
                .set("borderSize", 1f));
    }

    public static final int[] BLUE_BTN_THEME = new int[] { ACCENT, ACCENT_DIM };
    public static final int[] GREEN_BTN_THEME = new int[] { SUCCESS, SUCCESS_DIM };

    public static void addPrimaryButtonStyles(JSONObject stylesheet, String buttonName) {
        addPrimaryButtonStyles(stylesheet, buttonName, BLUE_BTN_THEME);
    }

    public static void addPrimaryButtonStyles(JSONObject stylesheet, String buttonName, int[] theme) {
        Screen screen = Screen.getInstance();

        stylesheet.set("btn-%s-glow".formatted(buttonName), new JSONObject()
                .set("bg", screen.color(theme[0] & 0x00FFFFFF | (60 << 24)))
                .set("borderRadius", 14));

        stylesheet.set("btn-%s-bg".formatted(buttonName), new JSONObject()
                .set("bg", StyleConstants.BG_CARD)
                .set("borderColor", StyleConstants.BORDER)
                .set("borderSize", 1f)
                .set("borderRadius", 8f));

        stylesheet.set("btn-%s-bg.hover".formatted(buttonName), new JSONObject()
                .set("bg", theme[1])
                .set("borderColor", theme[0])
                .set("borderSize", 1f)
                .set("borderRadius", 8f));

        stylesheet.set("btn-%s-bar".formatted(buttonName), new JSONObject()
                .set("bg", theme[0]));

        stylesheet.set("btn-%s-label".formatted(buttonName), new JSONObject()
                .set("bg", StyleConstants.TEXT_DIM)
                .set("txtAlignX", PApplet.CENTER)
                .set("txtAlignY", PApplet.CENTER)
                .set("font", screen.fontSans12));

        stylesheet.set("btn-%s-label.hover".formatted(buttonName), new JSONObject()
                .set("bg", StyleConstants.TEXT_PRIMARY));
    }

    public static void addGhostButtonStyles(JSONObject stylesheet, String buttonName) {

        Screen screen = Screen.getInstance();

        stylesheet.set("btn-settings-bg", new JSONObject()
                .set("borderColor", StyleConstants.BORDER)
                .set("borderSize", 1f)
                .set("borderRadius", 6f));

        stylesheet.set("btn-settings-bg.hover", new JSONObject()
                .set("borderColor", StyleConstants.TEXT_DIM)
                .set("borderSize", 1f)
                .set("borderRadius", 6f));

        stylesheet.set("btn-settings-label", new JSONObject()
                .set("bg", StyleConstants.TEXT_LABEL)
                .set("txtAlignX", PApplet.CENTER)
                .set("txtAlignY", PApplet.CENTER)
                .set("font", screen.fontSans11));

        stylesheet.set("btn-settings-label.hover", new JSONObject()
                .set("bg", StyleConstants.TEXT_DIM));
    }

}
