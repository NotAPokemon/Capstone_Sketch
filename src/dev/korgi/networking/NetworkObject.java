package dev.korgi.networking;

import java.util.function.Supplier;

import dev.korgi.game.Game;
import dev.korgi.json.JSONObject;

public abstract class NetworkObject {

    public String internal_id;
    private boolean canceledTickEnd = false;
    private Supplier<Boolean> cancelTick;

    public void loop(double dt) {
        Packet incomming_packet = NetworkStream.getPacket(internal_id, Game.isClient);
        if (incomming_packet != null) {
            handleInPacket(incomming_packet);
            incomming_packet.getData().fillObject(this);
        }
        if (cancelTick != null && cancelTick.get()) {
            return;
        }
        if (Game.isClient) {
            client(dt);
        } else {
            server(dt);
        }

        if (canceledTickEnd) {
            canceledTickEnd = false;
            return;
        }

    }

    protected void cancelTickEnd() {
        canceledTickEnd = true;
    }

    protected void setCancelProtocol(Supplier<Boolean> supplier) {
        this.cancelTick = supplier;
    }

    protected void handleOutPacket(Packet out) {

    }

    protected void handleInPacket(Packet in) {

    }

    public void sendOut() {
        JSONObject outData = new JSONObject(this);
        Packet outPacket = new Packet(internal_id, Game.isClient ? NetworkStream.SERVER : NetworkStream.CLIENT,
                Game.isClient ? NetworkStream.INPUT_HANDLE_REQUEST : NetworkStream.BROADCAST, outData);
        handleOutPacket(outPacket);
        NetworkStream.sendPacket(outPacket);
    }

    protected abstract void client(double dt);

    protected abstract void server(double dt);

}
