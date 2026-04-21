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

constant int CHUNK_SIZE = 8;

kernel void raytraceKernel(
    device int* pixels          [[buffer(0)]],
    device int* voxelGrid       [[buffer(1)]],
    device int* colors          [[buffer(2)]],
    device float* opacity       [[buffer(3)]],
    device RayParams& params    [[buffer(4)]],
    device int* widthBuf        [[buffer(5)]],
    device int* heightBuf       [[buffer(6)]],
    device int* textureLocation [[buffer(7)]],
    device int* overlayLocation [[buffer(8)]],
    device int* textureAtlas    [[buffer(9)]],
    device int* chunkGrid       [[buffer(10)]],
    device int* chunkSize       [[buffer(11)]],
    device float* tBuffer       [[buffer(12)]],
    uint2 gid                   [[thread_position_in_grid]]
) {
    const int width = widthBuf[0];
    const int height = heightBuf[0];

    const int px = (int)gid.x;
    const int py = (int)gid.y;
    if (px >= width || py >= height) return;

    const float aspect = (float)width / (float)height;
    const float ndcX = (2.0f * ((float)px + 0.5f) / (float)width  - 1.0f) * aspect * params.tanFov;
    const float ndcY = (1.0f -  2.0f * ((float)py + 0.5f) / (float)height) * params.tanFov;

    const float3 dir = normalize(params.forward + ndcX * params.right + ndcY * params.up);

    const float3 invDir = float3(
        select(1e20f, 1.0f / dir.x, dir.x != 0.0f),
        select(1e20f, 1.0f / dir.y, dir.y != 0.0f),
        select(1e20f, 1.0f / dir.z, dir.z != 0.0f)
    );

    const float3 minB = float3(params.worldMin);
    const float3 maxB = float3(params.worldMin + params.worldSize);
    const int idx = py * width + px;

    float tMin = 0.0f;
    float tMax = 100.0f;

    if (dir.x != 0.0f) {
        float tx1 = (minB.x - params.cam.x) * invDir.x;
        float tx2 = (maxB.x - params.cam.x) * invDir.x;
        tMin = fmax(tMin, fmin(tx1, tx2));
        tMax = fmin(tMax, fmax(tx1, tx2));
    } else if (params.cam.x < minB.x || params.cam.x > maxB.x) {
        tBuffer[idx] = -1;
        pixels[idx] = 0xFF87CEEB; return;
    }

    if (dir.y != 0.0f) {
        float ty1 = (minB.y - params.cam.y) * invDir.y;
        float ty2 = (maxB.y - params.cam.y) * invDir.y;
        tMin = fmax(tMin, fmin(ty1, ty2));
        tMax = fmin(tMax, fmax(ty1, ty2));
    } else if (params.cam.y < minB.y || params.cam.y > maxB.y) {
        tBuffer[idx] = -1;
        pixels[idx] = 0xFF87CEEB; return;
    }

    if (dir.z != 0.0f) {
        float tz1 = (minB.z - params.cam.z) * invDir.z;
        float tz2 = (maxB.z - params.cam.z) * invDir.z;
        tMin = fmax(tMin, fmin(tz1, tz2));
        tMax = fmin(tMax, fmax(tz1, tz2));
    } else if (params.cam.z < minB.z || params.cam.z > maxB.z) {
        tBuffer[idx] = -1;
        pixels[idx] = 0xFF87CEEB; return;
    }

    if (tMin > tMax) { 
        tBuffer[idx] = -1;
        pixels[idx] = 0xFF87CEEB; 
        return; 
    }

    const float3 startPos = params.cam + dir * tMin;
    int3 cell = int3(floor(startPos));

    const int3 step = int3(dir.x > 0 ? 1 : -1,
                           dir.y > 0 ? 1 : -1,
                           dir.z > 0 ? 1 : -1);

    const float3 cellF  = float3(cell);
    const float3 border = select(cellF, cellF + 1.0f, step > 0);
    float3 tMaxVals = tMin + (border - startPos) * invDir;

    const float3 tDelta = float3(
        dir.x != 0.0f ? fabs(invDir.x) : 1e20f,
        dir.y != 0.0f ? fabs(invDir.y) : 1e20f,
        dir.z != 0.0f ? fabs(invDir.z) : 1e20f
    );

    const int3 wMin = params.worldMin;
    const int3 wMax = params.worldMin + params.worldSize;
    const int wSX = params.worldSize.x;
    const int wSXY = params.worldSize.x * params.worldSize.y;
    const int csx  = chunkSize[0];
    const int csy  = chunkSize[1];
    const int csxy = csx * csy;

    int hitColor = 0;
    float opacAccum = 1.0f;
    int hitFace = -1;
    float3 hitPos = startPos;
    float t = tMin;

    for (int steps = 0; steps < 500 && t < tMax && opacAccum > 0.01f; ++steps) {

        if (all(cell >= wMin) && all(cell < wMax)) {
            const int3 relCell = cell - wMin;
            const int3 cIdx3   = relCell / CHUNK_SIZE;
            const int chunkIdx = cIdx3.x + cIdx3.y * csx + cIdx3.z * csxy;

            if (chunkGrid[chunkIdx] == 0) {
                float3 exitBorder;
                exitBorder.x = float(wMin.x + (step.x > 0 ? cIdx3.x + 1 : cIdx3.x) * CHUNK_SIZE);
                exitBorder.y = float(wMin.y + (step.y > 0 ? cIdx3.y + 1 : cIdx3.y) * CHUNK_SIZE);
                exitBorder.z = float(wMin.z + (step.z > 0 ? cIdx3.z + 1 : cIdx3.z) * CHUNK_SIZE);

                float3 tExit;
                tExit.x = dir.x != 0.0f ? (exitBorder.x - params.cam.x) * invDir.x : 1e20f;
                tExit.y = dir.y != 0.0f ? (exitBorder.y - params.cam.y) * invDir.y : 1e20f;
                tExit.z = dir.z != 0.0f ? (exitBorder.z - params.cam.z) * invDir.z : 1e20f;

                float tJump = min(min(tExit.x, tExit.y), tExit.z) + 1e-4f;

                if (tJump > t) {
                    t      = tJump;
                    hitPos = params.cam + dir * t;
                    cell   = int3(floor(hitPos));

                    float3 newBorder;
                    newBorder.x = float(step.x > 0 ? cell.x + 1 : cell.x);
                    newBorder.y = float(step.y > 0 ? cell.y + 1 : cell.y);
                    newBorder.z = float(step.z > 0 ? cell.z + 1 : cell.z);
                    tMaxVals.x = dir.x != 0.0f ? (newBorder.x - params.cam.x) * invDir.x : 1e20f;
                    tMaxVals.y = dir.y != 0.0f ? (newBorder.y - params.cam.y) * invDir.y : 1e20f;
                    tMaxVals.z = dir.z != 0.0f ? (newBorder.z - params.cam.z) * invDir.z : 1e20f;
                    continue;
                }
            }
            
            const int vIdx  = relCell.x + relCell.y * wSX + relCell.z * wSXY;
            const int voxel = voxelGrid[vIdx];

            if ((uint)voxel < (uint)params.voxelCount) {

                const int texId = textureLocation[voxel];
                const int overlayId = overlayLocation[voxel];
                const float alpha = opacity[voxel];

                if (texId != -1 && hitFace >= 0) {
                    const float3 localUV = hitPos - floor(hitPos);

                    float u, v;
                    if (hitFace <= 1) {          // X faces
                        u = localUV.z;
                        v = 1.0f - localUV.y;
                    } else if (hitFace <= 3) {   // Y faces
                        u = localUV.x;
                        v = 1.0f - localUV.z;
                    } else {                     // Z faces
                        u = localUV.x;  
                        v = 1.0f - localUV.y;
                    }

                    const int iu = clamp((int)(u * 32.0f), 0, 31);
                    const int iv = clamp((int)(v * 32.0f), 0, 31);
                    const int base = texId * (192 * 32);
                    const int lookupIdx = iv * 192 + hitFace * 32 + iu;
                    hitColor = textureAtlas[base + lookupIdx];
                    opacAccum = 0.0f;
                    if (overlayId != -1){
                        const int overlayBase = overlayId * (192 * 32);
                        const int overlayHitColor = textureAtlas[overlayBase + lookupIdx];
                        const int overlayAlpha = (overlayHitColor >> 24) & 0xFF;
                        if (overlayAlpha != 0 && (overlayHitColor & 0x00FFFFFF) != 0x00FFFFFF) {
                            hitColor = overlayHitColor;
                        }
                    }

                } else {
                    const int c = colors[voxel];
                    const float a = alpha * opacAccum;
                    const float ia = 1.0f - a;

                    const int r = (int)(((c >> 16) & 0xFF) * a + ((hitColor >> 16) & 0xFF) * ia);
                    const int g = (int)(((c >> 8) & 0xFF) * a + ((hitColor >> 8) & 0xFF) * ia);
                    const int b = (int)((c & 0xFF) * a + (hitColor & 0xFF) * ia);
                    hitColor = (0xFF << 24) | (r << 16) | (g << 8) | b;

                    opacAccum *= (1.0f - alpha);

                    if (overlayId != -1){
                        const float3 localUV = hitPos - floor(hitPos);

                        float u, v;
                        if (hitFace <= 1) {          // X faces
                            u = localUV.z;
                            v = 1.0f - localUV.y;
                        } else if (hitFace <= 3) {   // Y faces
                            u = localUV.x;
                            v = 1.0f - localUV.z;
                        } else {                     // Z faces
                            u = localUV.x;  
                            v = 1.0f - localUV.y;
                        }

                        const int iu = clamp((int)(u * 32.0f), 0, 31);
                        const int iv = clamp((int)(v * 32.0f), 0, 31);
                        const int lookupIdx = iv * 192 + hitFace * 32 + iu;
                        const int overlayBase = overlayId * (192 * 32);
                        const int overlayHitColor = textureAtlas[overlayBase + lookupIdx];
                        const int overlayAlpha = (overlayHitColor >> 24) & 0xFF;
                        if (overlayAlpha != 0 && (overlayHitColor & 0x00FFFFFF) != 0x00FFFFFF) {
                            hitColor = overlayHitColor;
                        }
                    }
                }
            }
        }

        const bool xMin = (tMaxVals.x <= tMaxVals.y) && (tMaxVals.x <= tMaxVals.z);
        const bool yMin = !xMin && (tMaxVals.y <= tMaxVals.z);

        if (xMin) {
            t = tMaxVals.x;  tMaxVals.x += tDelta.x;  cell.x += step.x;
            hitFace = step.x > 0 ? 0 : 1;
        } else if (yMin) {
            t = tMaxVals.y;  tMaxVals.y += tDelta.y;  cell.y += step.y;
            hitFace = step.y > 0 ? 3 : 2;
        } else {
            t = tMaxVals.z;  tMaxVals.z += tDelta.z;  cell.z += step.z;
            hitFace = step.z > 0 ? 4 : 5;
        }

        hitPos = params.cam + dir * t;
    }

    const int sky = 0xFF87CEEB;
    const float a = opacAccum;
    const float ia = 1.0f - a;
    const int r = (int)(((sky >> 16) & 0xFF) * a + ((hitColor >> 16) & 0xFF) * ia);
    const int g = (int)(((sky >>  8) & 0xFF) * a + ((hitColor >>  8) & 0xFF) * ia);
    const int b = (int)( (sky & 0xFF) * a + ( hitColor & 0xFF) * ia);
    tBuffer[idx] = t;
    pixels[idx] = (0xFF << 24) | (r << 16) | (g << 8) | b;
}