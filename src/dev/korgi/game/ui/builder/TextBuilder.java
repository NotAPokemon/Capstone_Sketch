package dev.korgi.game.ui.builder;

import dev.korgi.game.ui.elements.Text;
import dev.korgi.json.JSONObject;

public class TextBuilder<P> {

    private final Text text;
    private final P parent;
    private final UIBuilder root;
    private final JSONObject elementStyle = new JSONObject();

    TextBuilder(Text text, P parent, UIBuilder root) {
        this.text = text;
        this.parent = parent;
        this.root = root;
    }

    public TextBuilder<P> position(float x, float y) {
        elementStyle.set("x", x);
        elementStyle.set("y", y);
        return this;
    }

    public TextBuilder<P> color(int color) {
        elementStyle.set("bg", color);
        return this;
    }

    public TextBuilder<P> fontSize(float size) {
        elementStyle.set("borderSize", size);
        return this;
    }

    private void ensureDefaults() {
        elementStyle.addFloat("x", 0);
        elementStyle.addFloat("y", 0);
        elementStyle.addInt("borderColor", 0xFFFFFFFF);
        elementStyle.addFloat("borderSize", 12);
    }

    public P backToParent() {
        ensureDefaults();
        root.applyElementStyle(text.getId(), elementStyle);
        return parent;
    }

}