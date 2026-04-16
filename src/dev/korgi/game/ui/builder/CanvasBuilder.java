package dev.korgi.game.ui.builder;

import dev.korgi.game.ui.elements.Canvas;
import dev.korgi.game.ui.elements.Text;
import dev.korgi.json.JSONObject;

public class CanvasBuilder<P> {
    private final Canvas canvas;
    private final P parent;
    private final UIBuilder root;
    private final JSONObject elementStyle = new JSONObject();

    CanvasBuilder(Canvas canvas, P parent, UIBuilder root) {
        this.canvas = canvas;
        this.parent = parent;
        this.root = root;
    }

    public CanvasBuilder<P> bg(int color) {
        elementStyle.set("bg", color);
        return this;
    }

    public CanvasBuilder<P> borderColor(int color) {
        elementStyle.set("borderColor", color);
        return this;
    }

    public CanvasBuilder<P> borderSize(float size) {
        elementStyle.set("borderSize", size);
        return this;
    }

    public CanvasBuilder<P> position(float x, float y) {
        elementStyle.set("x", x);
        elementStyle.set("y", y);
        return this;
    }

    public CanvasBuilder<P> size(float width, float height) {
        elementStyle.set("width", width);
        elementStyle.set("height", height);
        return this;
    }

    public CanvasBuilder<CanvasBuilder<P>> canvas(Canvas child) {
        canvas.addChild(child);
        return new CanvasBuilder<>(child, this, root);
    }

    public TextBuilder<CanvasBuilder<P>> text(Text text) {
        canvas.addChild(text);
        return new TextBuilder<>(text, this, root);
    }

    private void ensureDefaults() {
        elementStyle.addFloat("width", 50);
        elementStyle.addFloat("height", 50);
        elementStyle.addFloat("x", 0);
        elementStyle.addFloat("y", 0);
    }

    public P backToParent() {
        ensureDefaults();
        root.applyElementStyle(canvas.getId(), elementStyle);
        return parent;
    }
}