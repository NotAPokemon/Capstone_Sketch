package dev.korgi.game.ui;

import java.util.List;
import java.util.UUID;

import dev.korgi.json.JSONIgnore;
import dev.korgi.json.JSONObject;
import dev.korgi.utils.ErrorHandler;
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
    private String id;

    public GUI() {
        createStyleSheet();
        id = UUID.randomUUID().toString();
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
        List<GUI> guis = getDisplayLocation();
        for (GUI gui : guis) {
            if (gui.id.equals(id)) {
                return true;
            }
        }
        return false;
    }

    protected List<GUI> getDisplayLocation() {
        return screen.getGameGui();
    }

    protected void cancelEvent() {
        screen.eventCanceled = true;
    }

    public final void show() {
        List<GUI> guis = getDisplayLocation();
        for (int i = 0; i < guis.size(); i++) {
            if (guis.get(i).id.equals(id)) {
                guis.remove(i);
                break;
            }
        }
        onOpen();
        if (screen.eventCanceled) {
            screen.eventCanceled = false;
            return;
        }
        guis.add(this);

    }

    public final void hide() {
        onClose();
        if (screen.eventCanceled) {
            screen.eventCanceled = false;
            return;
        }
        getDisplayLocation().removeIf((v) -> v.id.equals(this.id));
    }

    protected void unloadStyle() {
        screen.pop();
        isPopped = true;
    }

    protected Integer getIntProp(String key) {
        checkStyle();
        return stylesheet.getJSONObject(currentStyle).getInt(key);
    }

    protected Float getFloatProp(String key) {
        checkStyle();
        return stylesheet.getJSONObject(currentStyle).getFloat(key);
    }

    private void checkStyle() {
        if (currentStyle.isBlank()) {
            throw ErrorHandler.error("No style loaded cannot fetch properties");
        }
    }

    protected void rect(float x, float y, float w, float h) {
        Float borderRadius = getFloatProp("borderRadius");
        if (borderRadius != null) {
            screen.rect(x, y, w, h, borderRadius);
        } else {
            screen.rect(x, y, w, h);
        }
    }

    protected boolean hitTest(int x, int y, int w, int h) {
        return screen.mouseX >= x && screen.mouseX <= x + w &&
                screen.mouseY >= y && screen.mouseY <= y + h;
    }

    protected void loadStyle(String name) {
        JSONObject styleObject = stylesheet.getJSONObject(name);
        currentStyle = name;
        if (!isPopped) {
            screen.pop();
            isPopped = true;
        }
        screen.push();
        isPopped = false;
        dumbLoad(styleObject);
    }

    protected void addModifer(String modifer) {
        if (stylesheet.hasKey(currentStyle + "." + modifer)) {
            dumbLoad(stylesheet.getJSONObject(currentStyle + "." + modifer));
        }
    }

    private void dumbLoad(JSONObject styleObject) {
        Integer bgcolor = styleObject.getInt("bg");
        Integer borderColor = styleObject.getInt("borderColor");
        Float borderSize = styleObject.getFloat("borderSize");
        Integer txtAlignX = styleObject.getInt("txtAlignX");
        Integer txtAlignY = styleObject.getInt("txtAlignY");
        Integer imgMode = styleObject.getInt("imgMode");
        Integer fontId = styleObject.getInt("font");
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

    protected void drawPrimaryButton(String label, int x, int y, int w, int h, String variant) {
        boolean hover = hitTest(x, y, w, h);
        if (hover) {
            loadStyle("btn-" + variant + "-glow");
            rect(x - 4, y - 4, w + 8, h + 8);
        }

        loadStyle("btn-" + variant + "-bg");
        if (hover)
            addModifer("hover");
        rect(x, y, w, h);

        loadStyle("btn-" + variant + "-bar");
        screen.rect(x, y, 3, h, 8, 0, 0, 8);

        loadStyle("btn-" + variant + "-label");
        if (hover)
            addModifer("hover");
        screen.text(label, x + w / 2f + 1, y + h / 2f);
    }

    protected void drawGhostButton(String label, int x, int y, int w, int h, String varient) {
        boolean hover = hitTest(x, y, w, h);
        loadStyle("btn-" + varient + "-bg");
        if (hover)
            addModifer("hover");
        rect(x, y, w, h);

        loadStyle("btn-" + varient + "-label");
        if (hover)
            addModifer("hover");
        screen.text(label, x + w / 2f, y + h / 2f);
    }

    public JSONObject getStylesheet() {
        return stylesheet;
    }

    protected void drawCachedBg() {
        screen.drawGrid();
    }

}
