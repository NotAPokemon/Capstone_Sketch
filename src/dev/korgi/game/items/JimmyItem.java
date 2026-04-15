package dev.korgi.game.items;

public class JimmyItem extends Item {

    public JimmyItem() {
        scale(0.125f);
    }

    @Override
    protected String getModelName() {
        return "item";
    }

    @Override
    protected String getIconName() {
        return "item.png";
    }

}
