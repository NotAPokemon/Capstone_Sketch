package dev.korgi.gui.rendering;

import java.util.ArrayList;
import java.util.List;

import com.aparapi.Range;

import dev.korgi.gui.Screen;
import dev.korgi.math.Vector3;

public class WorldSpace {

    private static List<Voxel> objects = new ArrayList<>();
    public static Camera camera = new Camera();
    public static final double VOXEL_SIZE = 1;
    public static GridRaytraceKernel kernel;

    public static void init() {
        // example voxels
        Voxel v1 = new Voxel(0, 0, 20, 1, 0, 0, 1);
        objects.add(v1);

        Voxel v2 = new Voxel(5, 5, 30, 0, 1, 0, 1);
        objects.add(v2);

        camera.position = new Vector3(0, 0, 0);
        camera.rotation = new Vector3(0, 0, 0);
        camera.fov = 50;
    }

    public static void execute() {
        Screen screen = Screen.getInstance();
        int width = screen.width;
        int height = screen.height;

        // --- Check if pixels array needs resizing
        boolean resizePixels = screen.pixels == null || screen.pixels.length != width * height;
        if (resizePixels) {
            screen.loadPixels(); // reload pixel buffer
        }

        // --- Flatten voxel data
        int voxelCount = objects.size();
        float[] vx = new float[voxelCount];
        float[] vy = new float[voxelCount];
        float[] vz = new float[voxelCount];
        float[] size = new float[voxelCount];
        int[] vcolor = new int[voxelCount];

        for (int i = 0; i < voxelCount; i++) {
            Voxel v = objects.get(i);
            vx[i] = (float) v.position.x;
            vy[i] = (float) v.position.y;
            vz[i] = (float) v.position.z;
            size[i] = (float) VOXEL_SIZE;

            int r = (int) (v.color.x * 255);
            int g = (int) (v.color.y * 255);
            int b = (int) (v.color.z * 255);
            int a = (int) (v.color.w * 255);

            vcolor[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }

        // --- Recreate kernel if needed
        if (kernel == null || resizePixels) {
            kernel = new GridRaytraceKernel(screen.pixels, width, height);
        }

        // --- Camera parameters
        kernel.camX = (float) camera.position.x;
        kernel.camY = (float) camera.position.y;
        kernel.camZ = (float) camera.position.z;
        kernel.fov = (float) camera.fov;

        // --- Precompute camera axes
        float cosX = (float) Math.cos(camera.rotation.x);
        float sinX = (float) Math.sin(camera.rotation.x);
        float cosY = (float) Math.cos(camera.rotation.y);
        float sinY = (float) Math.sin(camera.rotation.y);

        // Forward
        kernel.forwardX = cosX * sinY;
        kernel.forwardY = sinX;
        kernel.forwardZ = cosX * cosY;

        // Right (yaw only, XZ plane)
        kernel.rightX = (float) Math.sin(camera.rotation.y - Math.PI / 2);
        kernel.rightY = 0f;
        kernel.rightZ = (float) Math.cos(camera.rotation.y - Math.PI / 2);

        // Up = cross(right, forward)
        kernel.upX = kernel.rightY * kernel.forwardZ - kernel.rightZ * kernel.forwardY;
        kernel.upY = kernel.rightZ * kernel.forwardX - kernel.rightX * kernel.forwardZ;
        kernel.upZ = kernel.rightX * kernel.forwardY - kernel.rightY * kernel.forwardX;

        kernel.tanFov = (float) Math.tan((camera.fov * 0.5) * (Math.PI / 180));

        // --- Voxel data
        kernel.vx = vx;
        kernel.vy = vy;
        kernel.vz = vz;
        kernel.size = size;
        kernel.vcolor = vcolor;
        kernel.voxelCount = voxelCount;

        // --- Execute kernel
        long time = System.nanoTime();
        kernel.execute(Range.create(screen.pixels.length));
        if ((System.nanoTime() - time) / 1e9 > 0.1) {
            System.out.println("Warning Kernal Latancy High: " + (System.nanoTime() - time) / 1e9);
        }

        // --- Push pixels to screen
        screen.updatePixels();
    }

}
