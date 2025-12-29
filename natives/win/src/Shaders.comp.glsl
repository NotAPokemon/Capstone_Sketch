#version 430

layout(local_size_x = 8, local_size_y = 8) in;

layout(std430, binding = 0) buffer Pixels {
    int pixels[];
};

layout(std430, binding = 1) buffer Voxels {
    int voxelGrid[];
};

layout(std430, binding = 2) buffer Colors {
    int colors[];
};

layout(std430, binding = 3) buffer Opacity {
    float opacity[];
};

// Uniforms
uniform vec3 cam;
uniform vec3 forward;
uniform vec3 right;
uniform vec3 up;
uniform float tanFov;
uniform int voxCount;
uniform ivec3 worldMin;
uniform ivec3 worldSize;
uniform int width;
uniform int height;

void main() {
    ivec2 gid = ivec2(gl_GlobalInvocationID.xy);
    int px = gid.x;
    int py = gid.y;
    if (px >= width || py >= height) return;
    int idx = py * width + px;

    float aspect = float(width) / float(height);
    float ndcX = 2.0 * (float(px) + 0.5) / float(width) - 1.0;
    float ndcY = 1.0 - 2.0 * (float(py) + 0.5) / float(height);

    vec3 dir = normalize(forward + ndcX * aspect * tanFov * right + ndcY * tanFov * up);
    vec3 invDir = vec3(
        dir.x != 0.0 ? 1.0 / dir.x : 1e20,
        dir.y != 0.0 ? 1.0 / dir.y : 1e20,
        dir.z != 0.0 ? 1.0 / dir.z : 1e20
    );

    vec3 minB = vec3(worldMin);
    vec3 maxB = vec3(worldMin + worldSize); // precompute max

    float tMin = 0.0;
    float tMax = 100.0; // render distance

    // Slab method
    for (int i = 0; i < 3; i++) {
        if (dir[i] != 0.0) {
            float t1 = (minB[i] - cam[i]) * invDir[i];
            float t2 = (maxB[i] - cam[i]) * invDir[i];
            tMin = max(tMin, min(t1, t2));
            tMax = min(tMax, max(t1, t2));
        } else if (cam[i] < minB[i] || cam[i] > maxB[i]) {
            pixels[idx] = 0xFF87CEEB;
            return;
        }
    }
    if (tMin > tMax) { pixels[idx] = 0xFF87CEEB; return; }

    vec3 startPos = cam + dir * tMin;
    ivec3 cell = ivec3(floor(startPos));

    vec3 tDelta = vec3(
        dir.x != 0.0 ? abs(invDir.x) : 1e20,
        dir.y != 0.0 ? abs(invDir.y) : 1e20,
        dir.z != 0.0 ? abs(invDir.z) : 1e20
    );

    ivec3 step = ivec3(sign(dir)); // cast sign(dir) to ivec3

    vec3 tMaxVals = vec3(
        tMin + ((step.x > 0 ? float(cell.x + 1) - startPos.x : float(cell.x) - startPos.x) * invDir.x),
        tMin + ((step.y > 0 ? float(cell.y + 1) - startPos.y : float(cell.y) - startPos.y) * invDir.y),
        tMin + ((step.z > 0 ? float(cell.z + 1) - startPos.z : float(cell.z) - startPos.z) * invDir.z)
    );

    int hitColor = 0;
    float opacityAccum = 1.0;
    int maxSteps = 500;
    int steps = 0;
    float t = tMin;

    ivec3 worldMax = worldMin + worldSize;

    while (t < tMax && opacityAccum > 0.01 && steps < maxSteps) {
        if (all(greaterThanEqual(cell, worldMin)) && all(lessThan(cell, worldMax))) {
            int voxelIdx = voxelGrid[(cell.x - worldMin.x)
                                    + (cell.y - worldMin.y) * worldSize.x
                                    + (cell.z - worldMin.z) * worldSize.x * worldSize.y];
            if (voxelIdx >= 0 && voxelIdx < voxCount) {
                int c = colors[voxelIdx];
                float a = opacity[voxelIdx] * opacityAccum;

                int r = int(((c >> 16) & 0xFF) * a + ((hitColor >> 16) & 0xFF) * (1.0 - a));
                int g = int(((c >> 8) & 0xFF) * a + ((hitColor >> 8) & 0xFF) * (1.0 - a));
                int b = int((c & 0xFF) * a + (hitColor & 0xFF) * (1.0 - a));
                hitColor = (0xFF << 24) | (r << 16) | (g << 8) | b;

                opacityAccum *= (1.0 - opacity[voxelIdx]);
            }
        }

        // Step along the ray
        if (tMaxVals.x <= tMaxVals.y && tMaxVals.x <= tMaxVals.z) {
            cell.x += step.x;
            t = tMaxVals.x;
            tMaxVals.x += tDelta.x;
        } else if (tMaxVals.y <= tMaxVals.z) {
            cell.y += step.y;
            t = tMaxVals.y;
            tMaxVals.y += tDelta.y;
        } else {
            cell.z += step.z;
            t = tMaxVals.z;
            tMaxVals.z += tDelta.z;
        }
        steps++;
    }

    int sky = 0xFF87CEEB;
    float a = opacityAccum;
    int r = int(((sky >> 16) & 0xFF) * a + ((hitColor >> 16) & 0xFF) * (1.0 - a));
    int g = int(((sky >> 8) & 0xFF) * a + ((hitColor >> 8) & 0xFF) * (1.0 - a));
    int b = int((sky & 0xFF) * a + (hitColor & 0xFF) * (1.0 - a));
    pixels[idx] = (0xFF << 24) | (r << 16) | (g << 8) | b;
}
