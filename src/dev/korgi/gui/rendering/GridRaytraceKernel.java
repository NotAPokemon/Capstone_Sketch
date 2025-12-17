package dev.korgi.gui.rendering;

import com.aparapi.Kernel;

public class GridRaytraceKernel extends Kernel {

    private final int width, height;
    private final float fov, aspectRatio;
    private final float[] camPos;
    private final float[] voxelPositions; // x,y,z
    private final float[] voxelColors; // r,g,b
    private final float voxelSize;
    private final int voxelCount;

    private final int gridSizeX, gridSizeY, gridSizeZ;
    private final float cellSize;
    private final int[][] cellVoxelIndices;
    private final int[] cellCount;

    private final float[] pixels;

    public GridRaytraceKernel(int width, int height, float fov, float[] camPos,
            float[] voxelPositions, float[] voxelColors, float voxelSize,
            int gridSizeX, int gridSizeY, int gridSizeZ, float cellSize,
            int[][] cellVoxelIndices, int[] cellCount) {
        this.width = width;
        this.height = height;
        this.fov = fov;
        this.aspectRatio = (float) width / height;
        this.camPos = camPos;
        this.voxelPositions = voxelPositions;
        this.voxelColors = voxelColors;
        this.voxelSize = voxelSize;
        this.voxelCount = voxelPositions.length / 3;

        this.gridSizeX = gridSizeX;
        this.gridSizeY = gridSizeY;
        this.gridSizeZ = gridSizeZ;
        this.cellSize = cellSize;
        this.cellVoxelIndices = cellVoxelIndices;
        this.cellCount = cellCount;

        this.pixels = new float[width * height * 3];
    }

    @Override
    public void run() {
        int id = getGlobalId();
        int x = id % width;
        int y = id / width;

        // ray direction
        float ndcX = (2f * (x + 0.5f) / width - 1f) * tan(fov / 2f) * aspectRatio;
        float ndcY = (1f - 2f * (y + 0.5f) / height) * tan(fov / 2f);
        float dirX = ndcX, dirY = ndcY, dirZ = 1f;
        float len = sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        dirX /= len;
        dirY /= len;
        dirZ /= len;

        float t = 0f; // march along ray
        float dt = cellSize * 0.5f; // step size

        float hitR = 0, hitG = 0, hitB = 1; // background

        while (t < 1000f) { // max distance
            float px = camPos[0] + dirX * t;
            float py = camPos[1] + dirY * t;
            float pz = camPos[2] + dirZ * t;

            int cx = (int) (px / cellSize);
            int cy = (int) (py / cellSize);
            int cz = (int) (pz / cellSize);

            if (cx < 0 || cy < 0 || cz < 0 || cx >= gridSizeX || cy >= gridSizeY || cz >= gridSizeZ)
                break;

            int cellIndex = cx + gridSizeX * (cy + gridSizeY * cz);
            int count = cellCount[cellIndex];
            for (int i = 0; i < count; i++) {
                int vi = cellVoxelIndices[cellIndex][i];
                float vx = voxelPositions[vi * 3];
                float vy = voxelPositions[vi * 3 + 1];
                float vz = voxelPositions[vi * 3 + 2];
                if (px >= vx && px <= vx + voxelSize &&
                        py >= vy && py <= vy + voxelSize &&
                        pz >= vz && pz <= vz + voxelSize) {
                    hitR = voxelColors[vi * 3];
                    hitG = voxelColors[vi * 3 + 1];
                    hitB = voxelColors[vi * 3 + 2];
                    t = 10000f; // hit voxel, terminate
                    break;
                }
            }
            t += dt;
        }

        pixels[id * 3] = hitR;
        pixels[id * 3 + 1] = hitG;
        pixels[id * 3 + 2] = hitB;
    }

    public float[] getPixels() {
        return pixels;
    }

    protected float tan(float x) {
        return (float) Math.tan(x);
    }

    protected float sqrt(float x) {
        return (float) Math.sqrt(x);
    }
}
