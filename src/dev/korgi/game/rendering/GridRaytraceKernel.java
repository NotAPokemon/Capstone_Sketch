package dev.korgi.game.rendering;

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

        float invDx = dx != 0 ? 1.0f / dx : 0.0f;
        float invDy = dy != 0 ? 1.0f / dy : 0.0f;
        float invDz = dz != 0 ? 1.0f / dz : 0.0f;

        // Voxel grid bounds
        float gridMinX = worldMinX;
        float gridMinY = worldMinY;
        float gridMinZ = worldMinZ;
        float gridMaxX = worldMinX + worldSizeX;
        float gridMaxY = worldMinY + worldSizeY;
        float gridMaxZ = worldMinZ + worldSizeZ;

        float tMin = 0;
        float tMax = renderDistance;

        if (dx != 0) {
            float tx1 = (gridMinX - camX) * invDx;
            float tx2 = (gridMaxX - camX) * invDx;
            tMin = max(tMin, min(tx1, tx2));
            tMax = min(tMax, max(tx1, tx2));
        } else if (camX < gridMinX || camX > gridMaxX) {
            pixels[id] = skyColor;
            return;
        }

        if (dy != 0) {
            float ty1 = (gridMinY - camY) * invDy;
            float ty2 = (gridMaxY - camY) * invDy;
            tMin = max(tMin, min(ty1, ty2));
            tMax = min(tMax, max(ty1, ty2));
        } else if (camY < gridMinY || camY > gridMaxY) {
            pixels[id] = skyColor;
            return;
        }

        if (dz != 0) {
            float tz1 = (gridMinZ - camZ) * invDz;
            float tz2 = (gridMaxZ - camZ) * invDz;
            tMin = max(tMin, min(tz1, tz2));
            tMax = min(tMax, max(tz1, tz2));
        } else if (camZ < gridMinZ || camZ > gridMaxZ) {
            pixels[id] = skyColor;
            return;
        }

        if (tMin > tMax) {
            pixels[id] = skyColor;
            return;
        }

        float startX = camX + dx * tMin;
        float startY = camY + dy * tMin;
        float startZ = camZ + dz * tMin;

        int cellX = (int) floor(startX);
        int cellY = (int) floor(startY);
        int cellZ = (int) floor(startZ);

        float tMaxX = tMin + ((dx > 0 ? (cellX + 1 - startX) : (cellX - startX)) * invDx);
        float tMaxY = tMin + ((dy > 0 ? (cellY + 1 - startY) : (cellY - startY)) * invDy);
        float tMaxZ = tMin + ((dz > 0 ? (cellZ + 1 - startZ) : (cellZ - startZ)) * invDz);

        float tDeltaX = dx != 0 ? abs(invDx) : Float.MAX_VALUE;
        float tDeltaY = dy != 0 ? abs(invDy) : Float.MAX_VALUE;
        float tDeltaZ = dz != 0 ? abs(invDz) : Float.MAX_VALUE;

        int stepX = dx > 0 ? 1 : -1;
        int stepY = dy > 0 ? 1 : -1;
        int stepZ = dz > 0 ? 1 : -1;

        int hitColor = 0;
        float currentOpacity = 1;
        float dist = 0;
        int steps = 0;

        while (dist < renderDistance && currentOpacity > 0.01f) {
            if (steps >= renderDistance - 1) {
                break;
            }
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
            steps++;
        }

        hitColor = blend(hitColor, skyColor, currentOpacity);

        pixels[id] = hitColor;
    }

    private int flattenIndex(int x, int y, int z) {
        return (x - worldMinX) +
                (y - worldMinY) * worldSizeX +
                (z - worldMinZ) * worldSizeX * worldSizeY;
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

        return (255 << 24) | (r << 16) | (g << 8) | b;
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

        camX = (float) cam.position.x;
        camY = (float) cam.position.y;
        camZ = (float) cam.position.z;

        worldMinX = minX;
        worldMinY = minY;
        worldMinZ = minZ;
        worldSizeX = maxX - minX + 1;
        worldSizeY = maxY - minY + 1;
        worldSizeZ = maxZ - minZ + 1;

        tanFov = (float) Math.tan(Math.toRadians(cam.fov * 0.5f));
        computeCameraBasis((float) cam.rotation.x, (float) cam.rotation.y);

        voxelCount = voxels.size();
        if (vcolor == null || voxelCount != vcolor.length) {
            vcolor = new int[voxelCount];
            opacity = new float[voxelCount];
        }

        if (voxelGrid == null || voxelGrid.length != worldSizeX * worldSizeY * worldSizeZ)
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

            Vector4 color = v.getMaterial().getColor();
            vcolor[i] = rgbToARGB((float) color.x, (float) color.y, (float) color.z, 1);
            opacity[i] = (float) v.getMaterial().getOpacity();

            voxelGrid[gx + gy * worldSizeX + gz * worldSizeX * worldSizeY] = i;
        }
    }

    private void computeCameraBasis(float pitchRad, float yawRad) {
        forwardX = (float) (Math.sin(yawRad) * Math.cos(pitchRad));
        forwardY = (float) Math.sin(pitchRad);
        forwardZ = (float) (Math.cos(yawRad) * Math.cos(pitchRad));

        rightX = (float) Math.sin(yawRad - Math.PI / 2.0);
        rightY = 0;
        rightZ = (float) Math.cos(yawRad - Math.PI / 2.0);

        upX = rightY * forwardZ - rightZ * forwardY;
        upY = rightZ * forwardX - rightX * forwardZ;
        upZ = rightX * forwardY - rightY * forwardX;

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
