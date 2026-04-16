package dev.korgi.game.ui.builder;

import dev.korgi.game.ui.elements.Canvas;
import dev.korgi.game.ui.elements.Element;
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

    public CanvasBuilder<UIBuilder> canvas(Canvas canvas) {
        this.rootElement = canvas;
        return new CanvasBuilder<>(canvas, this, this);
    }

    void applyElementStyle(String id, JSONObject elementStyle) {
        style.set(id, elementStyle);
    }

    public UI build() {
        if (rootElement == null)
            throw new IllegalStateException("UI '%s' must have a root element".formatted(name));

        UI ui = new UI(name);
        ui.setRootElement(rootElement);
        ui.setStyle(style);
        return ui;
    }

}