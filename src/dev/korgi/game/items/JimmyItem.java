package dev.korgi.game.items;

import dev.korgi.game.ui.builder.UIBuilder;
import dev.korgi.game.ui.elements.UI;
import dev.korgi.json.JSONIgnore;
import dev.korgi.utils.ClientSide;
import dev.korgi.utils.ServerSide;
import dev.korgi.utils.Time;

public class JimmyItem extends Item {

    @JSONIgnore
    @ClientSide
    private UI hehe = UIBuilder.debugValue();

    @ServerSide
    private boolean shouldOpen = false;

    public JimmyItem() {
        scale(0.125f);
    }

    @Override
    @ServerSide
    public void rmb() {
        Time.cooldown(internal_id + "_rmb", () -> shouldOpen = !shouldOpen, 5);
    }

    @Override
    @ClientSide
    protected void client(double dt) {
        super.client(dt);
        if (shouldOpen) {
            hehe.getStyle().set("display.value", "JIMMY");
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
