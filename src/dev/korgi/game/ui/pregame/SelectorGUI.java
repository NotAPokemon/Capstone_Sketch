package dev.korgi.game.ui.pregame;

import java.util.List;

import dev.korgi.game.Game;
import dev.korgi.game.physics.WorldEngine;
import dev.korgi.game.rendering.NativeGPUKernal;
import dev.korgi.game.ui.GUI;
import dev.korgi.json.JSONObject;
import dev.korgi.utils.ErrorHandler;
import dev.korgi.utils.InstallConstants;
import dev.korgi.utils.StyleConstants;
import processing.core.PApplet;

public class SelectorGUI extends GUI {

  private void launchGame(boolean asClient) {
    try {
      Game.isClient = asClient;
      if (asClient)
        NativeGPUKernal.loadTextureMap();

      new Thread(() -> {
        try {
          hide();
          screen.loadingScreen.show();
          Game.init();
          while (!Game.isInitialized()) {
            Game.networkStartLoop();
            WorldEngine.updateClient();
            Game.networkEndLoop();
          }
          screen.loadingScreen.hide();
          screen.infoDisplay.show();
        } catch (Exception e) {
          e.printStackTrace();
        }
      }, "initialize-game").start();

    } catch (Exception e) {
      e.printStackTrace();
      ErrorHandler.error("Failed to start: %s", e.getMessage());
    }
  }

  private static final int BTN_W = 220;
  private static final int BTN_H = 56;
  private static final int GAP_X = 24;

  private int row2Y;
  private int hostX;
  private int joinX;
  private int cbgX;
  private int cbgY;
  private int titleY;
  private int tx;

  private void updateSizes() {
    row2Y = screen.height / 2 + 10;
    hostX = screen.width / 2 - BTN_W - GAP_X / 2;
    joinX = screen.width / 2 + GAP_X / 2;
    cbgX = screen.width / 2 - BTN_W / 2;
    cbgY = row2Y + BTN_H + 16;
    titleY = screen.height / 2 - 110;
    tx = screen.width / 2;
  }

  @Override
  protected void drawGUI() {
    updateSizes();
    screen.background(StyleConstants.BG_DARK);
    drawCachedBg();

    loadStyle("title-eyebrow");
    screen.text("KORGI ENGINE", tx, titleY - 22);

    loadStyle("title-heading");
    screen.text("GAME LAUNCHER", tx, titleY);

    loadStyle("divider");
    screen.line(tx - 120, titleY + 27, tx + 120, titleY + 27);

    drawPrimaryButton("HOST GAME", hostX, row2Y, BTN_W, BTN_H, "host");

    drawPrimaryButton("JOIN GAME", joinX, row2Y, BTN_W, BTN_H, "join");

    drawGhostButton("SETTINGS", cbgX, cbgY, BTN_W, BTN_H - 10, "settings");

    loadStyle("version-label");
    screen.text("v" + InstallConstants.version + " · korgi engine",
        screen.width / 2, screen.height - 30);
  }

  @Override
  protected void onClick() {
    if (hitTest(hostX, row2Y, BTN_W, BTN_H)) {
      launchGame(false);
    } else if (hitTest(joinX, row2Y, BTN_W, BTN_H)) {
      launchGame(true);
    } else if (hitTest(cbgX, cbgY, BTN_W, BTN_H - 10)) {
      hide();
      screen.config.show();
    }
  }

  @Override
  protected List<GUI> getDisplayLocation() {
    return screen.getPregameGUI();
  }

  @Override
  protected void createStyleSheet() {

    stylesheet.set("title-eyebrow", new JSONObject()
        .set("bg", StyleConstants.TEXT_LABEL)
        .set("txtAlignX", PApplet.CENTER)
        .set("txtAlignY", PApplet.TOP)
        .set("font", screen.fontMono11));

    stylesheet.set("title-heading", new JSONObject()
        .set("bg", StyleConstants.TEXT_PRIMARY)
        .set("txtAlignX", PApplet.CENTER)
        .set("txtAlignY", PApplet.TOP)
        .set("font", screen.fontSans28));

    stylesheet.set("divider", new JSONObject()
        .set("borderColor", StyleConstants.BORDER)
        .set("borderSize", 1f));

    stylesheet.set("version-label", new JSONObject()
        .set("bg", StyleConstants.TEXT_LABEL)
        .set("txtAlignX", PApplet.CENTER)
        .set("txtAlignY", PApplet.TOP)
        .set("font", screen.fontMono10));

    StyleConstants.addPrimaryButtonStyles(stylesheet, "host");

    StyleConstants.addPrimaryButtonStyles(stylesheet, "join", StyleConstants.GREEN_BTN_THEME);

    StyleConstants.addGhostButtonStyles(stylesheet, "settings");

  }
}
