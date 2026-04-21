package dev.korgi.game.rendering;

import dev.korgi.game.physics.WorldEngine;
import dev.korgi.game.ui.Screen;
import dev.korgi.math.Vector3;
import dev.korgi.utils.ClientSide;

@ClientSide
public class Graphics {

    public static Camera camera = new Camera();
    private static boolean init = false;

    private static boolean isWarping = false;

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
        if (isWarping) {
            distort(screen.pixels, width, height);
        }
        screen.updatePixels();
    }

    public static final Vector3 distortionModifer = new Vector3(6, 6, 6);
    public static final Vector3 distortionBase = new Vector3(Math.PI * 2.3, 6, 6);

    private static void distort(int[] pixels, int W, int H) {
        final double TAU = distortionBase.x + distortionModifer.x * Math.random();
        final double AMP = distortionBase.y + distortionModifer.y * Math.random();
        final double FRQ = distortionBase.z + distortionModifer.z * Math.random();

        final int[] src = pixels.clone();

        for (int y = 0; y < H; y++) {
            double ny = (double) y / H;
            for (int x = 0; x < W; x++) {
                double nx = (double) x / W;

                int dx = (int) Math.round(AMP * (Math.sin(ny * TAU * FRQ) * 0.6 +
                        Math.cos(nx * TAU * FRQ * 0.7 + ny * TAU * 1.3) * 0.4));
                int dy = (int) Math.round(AMP * (Math.sin(nx * TAU * FRQ) * 0.4 +
                        Math.cos(ny * TAU * FRQ * 0.8 + nx * TAU * 1.7) * 0.6));

                int sx = ((x + dx) % W + W) % W;
                int sy = ((y + dy) % H + H) % H;

                pixels[y * W + x] = src[sy * W + sx];
            }
        }
    }

    public static void setWarping(boolean isWarping) {
        Graphics.isWarping = isWarping;
    }

}
