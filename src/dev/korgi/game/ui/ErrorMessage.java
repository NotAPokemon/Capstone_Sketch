package dev.korgi.game.ui;

import dev.korgi.json.JSONObject;
import dev.korgi.utils.StyleConstants;

public class ErrorMessage extends GUI {

    private String value = "";

    @Override
    protected void drawGUI() {
        loadStyle("txt");
        screen.text(value, 100, 100);
    }

    @Override
    protected void createStyleSheet() {
        stylesheet.set("txt", new JSONObject()
                .set("bg", StyleConstants.RED)
                .set("font", screen.fontSans28));
    }

    public void setValue(String value) {
        this.value = value;
    }

}
