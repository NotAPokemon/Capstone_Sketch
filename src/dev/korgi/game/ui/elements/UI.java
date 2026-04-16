package dev.korgi.game.ui.elements;

import java.util.List;

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
        List<UI> ui = Screen.getInstance().getOpenUi();
        if (!ui.contains(this)) {
            ui.add(this);
            if (openSubscriber != null)
                openSubscriber.run();
        }
    }

    public void close() {
        Screen.getInstance().getOpenUi().remove(this);
        if (closeSubscriber != null)
            closeSubscriber.run();
    }
}
