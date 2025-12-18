package dev.korgi.networking;

import java.util.function.Supplier;

import dev.korgi.json.JSONObject;

public abstract class NetworkObject {

    public String internal_id;
    private boolean canceledTickEnd = false;
    private Supplier<Boolean> cancelTick;

    public void loop(double dt, boolean isClient) {
        Packet incomming_packet = NetworkStream.getPacket(internal_id, isClient);
        if (incomming_packet != null) {
            handleInPacket(incomming_packet);
            incomming_packet.getData().fillObject(this);
        }
        if (cancelTick != null && cancelTick.get()) {
            return;
        }
        if (isClient) {
            client(dt);
        } else {
            server(dt);
        }

        if (canceledTickEnd) {
            canceledTickEnd = false;
            return;
        }

        JSONObject outData = new JSONObject(this);
        Packet outPacket = new Packet(internal_id, isClient ? NetworkStream.SERVER : NetworkStream.CLIENT,
                isClient ? NetworkStream.INPUT_HANDLE_REQUEST : NetworkStream.BROADCAST, outData);
        handleOutPacket(outPacket);
        NetworkStream.sendPacket(outPacket);

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

    protected abstract void client(double dt);

    protected abstract void server(double dt);

}
