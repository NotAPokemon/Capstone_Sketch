package dev.korgi.game.ui;

import java.util.List;

import dev.korgi.game.Game;
import dev.korgi.json.JSONObject;
import dev.korgi.utils.StyleConstants;
import processing.core.PApplet;

public class ErrorMessage extends GUI {

    @Override
    protected List<GUI> getDisplayLocation() {
        if (Game.isInitialized()) {
            return screen.getGameGui();
        } else {
            return screen.getPregameGUI();
        }
    }

    @Override
    protected void onOpen() {
        startTime = System.nanoTime();
    }

    private String value;
    private long startTime;
    private double time;
    private boolean isError;

    public ErrorMessage(String value, double time) {
        this.value = value;
        this.time = time;
        this.isError = true;
    }

    public void setError(boolean isError) {
        this.isError = isError;
    }

    @Override
    protected void drawGUI() {
        if (value.isBlank()) {
            return;
        }

        loadStyle("inner");
        rect(screen.width / 2f - 200, 12, 400, 30);

        loadStyle("border");
        rect(screen.width / 2f - 200, 12, 400, 30);

        loadStyle("txt");
        if (isError) {
            addModifer("error");
        } else {
            addModifer("warn");
        }
        screen.text(value, screen.width / 2, 27);
        if (((System.nanoTime() - startTime) / 1e9) >= time) {
            hide();
        }
    }

    @Override
    protected void createStyleSheet() {

        stylesheet.set("inner", new JSONObject()
                .set("bg", StyleConstants.DANGER & 0x00FFFFFF | (int) (255 * 0.15f) << 24)
                .set("borderRadius", 6));

        stylesheet.set("border", new JSONObject()
                .set("borderColor", StyleConstants.DANGER & 0x00FFFFFF | (int) (255 * 0.4f) << 24)
                .set("borderSize", 1)
                .set("borderRadius", 6));

        stylesheet.set("txt", new JSONObject()
                .set("font", screen.fontSans12)
                .set("txtAlignX", PApplet.CENTER)
                .set("txtAlignY", PApplet.CENTER));

        stylesheet.set("txt.error", new JSONObject()
                .set("bg", StyleConstants.DANGER));

        stylesheet.set("txt.warn", new JSONObject()
                .set("bg", StyleConstants.WARN));

    }

}
