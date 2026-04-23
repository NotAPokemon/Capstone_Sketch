package dev.korgi.game.ui;

import dev.korgi.game.items.Item;
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
            if (items[i] == item) {
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

    public Item get(int i) {
        return items[i];
    }

    @Override
    protected void drawGUI() {
        int slotSize = 42;
        int gap = 6;

        int totalW = 9 * slotSize + 8 * gap;
        int startX = screen.width / 2 - totalW / 2;
        int y = screen.height - 70;

        int padding = 6;
        float iconSize = slotSize - padding * 2;
        loadStyle("container");
        rect(startX - 12, y - 10, totalW + 24, slotSize + 20);
        for (int i = 0; i < items.length; i++) {
            int x = startX + i * (slotSize + gap);
            boolean selected = i == this.selected;
            if (selected) {
                loadStyle("slotActive");
            } else {
                loadStyle("slot");
            }
            rect(x, y, slotSize, slotSize);
            loadStyle("numTxt");
            screen.text("%d".formatted(i + 1), x + slotSize / 2f, y + slotSize - 4);
            if (items[i] != null) {
                loadStyle("icon");
                screen.image(items[i].getIcon(), x + slotSize / 2f, y + slotSize / 2f, iconSize, iconSize);
            }
        }
    }

    @Override
    protected void createStyleSheet() {
        JSONObject container = new JSONObject();

        container.set("bg", 0x78000000);
        container.set("borderRadius", 10);

        JSONObject slot = new JSONObject();

        slot.set("bg", 0xFF1C2130);
        slot.set("borderColor", 0xFF252B3B);
        slot.set("borderSize", 1);
        slot.set("borderRadius", 6);

        JSONObject slotActive = new JSONObject();

        slotActive.set("bg", 0xFF4F8EF7);
        slotActive.set("borderColor", 0xFF4F8EF7);
        slotActive.set("borderSize", 2);
        slotActive.set("borderRadius", 6);

        JSONObject numTxt = new JSONObject();

        numTxt.set("bg", 0xFF7A8099);
        numTxt.set("font", screen.fontSans12);
        numTxt.set("txtAlignX", PApplet.CENTER);
        numTxt.set("txtAlignY", PApplet.BOTTOM);

        JSONObject icon = new JSONObject();
        icon.set("imgMode", PApplet.CENTER);

        stylesheet.set("container", container);
        stylesheet.set("slot", slot);
        stylesheet.set("slotActive", slotActive);
        stylesheet.set("numTxt", numTxt);
        stylesheet.set("icon", icon);
    }

}
