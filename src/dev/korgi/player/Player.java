package dev.korgi.player;

import dev.korgi.json.JSONObject;
import dev.korgi.networking.NetworkStream;
import dev.korgi.networking.Packet;

public class Player {

    public String internal_id;
    public boolean connected;

    public void serverLoop(double dt) {
        if (!connected) {
            return;
        }
        // handle key press logic here

        JSONObject outData = new JSONObject(this);
        Packet outPacket = new Packet(internal_id, Packet.CLIENT, Packet.BROADCAST, outData);
        NetworkStream.sendPacket(outPacket);
    }

    public void clientLoop(double dt) {
        if (!connected || !NetworkStream.clientId.equals(internal_id)) {
            return;
        }

        JSONObject outData = new JSONObject(this);
        Packet outPacket = new Packet(internal_id, Packet.SERVER, Packet.INPUT_HANDLE_REQUEST, outData);
        NetworkStream.sendPacket(outPacket);

    }

    public String getInternalId() {
        return internal_id;
    }

}
