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
import dev.korgi.game.ui.Inventory;
import dev.korgi.json.JSONIgnore;
import dev.korgi.math.Vector3;
import dev.korgi.math.VectorConstants;
import dev.korgi.networking.NetworkStream;
import dev.korgi.networking.Packet;
import dev.korgi.utils.ClientSide;
import dev.korgi.utils.ServerSide;
import dev.korgi.utils.VoxTranslator;

public class Player extends Entity implements StorageEntity {

    public boolean connected;

    @ClientSide
    public List<String> pressedKeys = new ArrayList<>();

    @ServerSide
    private boolean fallProtected;

    private int speed;

    @ServerSide
    private Inventory inventory = new Inventory(9);

    @JSONIgnore
    private int selectedBlock = TextureAtlas.DUNGEON_BLOCK;

    @JSONIgnore
    private Voxel last;

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
        inventory.show();
        overlay();

    }

    private void overlay() {

        if (last != null) {
            last.getMaterial().setOverlayLocation(-1);
        }

        withHit((hit) -> {
            Vector3 pos = hit.getVoxelPos();
            Voxel voxel = WorldEngine.voxelAt(pos);
            voxel.getMaterial().setOverlayLocation(TextureAtlas.OUTLINE);
            last = voxel;
        }, 5);
    }

    @Override
    @ServerSide
    protected void server(double dt) {
        Vector3 posSnapshot = position.copy();
        handleKeyPresses(dt);
        super.server(dt);

        if (fallProtected && !onGround) {
            Vector3 dist = posSnapshot.subtract(position).multiplyBy(1, 0, 1);
            position.copyFrom(posSnapshot);
            position.addTo(dist.multiplyBy(0.01));
            velocity.copyFrom(VectorConstants.ZERO);
            position.y = posSnapshot.y;
            checkGravity();
        }
    }

    @Override
    @ServerSide
    protected void handlePhysics(double dt) {
        gravityEnabled = !Game.canFly;
        super.handlePhysics(dt);
    }

    @ServerSide
    private void handleKeyPresses(double dt) {
        fallProtected = false;
        speed = 5;

        checkKey("CTRL", () -> speed = 10);

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
            withHeldItem((itm) -> {
                itm.rmb();
            });
        }, true);
        checkKey("LMB", () -> {
            withHit((hit) -> {
                Vector3 breakPos = hit.getVoxelPos();
                WorldEngine.removeVoxel(WorldEngine.voxelAt(breakPos));
            }, 5);
            withHeldItem((itm) -> {
                itm.lmb();
            });
        }, true);

        checkKey(" ", () -> {
            if (!Game.canFly && onGround) {
                velocity.y = 7;
                onGround = false;
            } else if (Game.canFly) {
                position.addTo(amt);
            }
        });

        checkKey("SHIFT", () -> {
            if (Game.canFly) {
                position.subtractFrom(amt);
            }
            fallProtected = true;
        });

        checkKey("SHIFT", () -> {
            fallProtected = true;
        }, true);

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
        inventory.select(val);
    }

    public void withHeldItem(Consumer<Item> handler) {
        Item heldItem = inventory.getSelected();
        if (heldItem != null) {
            handler.accept(heldItem);
        }
    }

    private void checkKey(String key, Runnable handler) {
        if (pressedKeys.contains(key) || pressedKeys.contains(key + "_HOLD")) {
            Vector3 pos = position.copy();
            handler.run();
            if (!pos.equals(position)) {
                WorldEngine.validatePosition(this);
            }
        }
    }

    private void checkKey(String key, Runnable handler, boolean onlyPress) {
        if (pressedKeys.contains(key) || (!onlyPress && pressedKeys.contains(key + "_HOLD"))) {
            Vector3 pos = position.copy();
            handler.run();
            if (!pos.equals(position)) {
                WorldEngine.validatePosition(this);
            }
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
        return inventory.addToInventory(item);
    }

    @Override
    public boolean removeFromInventory(Item item) {
        return inventory.addToInventory(item);
    }

    @Override
    public void clearInventory(boolean drop) {
        for (int i = 0; i < inventory.totalCapacity(); i++) {
            if (inventory.get(i) != null) {
                inventory.get(i).drop(this);
            }
        }
    }

    @Override
    public int inventorySize() {
        return inventory.itemCount();
    }

}
