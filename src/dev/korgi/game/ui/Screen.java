package dev.korgi.game.ui;

import java.awt.AWTException;
import java.awt.Component;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.List;

import dev.korgi.game.Game;
import dev.korgi.game.rendering.Graphics;
import dev.korgi.game.ui.game.InfoDisplay;
import dev.korgi.game.ui.pregame.ConfigGUI;
import dev.korgi.game.ui.pregame.LoadingGUI;
import dev.korgi.game.ui.pregame.SelectorGUI;
import dev.korgi.player.Player;
import dev.korgi.utils.ClientSide;
import dev.korgi.utils.StyleConstants;
import processing.core.PApplet;
import processing.core.PFont;

public final class Screen extends PApplet {

    private static Screen mInstance;

    public static Screen getInstance() {
        if (mInstance == null)
            mInstance = new Screen();
        return mInstance;
    }

    public int fontMono10, fontMono11;
    public int fontSans11, fontSans12, fontSans13, fontSans14, fontSans20, fontSans22, fontSans28;

    private Robot robot;
    private int rawMouseX = -1;
    private int rawMouseY = -1;
    private boolean firstMouse = true;
    private boolean cursorEnabled;

    boolean eventCanceled = false;

    private processing.core.PGraphics bgCache;

    public static ErrorMessage errorMsg;

    private List<GUI> gameGui = new ArrayList<>();

    private List<GUI> pregameGUI = new ArrayList<>();

    public ConfigGUI config;
    public SelectorGUI selector;
    public LoadingGUI loadingScreen;
    public InfoDisplay infoDisplay;

    @Override
    public void settings() {
        size(900, 600);
    }

    @Override
    public void setup() {
        frameRate(60);
        PFont fontMono10 = createFont("Courier New", 10, true);
        PFont fontMono11 = createFont("Courier New", 11, true);
        PFont fontSans11 = createFont("Tahoma", 11, true);
        PFont fontSans12 = createFont("Tahoma", 12, true);
        PFont fontSans13 = createFont("Tahoma", 13, true);
        PFont fontSans14 = createFont("Tahoma", 14, true);
        PFont fontSans20 = createFont("Tahoma", 20, true);
        PFont fontSans22 = createFont("Tahoma", 22, true);
        PFont fontSans28 = createFont("Tahoma", 28, true);
        this.fontMono10 = StyleConstants.getFontId(fontMono10);
        this.fontMono11 = StyleConstants.getFontId(fontMono11);
        this.fontSans11 = StyleConstants.getFontId(fontSans11);
        this.fontSans12 = StyleConstants.getFontId(fontSans12);
        this.fontSans13 = StyleConstants.getFontId(fontSans13);
        this.fontSans14 = StyleConstants.getFontId(fontSans14);
        this.fontSans20 = StyleConstants.getFontId(fontSans20);
        this.fontSans22 = StyleConstants.getFontId(fontSans22);
        this.fontSans28 = StyleConstants.getFontId(fontSans28);
        textFont(fontSans14);

        bgCache = createGraphics(width, height);
        bgCache.beginDraw();
        bgCache.background(StyleConstants.BG_DARK);
        int spacing = 30;
        bgCache.stroke(StyleConstants.BORDER);
        bgCache.strokeWeight(1);
        for (int x = spacing; x < width; x += spacing)
            for (int y = spacing; y < height; y += spacing)
                bgCache.point(x, y);
        bgCache.noStroke();
        for (int r = min(width, height); r > 0; r -= 8) {
            bgCache.fill(StyleConstants.BG_DARK, map(r, 0, min(width, height), 0, 80));
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
        config = new ConfigGUI();
        loadingScreen = new LoadingGUI();
        selector = new SelectorGUI();
        infoDisplay = new InfoDisplay();
        selector.show();
    }

    @Override
    public void draw() {
        if (Game.isInitialized()) {
            if (Game.isClient) {
                handleMouseMovement();
            }
            Game.loop();
        }

        List<GUI> list = getGUIList();
        for (int i = 0; i < list.size(); i++) {
            if (eventCanceled) {
                eventCanceled = false;
                break;
            }
            list.get(i).draw();
        }
    }

    private List<GUI> getGUIList() {
        return Game.isInitialized() ? gameGui : pregameGUI;
    }

    @Override
    public void keyPressed() {

        List<GUI> list = getGUIList();

        for (int i = 0; i < list.size(); i++) {
            if (eventCanceled) {
                eventCanceled = false;
                return;
            }
            list.get(i).onKeyPress();
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

    @Override
    @ClientSide
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
    public void mousePressed() {

        List<GUI> list = getGUIList();

        for (int i = 0; i < list.size(); i++) {
            if (eventCanceled) {
                eventCanceled = false;
                return;
            }
            list.get(i).onClick();
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
        }
    }

    @Override
    @ClientSide
    public void mouseReleased() {
        List<GUI> list = getGUIList();

        for (int i = 0; i < list.size(); i++) {
            if (eventCanceled) {
                eventCanceled = false;
                return;
            }
            list.get(i).onClickOff();
        }

        Game.withClient((client) -> {
            if (mouseButton == LEFT)
                client.pressedKeys.remove("LMB_HOLD");
            if (mouseButton == RIGHT)
                client.pressedKeys.remove("RMB_HOLD");
            if (mouseButton == CENTER)
                client.pressedKeys.remove("WD");
        });
    }

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

    public void drawGrid() {
        image(bgCache, 0, 0);
    }

    public List<GUI> getGameGui() {
        return gameGui;
    }

    public List<GUI> getPregameGUI() {
        return pregameGUI;
    }

}