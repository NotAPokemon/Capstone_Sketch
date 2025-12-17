package dev.korgi.networking;

import dev.korgi.json.JSONObject;

public class Packet {

    public static final int CLIENT = 0;
    public static final int SERVER = 1;

    public static final int INPUT_HANDLE_REQUEST = 0;
    public static final int BROADCAST = -1;

    private String internal_id;
    private int destination;
    private int type;
    private JSONObject data;
    public String network_destination = null;

    public Packet(String internal_id, int destination, int type, JSONObject data) {
        this.internal_id = internal_id;
        this.destination = destination;
        this.type = type;
        this.data = data;
    }

    public int getDestination() {
        return destination;
    }

    public String getInternalId() {
        return internal_id;
    }

    public int getType() {
        return type;
    }

    public JSONObject getData() {
        return data;
    }

}
