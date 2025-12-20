package dev.korgi.gui.rendering;

import com.aparapi.Kernel;

import dev.korgi.math.Vector4;

import java.util.List;

public class GridRaytraceKernel extends Kernel {

    private int[] pixels;
    private int width, height;

    private float camX, camY, camZ;
    private float forwardX, forwardY, forwardZ;
    private float rightX, rightY, rightZ;
    private float upX, upY, upZ;
    private float tanFov;

    private float renderDistance = 100;

    private int voxelCount;
    private int[] vx, vy, vz;
    private int[] vcolor;
    private float[] opacity;
    private int skyColor = 0xFF87CEEB;

    private int worldMinX, worldMinY, worldMinZ;
    private int worldSizeX, worldSizeY, worldSizeZ;
    private int[] voxelGrid;

    public GridRaytraceKernel(int[] pixels, int width, int height) {
        this.pixels = pixels;
        this.width = width;
        this.height = height;
    }

    @Override
    public void run() {
        int id = getGlobalId();
        int px = id % width;
        int py = id / width;
        if (py >= height)
            return;

        float aspect = (float) width / height;
        float ndcX = 2f * (px + 0.5f) / width - 1f;
        float ndcY = 1f - 2f * (py + 0.5f) / height;

        float dx = forwardX + ndcX * aspect * tanFov * rightX + ndcY * tanFov * upX;
        float dy = forwardY + ndcX * aspect * tanFov * rightY + ndcY * tanFov * upY;
        float dz = forwardZ + ndcX * aspect * tanFov * rightZ + ndcY * tanFov * upZ;

        float invLen = rsqrt(dx * dx + dy * dy + dz * dz);
        dx *= invLen;
        dy *= invLen;
        dz *= invLen;

        int cellX = (int) floor(camX);
        int cellY = (int) floor(camY);
        int cellZ = (int) floor(camZ);

        float[] tX = computeDDAParams(camX, dx, cellX);
        float[] tY = computeDDAParams(camY, dy, cellY);
        float[] tZ = computeDDAParams(camZ, dz, cellZ);

        float tMaxX = tX[0], tDeltaX = tX[1];
        int stepX = (int) tX[2];
        float tMaxY = tY[0], tDeltaY = tY[1];
        int stepY = (int) tY[2];
        float tMaxZ = tZ[0], tDeltaZ = tZ[1];
        int stepZ = (int) tZ[2];

        int hitColor = 0;
        float currentOpacity = 1;
        float dist = 0;

        while (dist < renderDistance && currentOpacity > 0.01f) {

            if (inBounds(cellX, cellY, cellZ)) {
                int i = voxelGrid[flattenIndex(cellX, cellY, cellZ)];
                if (i >= 0) {
                    hitColor = blend(hitColor, vcolor[i], opacity[i] * currentOpacity);
                    currentOpacity *= (1 - opacity[i]);
                }
            }

            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                cellX += stepX;
                dist = tMaxX;
                tMaxX += tDeltaX;
            } else if (tMaxY < tMaxZ) {
                cellY += stepY;
                dist = tMaxY;
                tMaxY += tDeltaY;
            } else {
                cellZ += stepZ;
                dist = tMaxZ;
                tMaxZ += tDeltaZ;
            }

            if (cellX < worldMinX && stepX < 0)
                break;
            if (cellY < worldMinY && stepY < 0)
                break;
            if (cellZ < worldMinZ && stepZ < 0)
                break;
            if (cellX >= worldMinX + worldSizeX && stepX > 0)
                break;
            if (cellY >= worldMinY + worldSizeY && stepY > 0)
                break;
            if (cellZ >= worldMinZ + worldSizeZ && stepZ > 0)
                break;
        }

        hitColor = blend(hitColor, skyColor, currentOpacity);

