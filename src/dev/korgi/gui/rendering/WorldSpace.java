package dev.korgi.gui.rendering;

import java.util.ArrayList;
import java.util.List;

import com.aparapi.Range;

import dev.korgi.math.Vector3;
import dev.korgi.math.Vector4;
import processing.core.PApplet;

public class WorldSpace {

    private static List<Voxel> objects = new ArrayList<>();
    public static Camera camera = new Camera();
    private static final double VOXEL_SIZE = 5;

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

        camera.position = new Vector3(0, 0, -50);
        camera.rotation = new Vector3(0, 0, 0);
        camera.fov = 500;
    }

    public static void execute(PApplet screen) {
        int width = screen.width;
        int height = screen.height;

        float[] voxelPositions = new float[objects.size() * 3];
        float[] voxelColors = new float[objects.size() * 3];
        for (int i = 0; i < objects.size(); i++) {
            Voxel v = objects.get(i);
            voxelPositions[i * 3] = (float) v.position.x;
            voxelPositions[i * 3 + 1] = (float) v.position.y;
            voxelPositions[i * 3 + 2] = (float) v.position.z;

            voxelColors[i * 3] = (float) v.color.x;
            voxelColors[i * 3 + 1] = (float) v.color.y;
            voxelColors[i * 3 + 2] = (float) v.color.z;
        }

        float cellSize = (float) VOXEL_SIZE * 1.5f;
        VoxelGrid grid = new VoxelGrid(objects, cellSize);

        int[][] cellVoxelIndices = grid.cellVoxelIndices;
        int[] cellCount = grid.cellCount;

        GridRaytraceKernel kernel = new GridRaytraceKernel(
                width, height,
                (float) Math.toRadians(camera.fov),
                new float[] { (float) camera.position.x, (float) camera.position.y, (float) camera.position.z },
                voxelPositions, voxelColors, (float) VOXEL_SIZE,
                grid.gridSizeX, grid.gridSizeY, grid.gridSizeZ, cellSize,
                cellVoxelIndices, cellCount);

        kernel.execute(Range.create(width * height, 10));
        float[] gpuPixels = kernel.getPixels();

        screen.loadPixels();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int idx = y * width + x;
                float r = gpuPixels[idx * 3];
                float g = gpuPixels[idx * 3 + 1];
                float b = gpuPixels[idx * 3 + 2];

                screen.pixels[idx] = screen.color(r * 255, g * 255, b * 255);
            }
        }

        screen.updatePixels();
    }

}
