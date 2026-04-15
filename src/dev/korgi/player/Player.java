package dev.korgi.player;

import java.util.ArrayList;
import java.util.List;

import dev.korgi.game.Game;
import dev.korgi.game.entites.Entity;
import dev.korgi.game.entites.StorageEntity;
import dev.korgi.game.items.Item;
import dev.korgi.game.physics.WorldEngine;
import dev.korgi.game.rendering.Graphics;
import dev.korgi.game.rendering.Screen;
import dev.korgi.game.rendering.TextureAtlas;
import dev.korgi.game.rendering.Voxel;
import dev.korgi.json.JSONIgnore;
import dev.korgi.math.Vector3;
import dev.korgi.math.VectorConstants;
import dev.korgi.networking.NetworkStream;
import dev.korgi.networking.Packet;
import dev.korgi.utils.ClientSide;
import dev.korgi.utils.VoxTranslator;
import processing.core.PApplet;
import processing.core.PImage;

public class Player extends Entity implements StorageEntity {

    public boolean connected;
    public List<String> pressedKeys = new ArrayList<>();
    private int speed = 4;
    private int selectedSlot = 0;
    private Item[] inventory = new Item[9];

    @JSONIgnore
    private int selectedBlock = TextureAtlas.DUNGEON_BLOCK;

    public Player() {
        setCancelProtocol(() -> !connected);
        scale(0.125f);
        scaleHitbox(-1);
        hitboxOffset(VectorConstants.FORWARD.multiply(VectorConstants.HALF));
    }

    @Override
    protected void client(double dt) {
        if (!internal_id.equals(NetworkStream.clientId)) {
            cancelTickEnd();
        }

        rotation = Graphics.camera.rotation;
        Graphics.camera.position = position.add(VectorConstants.HALF);
    }

    @Override
    protected void server(double dt) {
        handleKeyPresses(dt);
        super.server(dt);
    }

    @Override
    protected void handlePhysics(double dt) {
        gravityEnabled = !Game.canFly;
        super.handlePhysics(dt);
    }

    private void handleKeyPresses(double dt) {
        speed = 3;

        checkKey("CTRL", () -> speed = 5);

        Vector3 forward = new Vector3(Math.sin(rotation.y), 0, Math.cos(
                rotation.y)).normalizeHere().multiplyBy(speed * dt);

        Vector3 right = new Vector3(
                Math.sin(rotation.y - Math.PI / 2),
                0,
                Math.cos(rotation.y - Math.PI / 2)).normalizeHere().multiplyBy(speed * dt);

        Vector3 amt = VectorConstants.UP.multiply(speed * dt);

        checkKey("w", () -> position.addTo(forward));
        checkKey("s", () -> position.subtractFrom(forward));
        checkKey("a", () -> position.subtractFrom(right));
        checkKey("d", () -> position.addTo(right));
        checkKey("j", () -> Game.canFly = !Game.canFly, true);
        checkKey("m", () -> {
            selectedBlock += 1;
            selectedBlock %= TextureAtlas.amt;
        }, true);
        checkKey("RMB", () -> {
            withHit((hit) -> {
                Vector3 newPos = hit.getFaceWithOffset();
                if (WorldEngine.canPlaceVoxel(newPos)) {
                    WorldEngine.addVoxelWithTexture(newPos, selectedBlock);
                }
            }, 5);
        }, true);
        checkKey("LMB", () -> {
            withHit((hit) -> {
                Vector3 breakPos = hit.getVoxelPos();
                WorldEngine.removeVoxel(WorldEngine.voxelAt(breakPos));
            }, 5);
        }, true);
        checkKey(" ", () -> {
            if (!Game.canFly && onGround) {
                velocity.addTo(VectorConstants.UP.multiply(5));
            } else if (Game.canFly) {
                position.addTo(amt);
            }
            onGround = false;
        });
        checkKey("SHIFT", () -> {
            if (Game.canFly)
                position.subtractFrom(amt);
        });

        checkKey("q", () -> {
            if (inventory[selectedSlot] != null) {
                inventory[selectedSlot].drop(this);
            }
        }, true);

        for (int i = 0; i < 9; i++) {
            int val = i;
            checkKey("%d".formatted(i + 1), () -> updatedSelectedSlot(val));
        }

    }

