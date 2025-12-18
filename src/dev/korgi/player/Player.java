package dev.korgi.player;

import java.util.ArrayList;
import java.util.List;

import dev.korgi.Game;
import dev.korgi.networking.NetworkObject;
import dev.korgi.networking.NetworkStream;
import dev.korgi.networking.Packet;

public class Player extends NetworkObject {

    public boolean connected;
    public List<String> pressedKeys = new ArrayList<>();

    public Player() {
        setCancelProtocol(() -> !connected);
    }

    @Override
    protected void client(double dt) {
        if (!internal_id.equals(NetworkStream.clientId)) {
            cancelTickEnd();
        }

    }

    @Override
    protected void server(double dt) {

    }

    @Override
    protected void handleInPacket(Packet in) {
        if (Game.isClient) {
            in.getData().set("pressedKeys", null);
        }
    }

    @Override
    protected void handleOutPacket(Packet out) {
        if (!Game.isClient && out.getType() != NetworkStream.BROADCAST) {
            out.network_destination = internal_id;
        }
    }

}
