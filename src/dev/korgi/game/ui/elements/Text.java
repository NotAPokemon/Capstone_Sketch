package dev.korgi.game.ui.elements;

import dev.korgi.game.ui.Screen;
import dev.korgi.json.JSONObject;

public class Text extends Element {

    private static int globalCount = 0;

    public Text(String name) {
        super(name);
    }

    public Text() {
        this("text%d".formatted(globalCount++));
    }

    @Override
    void draw(Screen screen, JSONObject data) {
        String txt = data.getString(id + ".value");
        if (txt == null) {
            txt = "null";
        }
        JSONObject mStyle = data.getJSONObject(id);
        float x = mStyle.getFloat("x");
        float y = mStyle.getFloat("y");

        screen.push();
        UI.applyStyle(screen, mStyle);
        screen.text(txt, x, y);
        screen.pop();
    }

}
