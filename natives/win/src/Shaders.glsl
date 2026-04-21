#version 430

layout(local_size_x = 8, local_size_y = 8) in;

layout(std430, binding = 0) buffer Pixels { int pixels[]; };
layout(std430, binding = 1) buffer Voxels { int voxelGrid[]; };
layout(std430, binding = 2) buffer Colors { int colors[]; };
layout(std430, binding = 3) buffer Opacity { float opacity[]; };
layout(std430, binding = 4) buffer TextureLoc { int textureLocation[]; };
layout(std430, binding = 5) buffer OverlayLoc { int overlayLocation[]; };
layout(std430, binding = 6) buffer TextureAtlas { int textureAtlas[]; };
layout(std430, binding = 7) buffer ChunkGrid { int chunkGrid[]; };
layout(std430, binding = 8) buffer ChunkSize { int chunkSize[]; };
layout(std430, binding = 9) buffer TBuffer { float tBuffer[]; };

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

const int CHUNK_SIZE = 8;

void main() {
    ivec2 gid = ivec2(gl_GlobalInvocationID.xy);
    int px = gid.x;
    int py = gid.y;
    if (px >= width || py >= height) return;

    int idx = py * width + px;

    float aspect = float(width) / float(height);
    float ndcX = (2.0 * (float(px) + 0.5) / float(width) - 1.0) * aspect * tanFov;
    float ndcY = (1.0 -  2.0 * (float(py) + 0.5) / float(height)) * tanFov;

    vec3 dir = normalize(forward + ndcX * right + ndcY * up);
    vec3 invDir = vec3(
        dir.x != 0.0 ? 1.0 / dir.x : 1e20,
        dir.y != 0.0 ? 1.0 / dir.y : 1e20,
        dir.z != 0.0 ? 1.0 / dir.z : 1e20
    );

    vec3 minB = vec3(worldMin);
    vec3 maxB = vec3(worldMin + worldSize);
    ivec3 worldMax = worldMin + worldSize;

    float tMin = 0.0;
    float tMax = 100.0;

    if (dir.x != 0.0) {
        float tx1 = (minB.x - cam.x) * invDir.x;
        float tx2 = (maxB.x - cam.x) * invDir.x;
        tMin = max(tMin, min(tx1, tx2));
        tMax = min(tMax, max(tx1, tx2));
    } else if (cam.x < minB.x || cam.x > maxB.x) {
        tBuffer[idx] = -1.0; pixels[idx] = 0xFF87CEEB; return;
    }

    if (dir.y != 0.0) {
        float ty1 = (minB.y - cam.y) * invDir.y;
        float ty2 = (maxB.y - cam.y) * invDir.y;
        tMin = max(tMin, min(ty1, ty2));
        tMax = min(tMax, max(ty1, ty2));
    } else if (cam.y < minB.y || cam.y > maxB.y) {
        tBuffer[idx] = -1.0; pixels[idx] = 0xFF87CEEB; return;
    }

    if (dir.z != 0.0) {
        float tz1 = (minB.z - cam.z) * invDir.z;
        float tz2 = (maxB.z - cam.z) * invDir.z;
        tMin = max(tMin, min(tz1, tz2));
        tMax = min(tMax, max(tz1, tz2));
    } else if (cam.z < minB.z || cam.z > maxB.z) {
        tBuffer[idx] = -1.0; pixels[idx] = 0xFF87CEEB; return;
    }

    if (tMin > tMax) {
        tBuffer[idx] = -1.0; pixels[idx] = 0xFF87CEEB; return;
    }

    vec3  startPos = cam + dir * tMin;
    ivec3 cell = ivec3(floor(startPos));

    ivec3 step = ivec3(
        dir.x > 0.0 ? 1 : -1,
        dir.y > 0.0 ? 1 : -1,
        dir.z > 0.0 ? 1 : -1
    );

    vec3 cellF = vec3(cell);
    vec3 border = vec3(
        step.x > 0 ? cellF.x + 1.0 : cellF.x,
        step.y > 0 ? cellF.y + 1.0 : cellF.y,
        step.z > 0 ? cellF.z + 1.0 : cellF.z
    );
    vec3 tMaxVals = tMin + (border - startPos) * invDir;

    vec3 tDelta = vec3(
        dir.x != 0.0 ? abs(invDir.x) : 1e20,
        dir.y != 0.0 ? abs(invDir.y) : 1e20,
        dir.z != 0.0 ? abs(invDir.z) : 1e20
    );

    const int csx = chunkSize[0];
    const int csy = chunkSize[1];
    const int csxy = csx * csy;
    const int wSX = worldSize.x;
    const int wSXY = worldSize.x * worldSize.y;

    int hitColor = 0;
    float opacAccum = 1.0;
    int hitFace = -1;
    vec3 hitPos = startPos;
    float t = tMin;

    for (int steps = 0; steps < 500 && t < tMax && opacAccum > 0.01; ++steps) {

        if (all(greaterThanEqual(cell, worldMin)) && all(lessThan(cell, worldMax))) {
            ivec3 relCell = cell - worldMin;
            ivec3 cIdx3 = relCell / CHUNK_SIZE;
            int chunkIdx = cIdx3.x + cIdx3.y * csx + cIdx3.z * csxy;

            if (chunkGrid[chunkIdx] == 0) {
                vec3 exitBorder = vec3(
                    float(worldMin.x + (step.x > 0 ? cIdx3.x + 1 : cIdx3.x) * CHUNK_SIZE),
                    float(worldMin.y + (step.y > 0 ? cIdx3.y + 1 : cIdx3.y) * CHUNK_SIZE),
                    float(worldMin.z + (step.z > 0 ? cIdx3.z + 1 : cIdx3.z) * CHUNK_SIZE)
                );

                vec3 tExit = vec3(
                    dir.x != 0.0 ? (exitBorder.x - cam.x) * invDir.x : 1e20,
                    dir.y != 0.0 ? (exitBorder.y - cam.y) * invDir.y : 1e20,
                    dir.z != 0.0 ? (exitBorder.z - cam.z) * invDir.z : 1e20
                );

                float tJump = min(min(tExit.x, tExit.y), tExit.z) + 1e-4;

                if (tJump > t) {
                    t = tJump;
                    hitPos = cam + dir * t;
                    cell = ivec3(floor(hitPos));

                    vec3 newBorder = vec3(
                        float(step.x > 0 ? cell.x + 1 : cell.x),
                        float(step.y > 0 ? cell.y + 1 : cell.y),
                        float(step.z > 0 ? cell.z + 1 : cell.z)
                    );
                    tMaxVals = vec3(
                        dir.x != 0.0 ? (newBorder.x - cam.x) * invDir.x : 1e20,
                        dir.y != 0.0 ? (newBorder.y - cam.y) * invDir.y : 1e20,
                        dir.z != 0.0 ? (newBorder.z - cam.z) * invDir.z : 1e20
                    );
                    continue;
                }
            }

            int voxel = voxelGrid[relCell.x + relCell.y * wSX + relCell.z * wSXY];

            if (uint(voxel) < uint(voxCount)) {
                int texId = textureLocation[voxel];
                int overlayId = overlayLocation[voxel];
                float alpha = opacity[voxel];

                if (texId != -1 && hitFace >= 0) {
                    vec3 local = hitPos - floor(hitPos);
                    float u, v;

                    if (hitFace <= 1) {        // X faces
                        u = local.z;
                        v = 1.0 - local.y;
                    } else if (hitFace <= 3) { // Y faces
                        u = local.x;
                        v = 1.0 - local.z;
                    } else {                   // Z faces
                        u = local.x;
                        v = 1.0 - local.y;
                    }

                    int iu = clamp(int(u * 32.0), 0, 31);
                    int iv = clamp(int(v * 32.0), 0, 31);
                    int base = texId * (192 * 32);
                    int lookupIdx = iv * 192 + hitFace * 32 + iu;
                    hitColor = textureAtlas[base + lookupIdx];
                    opacAccum = 0.0;
                    
                    if (overlayId != -1){
                        int overlayBase = overlayId * (192 * 32);
                        int overlayHitColor = textureAtlas[overlayBase + lookupIdx];
                        int overlayAlpha = (overlayHitColor >> 24) & 0xFF;
                        if (overlayAlpha != 0 && (overlayHitColor & 0x00FFFFFF) != 0x00FFFFFF) {
                            hitColor = overlayHitColor;
                        }
                    }

                } else {
                    int c = colors[voxel];
                    float a = alpha * opacAccum;
                    float ia = 1.0 - a;

                    int r = int(((c >> 16) & 0xFF) * a + ((hitColor >> 16) & 0xFF) * ia);
                    int g = int(((c >> 8) & 0xFF) * a + ((hitColor >> 8) & 0xFF) * ia);
                    int b = int((c & 0xFF) * a + (hitColor & 0xFF) * ia);
                    hitColor = (0xFF << 24) | (r << 16) | (g << 8) | b;

                    opacAccum *= (1.0 - alpha);

                    if (overlayId != -1){
                        vec3 local = hitPos - floor(hitPos);
                        float u, v;

                        if (hitFace <= 1) {        // X faces
                            u = local.z;
                            v = 1.0 - local.y;
                        } else if (hitFace <= 3) { // Y faces
                            u = local.x;
                            v = 1.0 - local.z;
                        } else {                   // Z faces
                            u = local.x;
                            v = 1.0 - local.y;
                        }

                        int iu = clamp(int(u * 32.0), 0, 31);
                        int iv = clamp(int(v * 32.0), 0, 31);
                        int lookupIdx = iv * 192 + hitFace * 32 + iu;
                        int overlayBase = overlayId * (192 * 32);
                        int overlayHitColor = textureAtlas[overlayBase + lookupIdx];
                        int overlayAlpha = (overlayHitColor >> 24) & 0xFF;
                        if (overlayAlpha != 0 && (overlayHitColor & 0x00FFFFFF) != 0x00FFFFFF) {
                            hitColor = overlayHitColor;
                        }
                    }
                }
            }
        }

        bool xMin = (tMaxVals.x <= tMaxVals.y) && (tMaxVals.x <= tMaxVals.z);
        bool yMin = !xMin && (tMaxVals.y <= tMaxVals.z);

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

        hitPos = cam + dir * t;
    }

    int sky = 0xFF87CEEB;
    float a = opacAccum;
    float ia = 1.0 - a;
    int r = int(((sky >> 16) & 0xFF) * a + ((hitColor >> 16) & 0xFF) * ia);
    int g = int(((sky >> 8) & 0xFF) * a + ((hitColor >> 8) & 0xFF) * ia);
    int b = int((sky & 0xFF) * a + (hitColor & 0xFF) * ia);

    tBuffer[idx] = t;
    pixels[idx] = (0xFF << 24) | (r << 16) | (g << 8) | b;
}
