package dev.korgi.player;

import java.util.ArrayList;
import java.util.List;

import dev.korgi.game.Game;
import dev.korgi.game.physics.Entity;
import dev.korgi.game.physics.WorldEngine;
import dev.korgi.game.rendering.Voxel;
import dev.korgi.json.JSONIgnore;
import dev.korgi.game.rendering.Graphics;
import dev.korgi.game.rendering.TextureAtlas;
import dev.korgi.math.Cooldown;
import dev.korgi.math.Vector3;
import dev.korgi.math.VectorConstants;
import dev.korgi.networking.NetworkStream;
import dev.korgi.networking.Packet;

public class Player extends Entity {

    public boolean connected;
    public List<String> pressedKeys = new ArrayList<>();
    private int speed = 3;
    private boolean onGround = false;

    @JSONIgnore
    private int selectedBlock = TextureAtlas.DUNGEON_BLOCK;

    public Player() {
        setCancelProtocol(() -> !connected);
        if (!Game.isClient) {
            Cooldown.createCooldown("m", 0.1);
            Cooldown.createCooldown("j", 0.1);
        }
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
        handlePhysics(dt);
    }

    private void handlePhysics(double dt) {
        if (!onGround && !Game.canFly)
            velocity.addTo(VectorConstants.DOWN.multiply(WorldEngine.g * dt));

        position.addTo(velocity.multiply(dt));
        WorldEngine.validatePosition(this);

        if (position.y < -80) {
            position.copyFrom(WorldEngine.getWorld().getFlat().get(0).position.add(VectorConstants.HALF)
                    .addTo(VectorConstants.UP.multiply(2)));
        }
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

        Vector3 originalPos = position.copy();

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

        if (!originalPos.equals(position)) {
            checkGravity();
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

    public void checkGravity() {
        onGround = WorldEngine.voxelAt(position.add(VectorConstants.DOWN)) != null;
    }

    @Override
    protected void handleInPacket(Packet in) {
        if (Game.isClient) {
            in.getData().set("pressedKeys", null);
            in.getData().set("cameraRotation", null);
        } else {
            in.getData().set("position", null);
            in.getData().set("velocity", null);
            in.getData().set("onGround", onGround);
        }
    }

    @Override
    protected void handleOutPacket(Packet out) {
        if (!Game.isClient && out.getType() != NetworkStream.BROADCAST) {
            out.network_destination = internal_id;
        }
    }

    @Override
    public void onCollide(Voxel other, Vector3 bodyVoxelWorldPos, Vector3 penetration) {
        if (penetration != null && penetration.y > 0 && other.position.y < position.y) {
            onGround = true;
            velocity.y = 0;
        }
    }

    @Override
    public List<Voxel> createBody() {
        return fromModel("entites/player");
    }

}
