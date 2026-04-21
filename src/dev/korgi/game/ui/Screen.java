package dev.korgi.game.ui;

import java.awt.AWTException;
import java.awt.Component;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.MouseMotionAdapter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import dev.korgi.game.Game;
import dev.korgi.game.physics.WorldEngine;
import dev.korgi.game.rendering.Graphics;
import dev.korgi.game.rendering.NativeGPUKernal;
import dev.korgi.game.ui.builder.UIBuilder;
import dev.korgi.game.ui.elements.UI;
import dev.korgi.networking.NetworkStream;
import dev.korgi.player.Player;
import dev.korgi.utils.ClientSide;
import dev.korgi.utils.ErrorHandler;
import dev.korgi.utils.InstallConstants;
import dev.korgi.utils.ServerSide;
import processing.core.PApplet;
import processing.core.PFont;

public class Screen extends PApplet {

    private static Screen mInstance;

    public static Screen getInstance() {
        if (mInstance == null)
            mInstance = new Screen();
        return mInstance;
    }

    private static final int BG_DARK = 0xFF0D0F14;
    private static final int BG_CARD = 0xFF1C2130;
    private static final int ACCENT = 0xFF4F8EF7;
    private static final int ACCENT_DIM = 0xFF2A4F9A;
    private static final int TEXT_PRIMARY = 0xFFE8EAF0;
    private static final int TEXT_DIM = 0xFF7A8099;
    private static final int TEXT_LABEL = 0xFF4F5A72;
    private static final int DANGER = 0xFFE05C5C;
    private static final int SUCCESS = 0xFF4FD17A;
    private static final int BORDER = 0xFF252B3B;

    public PFont fontMono10, fontMono11;
    public PFont fontSans11, fontSans12, fontSans13, fontSans14, fontSans20, fontSans22, fontSans28;

    private enum MenuState {
        SELECTOR, CONFIG, GAME, LOADING
    }

    private MenuState menuState = MenuState.SELECTOR;

    private Robot robot;
    private int rawMouseX = -1;
    private int rawMouseY = -1;
    private boolean firstMouse = true;

    private String uiMessage = null;
    private int uiMessageTimer = 0;

    private static final int BTN_W = 220;
    private static final int BTN_H = 56;

    private boolean hoverHost = false;
    private boolean hoverJoin = false;
    private boolean hoverConfig = false;

    private boolean stopHostingHover = false;

    private final String[] cfgKeys = { "mouse_sensitivity", "fov", "render_dist", "ip", "pack" };
    private final String[] cfgLabels = { "Mouse Sensitivity", "Field of View", "Render Distance", "Server IP",
            "Resource Pack" };
    private final float[] cfgMin = { 1f, 40f, 10f, 0, 0 };
    private final float[] cfgMax = { 6f, 120f, 80f, 0, 0 };

    private final boolean[] cfgIsToggle = { false, false, false, false, false };
    private final boolean[] cfgIsString = { false, false, false, true, true };

    private float[] cfgValues = new float[] { 3f, 60f, 50f, 0f, 0f };

    private String[] cfgStrings = new String[] { "", "", "", "localhost", "default" };

    private boolean cfgDragging = false;
    private int cfgDragIndex = -1;

    private int cfgEditIndex = -1;

    private boolean hoverBack = false;
    private boolean hoverApply = false;

    private float[] btnHoverT = new float[4];

    private processing.core.PGraphics bgCache;

    @ClientSide
    private List<UI> openUi = new ArrayList<>();

    @ServerSide
    private List<UI> serverUi = new ArrayList<>();

    public static UI errorMsg;

    @Override
    public void settings() {
        size(900, 600);
    }