        pixels[id] = hitColor;
    }

    private int flattenIndex(int x, int y, int z) {
        return (x - worldMinX) +
                (y - worldMinY) * worldSizeX +
                (z - worldMinZ) * worldSizeX * worldSizeY;
    }

    private float[] computeDDAParams(float rayOrigin, float rayDir, int cell) {
        float step = rayDir > 0 ? 1 : -1;
        float tMax = rayDir != 0 ? ((cell + (step > 0 ? 1 : 0)) - rayOrigin) / rayDir : Float.MAX_VALUE;
        float tDelta = rayDir != 0 ? step / rayDir : Float.MAX_VALUE;
        return new float[] { tMax, tDelta, step };
    }

    private int blend(int bg, int fg, float alpha) {
        alpha = Math.min(Math.max(alpha, 0f), 1f);

        int rBg = (bg >> 16) & 0xFF;
        int gBg = (bg >> 8) & 0xFF;
        int bBg = bg & 0xFF;

        int rFg = (fg >> 16) & 0xFF;
        int gFg = (fg >> 8) & 0xFF;
        int bFg = fg & 0xFF;

        int r = (int) (rFg * alpha + rBg * (1f - alpha));
        int g = (int) (gFg * alpha + gBg * (1f - alpha));
        int b = (int) (bFg * alpha + bBg * (1f - alpha));

        return (255 << 24) | (r << 16) | (g << 8) | b; // alpha = 255
    }

    private boolean inBounds(int x, int y, int z) {
        return x >= worldMinX && y >= worldMinY && z >= worldMinZ &&
                x < worldMinX + worldSizeX &&
                y < worldMinY + worldSizeY &&
                z < worldMinZ + worldSizeZ;
    }

    private int rgbToARGB(float r, float g, float b, int a) {
        int ri = (int) (Math.min(r, 1f) * 255);
        int gi = (int) (Math.min(g, 1f) * 255);
        int bi = (int) (Math.min(b, 1f) * 255);
        return (a << 24) | (ri << 16) | (gi << 8) | bi;
    }

    public void precompute(List<Voxel> voxels, Camera cam) {
        if (voxels.isEmpty())
            return;

        // --- Find the bounds of all voxels
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (Voxel v : voxels) {
            int x = (int) v.position.x;
            int y = (int) v.position.y;
            int z = (int) v.position.z;
            if (x < minX)
                minX = x;
            if (y < minY)
                minY = y;
            if (z < minZ)
                minZ = z;
            if (x > maxX)
                maxX = x;
            if (y > maxY)
                maxY = y;
            if (z > maxZ)
                maxZ = z;
        }

        // --- Include the camera in the bounds
        camX = (float) cam.position.x;
        camY = (float) cam.position.y;
        camZ = (float) cam.position.z;

        if (camX < minX)
            minX = (int) camX;
        if (camY < minY)
            minY = (int) camY;
        if (camZ < minZ)
            minZ = (int) camZ;
        if (camX > maxX)
            maxX = (int) camX;
        if (camY > maxY)
            maxY = (int) camY;
        if (camZ > maxZ)
            maxZ = (int) camZ;

        // --- World size
        worldMinX = minX;
        worldMinY = minY;
        worldMinZ = minZ;
        worldSizeX = maxX - minX + 1;
        worldSizeY = maxY - minY + 1;
        worldSizeZ = maxZ - minZ + 1;

        tanFov = (float) Math.tan(Math.toRadians(cam.fov * 0.5f));
        computeCameraBasis((float) cam.rotation.x, (float) cam.rotation.y);

        // --- Initialize voxel arrays
        voxelCount = voxels.size();
        vx = new int[voxelCount];
        vy = new int[voxelCount];
        vz = new int[voxelCount];
        vcolor = new int[voxelCount];
        opacity = new float[voxelCount];

        voxelGrid = new int[worldSizeX * worldSizeY * worldSizeZ];
        for (int i = 0; i < voxelGrid.length; i++)
            voxelGrid[i] = -1;

        for (int i = 0; i < voxelCount; i++) {
            Voxel v = voxels.get(i);
            int gx = (int) v.position.x - worldMinX;
            int gy = (int) v.position.y - worldMinY;
            int gz = (int) v.position.z - worldMinZ;

            if (gx < 0 || gx >= worldSizeX ||
                    gy < 0 || gy >= worldSizeY ||
                    gz < 0 || gz >= worldSizeZ)
                continue;

            vx[i] = (int) v.position.x;
            vy[i] = (int) v.position.y;
            vz[i] = (int) v.position.z;

            Vector4 color = v.getMaterial().getColor();
            vcolor[i] = rgbToARGB((float) color.x, (float) color.y, (float) color.z, 1);
            opacity[i] = (float) v.getMaterial().getOpacity();

            voxelGrid[gx + gy * worldSizeX + gz * worldSizeX * worldSizeY] = i;
        }
    }

    private void computeCameraBasis(float pitchRad, float yawRad) {
        // --- Forward vector (look direction)
        forwardX = (float) (Math.sin(yawRad) * Math.cos(pitchRad));
        forwardY = (float) Math.sin(pitchRad);
        forwardZ = (float) (Math.cos(yawRad) * Math.cos(pitchRad));

        // --- Right vector (perpendicular to forward on horizontal plane)
        rightX = (float) Math.sin(yawRad - Math.PI / 2.0);
        rightY = 0;
        rightZ = (float) Math.cos(yawRad - Math.PI / 2.0);

        // --- Up vector (cross product of right and forward)
        upX = rightY * forwardZ - rightZ * forwardY;
        upY = rightZ * forwardX - rightX * forwardZ;
        upZ = rightX * forwardY - rightY * forwardX;

        // --- Normalize all vectors
        float fLen = (float) Math.sqrt(forwardX * forwardX + forwardY * forwardY + forwardZ * forwardZ);
        forwardX /= fLen;
        forwardY /= fLen;
        forwardZ /= fLen;

        float rLen = (float) Math.sqrt(rightX * rightX + rightY * rightY + rightZ * rightZ);
        rightX /= rLen;
        rightY /= rLen;
        rightZ /= rLen;

        float uLen = (float) Math.sqrt(upX * upX + upY * upY + upZ * upZ);
        upX /= uLen;
        upY /= uLen;
        upZ /= uLen;
    }

}
