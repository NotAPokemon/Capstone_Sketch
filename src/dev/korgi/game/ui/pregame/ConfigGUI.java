package dev.korgi.game.ui.pregame;

import java.io.IOException;
import java.util.List;

import dev.korgi.game.Game;
import dev.korgi.game.ui.GUI;
import dev.korgi.json.JSONObject;
import dev.korgi.utils.StyleConstants;
import processing.core.PApplet;

public class ConfigGUI extends GUI {

    private final String[] cbgKeys = { "mouse_sensitivity", "fov", "render_dist", "ip", "pack" };
    private final String[] cbgLabels = { "Mouse Sensitivity", "Field of View", "Render Distance", "Server IP",
            "Resource Pack" };
    private final float[] cbgMin = { 1f, 40f, 10f, 0, 0 };
    private final float[] cbgMax = { 6f, 120f, 80f, 0, 0 };

    private final boolean[] cbgIsToggle = { false, false, false, false, false };
    private final boolean[] cbgIsString = { false, false, false, true, true };

    private float[] cbgValues = new float[] { 3f, 60f, 50f, 0f, 0f };
    private String[] cbgStrings = new String[] { "", "", "", "localhost", "default" };

    private boolean cbgDragging = false;
    private int cbgDragIndex = -1;
    private int cbgEditIndex = -1;

    private static final int ROW_H = 58;
    private static final int PANEL_W = 560;

    public ConfigGUI() {
        for (int i = 0; i < cbgKeys.length; i++) {
            if (cbgIsString[i]) {
                String v = Game.config.getString(cbgKeys[i]);
                if (v != null)
                    cbgStrings[i] = v;
            } else {
                cbgValues[i] = Game.config.getFloat(cbgKeys[i]);
            }
        }
    }

    @Override
    protected void drawGUI() {
        screen.background(StyleConstants.BG_DARK);
        drawCachedBg();

        int panelH = 96 + cbgKeys.length * ROW_H + 70;
        int panelX = screen.width / 2 - PANEL_W / 2;
        int panelY = screen.height / 2 - panelH / 2;

        loadStyle("shadow");
        rect(panelX + 4, panelY + 6, PANEL_W, panelH);

        loadStyle("card");
        rect(panelX, panelY, PANEL_W, panelH);

        loadStyle("card-border");
        rect(panelX, panelY, PANEL_W, panelH);

        loadStyle("label");
        screen.text("CONFIGURATION", panelX + PANEL_W / 2, panelY + 28);

        loadStyle("heading");
        screen.text("Settings", panelX + PANEL_W / 2, panelY + 52);

        loadStyle("divider");
        screen.line(panelX + 32, panelY + 77, panelX + PANEL_W - 32, panelY + 77);

        int rowX = panelX + 32;
        int rowY = panelY + 96;
        int totalW = PANEL_W - 64;

        for (int i = 0; i < cbgKeys.length; i++) {
            int y = rowY + i * ROW_H;

            loadStyle("row-label");
            screen.text(cbgLabels[i], rowX, y + 16);

            loadStyle("row-key");
            screen.text(cbgKeys[i], rowX, y + 32);

            if (cbgIsString[i])
                drawTextInputRow(i, rowX, y, totalW);
            else if (cbgIsToggle[i])
                drawToggleRow(i, rowX, y, totalW);
            else
                drawSliderRow(i, rowX, y, totalW);

            loadStyle("divider");
            screen.line(rowX, y + ROW_H - 2, rowX + totalW, y + ROW_H - 2);
        }

        int btnY = panelY + panelH - 60;
        drawGhostButton("← BACK", panelX + 32, btnY, 120, 36, "ghost");
        drawPrimaryButton("APPLY", panelX + PANEL_W - 152, btnY, 120, 36, "primary");
    }

    private void drawTextInputRow(int idx, int x, int y, int totalW) {
        boolean active = cbgEditIndex == idx;
        int fw = 200, fh = 26;
        int fx = x + totalW - fw;
        int fy = y + 8;

        loadStyle("input");
        if (active)
            addModifer("active");
        rect(fx, fy, fw, fh);

        String display = cbgStrings[idx];
        if (active && (screen.millis() / 500) % 2 == 0)
            display += "|";

        // textWidth measurement is structural, not styling
        screen.textFont(StyleConstants.getFont(screen.fontMono11));
        while (display.length() > 1 && screen.textWidth(display) > fw - 12)
            display = display.substring(1);

        loadStyle("input-text");
        if (active)
            addModifer("active");
        screen.text(display, fx + 6, fy + fh / 2f);
    }

