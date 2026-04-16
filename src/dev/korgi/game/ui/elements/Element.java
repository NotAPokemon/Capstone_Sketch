package dev.korgi.game.ui.elements;

import dev.korgi.game.ui.Screen;
import dev.korgi.json.JSONObject;

public abstract class Element {
    protected String id;

    public Element(String id) {
        this.id = id;
    }

    abstract void draw(Screen screen, JSONObject data);

    public String getId() {
        return id;
    }
}