    public void updatedSelectedSlot(int val) {
        if (val < 0 || val > 9) {
            return;
        }
        selectedSlot = val;
    }

    private void checkKey(String key, Runnable handler) {
        if (pressedKeys.contains(key) || pressedKeys.contains(key + "_HOLD")) {
            pressedKeys.remove(key);
            Vector3 pos = position.copy();
            handler.run();
            if (!pos.equals(position)) {
                WorldEngine.validatePosition(this);
            }
        }
    }

    private void checkKey(String key, Runnable handler, boolean onlyPress) {
        if (pressedKeys.contains(key) || (!onlyPress && pressedKeys.contains(key + "_HOLD"))) {
            pressedKeys.remove(key);
            Vector3 pos = position.copy();
            handler.run();
            if (!pos.equals(position)) {
                WorldEngine.validatePosition(this);
            }
        }
    }

    @Override
    protected void handleInPacket(Packet in) {
        super.handleInPacket(in);
        if (Game.isClient) {
            in.getData().set("pressedKeys", null);
            in.getData().set("cameraRotation", null);
        }
    }

    @Override
    protected void handleOutPacket(Packet out) {
        if (!Game.isClient && out.getType() != NetworkStream.BROADCAST) {
            out.network_destination = internal_id;
        }
    }

    @ClientSide
    public void drawHotbar(Screen screen) {

        int slotSize = 42;
        int gap = 6;

        int totalW = 9 * slotSize + 8 * gap;
        int startX = screen.width / 2 - totalW / 2;
        int y = screen.height - 70;

        screen.noStroke();
        screen.fill(0, 120);
        screen.rect(startX - 12, y - 10, totalW + 24, slotSize + 20, 10);

        for (int i = 0; i < 9; i++) {
            int x = startX + i * (slotSize + gap);

            boolean selected = (i == selectedSlot);

            screen.fill(selected ? 0xFF4F8EF7 : 0xFF1C2130);
            screen.stroke(selected ? 0xFF4F8EF7 : 0xFF252B3B);
            screen.strokeWeight(selected ? 2 : 1);
            screen.rect(x, y, slotSize, slotSize, 6);
            screen.noStroke();

            if (inventory[i] != null) {
                PImage icon = inventory[i].getIcon();

                int padding = 6;
                float iconSize = slotSize - padding * 2;

                screen.imageMode(PApplet.CENTER);
                screen.image(
                        icon,
                        x + slotSize / 2f,
                        y + slotSize / 2f,
                        iconSize,
                        iconSize);
                screen.imageMode(PApplet.CORNER);
            }

            screen.fill(0xFF7A8099);
            screen.textFont(screen.fontSans12);
            screen.textAlign(PApplet.CENTER, PApplet.BOTTOM);
            screen.text(String.valueOf(i + 1), x + slotSize / 2f, y + slotSize - 4);
        }
    }

    @Override
    public List<Voxel> createBody() {
        return VoxTranslator.fromModel("player");
    }

    @Override
    protected List<Voxel> createHitbox() {
        return VoxTranslator.fromModel("default");
    }

    @Override
    public boolean addToInventory(Item item) {
        for (int i = 0; i < inventory.length; i++) {
            if (inventory[i] == null) {
                inventory[i] = item;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean removeFromInventory(Item item) {
        for (int i = 0; i < inventory.length; i++) {
            if (inventory[i] == item) {
                inventory[i] = null;
                return true;
            }
        }
        return false;
    }

    @Override
    public void clearInventory(boolean drop) {
        for (int i = 0; i < inventory.length; i++) {
            if (inventory[i] != null) {
                inventory[i].drop(this);
            }
        }
    }

}
