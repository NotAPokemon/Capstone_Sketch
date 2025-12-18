package dev.korgi.gui;

import java.util.List;

import dev.korgi.Game;
import dev.korgi.gui.rendering.WorldSpace;
import dev.korgi.player.Player;
import processing.core.PApplet;

public class Screen extends PApplet {

    private static Screen mInstance;

    public static Screen getInstance() {
        if (mInstance == null) {
            mInstance = new Screen();
        }
        return mInstance;
    }

    @Override
    public void settings() {
        size(900, 600);
    }

    @Override
    public void setup() {
        frameRate(60);
        textFont(createFont("Arial", 14));
    }

    @Override
    public void keyPressed() {
        if (Game.isClient) {
            Player client = Game.getClient();
            client.pressedKeys.add("" + key);
        }
    }

    @Override
    public void keyReleased() {
        if (Game.isClient) {
            Player client = Game.getClient();
            client.pressedKeys.remove("" + key);
        }
    }

    @Override
    public void draw() {
        if (Game.isInitialized()) {
            Game.loop();
        } else {
            drawSelector();
            return;
        }

        if (Game.isClient) {
            WorldSpace.execute();
            drawOpenClientMenus();
        } else {
            drawServerInfo();
        }

        drawHUD();
    }

    private void drawHUD() {
        fill(255);
        text("FPS: " + (int) frameRate, 30, 50);
    }

    private boolean stopHostingHover = false;

    private void drawOpenClientMenus() {

    }

    private void drawServerInfo() {
        background(50);

        int buttonWidth = 200;
        int buttonHeight = 60;
        int hostX = width / 2 - buttonWidth - 20;
        int buttonsY = height / 4 - buttonHeight / 2;

        stopHostingHover = mouseX > hostX && mouseX < hostX + buttonWidth &&
                mouseY > buttonsY && mouseY < buttonsY + buttonHeight;

        fill(stopHostingHover ? 100 : 200);
        rect(hostX, buttonsY, buttonWidth, buttonHeight, 10);
        fill(0);
        textAlign(CENTER, CENTER);
        text("Stop Hosting", hostX + buttonWidth / 2, buttonsY + buttonHeight / 2);

        List<Player> players = Game.getPlayers();
        int centerX = width / 2;
        int centerY = height / 2;
        int radius = 200; // radius of circle layout
        int playerCount = players.size();

        for (int i = 0; i < playerCount; i++) {
            Player player = players.get(i);
            float angle = map(i, 0, playerCount, 0, TWO_PI);
            float px = centerX + cos(angle) * radius;
            float py = centerY + sin(angle) * radius;

            // Generate a consistent random color based on UUID
            int hash = player.internal_id.hashCode();
            int r = (hash >> 16) & 0xFF;
            int g = (hash >> 8) & 0xFF;
            int b = hash & 0xFF;

            fill(r, g, b);
            ellipse(px, py, 50, 50); // draw player circle

            // Draw first 8 characters of UUID
            fill(255);
            textAlign(CENTER, CENTER);
            text(player.internal_id.substring(0, 8), px, py);
        }

    }

    private boolean hoverHost = false;
    private boolean hoverJoin = false;

    private void drawSelector() {
        background(50);

        int buttonWidth = 200;
        int buttonHeight = 60;
        int hostX = width / 2 - buttonWidth - 20;
        int joinX = width / 2 + 20;
        int buttonsY = height / 2 - buttonHeight / 2;

        hoverHost = mouseX > hostX && mouseX < hostX + buttonWidth &&
                mouseY > buttonsY && mouseY < buttonsY + buttonHeight;
        hoverJoin = mouseX > joinX && mouseX < joinX + buttonWidth &&
                mouseY > buttonsY && mouseY < buttonsY + buttonHeight;

        fill(hoverHost ? 100 : 200);
        rect(hostX, buttonsY, buttonWidth, buttonHeight, 10);
        fill(0);
        textAlign(CENTER, CENTER);
        text("Host Game", hostX + buttonWidth / 2, buttonsY + buttonHeight / 2);

        fill(hoverJoin ? 100 : 200);
        rect(joinX, buttonsY, buttonWidth, buttonHeight, 10);
        fill(0);
        text("Join Game", joinX + buttonWidth / 2, buttonsY + buttonHeight / 2);
    }

    @Override
    public void mousePressed() {
        if (!Game.isInitialized()) {
            try {
                if (hoverHost) {
                    Game.isClient = false;
                    Game.init();
                } else if (hoverJoin) {
                    Game.isClient = true;
                    Game.init();
                }
            } catch (Exception e) {

            }

        } else if (Game.isClient) {

        } else {
            if (stopHostingHover) {
                exit();
            }

        }
    }

    @Override
    public void exit() {
        if (Game.isClient) {
            WorldSpace.kernel.dispose();
        }
        super.exit();
    }
}
