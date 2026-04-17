package dev.korgi.game.items;

import dev.korgi.game.ui.builder.UIBuilder;
import dev.korgi.game.ui.elements.UI;
import dev.korgi.json.JSONIgnore;
import dev.korgi.utils.ClientSide;
import dev.korgi.utils.ServerSide;

public class JimmyItem extends Item {

    @JSONIgnore
    @ClientSide
    private UI hehe = UIBuilder.debugValue("Jimmy");

    @ServerSide
    private boolean shouldOpen = false;

    public JimmyItem() {
        scale(0.125f);
        hehe.getStyle().set("display.value", "JIMMY");
    }

    @Override
    @ServerSide
    public void rmb() {
        if (!shouldOpen) {
            shouldOpen = true;
        }
    }

    @Override
    @ClientSide
    protected void client(double dt) {
        super.client(dt);
        if (shouldOpen && !hehe.isOpen()) {
            hehe.open();
        }
    }

    @Override
    protected String getModelName() {
        return "jimmy_item";
    }

    @Override
    protected String getIconName() {
        return "jimmy_item.png";
    }

}
