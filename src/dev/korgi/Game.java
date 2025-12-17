package dev.korgi;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import dev.korgi.networking.NetworkStream;
import dev.korgi.networking.Packet;
import dev.korgi.player.Player;

public class Game {

    private static long lastTime;
    public static boolean isClient;
    private static boolean initialized = false;
    private static List<Player> players = new ArrayList<>();

    public static void init() throws IOException {
        lastTime = System.nanoTime();
        if (isClient) {
            NetworkStream.startClient("localhost", 6967);
        } else {
            NetworkStream.startServer(6967);
        }
        initialized = true;
    }

    public static List<Player> getPlayers() {
        return players;
    }

    public static void loop() {
        NetworkStream.update(!isClient);
        double dt = (System.nanoTime() - lastTime) / 1e9;
        if (isClient) {
            clientLoop(dt);
        } else {
            serverLoop(dt);
        }
        lastTime = System.nanoTime();
    }

    private static void serverLoop(double dt) {
        for (Player p : players) {
            Packet incomming_packet = NetworkStream.getPacket(p.getInternalId(), isClient);
            if (incomming_packet != null) {
                incomming_packet.getData().fillObject(p);
            }
            p.serverLoop(dt);
        }

    }

    private static void clientLoop(double dt) {
        for (Player p : players) {
            Packet incomming_packet = NetworkStream.getPacket(p.getInternalId(), isClient);
            if (incomming_packet != null) {
                incomming_packet.getData().fillObject(p);
            }
            p.clientLoop(dt);
        }
    }

    public static boolean isInitialized() {
        return initialized;
    }

}
