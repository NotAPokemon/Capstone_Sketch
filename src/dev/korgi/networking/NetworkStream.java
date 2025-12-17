package dev.korgi.networking;

import java.util.ArrayList;
import java.util.List;

public class NetworkStream {

    private static List<Packet> serverPackets = new ArrayList<>();
    private static List<Packet> clientPackets = new ArrayList<>();

    public static void sendPacket(Packet packet) {

    }

    public static Packet getPacket(String internal_id, boolean isClient) {
        List<Packet> array = isClient ? clientPackets : serverPackets;
        for (Packet p : array) {
            if (p.getInternalId().equals(internal_id)) {
                return p;
            }
        }
        return null;
    }

}
