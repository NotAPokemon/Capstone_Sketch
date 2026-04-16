package dev.korgi.game.ui.elements;

import dev.korgi.game.ui.Screen;
import dev.korgi.json.JSONObject;
import dev.korgi.math.Vector2;
import processing.core.PImage;

public class Image extends Element {

    private PImage img;

    public Image(String name) {
        super(name);
    }

    public Image() {
        super();
    }

    @Override
    void draw(Screen screen, JSONObject data) {
        if (img == null) {
            return;
        }
        JSONObject mStyle = data.getJSONObject(id);
        Float sizeX = mStyle.getFloat("width");
        Float sizeY = mStyle.getFloat("height");
        Vector2 pos = getPosition(data);

        screen.push();
        UI.applyStyle(screen, mStyle);
        if (sizeX != null && sizeY != null) {
            screen.image(img, pos.x, pos.y, sizeX, sizeY);
        } else {
            screen.image(img, pos.x, pos.y);
        }
        screen.pop();
    }

    public void setImg(PImage img) {
        this.img = img;
    }

}
