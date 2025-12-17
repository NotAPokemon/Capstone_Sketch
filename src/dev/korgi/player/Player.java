package dev.korgi.player;

import dev.korgi.networking.NetworkObject;
import dev.korgi.networking.NetworkStream;

public class Player extends NetworkObject {

    public boolean connected;

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

}
