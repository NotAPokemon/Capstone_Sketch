package dev.korgi.game.ui.elements;

import java.util.ArrayList;
import java.util.List;

import dev.korgi.game.ui.Screen;
import dev.korgi.json.JSONObject;

public class Canvas extends Element {

    private static int globalCount = 0;
    private List<Element> children = new ArrayList<>();

    public Canvas(String name) {
        super(name);
    }

    public Canvas() {
        this("canvas%d".formatted(globalCount++));
    }

    @Override
    void draw(Screen screen, JSONObject data) {
        JSONObject mStyle = data.getJSONObject(id);
        float sizeX = mStyle.getFloat("width");
        float sizeY = mStyle.getFloat("height");
        float x = mStyle.getFloat("x");
        float y = mStyle.getFloat("y");

        screen.push();
        UI.applyStyle(screen, mStyle);
        screen.rect(x, y, sizeX, sizeY);
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
