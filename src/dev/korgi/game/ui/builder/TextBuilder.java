package dev.korgi.game.ui.builder;

import dev.korgi.game.ui.elements.Text;
import dev.korgi.json.JSONObject;
import processing.core.PFont;

public class TextBuilder<P> {

    private final Text text;
    private final P parent;
    private final String parentId;
    private final UIBuilder root;
    private final JSONObject elementStyle = new JSONObject();

    TextBuilder(Text text, P parent, String parentId, UIBuilder root) {
        this.text = text;
        this.parent = parent;
        this.root = root;
        this.parentId = parentId;
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

    public TextBuilder<P> font(PFont font) {
        text.setFont(font);
        return this;
    }

    public TextBuilder<P> align(int alignX, int alignY) {
        elementStyle.set("txtAlignX", alignX);
        elementStyle.set("txtAlignY", alignY);
        return this;
    }

    public TextBuilder<P> setText(String value) {
        root.setText(text.getId(), value);
        return this;
    }

    private void ensureDefaults() {
        elementStyle.addFloat("x", 0);
        elementStyle.addFloat("y", 0);
        elementStyle.addInt("borderColor", 0xFFFFFFFF);
        elementStyle.addFloat("borderSize", 12);
        elementStyle.set(".parent", parentId);
    }

    public P backToParent() {
        ensureDefaults();
        root.applyElementStyle(text.getId(), elementStyle);
        return parent;
    }

}