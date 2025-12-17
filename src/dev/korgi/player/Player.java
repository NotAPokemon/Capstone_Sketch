package dev.korgi.player;

import dev.korgi.json.JSONObject;
import dev.korgi.networking.NetworkStream;
import dev.korgi.networking.Packet;

public class Player {

    private String internal_id;
    private boolean connected;

    public void serverLoop(double dt) {
        if (!connected) {
            return;
        }
        // handle key press logic here

        JSONObject outData = new JSONObject(this);
        Packet outPacket = new Packet(internal_id, 0, 0, outData);
        NetworkStream.sendPacket(outPacket);
    }

    public void clientLoop(double dt) {
        if (!connected) {
            return;
        }

        JSONObject outData = new JSONObject(this);
        Packet outPacket = new Packet(internal_id, 1, 0, outData);
        NetworkStream.sendPacket(outPacket);

    }

    public String getInternalId() {
        return internal_id;
    }

}
