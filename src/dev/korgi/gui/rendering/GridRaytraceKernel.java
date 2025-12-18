package dev.korgi.gui.rendering;

import com.aparapi.Kernel;

public class GridRaytraceKernel extends Kernel {

    // --- Output
    public int[] pixels;
    public int width;
    public int height;

    // --- Camera
    public float camX, camY, camZ;
    public float fov;
    public float rotX, rotY; // pitch, yaw

    // --- Camera basis (precomputed on CPU)
    public float forwardX, forwardY, forwardZ;
    public float rightX, rightY, rightZ;
    public float upX, upY, upZ;
    public float tanFov;

    // --- Voxels
    public float[] vx, vy, vz;
    public float[] size;
    public int[] vcolor;
    public int voxelCount;

    public GridRaytraceKernel(int[] pixels, int width, int height) {
        this.pixels = pixels;
        this.width = width;
        this.height = height;
    }

    @Override
    public void run() {
        int id = getGlobalId();
        int x = id % width;
        int y = id / width;
        if (y >= height)
            return;

        float aspect = (float) width / height;
        float ndcX = 2f * (x + 0.5f) / width - 1f;
        float ndcY = 1f - 2f * (y + 0.5f) / height;

        // --- Ray direction (using precomputed axes)
        float dx = forwardX + ndcX * aspect * tanFov * rightX + ndcY * tanFov * upX;
        float dy = forwardY + ndcX * aspect * tanFov * rightY + ndcY * tanFov * upY;
        float dz = forwardZ + ndcX * aspect * tanFov * rightZ + ndcY * tanFov * upZ;

        // Normalize ray
        float invLen = rsqrt(dx * dx + dy * dy + dz * dz);
        dx *= invLen;
        dy *= invLen;
        dz *= invLen;

        // Ray origin
        float ox = camX;
        float oy = camY;
        float oz = camZ;

        // Trace voxels
        float closestT = 1e20f;
        int hitColor = 0xFF87CEEB; // sky color

        for (int i = 0; i < voxelCount; i++) {
            float minx = vx[i];
            float miny = vy[i];
            float minz = vz[i];
            float s = size[i];

            float maxx = minx + s;
            float maxy = miny + s;
            float maxz = minz + s;

            float t = intersectAABB(ox, oy, oz, dx, dy, dz, minx, miny, minz, maxx, maxy, maxz);
            if (t > 0f && t < closestT) {
                closestT = t;
                hitColor = vcolor[i];
            }
        }

        // Depth shading
        if (closestT < 1e19f) {
            float shade = exp(-closestT * 0.03f);
            int a = (hitColor >> 24) & 255;
            int r = (hitColor >> 16) & 255;
            int g = (hitColor >> 8) & 255;
            int b = hitColor & 255;

            r = (int) (r * shade);
            g = (int) (g * shade);
            b = (int) (b * shade);

            hitColor = (a << 24) | (r << 16) | (g << 8) | b;
        }

        pixels[id] = hitColor;
    }

    float intersectAABB(
            float ox, float oy, float oz,
            float dx, float dy, float dz,
            float minx, float miny, float minz,
            float maxx, float maxy, float maxz) {

        float tx1 = (minx - ox) / dx;
        float tx2 = (maxx - ox) / dx;
        float tmin = min(tx1, tx2);
        float tmax = max(tx1, tx2);

        float ty1 = (miny - oy) / dy;
        float ty2 = (maxy - oy) / dy;
        tmin = max(tmin, min(ty1, ty2));
        tmax = min(tmax, max(ty1, ty2));

        float tz1 = (minz - oz) / dz;
        float tz2 = (maxz - oz) / dz;
        tmin = max(tmin, min(tz1, tz2));
        tmax = min(tmax, max(tz1, tz2));

        if (tmax >= max(tmin, 0f))
            return tmin;
        return -1f;
    }

    float radians(float angle) {
        return (float) Math.PI * angle / 180f;
    }
}
