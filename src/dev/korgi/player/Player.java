package dev.korgi.player;

import java.util.ArrayList;
import java.util.List;

import dev.korgi.game.Game;
import dev.korgi.game.physics.Entity;
import dev.korgi.game.rendering.Voxel;
import dev.korgi.game.rendering.WorldSpace;
import dev.korgi.math.Vector3;
import dev.korgi.math.VectorConstants;
import dev.korgi.networking.NetworkStream;
import dev.korgi.networking.Packet;

public class Player extends Entity {

    public boolean connected;
    public List<String> pressedKeys = new ArrayList<>();
    private int speed = 10;

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
        double yaw = rotation.y;

        Vector3 forward = new Vector3(Math.sin(yaw), 0, Math.cos(yaw)).normalizeHere().multiplyBy(speed * dt);

        Vector3 right = new Vector3(
                Math.sin(yaw - Math.PI / 2),
                0,
                Math.cos(yaw - Math.PI / 2)).normalizeHere().multiplyBy(speed * dt);

        // --- Movement
        if (pressedKeys.contains("w"))
            position.addTo(forward);
        if (pressedKeys.contains("s"))
            position.subtractFrom(forward);
        if (pressedKeys.contains("a"))
            position.subtractFrom(right);
        if (pressedKeys.contains("d"))
            position.addTo(right);
        if (pressedKeys.contains(" ") && Game.canFly)
            position.addTo(new Vector3(0, speed * dt, 0));
        if (pressedKeys.contains("SHIFT") && Game.canFly)
            position.subtractFrom(new Vector3(0, speed * dt, 0));

        position.addTo(velocity);
    }

    @Override
    protected void handleInPacket(Packet in) {
        if (Game.isClient) {
            in.getData().set("pressedKeys", null);
            in.getData().set("cameraRotation", null);
        } else {
            in.getData().set("position", null);
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
        List<Voxel> voxels = new ArrayList<>();
        Voxel v = new Voxel(0, 0, 0, VectorConstants.DARK_GREEN);
        Voxel v2 = new Voxel(0, 1, 0, VectorConstants.DARK_GREEN);
        voxels.add(v);
        voxels.add(v2);
        return voxels;
    }

}
