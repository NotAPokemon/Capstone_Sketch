package dev.korgi.game.ui.builder;

import dev.korgi.game.ui.elements.Canvas;
import dev.korgi.game.ui.elements.Element;
import dev.korgi.game.ui.elements.Text;
import dev.korgi.game.ui.elements.UI;
import dev.korgi.json.JSONObject;

public class UIBuilder {

    private Element rootElement;
    private final JSONObject style = new JSONObject();

    private UIBuilder() {
    }

    public static UIBuilder create() {
        return new UIBuilder();
    }

    public static UI debugValue(float x, float y) {
        return create()
                .drawMode(DrawMode.ABSOLUTE)
                .canvas(new Canvas("main_canvas"))
                .text(new Text("display"))
                .position(x, y)
                .color(0xFFFF0000)
                .backToParent()
                .backToParent()
                .build();
    }

    public static UI debugValue() {
        return debugValue(100, 100);
    }

    public UIBuilder drawMode(DrawMode mode) {
        style.set(".mode", mode.toString());
        return this;
    }

    public CanvasBuilder<UIBuilder> canvas(Canvas canvas) {
        this.rootElement = canvas;
        return new CanvasBuilder<>(canvas, this, null, this);
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
        UI ui = new UI();
        ui.setRootElement(rootElement);
        ensureDefaults();
        ui.setStyle(style);
        return ui;
    }

}