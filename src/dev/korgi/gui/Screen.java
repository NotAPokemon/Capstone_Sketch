package dev.korgi.gui;

import dev.korgi.Game;
import dev.korgi.gui.rendering.WorldSpace;
import dev.korgi.player.Player;
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

    @Override
    public void settings() {
        size(900, 600);
    }

    @Override
    public void setup() {
        frameRate(60);
        textFont(createFont("Arial", 14));
    }

    @Override
    public void keyPressed() {
        if (key == 'w') {
            WorldSpace.camera.position.z += 1;
        } else if (key == 's') {
            WorldSpace.camera.position.z -= 1;
        } else if (key == 'a') {
            WorldSpace.camera.position.x -= 1;
        } else if (key == 'd') {
            WorldSpace.camera.position.x += 1;
        } else if (keyCode == SHIFT) {
            WorldSpace.camera.position.y -= 1;
        } else if (key == ' ') {
            WorldSpace.camera.position.y += 1;
        }
    }

    @Override
    public void draw() {
        if (Game.isInitialized()) {
            Game.loop();
        }

        WorldSpace.execute();
        drawPlayers();
        drawHUD();
    }

    private void drawHUD() {
        fill(255);
        text("FPS: " + (int) frameRate, 20, 50);
    }

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
            text(p.internal_id.substring(0, 6), x - 15, y + 30);

            i++;
        }
    }
}
