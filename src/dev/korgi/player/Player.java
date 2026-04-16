package dev.korgi.player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import dev.korgi.game.Game;
import dev.korgi.game.entites.Entity;
import dev.korgi.game.entites.StorageEntity;
import dev.korgi.game.items.Item;
import dev.korgi.game.physics.WorldEngine;
import dev.korgi.game.rendering.Graphics;
import dev.korgi.game.rendering.TextureAtlas;
import dev.korgi.game.rendering.Voxel;
import dev.korgi.game.ui.Screen;
import dev.korgi.game.ui.builder.CanvasBuilder;
import dev.korgi.game.ui.builder.DrawMode;
import dev.korgi.game.ui.builder.UIBuilder;
import dev.korgi.game.ui.elements.Canvas;
import dev.korgi.game.ui.elements.Image;
import dev.korgi.game.ui.elements.Text;
import dev.korgi.game.ui.elements.UI;
import dev.korgi.json.JSONIgnore;
import dev.korgi.json.JSONObject;
import dev.korgi.math.Vector3;
import dev.korgi.math.VectorConstants;
import dev.korgi.networking.NetworkStream;
import dev.korgi.networking.Packet;
import dev.korgi.utils.ClientSide;
import dev.korgi.utils.InstallConstants;
import dev.korgi.utils.VoxTranslator;
import processing.core.PApplet;

public class Player extends Entity implements StorageEntity {

    public boolean connected;
    public List<String> pressedKeys = new ArrayList<>();
    private int speed = 4;
    private int selectedSlot = 0;
    private Item[] inventory = new Item[9];

    @JSONIgnore
    private int selectedBlock = TextureAtlas.DUNGEON_BLOCK;

    @JSONIgnore
    @ClientSide
    private static UI hotbar;

    static {
        if (Game.isClient) {
            Screen screen = Screen.getInstance();
            int slotSize = 42;
            int gap = 6;

            int totalW = 9 * slotSize + 8 * gap;
            int startX = screen.width / 2 - totalW / 2;
            int y = screen.height - 70;

            int padding = 6;
            float iconSize = slotSize - padding * 2;

            UIBuilder builder = UIBuilder.create("hotbar")
                    .drawMode(DrawMode.ABSOLUTE);

            CanvasBuilder<UIBuilder> canvasBuilder = builder.canvas(new Canvas())
                    .bg(0x78000000)
                    .position(startX - 12, y - 10)
                    .size(totalW + 24, slotSize + 20)
                    .borderRadius(10);

            for (int i = 0; i < 9; i++) {
                int x = startX + i * (slotSize + gap);

                canvasBuilder = canvasBuilder
                        .canvas(new Canvas("slot" + i))
                        .bg(0xFF1C2130)
                        .borderColor(0xFF252B3B)
                        .borderSize(1)
                        .borderRadius(6)
                        .position(x, y)
                        .size(slotSize, slotSize)

                        .image(new Image("img" + i))
                        .imgMode(PApplet.CENTER)
                        .size(iconSize, iconSize)
                        .position(x + slotSize / 2f, y + slotSize / 2f)
                        .backToParent()

                        .text(new Text("num" + i))
                        .color(0xFF7A8099)
                        .font(screen.fontSans12)
                        .align(PApplet.CENTER, PApplet.BOTTOM)
                        .position(x + slotSize / 2f, y + slotSize - 4)
                        .setText("" + (i + 1))
                        .backToParent()

                        .backToParent();
            }

            builder = canvasBuilder.backToParent();

            hotbar = builder.build();
        }
    }

    public Player() {
        setCancelProtocol(() -> !connected);
        scale(0.125f);
        scaleHitbox(-1);
        hitboxOffset(VectorConstants.FORWARD.multiply(VectorConstants.HALF));
    }

    @Override
    @ClientSide
    protected void client(double dt) {
        if (!internal_id.equals(NetworkStream.clientId)) {
            cancelTickEnd();
        }

        rotation = Graphics.camera.rotation;
        Graphics.camera.position = position.add(VectorConstants.HALF);
        if (!hotbar.isOpen()) {
            hotbar.open();
        }
        ensureStyle();
    }

    @ClientSide
    private void ensureStyle() {
        JSONObject rootStyle = hotbar.getStyle();
        for (int i = 0; i < 9; i++) {
            JSONObject localStyle = rootStyle.getJSONObject("slot" + i);
            boolean selected = (i == selectedSlot);
            if (selected) {
                localStyle.set("bg", 0xFF4F8EF7);
                localStyle.set("borderColor", 0xFF4F8EF7);
                localStyle.set("borderSize", 2);
            } else {
                localStyle.set("bg", 0xFF1C2130);
                localStyle.set("borderColor", 0xFF252B3B);
                localStyle.set("borderSize", 1);
            }
            Image img = (Image) hotbar.findElement("img" + i, true);
            if (inventory[i] == null) {
                img.setImg(null);
            } else {
                img.setImg(inventory[i].getIcon());
            }
        }
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
            if (InstallConstants.dev) {
                return;
            }
            withHeldItem((itm) -> {
                itm.rmb();
            });
        }, true);
        checkKey("LMB", () -> {
            withHit((hit) -> {
                Vector3 breakPos = hit.getVoxelPos();
                WorldEngine.removeVoxel(WorldEngine.voxelAt(breakPos));
            }, 5);
            if (InstallConstants.dev) {
                return;
            }
            withHeldItem((itm) -> {
                itm.lmb();
            });
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
            withHeldItem((itm) -> {
                itm.drop(this);
            });
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

    public void withHeldItem(Consumer<Item> handler) {
        if (inventory[selectedSlot] != null) {
            handler.accept(inventory[selectedSlot]);
        }
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
