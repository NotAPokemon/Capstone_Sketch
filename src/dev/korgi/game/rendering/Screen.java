package dev.korgi.game.rendering;

import java.awt.AWTException;
import java.awt.Component;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.MouseMotionAdapter;

import dev.korgi.game.Game;
import dev.korgi.networking.NetworkStream;
import dev.korgi.player.Player;
import processing.core.PApplet;

public class Screen extends PApplet {

    private static Screen mInstance;
    private Robot robot;
    private String uiMessage = null;
    private int uiMessageTimer = 0;

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

    private int rawMouseX = -1;
    private int rawMouseY = -1;

    @Override
    public void setup() {
        frameRate(60);
        textFont(createFont("Arial", 14));
        try {
            robot = new Robot();
        } catch (AWTException e) {
            e.printStackTrace();
        }
        Component window = (Component) surface.getNative();

        window.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                rawMouseX = e.getX();
                rawMouseY = e.getY();
            }

            @Override
            public void mouseDragged(java.awt.event.MouseEvent e) {
                mouseMoved(e);
            }

        });

    }

    @Override
    public void keyPressed() {
        if (Game.isClient) {
            Player client = Game.getClient();
            String k = normalizeKey();
            if (k != null && client != null && !client.pressedKeys.contains(k + "_HOLD")
                    && !client.pressedKeys.contains(k)) {
                client.pressedKeys.add(k);
            } else if (client != null && client.pressedKeys.contains(k)) {
                client.pressedKeys.remove(k);
                client.pressedKeys.add(k + "_HOLD");
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
                client.pressedKeys.remove(normalizeKey() + "_HOLD");
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

    @Override
    public void draw() {
        if (!Game.isInitialized()) {
            drawSelector();
            return;
        }
        Game.loop(this);
    }

    public void drawHUD() {
        fill(255);
        text("FPS: " + (int) frameRate, 30, 50);
        text("Sync Rate: " + Math.floor((NetworkStream.packetCount / NetworkStream.frameCount) * 100) + "%", 62,
                80);

        if (uiMessage != null && uiMessageTimer > 0) {
            fill(255, 0, 0);
            textAlign(CENTER, CENTER);
            text(uiMessage, width / 2, 30);
            uiMessageTimer--;
        }
    }

    private boolean stopHostingHover = false;

    public void drawOpenClientMenus() {
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

        text("Ping: " + (int) NetworkStream.getPing(), 30, 65);

        Game.withClient((p, pos) -> {
            text("XYZ: %.1f / %.1f / %.1f".formatted(pos.x, pos.y, pos.z), width / 2, 40);
        });
    }

    public void handleMouseMovement() {
        noCursor();
        Point p = ((Component) surface.getNative()).getLocationOnScreen();
        if (firstMouse) {
            robot.mouseMove((int) (p.x + width / 2), (int) (p.y + height / 2));
            firstMouse = false;
            return;
        }

        float deltaX = rawMouseX - width / 2;
        float deltaY = rawMouseY - height / 2;

        robot.mouseMove((int) (p.x + width / 2), (int) (p.y + height / 2));

        Graphics.camera.rotation.y -= deltaX * (Game.config.getFloat("mouse_sensitivity") / 1000);
        Graphics.camera.rotation.x -= deltaY * (Game.config.getFloat("mouse_sensitivity") / 1000);

        Graphics.camera.rotation.x = Math.max((float) -Math.PI / 2,
                Math.min((float) Math.PI / 2, Graphics.camera.rotation.x));

    }

    int selectedIndex = -1;

    int scrollOffset = 0;
    int maxVisibleItems = 5;

    int buttonWidth = 200;
    int buttonHeight = 50;

    int spacing = 20;
    int colorBoxSize = 30;

    public void drawServerInfo() {
        background(50);

        int centerX = width / 2;
        int topY = height / 4;

        int hostX = centerX - buttonWidth / 2;
        int hostY = topY;

        stopHostingHover = mouseX > hostX && mouseX < hostX + buttonWidth &&
                mouseY > hostY && mouseY < hostY + buttonHeight;

        fill(stopHostingHover ? 100 : 200);
        rect(hostX, hostY, buttonWidth, buttonHeight, 10);
        fill(0);
        textAlign(CENTER, CENTER);
        text("Stop Hosting", hostX + buttonWidth / 2, hostY + buttonHeight / 2);

    }

    public void showMessage(String msg, int durationFrames) {
        uiMessage = msg;
        uiMessageTimer = durationFrames;
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
                } else if (hoverJoin) {
                    Game.isClient = true;
                    NativeGPUKernal.loadTextureMap();
                } else {
                    return;
                }
                Game.init();
                return;
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (Game.isClient) {
            Game.withClient((client) -> {
                if (mouseButton == LEFT && !client.pressedKeys.contains("LMB") && !client.pressedKeys
                        .contains("LMB_HOLD")) {
                    client.pressedKeys.add("LMB");
                }
                if (mouseButton == RIGHT && !client.pressedKeys.contains("RMB") && !client.pressedKeys
                        .contains("RMB_HOLD")) {
                    client.pressedKeys.add("RMB");
                }
                if (mouseButton == CENTER && !client.pressedKeys.contains("WD")) {
                    client.pressedKeys.add("WD");
                }
            });

        } else {
            int centerX = width / 2;
            int hostY = height / 4;

            int hostX = centerX - buttonWidth / 2;
            int hostYButton = hostY;
            if (mouseX > hostX && mouseX < hostX + buttonWidth &&
                    mouseY > hostYButton && mouseY < hostYButton + buttonHeight) {
                exit();
            }
        }
    }

    @Override
    public void mouseReleased() {
        Game.withClient((client) -> {
            if (mouseButton == LEFT) {
                client.pressedKeys.remove("LMB_HOLD");
            }
            if (mouseButton == RIGHT) {
                client.pressedKeys.remove("RMB_HOLD");
            }
            if (mouseButton == CENTER) {
                client.pressedKeys.remove("WD");
            }
        });
    }
}
