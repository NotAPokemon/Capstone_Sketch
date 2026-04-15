package dev.korgi.game.entites;

import dev.korgi.game.items.Item;

public interface StorageEntity {
    public boolean addToInventory(Item item);

    public boolean removeFromInventory(Item item);

    public void clearInventory(boolean drop);
}
