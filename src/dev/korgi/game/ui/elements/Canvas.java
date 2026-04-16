package dev.korgi.game.ui.elements;

import java.util.ArrayList;
import java.util.List;

import dev.korgi.game.ui.Screen;
import dev.korgi.json.JSONObject;
import dev.korgi.math.Vector2;

public class Canvas extends Element {

    private List<Element> children = new ArrayList<>();

    public Canvas(String name) {
        super(name);
    }

    public Canvas() {
    }

    @Override
    void draw(Screen screen, JSONObject data) {
        JSONObject mStyle = data.getJSONObject(id);
        float sizeX = mStyle.getFloat("width");
        float sizeY = mStyle.getFloat("height");
        Float borderRadius = mStyle.getFloat("borderRadius");
        Vector2 pos = getPosition(data);

        screen.push();
        UI.applyStyle(screen, mStyle);
        if (borderRadius == null) {
            screen.rect(pos.x, pos.y, sizeX, sizeY);
        } else {
            screen.rect(pos.x, pos.y, sizeX, sizeY, borderRadius);
        }
        screen.pop();

        for (Element element : children) {
            element.draw(screen, data);
        }
    }

    public void addChild(Element element) {
        this.children.add(element);
    }

    public void removeChild(String name) {
        children.removeIf((e) -> e.getId().equals(name));
    }

    public List<Element> getChildren() {
        return children;
    }

}
