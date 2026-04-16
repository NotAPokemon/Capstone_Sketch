package dev.korgi.game.ui.builder;

import dev.korgi.game.ui.elements.Canvas;
import dev.korgi.game.ui.elements.Image;
import dev.korgi.game.ui.elements.Text;
import dev.korgi.json.JSONObject;

public class CanvasBuilder<P> {
    private final Canvas canvas;
    private final P parent;
    private final String parentId;
    private final UIBuilder root;
    private final JSONObject elementStyle = new JSONObject();

    CanvasBuilder(Canvas canvas, P parent, String parentId, UIBuilder root) {
        this.canvas = canvas;
        this.parent = parent;
        this.root = root;
        this.parentId = parentId;
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

    public CanvasBuilder<P> borderRadius(float size) {
        elementStyle.set("borderRadius", size);
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
        return new CanvasBuilder<>(child, this, canvas.getId(), root);
    }

    public TextBuilder<CanvasBuilder<P>> text(Text text) {
        canvas.addChild(text);
        return new TextBuilder<>(text, this, canvas.getId(), root);
    }

    public ImageBuilder<CanvasBuilder<P>> image(Image image) {
        canvas.addChild(image);
        return new ImageBuilder<CanvasBuilder<P>>(image, this, canvas.getId(), root);
    }

    private void ensureDefaults() {
        elementStyle.addFloat("width", 50);
        elementStyle.addFloat("height", 50);
        elementStyle.addFloat("x", 0);
        elementStyle.addFloat("y", 0);
        elementStyle.set(".parent", parentId);
    }

    public P backToParent() {
        ensureDefaults();
        root.applyElementStyle(canvas.getId(), elementStyle);
        return parent;
    }
}