package dev.korgi.game.ui.game;

import dev.korgi.game.items.Item;
import dev.korgi.game.ui.GUI;
import dev.korgi.json.JSONObject;
import processing.core.PApplet;

public class Inventory extends GUI {

    private Item[] items;
    private int selected;

    public Inventory() {
        this.items = new Item[9];
    }

    public Inventory(int size) {
        this.items = new Item[size];
    }

    public void select(int val) {
        selected = val;
    }

    public Item getSelected() {
        return items[selected];
    }

    public int getSelectedIndex() {
        return selected;
    }

    public boolean addToInventory(Item item) {
        for (int i = 0; i < items.length; i++) {
            if (items[i] == null) {
                items[i] = item;
                return true;
            }
        }
        return false;
    }

    public boolean removeFromInventory(Item item) {
        for (int i = 0; i < items.length; i++) {
            if (items[i].internal_id.equals(item.internal_id)) {
                items[i] = null;
                return true;
            }
        }
        return false;
    }

    public int totalCapacity() {
        return items.length;
    }

    public int itemCount() {
        int size = 0;
        for (int i = 0; i < items.length; i++) {
            if (items[i] != null) {
                size++;
            }
        }
        return size;
    }

    public void clear() {
        for (int i = 0; i < items.length; i++) {
            items[i] = null;
        }
    }

    public Item get(int i) {
        return items[i];
    }

    public Item[] getItems() {
        return items;
    }

    @Override
    protected void drawGUI() {
        final int slotSize = 42;
        final int gap = 6;

        final int totalW = 9 * slotSize + 8 * gap;
        final int startX = screen.width / 2 - totalW / 2;
        final int y = screen.height - 70;

        final float iconSize = slotSize - 12;
        loadStyle("container");
        rect(startX - 12, y - 10, totalW + 24, slotSize + 20);

        loadStyle("slot");
        for (int i = 0; i < items.length; i++) {
            int x = startX + i * (slotSize + gap);
            boolean selected = i == this.selected;
            if (selected) {
                loadStyle("slotActive");
            }
            rect(x, y, slotSize, slotSize);
            if (selected) {
                loadStyle("slot");
            }
        }

        loadStyle("numTxt");
        for (int i = 0; i < items.length; i++) {
            int x = startX + i * (slotSize + gap);
            screen.text("%d".formatted(i + 1), x + slotSize / 2f, y + slotSize - 4);
        }

        loadStyle("icon");
        for (int i = 0; i < items.length; i++) {
            int x = startX + i * (slotSize + gap);

            if (items[i] != null) {
                screen.image(items[i].getIcon(), x + slotSize / 2f, y + slotSize / 2f, iconSize, iconSize);
            }
        }

    }

    @Override
    protected void createStyleSheet() {

        stylesheet.set("container", new JSONObject()
                .set("bg", 0x78000000)
                .set("borderRadius", 10));

        stylesheet.set("slot", new JSONObject()
                .set("bg", 0xFF1C2130)
                .set("borderColor", 0xFF252B3B)
                .set("borderSize", 1)
                .set("borderRadius", 6));

        stylesheet.set("slotActive", new JSONObject()
                .set("bg", 0xFF4F8EF7)
                .set("borderColor", 0xFF4F8EF7)
                .set("borderSize", 2)
                .set("borderRadius", 6));

        stylesheet.set("numTxt", new JSONObject()
                .set("bg", 0xFF7A8099)
                .set("font", screen.fontSans12)
                .set("txtAlignX", PApplet.CENTER)
                .set("txtAlignY", PApplet.BOTTOM));

        stylesheet.set("icon", new JSONObject()
                .set("imgMode", PApplet.CENTER));
    }

}
