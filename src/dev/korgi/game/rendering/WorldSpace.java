package dev.korgi.game.rendering;

import dev.korgi.game.physics.WorldEngine;

public class WorldSpace {

    public static Camera camera = new Camera();
    private static boolean init = false;

    public static void execute() {
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

        long time = System.nanoTime();
        NativeGPUKernal.execute(WorldEngine.getWorld().voxels, camera);
        if ((System.nanoTime() - time) / 1e9 > 0.05) {
            System.out.println("Warning Kernal Latancy High: " + (System.nanoTime() - time) / 1e9);
        }

        screen.updatePixels();
    }
}
