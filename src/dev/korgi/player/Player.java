package dev.korgi.player;

import java.util.ArrayList;
import java.util.List;

import dev.korgi.Game;
import dev.korgi.gui.rendering.WorldSpace;
import dev.korgi.math.Vector3;
import dev.korgi.networking.NetworkObject;
import dev.korgi.networking.NetworkStream;
import dev.korgi.networking.Packet;

public class Player extends NetworkObject {

    public boolean connected;
    public List<String> pressedKeys = new ArrayList<>();
    private Vector3 cameraRotation = new Vector3();
    private Vector3 position = new Vector3();
    private int speed = 10;

    public Player() {
        setCancelProtocol(() -> !connected);
    }

    @Override
    protected void client(double dt) {
        if (!internal_id.equals(NetworkStream.clientId)) {
            cancelTickEnd();
        }
        cameraRotation = WorldSpace.camera.rotation;
        WorldSpace.camera.position = position;
    }

    @Override
    protected void server(double dt) {
        double yaw = cameraRotation.y;

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

}