    private void drawToggleRow(int idx, int x, int y, int totalW) {
        boolean on = cbgValues[idx] > 0.5f;
        int tx = x + totalW - 52;
        int ty = y + 10;
        int tw = 44, th = 22;

        loadStyle("toggle-track");
        if (on)
            addModifer("on");
        screen.rect(tx, ty, tw, th, th / 2f);

        float knobX = PApplet.lerp(tx + 4, tx + tw - 18, on ? 1f : 0f);
        loadStyle("toggle-knob");
        if (on)
            addModifer("on");
        screen.ellipse(knobX + 7, ty + th / 2f, 16, 16);

        loadStyle("toggle-label");
        if (on)
            addModifer("on");
        screen.text(on ? "ON" : "OFF", tx - 8, ty + th / 2f);
    }

    private void drawSliderRow(int idx, int x, int y, int totalW) {
        int sliderW = 200;
        int sx = x + totalW - sliderW;
        int sy = y + 14;
        int sh = 4;

        float pct = (cbgValues[idx] - cbgMin[idx]) / (cbgMax[idx] - cbgMin[idx]);
        float fillW = pct * sliderW;

        boolean dragging = cbgDragging && cbgDragIndex == idx;
        boolean hovering = !cbgDragging
                && screen.mouseX >= sx - 8 && screen.mouseX <= sx + sliderW + 8
                && screen.mouseY >= sy - 10 && screen.mouseY <= sy + sh + 10;

        loadStyle("slider-track");
        rect(sx, sy, sliderW, sh);

        loadStyle("slider-fill");
        rect(sx, sy, Math.max(sh, fillW), sh);

        loadStyle("slider-knob");
        if (dragging)
            addModifer("active");
        else if (hovering)
            addModifer("hover");
        screen.ellipse(sx + fillW, sy + sh / 2f, dragging ? 14 : 10, dragging ? 14 : 10);

        loadStyle("slider-value");
        screen.text(String.format("%.2f", cbgValues[idx]), sx - 12, sy + sh / 2f);

        if (dragging) {
            float newPct = PApplet.constrain((float) (screen.mouseX - sx) / sliderW, 0, 1);
            cbgValues[idx] = cbgMin[idx] + newPct * (cbgMax[idx] - cbgMin[idx]);
        }
    }

    @Override
    protected void onClick() {
        cbgEditIndex = -1;

        int panelH = 96 + cbgKeys.length * ROW_H + 70;
        int panelX = screen.width / 2 - PANEL_W / 2;
        int panelY = screen.height / 2 - panelH / 2;
        int rowX = panelX + 32;
        int rowY = panelY + 96;
        int totalW = PANEL_W - 64;
        int sliderW = 200;
        int btnY = panelY + panelH - 60;

        if (hitTest(panelX + 32, btnY, 120, 36)) {
            hide();
            screen.selector.show();
            return;
        }
        if (hitTest(panelX + PANEL_W - 152, btnY, 120, 36)) {
            applyConfig();
            return;
        }

        for (int i = 0; i < cbgKeys.length; i++) {
            int ry = rowY + i * ROW_H;

            if (cbgIsString[i]) {
                int fx = rowX + totalW - 200;
                int fy = ry + 8;
                if (screen.mouseX >= fx && screen.mouseX <= fx + 200 &&
                        screen.mouseY >= fy && screen.mouseY <= fy + 26)
                    cbgEditIndex = i;

            } else if (cbgIsToggle[i]) {
                int tx = rowX + totalW - 52;
                int ty = ry + 10;
                if (screen.mouseX >= tx && screen.mouseX <= tx + 44 &&
                        screen.mouseY >= ty && screen.mouseY <= ty + 22)
                    cbgValues[i] = cbgValues[i] > 0.5f ? 0f : 1f;

            } else {
                int sx = rowX + totalW - sliderW;
                int sy = ry + 14;
                if (screen.mouseX >= sx - 8 && screen.mouseX <= sx + sliderW + 8 &&
                        screen.mouseY >= sy - 10 && screen.mouseY <= sy + 14) {
                    cbgDragging = true;
                    cbgDragIndex = i;
                }
            }
        }
    }

    @Override
    protected void onClickOff() {
        cbgDragging = false;
        cbgDragIndex = -1;
    }

