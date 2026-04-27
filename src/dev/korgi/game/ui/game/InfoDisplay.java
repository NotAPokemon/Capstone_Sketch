package dev.korgi.game.ui.game;

import dev.korgi.game.Game;
import dev.korgi.game.ui.GUI;
import dev.korgi.json.JSONObject;
import dev.korgi.networking.NetworkStream;
import dev.korgi.utils.StyleConstants;
import processing.core.PApplet;

public class InfoDisplay extends GUI {

    int panelW = 340;
    int panelH = 180;
    int panelX = screen.width / 2 - panelW / 2;
    int panelY = screen.height / 2 - panelH / 2;

    int bx = panelX + 24;
    int by = panelY + 86;
    int bw = panelW - 48;
    int bh = 46;

    @Override
    protected void drawGUI() {
        if (Game.isClient) {
            loadStyle("info-text");
            screen.text("PING: " + (int) NetworkStream.getPing() + "ms", 16, 48);
            Game.withClient((p, pos) -> {
                loadStyle("pos-text");
                screen.text(String.format("%.1f  /  %.1f  /  %.1f", pos.x, pos.y, pos.z), screen.width / 2f, 16);
            });

            loadStyle("crosshair");
            int cx = screen.width / 2, cy = screen.height / 2;
            int gap = 5, size = 9;
            screen.line(cx - gap - size, cy, cx - gap, cy);
            screen.line(cx + gap, cy, cx + gap + size, cy);
            screen.line(cx, cy - gap - size, cx, cy - gap);
            screen.line(cx, cy + gap, cx, cy + gap + size);
        } else {
            screen.background(StyleConstants.BG_DARK);
            drawCachedBg();

            loadStyle("shadow");
            rect(panelX + 4, panelY + 6, panelW, panelH);

            loadStyle("card");
            rect(panelX, panelY, panelW, panelH);

            loadStyle("card-border");
            rect(panelX, panelY, panelW, panelH);

            loadStyle("label");
            screen.text("SERVER CONTROL", panelX + panelW / 2, panelY + 24);

            loadStyle("heading");
            screen.text("Hosting", panelX + panelW / 2, panelY + 46);

            loadStyle("divider");
            screen.line(panelX + 24, panelY + 71, panelX + panelW - 24, panelY + 71);

            drawPrimaryButton("STOP HOSTING", bx, by, bw, bh, "stop-host");

        }
        loadStyle("info-text");
        screen.text("FPS: " + (int) screen.frameRate, 16, 16);
        screen.text("Sync: " + Math.floor((NetworkStream.packetCount / NetworkStream.frameCount) * 100) + "%", 16, 32);

    }

    @Override
    protected void onClick() {
        boolean hover = hitTest(bx, by, bw, bh);
        if (!Game.isClient && hover) {
            screen.exit();
            cancelEvent();
        }
    }

    @Override
    protected void createStyleSheet() {

        stylesheet.set("info-text", new JSONObject()
                .set("font", screen.fontSans14)
                .set("bg", StyleConstants.TEXT_LABEL)
                .set("txtAlignX", PApplet.LEFT)
                .set("txtAlignY", PApplet.TOP));

        stylesheet.set("pos-text", new JSONObject()
                .set("font", screen.fontSans14)
                .set("bg", StyleConstants.TEXT_PRIMARY)
                .set("txtAlignX", PApplet.CENTER)
                .set("txtAlignY", PApplet.TOP));

        stylesheet.set("crosshair", new JSONObject()
                .set("bg", 0xB4FFFFFF)
                .set("borderSize", 4));

        StyleConstants.addPanelStyles(stylesheet);
        StyleConstants.addPrimaryButtonStyles(stylesheet, "stop-host");
    }

}
