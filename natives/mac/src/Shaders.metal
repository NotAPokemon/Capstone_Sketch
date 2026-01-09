#include <metal_stdlib>
using namespace metal;

struct RayParams {
    float3 cam;
    float3 forward;
    float3 right;
    float3 up;
    int3 worldMin;
    int3 worldSize;
    float tanFov;
    int voxelCount;
};

// Buffers 0-3: pixels, voxelGrid, colors, opacity
// Buffer 4: RayParams
// Buffer 5: width
// Buffer 6: height
kernel void raytraceKernel(
    device int* pixels          [[buffer(0)]],
    device int* voxelGrid       [[buffer(1)]],
    device int* colors          [[buffer(2)]],
    device float* opacity       [[buffer(3)]],
    device RayParams& params    [[buffer(4)]],
    device int* widthBuf        [[buffer(5)]],
    device int* heightBuf       [[buffer(6)]],
    device int* textureLocation [[buffer(7)]],
    device int* textureAtlas    [[buffer(8)]],
    uint2 gid                 [[thread_position_in_grid]]
) {
    int width = widthBuf[0];
    int height = heightBuf[0];

    int px = gid.x;
    int py = gid.y;
    if (px >= width || py >= height) return;

    int idx = py * width + px;

    float aspect = float(width) / float(height);
    float ndcX = 2.0f * (float(px) + 0.5f) / float(width) - 1.0f;
    float ndcY = 1.0f - 2.0f * (float(py) + 0.5f) / float(height);

    // Ray direction
    float3 dir = normalize(params.forward +
                           ndcX * aspect * params.tanFov * params.right +
                           ndcY * params.tanFov * params.up);

    float3 invDir = float3((dir.x != 0) ? 1.0f / dir.x : 1e20f,
                           (dir.y != 0) ? 1.0f / dir.y : 1e20f,
                           (dir.z != 0) ? 1.0f / dir.z : 1e20f);

    float3 minB = float3(params.worldMin);
    float3 maxB = float3(params.worldMin + params.worldSize);

    float tMin = 0.0f;
    float tMax = 100.0f; // render distance

    // Slab method
    if (dir.x != 0.0f) {
        float tx1 = (minB.x - params.cam.x) * invDir.x;
        float tx2 = (maxB.x - params.cam.x) * invDir.x;
        tMin = fmax(tMin, fmin(tx1, tx2));
        tMax = fmin(tMax, fmax(tx1, tx2));
    } else if (params.cam.x < minB.x || params.cam.x > maxB.x) {
        pixels[idx] = 0xFF87CEEB;
        return;
    }

    if (dir.y != 0.0f) {
        float ty1 = (minB.y - params.cam.y) * invDir.y;
        float ty2 = (maxB.y - params.cam.y) * invDir.y;
        tMin = fmax(tMin, fmin(ty1, ty2));
        tMax = fmin(tMax, fmax(ty1, ty2));
    } else if (params.cam.y < minB.y || params.cam.y > maxB.y) {
        pixels[idx] = 0xFF87CEEB;
        return;
    }

    if (dir.z != 0.0f) {
        float tz1 = (minB.z - params.cam.z) * invDir.z;
        float tz2 = (maxB.z - params.cam.z) * invDir.z;
        tMin = fmax(tMin, fmin(tz1, tz2));
        tMax = fmin(tMax, fmax(tz1, tz2));
    } else if (params.cam.z < minB.z || params.cam.z > maxB.z) {
        pixels[idx] = 0xFF87CEEB;
        return;
    }

    if (tMin > tMax) {
        pixels[idx] = 0xFF87CEEB;
        return;
    }

    // Start position
    float3 startPos = params.cam + dir * tMin;
    int3 cell = int3(floor(startPos));

    float3 tMaxVals;
    tMaxVals.x = tMin + ((dir.x > 0 ? (cell.x + 1.0f - startPos.x) : (cell.x - startPos.x)) * invDir.x);
    tMaxVals.y = tMin + ((dir.y > 0 ? (cell.y + 1.0f - startPos.y) : (cell.y - startPos.y)) * invDir.y);
    tMaxVals.z = tMin + ((dir.z > 0 ? (cell.z + 1.0f - startPos.z) : (cell.z - startPos.z)) * invDir.z);

    float3 tDelta = float3(dir.x != 0.0f ? fabs(invDir.x) : 1e20f,
                           dir.y != 0.0f ? fabs(invDir.y) : 1e20f,
                           dir.z != 0.0f ? fabs(invDir.z) : 1e20f);

    int3 step = int3((dir.x > 0 ? 1 : -1),
                     (dir.y > 0 ? 1 : -1),
                     (dir.z > 0 ? 1 : -1));

    int hitColor = 0;
    float opacityAccum = 1.0f;
    int maxSteps = 500;
    int steps = 0;
    float t = tMin;
    int hitFace = -1;
    float3 hitPos = 0;

    while (t < tMax && opacityAccum > 0.01f && steps < maxSteps) {
        if (all(cell >= int3(params.worldMin)) &&
            all(cell < int3(params.worldMin + params.worldSize))) {

            int voxelIdx = voxelGrid[(cell.x - params.worldMin.x) +
                                     (cell.y - params.worldMin.y) * params.worldSize.x +
                                     (cell.z - params.worldMin.z) * params.worldSize.x * params.worldSize.y];

            if (voxelIdx >= 0 && voxelIdx < int(params.voxelCount)) {
                int c = colors[voxelIdx];
                float a = opacity[voxelIdx] * opacityAccum;

                float u = 0.0f;
                float v = 0.0f;

                float3 local = hitPos - floor(hitPos);

                switch (hitFace) {
                    case 0: // +X
                    case 1: // -X
                        u = local.z;
                        v = 1.0f - local.y;
                        break;

                    case 2: // -Y
                    case 3: // +Y
                        u = local.x;
                        v = 1.0f - local.z;
                        break;

                    case 4: // +Z
                    case 5: // -Z
                        u = local.x;
                        v = 1.0f - local.y;
                        break;
                }

                int texId = textureLocation[voxelIdx];

                if (texId != -1 && hitFace >= 0){
                    int iu = clamp(int(u * 32), 0, 32 - 1);
                    int iv = clamp(int(v * 32), 0, 32 - 1);

                    int x = hitFace * 32 + iu;
                    int base = texId * 192 * 32;

                    int texel = textureAtlas[
                        base + iv * 192 + x
                    ];

                    hitColor = texel;
                    opacityAccum *= 0.0f;
                } else {
                    int r = int(((c >> 16) & 0xFF) * a + ((hitColor >> 16) & 0xFF) * (1.0f - a));
                    int g = int(((c >> 8) & 0xFF) * a + ((hitColor >> 8) & 0xFF) * (1.0f - a));
                    int b = int((c & 0xFF) * a + (hitColor & 0xFF) * (1.0f - a));
                    hitColor = (0xFF << 24) | (r << 16) | (g << 8) | b;

                    opacityAccum *= (1.0f - opacity[voxelIdx]);
                }
            }
        }

        float tHit;

        if (tMaxVals.x <= tMaxVals.y && tMaxVals.x <= tMaxVals.z) {
            tHit = tMaxVals.x;
            cell.x += step.x;
            tMaxVals.x += tDelta.x;
            hitFace = (step.x > 0) ? 0 : 1;
        }
        else if (tMaxVals.y <= tMaxVals.z) {
            tHit = tMaxVals.y;
            cell.y += step.y;
            tMaxVals.y += tDelta.y;
            hitFace = (step.y > 0) ? 3 : 2;
        }
        else {
            tHit = tMaxVals.z;
            cell.z += step.z;
            tMaxVals.z += tDelta.z;
            hitFace = (step.z > 0) ? 4 : 5;
        }

        t = tHit;
        hitPos = params.cam + dir * tHit;

        steps++;
    }

    // Blend with sky
    int sky = 0xFF87CEEB;
    float a = opacityAccum;
    int r = int(((sky >> 16) & 0xFF) * a + ((hitColor >> 16) & 0xFF) * (1.0f - a));
    int g = int(((sky >> 8) & 0xFF) * a + ((hitColor >> 8) & 0xFF) * (1.0f - a));
    int b = int((sky & 0xFF) * a + (hitColor & 0xFF) * (1.0f - a));
    pixels[idx] = (0xFF << 24) | (r << 16) | (g << 8) | b;
}
