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
        camera.fov = 500;
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

            vcolor[i] = (a << 24) |
                    (r << 16) |
                    (g << 8) |
                    b;
        }

        // --- Recreate kernel if needed
        if (kernel == null || resizePixels) {
            kernel = new GridRaytraceKernel(screen.pixels, width, height);
        }

        // --- Camera parameters
        kernel.camX = (float) camera.position.x;
        kernel.camY = (float) camera.position.y;
        kernel.camZ = (float) camera.position.z;

        kernel.rotX = (float) camera.rotation.x; // pitch
        kernel.rotY = (float) camera.rotation.y; // yaw
        kernel.fov = (float) camera.fov;

        // --- Voxel data
        kernel.vx = vx;
        kernel.vy = vy;
        kernel.vz = vz;
        kernel.size = size;
        kernel.vcolor = vcolor;
        kernel.voxelCount = voxelCount;

        // --- Execute kernel
        kernel.execute(Range.create(screen.pixels.length));

        // --- Push pixels to screen
        screen.updatePixels();
    }

}
