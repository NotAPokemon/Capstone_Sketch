package dev.korgi.game.ui.builder;

import dev.korgi.game.ui.elements.Image;
import dev.korgi.json.JSONObject;
import processing.core.PImage;

public class ImageBuilder<P> {

    private final Image img;
    private final P parent;
    private final String parentId;
    private final UIBuilder root;
    private final JSONObject elementStyle = new JSONObject();

    ImageBuilder(Image img, P parent, String parentId, UIBuilder root) {
        this.img = img;
        this.parent = parent;
        this.root = root;
        this.parentId = parentId;
    }

    public ImageBuilder<P> position(float x, float y) {
        elementStyle.set("x", x);
        elementStyle.set("y", y);
        return this;
    }

    public ImageBuilder<P> size(float width, float height) {
        elementStyle.set("width", width);
        elementStyle.set("height", height);
        return this;
    }

    public ImageBuilder<P> setImg(PImage img) {
        this.img.setImg(img);
        return this;
    }

    public ImageBuilder<P> imgMode(int mode) {
        elementStyle.set("imgMode", mode);
        return this;
    }

    private void ensureDefaults() {
        elementStyle.addFloat("x", 0);
        elementStyle.addFloat("y", 0);
        elementStyle.set(".parent", parentId);
    }

    public P backToParent() {
        ensureDefaults();
        root.applyElementStyle(img.getId(), elementStyle);
        return parent;
    }

}