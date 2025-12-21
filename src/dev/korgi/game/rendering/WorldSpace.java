package dev.korgi.game.rendering;

import java.util.ArrayList;
import java.util.List;

import com.aparapi.Range;

import dev.korgi.math.Vector3;

public class WorldSpace {

    private static List<Voxel> objects = new ArrayList<>();
    public static Camera camera = new Camera();
    public static GridRaytraceKernel kernel;

    public static void init() {
        // example voxels
        Voxel v1 = new Voxel(0, 0, 20, 1, 0, 0, 1);
        objects.add(v1);

        Voxel v2 = new Voxel(5, 5, 30, 0, 1, 0, 1);
        objects.add(v2);

        Voxel v3 = new Voxel(0, 5, 6, 0, 1, 0, 1);
        objects.add(v3);

        Voxel v4 = new Voxel(0, 5, 7, 0, 1, 0, 1);
        v4.getMaterial().setOpacity(0.5);
        objects.add(v4);

        camera.position = new Vector3(0, 0, 0);
        camera.rotation = new Vector3(0, 0, 0);
        camera.fov = 50;
    }

    public static void addVoxel(Voxel v) {
        objects.add(v);
    }

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
        kernel.precompute(objects, camera);
        kernel.execute(Range.create(screen.pixels.length));
        if ((System.nanoTime() - time) / 1e9 > 0.05) {
            System.out.println("Warning Kernal Latancy High: " + (System.nanoTime() - time) / 1e9);
        }

        screen.updatePixels();
    }

    public static List<Voxel> getObjects() {
        return objects;
    }

}
