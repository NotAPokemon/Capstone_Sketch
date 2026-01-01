package dev.korgi.player;

import java.util.ArrayList;
import java.util.List;

import dev.korgi.game.Game;
import dev.korgi.game.physics.Entity;
import dev.korgi.game.physics.WorldEngine;
import dev.korgi.game.rendering.Voxel;
import dev.korgi.game.rendering.WorldSpace;
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

        rotation = WorldSpace.camera.rotation;
        WorldSpace.camera.position = position;
    }

    @Override
    protected void server(double dt) {
        handleKeyPresses(dt);
        handlePhysics(dt);
    }

    private void handlePhysics(double dt) {
        if (!onGround && !Game.canFly)
            velocity.subtractFrom(0, WorldEngine.g * dt, 0);

        position.addTo(velocity.multiply(dt));
    }

    private void handleKeyPresses(double dt) {
        speed = 3;

        checkKey("CTRL", () -> speed = 5);

        double yaw = rotation.y;

        Vector3 forward = new Vector3(Math.sin(yaw), 0, Math.cos(yaw)).normalizeHere().multiplyBy(speed * dt);

        Vector3 right = new Vector3(
                Math.sin(yaw - Math.PI / 2),
                0,
                Math.cos(yaw - Math.PI / 2)).normalizeHere().multiplyBy(speed * dt);

        Vector3 originalPos = position.copy();

        checkKey("w", () -> position.addTo(forward));
        checkKey("s", () -> position.subtractFrom(forward));
        checkKey("a", () -> position.subtractFrom(right));
        checkKey("d", () -> position.addTo(right));
        checkKey("j", () -> Game.canFly = !Game.canFly);
        checkKey("RMB", () -> WorldEngine.addRandomVoxel(position.add(0, -1, 0)));
        checkKey(" ", Game.canFly, () -> position.addTo(0, speed * dt, 0));
        checkKey(" ", !Game.canFly && onGround, () -> {
            velocity.addTo(0, 5, 0);
            onGround = false;
        });
        checkKey("SHIFT", Game.canFly, () -> position.subtractFrom(0, speed * dt, 0));

        if (!originalPos.equals(position)) {
            onGround = WorldEngine.voxelAt(position.subtract(0, 1, 0)) != null;
        }
    }

    private void checkKey(String key, Runnable handler) {
        if (pressedKeys.contains(key)) {
            pressedKeys.remove(key);
            handler.run();
        }
    }

    private void checkKey(String key, boolean otherCheck, Runnable handler) {
        if (pressedKeys.contains(key) && otherCheck) {
            pressedKeys.remove(key);
            handler.run();
        }
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
        Voxel v2 = new Voxel(0, -1, 0, VectorConstants.DARK_GREEN);
        voxels.add(v);
        voxels.add(v2);
        return voxels;
    }

}
