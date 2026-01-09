package dev.korgi.player;

import java.util.ArrayList;
import java.util.List;

import dev.korgi.game.Game;
import dev.korgi.game.physics.Entity;
import dev.korgi.game.physics.WorldEngine;
import dev.korgi.game.rendering.Voxel;
import dev.korgi.game.rendering.Graphics;
import dev.korgi.game.rendering.TextureAtlas;
import dev.korgi.math.Vector3;
import dev.korgi.math.VectorConstants;
import dev.korgi.networking.NetworkStream;
import dev.korgi.networking.Packet;

public class Player extends Entity {

    public boolean connected;
    public List<String> pressedKeys = new ArrayList<>();
    private int speed = 3;
    private boolean onGround = false;

    public Player() {
        setCancelProtocol(() -> !connected);
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
            position.copyFrom(WorldEngine.getWorld().voxels.get(0).position.add(VectorConstants.HALF)
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
        checkKey("j", () -> Game.canFly = !Game.canFly);
        checkKey("RMB", () -> {
            withHit((hit) -> {
                Vector3 newPos = hit.getFaceWithOffset();
                if (WorldEngine.canPlaceVoxel(newPos)) {
                    WorldEngine.addVoxelWithTexture(newPos, TextureAtlas.TEST_BLOCK);
                }
            }, 5);
        });
        checkKey("LMB", () -> {
            withHit((hit) -> {
                Vector3 breakPos = hit.getVoxelPos();
                WorldEngine.removeVoxel(WorldEngine.voxelAt(breakPos));
            }, 5);
        });
        checkKey(" ", Game.canFly, () -> position.addTo(amt));
        checkKey(" ", !Game.canFly && onGround, () -> {
            velocity.addTo(VectorConstants.UP.multiply(5));
            onGround = false;
        });
        checkKey("SHIFT", Game.canFly, () -> position.subtractFrom(amt));

        if (!originalPos.equals(position)) {
            checkGravity();
        }
    }

    private void checkKey(String key, Runnable handler) {
        if (pressedKeys.contains(key)) {
            pressedKeys.remove(key);
            Vector3 pos = position.copy();
            handler.run();
            if (!pos.equals(position)) {
                WorldEngine.validatePosition(this);
            }
        }
    }

    private void checkKey(String key, boolean otherCheck, Runnable handler) {
        if (pressedKeys.contains(key) && otherCheck) {
            pressedKeys.remove(key);
            Vector3 pos = position.copy();
            handler.run();
            if (!pos.equals(position)) {
                Vector3 delta = position.subtract(pos).multiplyBy(0.5);
                position.subtractFrom(delta);
                checkGravity();
                position.addTo(delta);
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
        if (penetration != null && penetration.y > 0 && bodyVoxelWorldPos.y < position.y) {
            onGround = true;
            velocity.y = 0;
        }

    }

    @Override
    public List<Voxel> createBody() {
        List<Voxel> voxels = new ArrayList<>();
        Voxel v = new Voxel(VectorConstants.DARK_GREEN);
        Voxel v2 = new Voxel(VectorConstants.DOWN, VectorConstants.DARK_GREEN);
        voxels.add(v);
        voxels.add(v2);
        return voxels;
    }

}
