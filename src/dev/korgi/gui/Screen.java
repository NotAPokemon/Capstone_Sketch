package dev.korgi.gui;

import java.awt.AWTException;
import java.awt.Component;
import java.awt.Point;
import java.awt.Robot;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;

import dev.korgi.Game;
import dev.korgi.gui.rendering.WorldSpace;
import dev.korgi.json.JSONObject;
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

        if (uiMessage != null && uiMessageTimer > 0) {
            fill(255, 0, 0);
            textAlign(CENTER, CENTER);
            text(uiMessage, width / 2, 30);
            uiMessageTimer--;
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

    private boolean skip = true;

    @Override
    public void mousePressed() {
        if (!Game.isInitialized()) {
            try {
                if (hoverHost) {
                    Game.isClient = false;
                    Game.init();
                } else if (hoverJoin) {
                    if (skip) {
                        Game.isClient = true;
                        Game.init();
                    }
                    File accFile = promptAccFile();
                    if (accFile != null) {
                        String data = "{}";
                        try {
                            byte[] fileBytes = Files.readAllBytes(accFile.toPath());
                            // Skip first 4 bytes (header)
                            byte[] jsonBytes = Arrays.copyOfRange(fileBytes, 4, fileBytes.length);
                            data = new String(jsonBytes, StandardCharsets.UTF_8);
                        } catch (Exception e) {
                        }
                        JSONObject obj = JSONObject.fromJSONString(data);
                        NetworkStream.clientId = obj.getString("internal_id");
                        Game.isClient = true;
                        Game.init();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (Game.isClient) {

        } else {
            if (stopHostingHover) {
                exit();
            }
        }
    }

    private File promptAccFile() {
        while (true) { // loop until valid file is selected or created
            String[] options = { "Select Existing", "Create New", "Cancel" };
            int choice = JOptionPane.showOptionDialog(
                    null,
                    "Choose your .acc file or create a new one.",
                    "Account File",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.INFORMATION_MESSAGE,
                    null,
                    options,
                    options[0]);

            if (choice == 0) { // Select Existing
                File file = selectExistingAcc();
                if (file != null)
                    return file;
            } else if (choice == 1) { // Create New
                File file = createNewAcc();
                if (file != null)
                    return file;
            } else { // Cancel
                showMessage("Join canceled.", 120);
                return null;
            }
        }
    }

    JFileChooser chooser = new JFileChooser();
    FileNameExtensionFilter filter = new FileNameExtensionFilter(".acc files", "acc");

    private File selectExistingAcc() {
        chooser.setDialogTitle("Select your .acc file");

        int result = chooser.showOpenDialog(null);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (file.getName().endsWith(".acc")) {
                return file;
            } else {
                showMessage("Invalid file selected! Please choose a .acc file.", 180);
            }
        } else {
            showMessage("File selection canceled.", 120);
        }
        return null;
    }

    private File createNewAcc() {
        String fileName = JOptionPane.showInputDialog("Enter a name for your new .acc file:");
        if (fileName == null || fileName.isBlank()) {
            showMessage("Invalid name. Try again.", 120);
            return null;
        }

        File newFile = new File(fileName + ".acc");
        try {
            if (newFile.createNewFile()) {
                JSONObject data = new JSONObject();
                data.set("name", fileName);
                data.set("internal_id", UUID.randomUUID().toString());
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                out.write(new byte[] { 0x00, (byte) 0xAC, 0x43, 0x43 });
                byte[] dataBytes = data.toJSONString().getBytes(StandardCharsets.UTF_8);
                out.write(dataBytes);
                Files.write(newFile.toPath(), out.toByteArray());

                showMessage("New .acc file created: " + fileName, 180);
                return newFile;
            } else {
                showMessage("File already exists! Select a different name.", 180);
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            showMessage("Failed to create file.", 180);
            return null;
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
