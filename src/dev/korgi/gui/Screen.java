package dev.korgi.gui;

import java.awt.AWTException;
import java.awt.Component;
import java.awt.Point;
import java.awt.Robot;
import java.util.List;

import dev.korgi.Game;
import dev.korgi.gui.rendering.WorldSpace;
import dev.korgi.networking.NetworkStream;
import dev.korgi.player.Player;
import processing.core.PApplet;

public class Screen extends PApplet {

    private static Screen mInstance;
    private Robot robot;

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
        try {
            robot = new Robot();
        } catch (AWTException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void keyPressed() {
        if (Game.isClient) {
            Player client = Game.getClient();
            String k = normalizeKey();
            if (k != null && client != null && !client.pressedKeys.contains(k)) {
                client.pressedKeys.add(k);
            }
        }
    }

    private String normalizeKey() {
        if (key != CODED) {
            return ("" + key).toLowerCase();
        }

        switch (keyCode) {
            case SHIFT:
                return "SHIFT";
            case CONTROL:
                return "CTRL";
            case ALT:
                return "ALT";
            case TAB:
                return "TAB";
            case ENTER:
                return "ENTER";
            case ESC:
                return "ESC";
            case UP:
                return "UP";
            case DOWN:
                return "DOWN";
            case LEFT:
                return "LEFT";
            case RIGHT:
                return "RIGHT";
        }

        return null;
    }

    @Override
    public void keyReleased() {
        if (Game.isClient) {
            Player client = Game.getClient();
            if (client != null) {
                client.pressedKeys.remove(normalizeKey());
            }
        }
    }

    @Override
    public void focusLost() {
        if (!Game.isClient)
            return;

        Player client = Game.getClient();
        if (client != null) {
            client.pressedKeys.clear();
        }
    }

    private boolean firstMouse = true;
    private float mouseSensitivity = 0.002f;

    @Override
    public void draw() {
        if (Game.isInitialized()) {
            Game.loop();
        } else {
            drawSelector();
            return;
        }

        if (Game.isClient) {
            handleMouseMovement();
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
        if (Game.isClient) {
            text("Ping: " + (int) NetworkStream.getPing(), 30, 60);
        }
    }

    private boolean stopHostingHover = false;

    private void drawOpenClientMenus() {
        fill(255);
        int size = 10;
        int gap = 4;
        int thickness = 2;

        int cx = width / 2;
        int cy = height / 2;

        stroke(255);
        strokeWeight(thickness);

        line(cx - gap - size, cy, cx - gap, cy);

        line(cx + gap, cy, cx + gap + size, cy);

        line(cx, cy - gap - size, cx, cy - gap);

        line(cx, cy + gap, cx, cy + gap + size);

        noStroke();

    }

    private void handleMouseMovement() {
        noCursor();
        Point p = ((Component) surface.getNative()).getLocationOnScreen();
        if (firstMouse) {
            robot.mouseMove((int) (p.x + width / 2), (int) (p.y + height / 2));
            firstMouse = false;
            return;
        }

        float deltaX = mouseX - width / 2;
        float deltaY = mouseY - height / 2;

        robot.mouseMove((int) (p.x + width / 2), (int) (p.y + height / 2));

        WorldSpace.camera.rotation.y -= deltaX * mouseSensitivity;
        WorldSpace.camera.rotation.x -= deltaY * mouseSensitivity;

        WorldSpace.camera.rotation.x = Math.max((float) -Math.PI / 2,
                Math.min((float) Math.PI / 2, WorldSpace.camera.rotation.x));

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
