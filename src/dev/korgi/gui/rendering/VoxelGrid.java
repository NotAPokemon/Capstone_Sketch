package dev.korgi.gui.rendering;

import java.util.List;

public class VoxelGrid {
    public final int gridSizeX, gridSizeY, gridSizeZ;
    public final float cellSize;
    public final int[][] cellVoxelIndices; // flattened list per cell
    public final int[] cellStart; // start index in flat array
    public final int[] cellCount; // number of voxels per cell

    public VoxelGrid(List<Voxel> voxels, float cellSize) {
        this.cellSize = cellSize;

        // compute bounds
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = Float.MIN_VALUE, maxY = Float.MIN_VALUE, maxZ = Float.MIN_VALUE;

        for (Voxel v : voxels) {
            minX = (float) Math.min(minX, v.position.x);
            minY = (float) Math.min(minY, v.position.y);
            minZ = (float) Math.min(minZ, v.position.z);
            maxX = (float) Math.max(maxX, v.position.x);
            maxY = (float) Math.max(maxY, v.position.y);
            maxZ = (float) Math.max(maxZ, (float) v.position.z);
        }

        gridSizeX = (int) Math.ceil((maxX - minX) / cellSize) + 1;
        gridSizeY = (int) Math.ceil((maxY - minY) / cellSize) + 1;
        gridSizeZ = (int) Math.ceil((maxZ - minZ) / cellSize) + 1;

        int totalCells = gridSizeX * gridSizeY * gridSizeZ;
        cellVoxelIndices = new int[totalCells][]; // each cell stores voxel indices
        cellStart = new int[totalCells];
        cellCount = new int[totalCells];

        // naive allocation: store voxels in each cell they occupy
        for (int i = 0; i < totalCells; i++) {
            cellVoxelIndices[i] = new int[10]; // assume <=10 voxels per cell initially
        }

        for (int vi = 0; vi < voxels.size(); vi++) {
            Voxel v = voxels.get(vi);
            int cellX = (int) ((v.position.x - minX) / cellSize);
            int cellY = (int) ((v.position.y - minY) / cellSize);
            int cellZ = (int) ((v.position.z - minZ) / cellSize);
            int cellIndex = cellX + gridSizeX * (cellY + gridSizeY * cellZ);

            int count = cellCount[cellIndex];
            if (count >= cellVoxelIndices[cellIndex].length) {
                int[] newArr = new int[count * 2];
                System.arraycopy(cellVoxelIndices[cellIndex], 0, newArr, 0, count);
                cellVoxelIndices[cellIndex] = newArr;
            }
            cellVoxelIndices[cellIndex][count] = vi;
            cellCount[cellIndex]++;
        }
    }

    // helper to convert world position to cell index
    public int getCellIndex(float x, float y, float z) {
        int cx = (int) (x / cellSize);
        int cy = (int) (y / cellSize);
        int cz = (int) (z / cellSize);
        return cx + gridSizeX * (cy + gridSizeY * cz);
    }
}
