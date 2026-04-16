package dev.korgi.game.ui.elements;

import java.util.List;

import dev.korgi.game.Game;
import dev.korgi.game.ui.Screen;
import dev.korgi.json.JSONObject;
import dev.korgi.utils.ClientSide;

@ClientSide
public class UI extends Element {

    private Element rootElement;
    private JSONObject style;
    private Runnable openSubscriber;
    private Runnable closeSubscriber;

    public UI(String name) {
        super(name);
    }

    public void onOpen(Runnable onOpen) {
        this.openSubscriber = onOpen;
    }

    public void onClose(Runnable closeSubscriber) {
        this.closeSubscriber = closeSubscriber;
    }

    public void setRootElement(Element rootElement) {
        this.rootElement = rootElement;
    }

    public void setStyle(JSONObject style) {
        this.style = style;
    }

    @Override
    void draw(Screen screen, JSONObject data) {
        rootElement.draw(screen, data);
    }

    public void draw(Screen screen) {
        draw(screen, style);
    }

    public static void applyStyle(Screen screen, JSONObject localStyle) {
        Integer bgcolor = localStyle.getInt("bg");
        Integer borderColor = localStyle.getInt("borderColor");
        Float borderSize = localStyle.getFloat("borderStyle");
        Integer txtAlignX = localStyle.getInt("txtAlignX");
        Integer txtAlignY = localStyle.getInt("txtAlignY");
        Integer imgMode = localStyle.getInt("imgMode");
        if (imgMode != null) {
            screen.imageMode(imgMode);
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

    public JSONObject getStyle() {
        return style;
    }

    public void open() {
        List<UI> ui = Game.isClient ? Screen.getInstance().getOpenUi() : Screen.getInstance().getServerUi();
        if (!ui.contains(this)) {
            ui.add(this);
            if (openSubscriber != null)
                openSubscriber.run();
        }
    }

    public void close() {
        List<UI> ui = Game.isClient ? Screen.getInstance().getOpenUi() : Screen.getInstance().getServerUi();
        ui.remove(this);
        if (closeSubscriber != null)
            closeSubscriber.run();
    }

    public boolean isOpen() {
        List<UI> ui = Game.isClient ? Screen.getInstance().getOpenUi() : Screen.getInstance().getServerUi();
        return ui.contains(this);
    }

    public Element findElement(String name) {
        return findElement(name, false);
    }

    private Element searchCanvas(Canvas c, String name) {
        if (c.id.equals(name)) {
            return c;
        }

        Element found = null;

        for (Element e : c.getChildren()) {
            if (e.id.equals(name)) {
                return e;
            }
            if (e instanceof Canvas can) {
                found = searchCanvas(can, name);
                if (found != null) {
                    return found;
                }
            }
        }

        return found;
    }

    public Element findElement(String name, boolean recursive) {
        if (rootElement.id.equals(name)) {
            return rootElement;
        } else if (recursive) {
            if (rootElement instanceof Canvas c) {
                return searchCanvas(c, name);
            } else {
                return null;
            }
        } else {
            return null;
        }
    }
}
