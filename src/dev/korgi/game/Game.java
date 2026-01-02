package dev.korgi.game;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import dev.korgi.game.physics.WorldEngine;
import dev.korgi.game.rendering.Screen;
import dev.korgi.game.rendering.Graphics;
import dev.korgi.json.JSONObject;
import dev.korgi.math.Vector3;
import dev.korgi.networking.NetworkStream;
import dev.korgi.player.Player;

public class Game {

    private static long lastTime;
    public static boolean isClient;
    private static boolean initialized = false;
    private static List<Player> players = new ArrayList<>();
    public static boolean canFly = false;
    private static JSONObject config;

    private static void loadConfigDefaults() {
        if (config == null) {
            System.out.println("Warning no config.json found using default config");
            config = new JSONObject();
        }
        config.addString("ip", "localhost");
        config.addInt("port", 6967);
    }

    public static void init() throws IOException {

        config = JSONObject.fromResource("config");

        loadConfigDefaults();

        lastTime = System.nanoTime();
        if (isClient) {
            NetworkStream.startClient(config.getString("ip"), config.getInt("port"));
        } else {
            NetworkStream.startServer(config.getInt("port"));
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

    public static void loop(Screen screen) {
        networkStartLoop();
        if (Game.isClient) {
            screen.handleMouseMovement();
            WorldEngine.updateClient();
            Graphics.display();
            screen.drawOpenClientMenus();
        } else {
            WorldEngine.execute();
            screen.drawServerInfo();
        }
        networkEndLoop();

        screen.drawHUD();

    }

    public static void networkEndLoop() {
        for (Player p : players) {
            p.sendOut();
        }

        withClient((p) -> {
            if (p.pressedKeys.contains("LMB")) {
                p.pressedKeys.remove("LMB");
                p.pressedKeys.add("LMB_HOLD");
            }

            if (p.pressedKeys.contains("RMB")) {
                p.pressedKeys.remove("RMB");
                p.pressedKeys.add("RMB_HOLD");
            }
        });
    }

    public static void networkStartLoop() {
        NetworkStream.update(!isClient);
        double dt = (System.nanoTime() - lastTime) / 1e9;
        for (Player p : players) {
            p.loop(dt);
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

    public static void withClient(Consumer<Player> operation) {
        if (!Game.isClient)
            return;
        Player client = getClient();
        if (client != null) {
            operation.accept(client);
        }
    }

    public static void withClient(BiConsumer<Player, Vector3> operation) {
        Player client = getClient();
        if (client != null) {
            operation.accept(client, client.getPosition());
        }
    }

}
