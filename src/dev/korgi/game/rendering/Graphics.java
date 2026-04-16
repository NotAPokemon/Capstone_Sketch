package dev.korgi.game.rendering;

import dev.korgi.game.physics.WorldEngine;
import dev.korgi.game.ui.Screen;
import dev.korgi.utils.ClientSide;

@ClientSide
public class Graphics {

    public static Camera camera = new Camera();
    private static boolean init = false;

    public static void display() {
        Screen screen = Screen.getInstance();
        int width = screen.width;
        int height = screen.height;

        boolean resizePixels = screen.pixels == null || screen.pixels.length != width * height;
        if (resizePixels) {
            screen.loadPixels();
        }

        if (resizePixels || !init) {
            NativeGPUKernal.resetSpecs(screen.pixels, width, height);
            init = true;
        }
        NativeGPUKernal.execute(WorldEngine.getWorld(), camera);
        screen.updatePixels();
    }
}
