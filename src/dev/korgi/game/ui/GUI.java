package dev.korgi.game.ui;

import dev.korgi.json.JSONIgnore;
import dev.korgi.json.JSONObject;
import dev.korgi.utils.StyleConstants;

public abstract class GUI {

    @JSONIgnore
    protected final JSONObject stylesheet = new JSONObject();
    @JSONIgnore
    protected final Screen screen = Screen.getInstance();
    @JSONIgnore
    private boolean isPopped = true;
    @JSONIgnore
    private String currentStyle;

    public GUI() {
        createStyleSheet();
    }

    protected abstract void drawGUI();

    public final void draw() {
        screen.push();
        drawGUI();
        if (!isPopped) {
            screen.pop();
            isPopped = true;
        }
        screen.pop();
    }

    protected abstract void createStyleSheet();

    protected void onClick() {
    }

    protected void onKeyPress() {
    }

    protected void onClickOff() {
    }

    protected void onOpen() {
    }

    protected void onClose() {
    }

    public final boolean isVisible() {
        return screen.getGameGui().contains(this);
    }

    public final void show() {
        if (!isVisible()) {
            onOpen();
            screen.getGameGui().add(this);
        }
    }

    public final void hide() {
        onClose();
        screen.getGameGui().removeIf((v) -> v == this);
    }

    protected void unloadStyle() {
        screen.pop();
        isPopped = true;
    }

    protected Integer getIntProp(String key) {
        return stylesheet.getJSONObject(currentStyle).getInt(key);
    }

    protected Float getFloatProp(String key) {
        return stylesheet.getJSONObject(currentStyle).getFloat(key);
    }

    protected void rect(int x, int y, int w, int h) {
        Float borderRadius = getFloatProp("borderRadius");
        if (borderRadius != null) {
            screen.rect(x, y, w, h, borderRadius);
        } else {
            screen.rect(x, y, w, h);
        }
    }

    protected void loadStyle(String name) {
        JSONObject styleObject = stylesheet.getJSONObject(name);
        currentStyle = name;
        Integer bgcolor = styleObject.getInt("bg");
        Integer borderColor = styleObject.getInt("borderColor");
        Float borderSize = styleObject.getFloat("borderStyle");
        Integer txtAlignX = styleObject.getInt("txtAlignX");
        Integer txtAlignY = styleObject.getInt("txtAlignY");
        Integer imgMode = styleObject.getInt("imgMode");
        Integer fontId = styleObject.getInt("font");
        if (!isPopped) {
            screen.pop();
            isPopped = true;
        }
        screen.push();
        isPopped = false;
        if (imgMode != null) {
            screen.imageMode(imgMode);
        }
        if (fontId != null) {
            screen.textFont(StyleConstants.getFont(fontId));
        }
        if (txtAlignX != null) {
            if (txtAlignY != null) {
                screen.textAlign(txtAlignX, txtAlignY);
            } else {
                screen.textAlign(txtAlignX);
            }
        }
        if (borderSize == null) {
            screen.noStroke();
        } else {
            screen.strokeWeight(borderSize);
        }
        if (borderColor != null) {
            screen.stroke(borderColor);
        }
        if (bgcolor == null) {
            screen.noFill();
        } else {
            screen.fill(bgcolor);
        }
    }

    public JSONObject getStylesheet() {
        return stylesheet;
    }

}
