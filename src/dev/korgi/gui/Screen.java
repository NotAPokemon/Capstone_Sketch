package dev.korgi.gui;

import dev.korgi.Game;
import dev.korgi.player.Player;
import dev.korgi.networking.NetworkStream;
import processing.core.PApplet;

import java.util.List;

public class Screen extends PApplet {

    private static Screen mInstance;

    public static Screen getInstance() {
        if (mInstance == null) {
            mInstance = new Screen();
        }
        return mInstance;
    }

    // ========================
    // Processing setup
    // ========================
    @Override
    public void settings() {
        size(900, 600);
    }

    @Override
    public void setup() {
        frameRate(60);
        textFont(createFont("Arial", 14));
    }

    // ========================
    // Main draw loop
    // ========================
    @Override
    public void draw() {
        background(20);

        if (Game.isInitialized()) {
            Game.loop();
        }

        drawHUD();
        drawPlayers();
        drawNetworkInfo();
    }

    // ========================
    // Heads-up display
    // ========================
    private void drawHUD() {
        fill(255);
        text("Mode: " + (Game.isClient ? "CLIENT" : "SERVER"), 20, 30);
        text("FPS: " + (int) frameRate, 20, 50);
    }

    // ========================
    // Player visualization
    // ========================
    private void drawPlayers() {
        List<Player> players = Game.getPlayers();
        if (players == null)
            return;

        int i = 0;
        for (Player p : players) {
            float x = 150 + (i * 60);
            float y = height / 2f;

            // Player circle
            fill(100, 200, 255);
            ellipse(x, y, 30, 30);

            // ID
            fill(255);
            text(p.getInternalId().substring(0, 6), x - 15, y + 30);

            i++;
        }
    }

    // ========================
    // Network diagnostics
    // ========================
    private void drawNetworkInfo() {
        int baseY = 100;

        fill(200);
        text("Network Debug", 20, baseY);
        text("----------------", 20, baseY + 15);

        text("Client packets queued: " + NetworkStream.clientPackets.size(), 20, baseY + 40);
        text("Server packets queued: " + NetworkStream.serverPackets.size(), 20, baseY + 60);
    }
}
