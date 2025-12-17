package dev.korgi;

import java.util.List;

import dev.korgi.networking.NetworkStream;
import dev.korgi.networking.Packet;
import dev.korgi.player.Player;

public class Game {

    private static long lastTime;
    private static boolean isClient;
    private static List<Player> players;

    public static void saveGame() {

    }

    public static void init() {
        lastTime = System.nanoTime();
    }

    public static void loop() {
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
            incomming_packet.getData().fillObject(p);
            p.serverLoop(dt);
        }

    }

    private static void clientLoop(double dt) {
        for (Player p : players) {
            Packet incomming_packet = NetworkStream.getPacket(p.getInternalId(), isClient);
            incomming_packet.getData().fillObject(p);
            p.clientLoop(dt);
        }
    }

}
