package dev.korgi.game;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import dev.korgi.game.physics.WorldEngine;
import dev.korgi.networking.NetworkStream;
import dev.korgi.player.Player;

public class Game {

    private static long lastTime;
    public static boolean isClient;
    private static boolean initialized = false;
    private static List<Player> players = new ArrayList<>();
    public static boolean canFly = false;
    public static final double g = 9.81;

    public static void init() throws IOException {
        lastTime = System.nanoTime();
        if (isClient) {
            NetworkStream.startClient("localhost", 6967);
        } else {
            NetworkStream.startServer(6967);
            WorldEngine.init();
        }
        initialized = true;
    }

    public static List<Player> getPlayers() {
        return players;
    }

    public static Player getClient() {
        if (isClient) {
            for (Player p : players) {
                if (p.internal_id.equals(NetworkStream.clientId)) {
                    return p;
                }
            }
        }
        return null;
    }

    public static void loop() {
        NetworkStream.update(!isClient);
        double dt = (System.nanoTime() - lastTime) / 1e9;
        for (Player p : players) {
            p.loop(dt, isClient);
        }

        lastTime = System.nanoTime();
    }

    public static void playerConnected(String internal_id) {
        List<Player> players = Game.getPlayers();
        for (Player p : players) {
            if (p.internal_id.equals(internal_id)) {
                p.connected = true;
                return;
            }
        }
        Player player = new Player();
        player.internal_id = internal_id;
        player.connected = true;
        players.add(player);
        player.addToWorld();
    }

    public static boolean isInitialized() {
        return initialized;
    }

}