    @Override
    public void setup() {
        frameRate(60);
        fontMono10 = createFont("Courier New", 10, true);
        fontMono11 = createFont("Courier New", 11, true);
        fontSans11 = createFont("Tahoma", 11, true);
        fontSans12 = createFont("Tahoma", 12, true);
        fontSans13 = createFont("Tahoma", 13, true);
        fontSans14 = createFont("Tahoma", 14, true);
        fontSans20 = createFont("Tahoma", 20, true);
        fontSans22 = createFont("Tahoma", 22, true);
        fontSans28 = createFont("Tahoma", 28, true);
        textFont(fontSans14);

        for (int i = 0; i < cfgKeys.length; i++) {
            try {
                if (cfgIsString[i]) {
                    String v = Game.config.getString(cfgKeys[i]);
                    if (v != null)
                        cfgStrings[i] = v;
                } else {
                    cfgValues[i] = Game.config.getFloat(cfgKeys[i]);
                }
            } catch (Exception ignored) {
            }
        }

        bgCache = createGraphics(width, height);
        bgCache.beginDraw();
        bgCache.background(BG_DARK);
        int spacing = 30;
        bgCache.stroke(BORDER);
        bgCache.strokeWeight(1);
        for (int x = spacing; x < width; x += spacing)
            for (int y = spacing; y < height; y += spacing)
                bgCache.point(x, y);
        bgCache.noStroke();
        for (int r = min(width, height); r > 0; r -= 8) {
            bgCache.fill(BG_DARK, map(r, 0, min(width, height), 0, 80));
            bgCache.noStroke();
            bgCache.ellipse(width / 2f, height / 2f, r * 2, r * 2);
        }
        bgCache.endDraw();

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
        errorMsg = UIBuilder.debugValue(width / 2.0f, height / 2.0f);
        errorMsg.getStyle().set("display.value", "");
    }

