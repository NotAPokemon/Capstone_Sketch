package dev.korgi.game.rendering;

import com.aparapi.Range;

import dev.korgi.game.physics.WorldEngine;

public class WorldSpace {

    public static Camera camera = new Camera();
    public static GridRaytraceKernel kernel;

    public static void execute() {
        Screen screen = Screen.getInstance();
        int width = screen.width;
        int height = screen.height;

        boolean resizePixels = screen.pixels == null || screen.pixels.length != width * height;
        if (resizePixels) {
            screen.loadPixels();
        }

        if (kernel == null || resizePixels) {
            kernel = new GridRaytraceKernel(screen.pixels, width, height);
        }

        long time = System.nanoTime();
        kernel.precompute(WorldEngine.getVoxels(), camera);
        kernel.execute(Range.create(screen.pixels.length));
        if ((System.nanoTime() - time) / 1e9 > 0.05) {
            System.out.println("Warning Kernal Latancy High: " + (System.nanoTime() - time) / 1e9);
        }

        screen.updatePixels();
    }
}
