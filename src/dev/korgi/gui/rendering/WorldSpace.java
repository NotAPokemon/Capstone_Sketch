package dev.korgi.gui.rendering;

import java.util.ArrayList;
import java.util.List;

import com.aparapi.Range;

import dev.korgi.gui.Screen;
import dev.korgi.math.Vector3;
import dev.korgi.math.Vector4;

public class WorldSpace {

    private static List<Voxel> objects = new ArrayList<>();
    public static Camera camera = new Camera();
    public static final double VOXEL_SIZE = 5;
    public static GridRaytraceKernel kernel;

    public static void init() {
        // example voxels
        Voxel v1 = new Voxel();
        v1.position = new Vector3(0, 0, 200);
        v1.color = new Vector4(1, 0, 0, 1);
        objects.add(v1);

        Voxel v2 = new Voxel();
        v2.position = new Vector3(50, 50, 300);
        v2.color = new Vector4(0, 1, 0, 1);
        objects.add(v2);

        camera.position = new Vector3(0, 0, 0);
        camera.rotation = new Vector3(0, 0, 0);
        camera.fov = 500;
    }

    private static GridRaytraceKernel reqire(int[] p, int w, int h) {
        kernel = kernel == null ? new GridRaytraceKernel(p, w, h) : kernel;
        return kernel;
    }

    public static void execute() {
        Screen screen = Screen.getInstance();
        int width = screen.width;
        int height = screen.height;

        // --- Prepare pixel buffer
        if (screen.pixels == null || screen.pixels.length != width * height) {
            screen.loadPixels();
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

        // --- Create kernel
        kernel = reqire(screen.pixels, width, height);

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
        int totalPixels = width * height;
        kernel.execute(Range.create(totalPixels));

        // --- Push pixels to screen
        screen.updatePixels();
    }

}