    private void initalizeGame() {
        new Thread(() -> {
            try {
                menuState = MenuState.LOADING;
                Game.init();
                while (!Game.isInitialized()) {
                    Game.networkStartLoop();
                    WorldEngine.updateClient();
                    Game.networkEndLoop();
                }
                menuState = MenuState.GAME;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "intialize-game").start();
    }

    @Override
    public void draw() {
        if (!Game.isInitialized()) {
            if (menuState == MenuState.CONFIG) {
                drawConfigMenu();
            } else if (menuState == MenuState.LOADING) {
                drawLoadingScreen();
            } else {
                drawSelector();
            }
        } else {
            Game.loop(this);
        }
        ErrorHandler.loòp();
        errorMsg.draw(this);
    }

    private void drawLoadingScreen() {
        background(BG_DARK);
        drawGrid();

        int cx = width / 2;
        int cy = height / 2;

        int spokes = 12;
        float angleStep = TWO_PI / spokes;
        float t = (float) (millis() % 1000) / 1000f;

        strokeWeight(2.5f);
        noFill();
        for (int i = 0; i < spokes; i++) {
            float angle = i * angleStep - HALF_PI;
            float spokeFade = ((i / (float) spokes) - t + 1f) % 1f;
            int alpha = (int) (spokeFade * 220);
            stroke(ACCENT & 0x00FFFFFF | (alpha << 24));
            float inner = 22, outer = 34;
            line(
                    cx + cos(angle) * inner, cy + sin(angle) * inner,
                    cx + cos(angle) * outer, cy + sin(angle) * outer);
        }
        noStroke();

        String dots = ".".repeat((int) ((millis() / 400) % 4));
        fill(TEXT_DIM);
        textFont(fontSans13);
        textAlign(CENTER, TOP);
        text("Loading" + dots, cx, cy + 70);

        float progress = Game.getInitProgress();

        int barW = 240, barH = 3;
        int barX = cx - barW / 2, barY = cy + 100;

        noStroke();
        fill(BORDER);
        rect(barX, barY, barW, barH, barH / 2f);

        fill(ACCENT_DIM);
        rect(barX, barY, barW * progress, barH, barH / 2f);

        if (progress > 0.02f) {
            fill(ACCENT);
            ellipse(barX + barW * progress, barY + barH / 2f, 6, 6);
        }

        drawMessageBanner();
    }

    private void drawSelector() {
        background(BG_DARK);
        drawGrid();

        int titleY = height / 2 - 110;
        drawLabel("KORGI ENGINE", width / 2, titleY - 22, TEXT_LABEL, 11);
        drawHeading("GAME LAUNCHER", width / 2, titleY, 28);
        drawDivider(width / 2 - 120, titleY + 27, 240);

        int gapX = 24;
        int row2Y = height / 2 + 10;

        int hostX = width / 2 - BTN_W - gapX / 2;
        int joinX = width / 2 + gapX / 2;
        int cfgX = width / 2 - BTN_W / 2;
        int cfgY = row2Y + BTN_H + 16;

        hoverHost = hitTest(hostX, row2Y, BTN_W, BTN_H);
        hoverJoin = hitTest(joinX, row2Y, BTN_W, BTN_H);
        hoverConfig = hitTest(cfgX, cfgY, BTN_W, BTN_H - 10);

        btnHoverT[0] = lerp(btnHoverT[0], hoverHost ? 1 : 0, 0.15f);
        btnHoverT[1] = lerp(btnHoverT[1], hoverJoin ? 1 : 0, 0.15f);
        btnHoverT[2] = lerp(btnHoverT[2], hoverConfig ? 1 : 0, 0.15f);

        drawPrimaryButton("HOST GAME", hostX, row2Y, BTN_W, BTN_H, btnHoverT[0], ACCENT);
        drawPrimaryButton("JOIN GAME", joinX, row2Y, BTN_W, BTN_H, btnHoverT[1], SUCCESS);
        drawGhostButton("SETTINGS", cfgX, cfgY, BTN_W, BTN_H - 10, btnHoverT[2]);

        drawLabel("v" + InstallConstants.version + " · korgi engine", width / 2, height - 30, TEXT_LABEL, 10);

        drawMessageBanner();
    }

    private void drawConfigMenu() {
        background(BG_DARK);
        drawGrid();

        int panelW = 560;
        int panelH = 96 + cfgKeys.length * ROW_H + 70;
        int panelX = width / 2 - panelW / 2;
        int panelY = height / 2 - panelH / 2;
        drawPanel(panelX, panelY, panelW, panelH);

        drawLabel("CONFIGURATION", panelX + panelW / 2, panelY + 28, TEXT_LABEL, 10);
        drawHeading("Settings", panelX + panelW / 2, panelY + 52, 22);
        drawDivider(panelX + 32, panelY + 77, panelW - 64);

        int rowX = panelX + 32;
        int rowY = panelY + 96;
        int sliderW = 200;

        for (int i = 0; i < cfgKeys.length; i++) {
            int ry = rowY + i * ROW_H;
            drawConfigRow(i, rowX, ry, panelW - 64, sliderW);
        }

        int btnY = panelY + panelH - 60;

        hoverBack = hitTest(panelX + 32, btnY, 120, 36);
        hoverApply = hitTest(panelX + panelW - 152, btnY, 120, 36);

        drawGhostButton("← BACK", panelX + 32, btnY, 120, 36, hoverBack ? 1 : 0);
        drawPrimaryButton("APPLY", panelX + panelW - 152, btnY, 120, 36, hoverApply ? 1 : 0, ACCENT);

        drawMessageBanner();
    }

    private static final int ROW_H = 58;

    private void drawConfigRow(int idx, int x, int y, int totalW, int sliderW) {
        textFont(fontSans13);
        fill(TEXT_PRIMARY);
        textAlign(LEFT, CENTER);
        text(cfgLabels[idx], x, y + 16);

        fill(TEXT_LABEL);
        textFont(fontMono10);
        text(cfgKeys[idx], x, y + 32);

        if (cfgIsString[idx]) {
            drawTextInputRow(idx, x, y, totalW);

        } else if (cfgIsToggle[idx]) {
            boolean on = cfgValues[idx] > 0.5f;
            int tx = x + totalW - 52;
            int ty = y + 10;
            int tw = 44, th = 22;
            float t = on ? 1f : 0f;

            noStroke();
            fill(lerpColor(BORDER, ACCENT_DIM, t));
            rect(tx, ty, tw, th, th / 2f);

            float knobX = lerp(tx + 4, tx + tw - 18, t);
            fill(on ? ACCENT : TEXT_DIM);
            ellipse(knobX + 7, ty + th / 2f, 16, 16);

            fill(on ? ACCENT : TEXT_DIM);
            textAlign(RIGHT, CENTER);
            textFont(fontMono11);
            text(on ? "ON" : "OFF", tx - 8, ty + th / 2f);

        } else {
            int sx = x + totalW - sliderW;
            int sy = y + 14;
            int sh = 4;
            float pct = (cfgValues[idx] - cfgMin[idx]) / (cfgMax[idx] - cfgMin[idx]);
            float fillW = pct * sliderW;

            noStroke();
            fill(BORDER);
            rect(sx, sy, sliderW, sh, sh / 2f);

            fill(ACCENT_DIM);
            rect(sx, sy, max(sh, fillW), sh, sh / 2f);

            boolean dragging = cfgDragging && cfgDragIndex == idx;
            boolean hovering = !cfgDragging
                    && mouseX >= sx - 8 && mouseX <= sx + sliderW + 8
                    && mouseY >= sy - 10 && mouseY <= sy + sh + 10;

            fill(dragging || hovering ? ACCENT : color(180));
            ellipse(sx + fillW, sy + sh / 2f, dragging ? 14 : 10, dragging ? 14 : 10);

            String fmt = (cfgMax[idx] <= 1f) ? "%.2f" : "%.0f";
            fill(TEXT_DIM);
            textAlign(RIGHT, CENTER);
            textFont(fontMono11);
            text(String.format(fmt, cfgValues[idx]), sx - 12, sy + sh / 2f);

            if (dragging) {
                float newPct = constrain((float) (mouseX - sx) / sliderW, 0, 1);
                cfgValues[idx] = cfgMin[idx] + newPct * (cfgMax[idx] - cfgMin[idx]);
            }
        }

        stroke(BORDER);
        strokeWeight(1);
        line(x, y + ROW_H - 2, x + totalW, y + ROW_H - 2);
        noStroke();
    }

    private void drawTextInputRow(int idx, int x, int y, int totalW) {
        boolean active = cfgEditIndex == idx;
        int fw = 200, fh = 26;
        int fx = x + totalW - fw;
        int fy = y + 8;

        noStroke();
        fill(active ? (BG_DARK) : BORDER);
        rect(fx, fy, fw, fh, 4);

        stroke(active ? ACCENT : TEXT_LABEL);
        strokeWeight(1);
        noFill();
        rect(fx, fy, fw, fh, 4);
        noStroke();

        String display = cfgStrings[idx];
        if (active && (millis() / 500) % 2 == 0)
            display += "|";

        textFont(fontMono11);
        while (display.length() > 1 && textWidth(display) > fw - 12)
            display = display.substring(1);

        fill(active ? TEXT_PRIMARY : TEXT_DIM);
        textAlign(LEFT, CENTER);
        text(display, fx + 6, fy + fh / 2f);
    }

    @Override
    public void keyPressed() {
        if (cfgEditIndex >= 0 && menuState == MenuState.CONFIG) {
            handleTextFieldKey();
            return;
        }

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

    private void handleTextFieldKey() {
        if (key == BACKSPACE) {
            String s = cfgStrings[cfgEditIndex];
            if (s.length() > 0)
                cfgStrings[cfgEditIndex] = s.substring(0, s.length() - 1);
        } else if (key == ENTER || key == RETURN || key == ESC) {
            cfgEditIndex = -1;
            if (key == ESC)
                key = 0;
        } else if (key >= 32 && key < 127) {
            if (cfgStrings[cfgEditIndex].length() < 64)
                cfgStrings[cfgEditIndex] += key;
        }
    }

    @Override
    @ClientSide
    public void keyReleased() {
        if (cfgEditIndex >= 0)
            return;

        if (Game.isClient) {
            Player client = Game.getClient();
            if (client != null) {
                client.pressedKeys.remove(normalizeKey() + "_HOLD");
                client.pressedKeys.remove(normalizeKey());
            }
        }
    }

    @Override
    public void mousePressed() {
        if (menuState == MenuState.LOADING)
            return;

        if (!Game.isInitialized()) {
            if (menuState == MenuState.CONFIG) {
                handleConfigClick();
            } else {
                handleSelectorClick();
            }
            return;
        }

        if (Game.isClient) {
            Game.withClient((client) -> {
                if (mouseButton == LEFT && !client.pressedKeys.contains("LMB")
                        && !client.pressedKeys.contains("LMB_HOLD"))
                    client.pressedKeys.add("LMB");
                if (mouseButton == RIGHT && !client.pressedKeys.contains("RMB")
                        && !client.pressedKeys.contains("RMB_HOLD"))
                    client.pressedKeys.add("RMB");
                if (mouseButton == CENTER && !client.pressedKeys.contains("WD"))
                    client.pressedKeys.add("WD");
            });
        } else {
            if (stopHostingHover)
                exit();
        }
    }

    @Override
    @ClientSide
    public void mouseReleased() {
        cfgDragging = false;
        cfgDragIndex = -1;

        Game.withClient((client) -> {
            if (mouseButton == LEFT)
                client.pressedKeys.remove("LMB_HOLD");
            if (mouseButton == RIGHT)
                client.pressedKeys.remove("RMB_HOLD");
            if (mouseButton == CENTER)
                client.pressedKeys.remove("WD");
        });
    }

    @Override
    public void mouseDragged() {
    }

    private void handleSelectorClick() {
        if (hoverHost)
            launchGame(false);
        else if (hoverJoin)
            launchGame(true);
        else if (hoverConfig)
            menuState = MenuState.CONFIG;
    }

    private void launchGame(boolean asClient) {
        try {
            Game.isClient = asClient;
            if (asClient)
                NativeGPUKernal.loadTextureMap();
            initalizeGame();
        } catch (Exception e) {
            e.printStackTrace();
            showMessage("Failed to start: " + e.getMessage(), 180);
        }
    }

    private void handleConfigClick() {
        cfgEditIndex = -1;

        if (hoverBack) {
            menuState = MenuState.SELECTOR;
            return;
        }
        if (hoverApply) {
            applyConfig();
            showMessage("Settings saved!", 120);
            menuState = MenuState.SELECTOR;
            return;
        }

        int panelW = 560;
        int panelH = 96 + cfgKeys.length * ROW_H + 70;
        int panelX = width / 2 - panelW / 2;
        int panelY = height / 2 - panelH / 2;
        int rowX = panelX + 32;
        int rowY = panelY + 96;
        int totalW = panelW - 64;
        int sliderW = 200;

        for (int i = 0; i < cfgKeys.length; i++) {
            int ry = rowY + i * ROW_H;

            if (cfgIsString[i]) {
                int fw = 200, fh = 26;
                int fx = rowX + totalW - fw;
                int fy = ry + 8;
                if (mouseX >= fx && mouseX <= fx + fw && mouseY >= fy && mouseY <= fy + fh) {
                    cfgEditIndex = i;
                }

            } else if (cfgIsToggle[i]) {
                int tx = rowX + totalW - 52;
                int ty = ry + 10;
                if (mouseX >= tx && mouseX <= tx + 44 && mouseY >= ty && mouseY <= ty + 22)
                    cfgValues[i] = cfgValues[i] > 0.5f ? 0f : 1f;

            } else {
                int sx = rowX + totalW - sliderW;
                int sy = ry + 14;
                if (mouseX >= sx - 8 && mouseX <= sx + sliderW + 8 && mouseY >= sy - 10 && mouseY <= sy + 14) {
                    cfgDragging = true;
                    cfgDragIndex = i;
                }
            }
        }
    }

    private void applyConfig() {
        for (int i = 0; i < cfgKeys.length; i++) {
            if (cfgIsString[i]) {
                Game.config.set(cfgKeys[i], cfgStrings[i]);
            } else {
                Game.config.set(cfgKeys[i], cfgValues[i]);
            }
        }
        try {
            Game.updateConfig();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void drawHUD() {
        textFont(fontSans14);
        fill(TEXT_LABEL);
        textAlign(LEFT, TOP);
        text("FPS  " + (int) frameRate, 16, 16);
        text("Sync " + Math.floor((NetworkStream.packetCount / NetworkStream.frameCount) * 100) + "%", 16, 32);
        drawMessageBanner();
    }

    @ClientSide
    public void drawCrosshair() {
        int cx = width / 2, cy = height / 2;
        int gap = 5, size = 9, thick = 4;
        stroke(255, 255, 255, 180);
        strokeWeight(thick);
        line(cx - gap - size, cy, cx - gap, cy);
        line(cx + gap, cy, cx + gap + size, cy);
        line(cx, cy - gap - size, cx, cy - gap);
        line(cx, cy + gap, cx, cy + gap + size);
        noStroke();
    }

    @ClientSide
    public void drawOpenClientMenus() {
        drawCrosshair();

        textFont(fontSans14);
        fill(TEXT_DIM);
        textAlign(LEFT, TOP);
        text("PING " + (int) NetworkStream.getPing() + "ms", 16, 48);

        Game.withClient((p, pos) -> {
            fill(TEXT_PRIMARY);
            textAlign(CENTER, TOP);
            text(String.format("%.1f  /  %.1f  /  %.1f", pos.x, pos.y, pos.z), width / 2f, 16);
        });

        for (UI ui : openUi) {
            ui.draw(this);
        }
    }

    @ServerSide
    public void drawServerInfo() {
        background(BG_DARK);
        drawGrid();

        int panelW = 340;
        int panelH = 180;
        int panelX = width / 2 - panelW / 2;
        int panelY = height / 2 - panelH / 2;
        drawPanel(panelX, panelY, panelW, panelH);

        drawLabel("SERVER CONTROL", panelX + panelW / 2, panelY + 24, TEXT_LABEL, 10);
        drawHeading("Hosting", panelX + panelW / 2, panelY + 46, 20);
        drawDivider(panelX + 24, panelY + 71, panelW - 48);

        int bx = panelX + 24;
        int by = panelY + 86;
        int bw = panelW - 48;
        int bh = 46;

        stopHostingHover = hitTest(bx, by, bw, bh);
        btnHoverT[3] = lerp(btnHoverT[3], stopHostingHover ? 1 : 0, 0.15f);
        drawPrimaryButton("STOP HOSTING", bx, by, bw, bh, btnHoverT[3], DANGER);

        drawMessageBanner();

        for (UI ui : serverUi) {
            ui.draw(this);
        }
    }

    private boolean cursorEnabled;

    @ClientSide
    public void handleMouseMovement() {
        if (cursorEnabled)
            return;
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

        float sens = Game.config.getFloat("mouse_sensitivity") / 1000f;
        Graphics.camera.rotation.y -= deltaX * sens;
        Graphics.camera.rotation.x -= deltaY * sens;
        Graphics.camera.rotation.x = Math.max((float) -Math.PI / 2,
                Math.min((float) Math.PI / 2, Graphics.camera.rotation.x));
    }

    @Override
    @ClientSide
    public void focusLost() {
        if (!Game.isClient || !Game.isInitialized())
            return;
        Player client = Game.getClient();
        if (client != null)
            client.pressedKeys.clear();
        cursor();
        cursorEnabled = true;
    }

    @Override
    public void focusGained() {
        if (Game.isClient && Game.isInitialized())
            noCursor();
        cursorEnabled = false;
    }

    @ClientSide
    private String normalizeKey() {
        if (key != CODED)
            return ("" + key).toLowerCase();
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

    public void showMessage(String msg, int durationFrames) {
        uiMessage = msg;
        uiMessageTimer = durationFrames;
    }

    private void drawGrid() {
        image(bgCache, 0, 0);
    }

    private void drawPanel(int x, int y, int w, int h) {
        noStroke();
        fill(0, 60);
        rect(x + 4, y + 6, w, h, 12);

        fill(BG_CARD);
        rect(x, y, w, h, 10);

        stroke(BORDER);
        strokeWeight(1);
        noFill();
        rect(x, y, w, h, 10);
        noStroke();
    }

    private void drawPrimaryButton(String label, int x, int y, int w, int h, float hoverT, int col) {
        if (hoverT > 0.01f) {
            noStroke();
            fill(col & 0x00FFFFFF | (int) (60 * hoverT) << 24);
            rect(x - 4, y - 4, w + 8, h + 8, 14);
        }

        int bg = lerpColor(BG_CARD, col, hoverT * 0.85f);
        fill(bg);
        stroke(lerpColor(BORDER, col, hoverT));
        strokeWeight(1);
        rect(x, y, w, h, 8);
        noStroke();

        fill(col);
        rect(x, y, 3, h, 8, 0, 0, 8);

        fill(lerpColor(TEXT_DIM, TEXT_PRIMARY, hoverT));
        textFont(fontSans12);
        textAlign(CENTER, CENTER);
        text(label, x + w / 2f + 1, y + h / 2f);
    }

    private void drawGhostButton(String label, int x, int y, int w, int h, float hoverT) {
        noFill();
        stroke(lerpColor(BORDER, TEXT_DIM, hoverT));
        strokeWeight(1);
        rect(x, y, w, h, 6);
        noStroke();

        fill(lerpColor(TEXT_LABEL, TEXT_DIM, hoverT));
        textFont(fontSans11);
        textAlign(CENTER, CENTER);
        text(label, x + w / 2f, y + h / 2f);
    }

    private void drawHeading(String txt, int cx, int y, int sz) {
        PFont f = sz >= 26 ? fontSans28 : sz >= 21 ? fontSans22 : fontSans20;
        fill(TEXT_PRIMARY);
        textFont(f);
        textAlign(CENTER, TOP);
        text(txt, cx, y);
    }

    private void drawLabel(String txt, int cx, int y, int col, int sz) {
        fill(col);
        textFont(sz <= 10 ? fontMono10 : fontMono11);
        textAlign(CENTER, TOP);
        text(txt, cx, y);
    }

    private void drawDivider(int x, int y, int w) {
        stroke(BORDER);
        strokeWeight(1);
        line(x, y, x + w, y);
        noStroke();
    }

    private void drawMessageBanner() {
        if (uiMessage == null || uiMessageTimer <= 0)
            return;
        float alpha = uiMessageTimer > 30 ? 255 : map(uiMessageTimer, 0, 30, 0, 255);
        noStroke();
        fill(DANGER & 0x00FFFFFF | (int) (alpha * 0.15f) << 24);
        rect(width / 2f - 200, 12, 400, 30, 6);
        stroke(DANGER & 0x00FFFFFF | (int) (alpha * 0.4f) << 24);
        strokeWeight(1);
        noFill();
        rect(width / 2f - 200, 12, 400, 30, 6);
        noStroke();

        fill(DANGER & 0x00FFFFFF | (int) alpha << 24);
        textFont(fontSans12);
        textAlign(CENTER, CENTER);
        text(uiMessage, width / 2f, 27);
        uiMessageTimer--;
    }

    private boolean hitTest(int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    public List<UI> getOpenUi() {
        return openUi;
    }

    public List<UI> getServerUi() {
        return serverUi;
    }
}