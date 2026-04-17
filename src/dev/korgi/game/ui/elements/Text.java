package dev.korgi.game.ui.elements;

import dev.korgi.game.ui.Screen;
import dev.korgi.json.JSONObject;
import dev.korgi.math.Vector2;
import processing.core.PFont;

public class Text extends Element {

    private PFont font;

    public Text(String name) {
        super(name);
    }

    public Text() {
        super();
    }

    @Override
    void draw(Screen screen, JSONObject data) {
        String txt = data.getString(id + ".value");
        if (txt == null) {
            txt = "null";
        }
        JSONObject mStyle = data.getJSONObject(id);
        Vector2 pos = getPosition(data);

        screen.push();
        UI.applyStyle(screen, mStyle);
        if (font != null) {
            screen.textFont(font);
        }
        screen.text(txt, pos.x, pos.y);
        screen.pop();
    }

    public void setFont(PFont font) {
        this.font = font;
    }

}
