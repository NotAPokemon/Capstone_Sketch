#version 450

layout(local_size_x = 8, local_size_y = 8) in;

struct RayParams {
    vec3 cam;
    vec3 forward;
    vec3 right;
    vec3 up;
    float tanFov;
    int voxelCount;
    ivec3 worldMin;
    ivec3 worldSize;
};

layout(std430, binding = 0) buffer Pixels {
    int pixels[];
};

layout(std430, binding = 1) buffer VoxelGrid {
    int voxelGrid[];
};

layout(std430, binding = 2) buffer Colors {
    int colors[];
};

layout(std430, binding = 3) buffer Opacity {
    float opacity[];
};

layout(std430, binding = 4) buffer Params {
    RayParams params;
};

layout(std430, binding = 5) buffer WidthBuf {
    int widthBuf[];
};

layout(std430, binding = 6) buffer HeightBuf {
    int heightBuf[];
};

void main() {
    int width = widthBuf[0];
    int height = heightBuf[0];

    uint px = gl_GlobalInvocationID.x;
    uint py = gl_GlobalInvocationID.y;
    if (px >= uint(width) || py >= uint(height)) return;

    int idx = int(py) * width + int(px);

    float aspect = float(width) / float(height);
    float ndcX = 2.0 * (float(px) + 0.5) / float(width) - 1.0;
    float ndcY = 1.0 - 2.0 * (float(py) + 0.5) / float(height);

    // Ray direction
    vec3 dir = normalize(params.forward +
                         ndcX * aspect * params.tanFov * params.right +
                         ndcY * params.tanFov * params.up);

    vec3 invDir = vec3(
        (dir.x != 0.0) ? 1.0 / dir.x : 1e20,
        (dir.y != 0.0) ? 1.0 / dir.y : 1e20,
        (dir.z != 0.0) ? 1.0 / dir.z : 1e20
    );

    vec3 minB = vec3(params.worldMin);
    vec3 maxB = vec3(params.worldMin + params.worldSize);

    float tMin = 0.0;
    float tMax = 100.0;

    // Slab intersection
    if (dir.x != 0.0) {
        float tx1 = (minB.x - params.cam.x) * invDir.x;
        float tx2 = (maxB.x - params.cam.x) * invDir.x;
        tMin = max(tMin, min(tx1, tx2));
        tMax = min(tMax, max(tx1, tx2));
    } else if (params.cam.x < minB.x || params.cam.x > maxB.x) {
        pixels[idx] = 0xFF87CEEB;
        return;
    }

    if (dir.y != 0.0) {
        float ty1 = (minB.y - params.cam.y) * invDir.y;
        float ty2 = (maxB.y - params.cam.y) * invDir.y;
        tMin = max(tMin, min(ty1, ty2));
        tMax = min(tMax, max(ty1, ty2));
    } else if (params.cam.y < minB.y || params.cam.y > maxB.y) {
        pixels[idx] = 0xFF87CEEB;
        return;
    }

    if (dir.z != 0.0) {
        float tz1 = (minB.z - params.cam.z) * invDir.z;
        float tz2 = (maxB.z - params.cam.z) * invDir.z;
        tMin = max(tMin, min(tz1, tz2));
        tMax = min(tMax, max(tz1, tz2));
    } else if (params.cam.z < minB.z || params.cam.z > maxB.z) {
        pixels[idx] = 0xFF87CEEB;
        return;
    }

    if (tMin > tMax) {
        pixels[idx] = 0xFF87CEEB;
        return;
    }

    // Start position
    vec3 startPos = params.cam + dir * tMin;
    ivec3 cell = ivec3(floor(startPos));

    vec3 tMaxVals;
    tMaxVals.x = tMin + ((dir.x > 0.0 ? (float(cell.x + 1) - startPos.x) : (float(cell.x) - startPos.x)) * invDir.x);
    tMaxVals.y = tMin + ((dir.y > 0.0 ? (float(cell.y + 1) - startPos.y) : (float(cell.y) - startPos.y)) * invDir.y);
    tMaxVals.z = tMin + ((dir.z > 0.0 ? (float(cell.z + 1) - startPos.z) : (float(cell.z) - startPos.z)) * invDir.z);

    vec3 tDelta = vec3(abs(invDir.x), abs(invDir.y), abs(invDir.z));
    tDelta = vec3((dir.x != 0.0) ? tDelta.x : 1e20,
                  (dir.y != 0.0) ? tDelta.y : 1e20,
                  (dir.z != 0.0) ? tDelta.z : 1e20);

    ivec3 step = ivec3((dir.x > 0.0) ? 1 : -1,
                       (dir.y > 0.0) ? 1 : -1,
                       (dir.z > 0.0) ? 1 : -1);

    int hitColor = 0;
    float opacityAccum = 1.0;
    int steps = 0;
    const int maxSteps = 500;
    float t = tMin;

    while (t < tMax && opacityAccum > 0.01 && steps < maxSteps) {
        if (all(greaterThanEqual(cell, params.worldMin)) &&
            all(lessThan(cell, params.worldMin + params.worldSize))) {

            int voxelIdx = voxelGrid[(cell.x - params.worldMin.x) +
                                     (cell.y - params.worldMin.y) * params.worldSize.x +
                                     (cell.z - params.worldMin.z) * params.worldSize.x * params.worldSize.y];

            if (voxelIdx >= 0 && voxelIdx < params.voxelCount) {
                int c = colors[voxelIdx];
                float a = opacity[voxelIdx] * opacityAccum;

                int r = int(float((c >> 16) & 0xFF) * a + float((hitColor >> 16) & 0xFF) * (1.0 - a));
                int g = int(float((c >> 8) & 0xFF) * a + float((hitColor >> 8) & 0xFF) * (1.0 - a));
                int b = int(float(c & 0xFF) * a + float(hitColor & 0xFF) * (1.0 - a));

                hitColor = (0xFF << 24) | (r << 16) | (g << 8) | b;

                opacityAccum *= (1.0 - opacity[voxelIdx]);
            }
        }

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

    // Blend with sky
    int sky = 0xFF87CEEB;
    float a = opacityAccum;
    int r = int(float((sky >> 16) & 0xFF) * a + float((hitColor >> 16) & 0xFF) * (1.0 - a));
    int g = int(float((sky >> 8) & 0xFF) * a + float((hitColor >> 8) & 0xFF) * (1.0 - a));
    int b = int(float(sky & 0xFF) * a + float(hitColor & 0xFF) * (1.0 - a));

    pixels[idx] = (0xFF << 24) | (r << 16) | (g << 8) | b;
}
