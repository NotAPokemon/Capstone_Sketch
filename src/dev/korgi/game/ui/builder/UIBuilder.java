package dev.korgi.game.ui.builder;

import dev.korgi.game.ui.elements.Canvas;
import dev.korgi.game.ui.elements.Element;
import dev.korgi.game.ui.elements.Text;
import dev.korgi.game.ui.elements.UI;
import dev.korgi.json.JSONObject;

public class UIBuilder {

    private final String name;
    private Element rootElement;
    private final JSONObject style = new JSONObject();

    private UIBuilder(String name) {
        this.name = name;
    }

    public static UIBuilder create(String name) {
        return new UIBuilder(name);
    }

    public static UI debugValue(float x, float y, String name) {
        return create(name)
                .canvas(new Canvas(name + "main_canvas"))
                .text(new Text("display"))
                .position(x, y)
                .color(0xFFFF0000)
                .backToParent()
                .backToParent()
                .build();
    }

    public static UI debugValue(String name) {
        return debugValue(100, 100, name);
    }

    public UIBuilder drawMode(DrawMode mode) {
        style.set(".mode", mode.toString());
        return this;
    }

    public CanvasBuilder<UIBuilder> canvas(Canvas canvas) {
        this.rootElement = canvas;
        return new CanvasBuilder<>(canvas, this, name, this);
    }

    public UIBuilder setText(String elementName, String value) {
        style.set(elementName + ".value", value);
        return this;
    }

    void applyElementStyle(String id, JSONObject elementStyle) {
        style.set(id, elementStyle);
    }

    private void ensureDefaults() {
        style.addString(".mode", DrawMode.RELATIVE.toString());
        style.set(".parent", null);
    }

    public UI build() {
        if (rootElement == null)
            throw new IllegalStateException("UI '%s' must have a root element".formatted(name));

        UI ui = new UI(name);
        ui.setRootElement(rootElement);
        ensureDefaults();
        ui.setStyle(style);
        return ui;
    }

}