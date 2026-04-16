package dev.korgi.game.ui.elements;

import dev.korgi.game.ui.Screen;
import dev.korgi.game.ui.builder.DrawMode;
import dev.korgi.json.JSONObject;
import dev.korgi.math.Vector2;

public abstract class Element {

    private static int globalCount = 0;

    protected String id;

    public Element(String id) {
        this.id = id;
    }

    public Element() {
        this("unamed-element-%d".formatted(globalCount++));
    }

    abstract void draw(Screen screen, JSONObject data);

    public String getId() {
        return id;
    }

    protected DrawMode getDrawMode(JSONObject data) {
        String dm = data.getString(".mode");
        if (dm == null) {
            return DrawMode.UNKNOWN;
        }
        return DrawMode.valueOf(dm);
    }

    private Vector2 computeAbsolutePosition(JSONObject data) {
        float x = 0;
        float y = 0;

        String currentId = id;

        while (currentId != null) {
            JSONObject style = data.getJSONObject(currentId);

            if (style.hasKey("x")) {
                x += style.getFloat("x");
            }
            if (style.hasKey("y")) {
                y += style.getFloat("y");
            }

            currentId = style.getString(".parent");
        }

        return new Vector2(x, y);
    }

    protected Vector2 getPosition(JSONObject data) {
        DrawMode mode = getDrawMode(data);
        if (mode == DrawMode.ABSOLUTE) {
            JSONObject mStyle = data.getJSONObject(id);
            float x = mStyle.getFloat("x");
            float y = mStyle.getFloat("y");
            Vector2 pos = new Vector2(x, y);
            return pos;
        } else if (mode == DrawMode.RELATIVE) {
            return computeAbsolutePosition(data);
        } else {
            throw new IllegalStateException("Draw Mode Cannot be Unknown or null");
        }
    }
}
