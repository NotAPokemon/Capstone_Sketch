package dev.korgi.game.ui.pregame;

import java.util.List;

import dev.korgi.game.Game;
import dev.korgi.game.ui.GUI;
import dev.korgi.json.JSONObject;
import dev.korgi.utils.StyleConstants;
import processing.core.PApplet;

public class LoadingGUI extends GUI {

    @Override
    protected List<GUI> getDisplayLocation() {
        return screen.getPregameGUI();
    }

    @Override
    protected void drawGUI() {
        screen.background(StyleConstants.BG_DARK);
        drawCachedBg();

        int cx = screen.width / 2;
        int cy = screen.height / 2;

        drawSpinner(cx, cy);
        drawProgressBar(cx, cy);

        loadStyle("loading-text");
        String dots = ".".repeat((int) ((screen.millis() / 400) % 4));
        screen.text("Loading" + dots, cx, cy + 70);
    }

    private void drawSpinner(int cx, int cy) {
        int spokes = 12;
        float angleStep = PApplet.TWO_PI / spokes;
        float t = (float) (screen.millis() % 1000) / 1000f;

        screen.strokeWeight(2.5f);
        screen.noFill();
        for (int i = 0; i < spokes; i++) {
            float angle = i * angleStep - PApplet.HALF_PI;
            float spokeFade = ((i / (float) spokes) - t + 1f) % 1f;
            int alpha = (int) (spokeFade * 220);
            screen.stroke(StyleConstants.ACCENT & 0x00FFFFFF | (alpha << 24));
            screen.line(
                    cx + PApplet.cos(angle) * 22, cy + PApplet.sin(angle) * 22,
                    cx + PApplet.cos(angle) * 34, cy + PApplet.sin(angle) * 34);
        }
        screen.noStroke();
    }

    @Override
    protected void onClick() {
        cancelEvent();
    }

    private void drawProgressBar(int cx, int cy) {
        float progress = Game.getInitProgress();
        int barW = 240, barH = 3;
        int barX = cx - barW / 2, barY = cy + 100;

        loadStyle("progress-track");
        screen.rect(barX, barY, barW, barH, barH / 2f);

        loadStyle("progress-fill");
        screen.rect(barX, barY, barW * progress, barH, barH / 2f);

        if (progress > 0.02f) {
            loadStyle("progress-dot");
            screen.ellipse(barX + barW * progress, barY + barH / 2f, 6, 6);
        }
    }

    @Override
    protected void createStyleSheet() {

        stylesheet.set("loading-text", new JSONObject()
                .set("bg", StyleConstants.TEXT_DIM)
                .set("txtAlignX", PApplet.CENTER)
                .set("txtAlignY", PApplet.TOP)
                .set("font", screen.fontSans13));

        stylesheet.set("progress-track", new JSONObject()
                .set("bg", StyleConstants.BORDER)
                .set("borderRadius", 2f));

        stylesheet.set("progress-fill", new JSONObject()
                .set("bg", StyleConstants.ACCENT_DIM)
                .set("borderRadius", 2f));

        stylesheet.set("progress-dot", new JSONObject()
                .set("bg", StyleConstants.ACCENT));
    }
}