    @Override
    protected void onKeyPress() {
        if (cbgEditIndex < 0)
            return;

        if (screen.key == PApplet.BACKSPACE) {
            String s = cbgStrings[cbgEditIndex];
            if (s.length() > 0)
                cbgStrings[cbgEditIndex] = s.substring(0, s.length() - 1);
        } else if (screen.key == PApplet.ENTER || screen.key == PApplet.RETURN || screen.key == PApplet.ESC) {
            cbgEditIndex = -1;
        } else if (screen.key >= 32 && screen.key < 127) {
            if (cbgStrings[cbgEditIndex].length() < 64)
                cbgStrings[cbgEditIndex] += screen.key;
        }
    }

    private void applyConfig() {
        for (int i = 0; i < cbgKeys.length; i++) {
            if (cbgIsString[i])
                Game.config.set(cbgKeys[i], cbgStrings[i]);
            else
                Game.config.set(cbgKeys[i], cbgValues[i]);
        }
        try {
            Game.updateConfig();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected List<GUI> getDisplayLocation() {
        return screen.getPregameGUI();
    }

    @Override
    protected void createStyleSheet() {

        StyleConstants.addPanelStyles(stylesheet);

        stylesheet.set("row-label", new JSONObject()
                .set("bg", StyleConstants.TEXT_PRIMARY)
                .set("txtAlignX", PApplet.LEFT)
                .set("txtAlignY", PApplet.CENTER)
                .set("font", screen.fontSans13));

        stylesheet.set("row-key", new JSONObject()
                .set("bg", StyleConstants.TEXT_DIM)
                .set("txtAlignX", PApplet.LEFT)
                .set("txtAlignY", PApplet.CENTER)
                .set("font", screen.fontMono10));

        stylesheet.set("input", new JSONObject()
                .set("bg", StyleConstants.BG_DARK)
                .set("borderColor", StyleConstants.TEXT_LABEL)
                .set("borderSize", 1f)
                .set("borderRadius", 4f));

        stylesheet.set("input.active", new JSONObject()
                .set("bg", StyleConstants.BG_DARK)
                .set("borderColor", StyleConstants.ACCENT)
                .set("borderSize", 1f)
                .set("borderRadius", 4f));

        stylesheet.set("input-text", new JSONObject()
                .set("bg", StyleConstants.TEXT_DIM)
                .set("txtAlignX", PApplet.LEFT)
                .set("txtAlignY", PApplet.CENTER)
                .set("font", screen.fontMono11));

        stylesheet.set("input-text.active", new JSONObject()
                .set("bg", StyleConstants.TEXT_PRIMARY));

        stylesheet.set("toggle-track", new JSONObject()
                .set("bg", StyleConstants.BORDER));

        stylesheet.set("toggle-track.on", new JSONObject()
                .set("bg", StyleConstants.ACCENT_DIM));

        stylesheet.set("toggle-knob", new JSONObject()
                .set("bg", StyleConstants.TEXT_DIM));

        stylesheet.set("toggle-knob.on", new JSONObject()
                .set("bg", StyleConstants.ACCENT));

        stylesheet.set("toggle-label", new JSONObject()
                .set("bg", StyleConstants.TEXT_DIM)
                .set("txtAlignX", PApplet.RIGHT)
                .set("txtAlignY", PApplet.CENTER)
                .set("font", screen.fontMono11));

        stylesheet.set("toggle-label.on", new JSONObject()
                .set("bg", StyleConstants.ACCENT));

        stylesheet.set("slider-track", new JSONObject()
                .set("bg", StyleConstants.BORDER)
                .set("borderRadius", 2f));

        stylesheet.set("slider-fill", new JSONObject()
                .set("bg", StyleConstants.ACCENT_DIM)
                .set("borderRadius", 2f));

        stylesheet.set("slider-knob", new JSONObject()
                .set("bg", screen.color(180)));

        stylesheet.set("slider-knob.hover", new JSONObject()
                .set("bg", StyleConstants.ACCENT));

        stylesheet.set("slider-knob.active", new JSONObject()
                .set("bg", StyleConstants.ACCENT));

        stylesheet.set("slider-value", new JSONObject()
                .set("bg", StyleConstants.TEXT_DIM)
                .set("txtAlignX", PApplet.RIGHT)
                .set("txtAlignY", PApplet.CENTER)
                .set("font", screen.fontMono11));

        StyleConstants.addPrimaryButtonStyles(stylesheet, "primary");

        StyleConstants.addGhostButtonStyles(stylesheet, "ghost");
    }
}