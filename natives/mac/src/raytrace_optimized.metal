#include <metal_stdlib>
using namespace metal;

constant int CHUNK_SIZE = 8;

struct RayParams {
    float3 cam;
    float3 forward;
    float3 right;
    float3 up;
    int3   worldMin;
    int3   worldSize;
    int3   chunkGridSize;
    float  tanFov;
    int    voxelCount;
};

// ── helpers ───────────────────────────────────────────────────────────────────

inline bool chunkOccupied(device const uint* chunkGrid,
                           int3 coord, int3 cgSize)
{
    if (any(coord < 0) || any(coord >= cgSize)) return false;
    int flat = coord.x + coord.y * cgSize.x + coord.z * cgSize.x * cgSize.y;
    return (chunkGrid[flat >> 5] >> (flat & 31)) & 1u;
}

inline int sampleVoxel(device const int* voxelGrid,
                        int3 cell, int3 worldMin, int3 worldSize)
{
    return voxelGrid[(cell.x - worldMin.x)
                   + (cell.y - worldMin.y) * worldSize.x
                   + (cell.z - worldMin.z) * worldSize.x * worldSize.y];
}

inline float2 faceUV(int face, float3 hitPos)
{
    float3 lp = hitPos - floor(hitPos);
    switch (face) {
        case 0: case 1: return float2(lp.z, 1.0f - lp.y);
        case 2: case 3: return float2(lp.x, 1.0f - lp.z);
        default:        return float2(lp.x, 1.0f - lp.y);
    }
}

// Compute per-axis t to the next cell boundary, given the ray is at world
// position 'pos' at parameter value 'tNow', stepping by 'stepDir'.
// Works for any cell size; invDir is always in world-space units.
inline float3 nextBoundaryT(float3 pos, int3 cell, int3 stepDir,
                              float cellSize, float tNow, float3 invDir)
{
    float3 r;
    r.x = tNow + ((stepDir.x > 0 ? (float(cell.x + 1) * cellSize - pos.x)
                                  : (float(cell.x)     * cellSize - pos.x)) * invDir.x);
    r.y = tNow + ((stepDir.y > 0 ? (float(cell.y + 1) * cellSize - pos.y)
                                  : (float(cell.y)     * cellSize - pos.y)) * invDir.y);
    r.z = tNow + ((stepDir.z > 0 ? (float(cell.z + 1) * cellSize - pos.z)
                                  : (float(cell.z)     * cellSize - pos.z)) * invDir.z);
    return r;
}

// ── kernel ────────────────────────────────────────────────────────────────────

kernel void raytraceKernel(
    device int*             pixels          [[buffer(0)]],
    device const int*       voxelGrid       [[buffer(1)]],
    device const int*       colors          [[buffer(2)]],
    device const float*     opacity         [[buffer(3)]],
    device const RayParams& params          [[buffer(4)]],
    device const int*       widthBuf        [[buffer(5)]],
    device const int*       heightBuf       [[buffer(6)]],
    device const int*       textureLocation [[buffer(7)]],
    device const int*       textureAtlas    [[buffer(8)]],
    device const uint*      chunkGrid       [[buffer(9)]],
    uint2 gid [[thread_position_in_grid]]
) {
    const int width  = widthBuf[0];
    const int height = heightBuf[0];
    const int px = (int)gid.x;
    const int py = (int)gid.y;
    if (px >= width || py >= height) return;

    const int   idx    = py * width + px;
    const float aspect = float(width) / float(height);
    const float ndcX   =  2.0f * (float(px) + 0.5f) / float(width)  - 1.0f;
    const float ndcY   =  1.0f - 2.0f * (float(py) + 0.5f) / float(height);
    const int   SKY    = (int)0xFF87CEEB;

    const float3 dir = normalize(params.forward
                                 + ndcX * aspect * params.tanFov * params.right
                                 + ndcY * params.tanFov * params.up);

    const float3 invDir = float3(
        dir.x != 0.0f ? 1.0f / dir.x : 1e20f,
        dir.y != 0.0f ? 1.0f / dir.y : 1e20f,
        dir.z != 0.0f ? 1.0f / dir.z : 1e20f);

    const int3   stepDir = int3(dir.x > 0 ? 1 : -1,
                                dir.y > 0 ? 1 : -1,
                                dir.z > 0 ? 1 : -1);
    const float3 tDelta  = float3(fabs(invDir.x), fabs(invDir.y), fabs(invDir.z));

    // ── world AABB clip ───────────────────────────────────────────────────────
    const float3 minB = float3(params.worldMin);
    const float3 maxB = float3(params.worldMin + params.worldSize);

    if (dir.x == 0.0f && (params.cam.x < minB.x || params.cam.x > maxB.x)) { pixels[idx] = SKY; return; }
    if (dir.y == 0.0f && (params.cam.y < minB.y || params.cam.y > maxB.y)) { pixels[idx] = SKY; return; }
    if (dir.z == 0.0f && (params.cam.z < minB.z || params.cam.z > maxB.z)) { pixels[idx] = SKY; return; }

    float3 t0    = (minB - params.cam) * invDir;
    float3 t1    = (maxB - params.cam) * invDir;
    float3 tNear = min(t0, t1);
    float3 tFar  = max(t0, t1);

    float tMin = max(0.0f, max(tNear.x, max(tNear.y, tNear.z)));
    float tMax = min(400.0f, min(tFar.x,  min(tFar.y,  tFar.z)));
    if (tMin > tMax) { pixels[idx] = SKY; return; }

    // ── chunk-level DDA setup ─────────────────────────────────────────────────
    const float  cs      = float(CHUNK_SIZE);
    const float3 tDeltaC = tDelta * cs;

    float3 startPos  = params.cam + dir * tMin;
    int3   chunkCell = int3(floor(startPos / cs));
    float3 tMaxChunk = nextBoundaryT(startPos, chunkCell, stepDir, cs, tMin, invDir);

    // chunk-grid origin in chunk coords
    const int3 cgOrig = int3(floor(float3(params.worldMin) / cs));

    int   hitColor  = 0;
    float opacAccum = 1.0f;
    float t         = tMin;

    for (int cstep = 0; cstep < 800 && t < tMax && opacAccum > 0.01f; ++cstep)
    {
        int3 cgLocal = chunkCell - cgOrig;

        if (chunkOccupied(chunkGrid, cgLocal, params.chunkGridSize))
        {
            // t at which the ray exits this chunk
            float tChunkExit;
            if      (tMaxChunk.x <= tMaxChunk.y && tMaxChunk.x <= tMaxChunk.z) tChunkExit = tMaxChunk.x;
            else if (tMaxChunk.y <= tMaxChunk.z)                                tChunkExit = tMaxChunk.y;
            else                                                                 tChunkExit = tMaxChunk.z;

            float tExit = min(tChunkExit, tMax);

            // ── voxel DDA inside this chunk ───────────────────────────────────
            // Initialise from 't' (the actual entry t), NOT from tMin.
            // This is the fix for the warping — tMin offset was wrong here.
            float3 vEntryPos = params.cam + dir * t;
            int3   vCell     = int3(floor(vEntryPos));
            float3 tMaxV     = nextBoundaryT(vEntryPos, vCell, stepDir, 1.0f, t, invDir);

            float  tv      = t;
            int    hitFace = -1;
            float3 hitPos  = vEntryPos;

            for (int vs = 0; vs < CHUNK_SIZE * 3 + 4 && tv < tExit && opacAccum > 0.01f; ++vs)
            {
                if (all(vCell >= params.worldMin) &&
                    all(vCell <  params.worldMin + params.worldSize))
                {
                    int voxelIdx = sampleVoxel(voxelGrid, vCell,
                                               params.worldMin, params.worldSize);

                    if (voxelIdx >= 0 && voxelIdx < params.voxelCount)
                    {
                        int   c     = colors[voxelIdx];
                        float a     = opacity[voxelIdx] * opacAccum;
                        int   texId = textureLocation[voxelIdx];

                        if (texId != -1 && hitFace >= 0)
                        {
                            float2 uv  = faceUV(hitFace, hitPos);
                            int iu     = clamp(int(uv.x * 32), 0, 31);
                            int iv     = clamp(int(uv.y * 32), 0, 31);
                            int base   = texId * 192 * 32;
                            hitColor   = textureAtlas[base + iv * 192 + hitFace * 32 + iu];
                            opacAccum  = 0.0f;
                        }
                        else
                        {
                            float ia = 1.0f - a;
                            int r = int(float((c >> 16) & 0xFF) * a + float((hitColor >> 16) & 0xFF) * ia);
                            int g = int(float((c >>  8) & 0xFF) * a + float((hitColor >>  8) & 0xFF) * ia);
                            int b = int(float( c        & 0xFF) * a + float( hitColor        & 0xFF) * ia);
                            hitColor  = (0xFF << 24) | (r << 16) | (g << 8) | b;
                            opacAccum *= 1.0f - opacity[voxelIdx];
                        }
                    }
                }

                // Advance voxel DDA — record face & hit position at the crossing
                // point so the NEXT cell knows which face the ray entered through.
                if (tMaxV.x <= tMaxV.y && tMaxV.x <= tMaxV.z) {
                    tv      = tMaxV.x;
                    hitFace = (stepDir.x > 0) ? 0 : 1;
                    hitPos  = params.cam + dir * tv;
                    tMaxV.x += tDelta.x;
                    vCell.x += stepDir.x;
                } else if (tMaxV.y <= tMaxV.z) {
                    tv      = tMaxV.y;
                    hitFace = (stepDir.y > 0) ? 3 : 2;
                    hitPos  = params.cam + dir * tv;
                    tMaxV.y += tDelta.y;
                    vCell.y += stepDir.y;
                } else {
                    tv      = tMaxV.z;
                    hitFace = (stepDir.z > 0) ? 4 : 5;
                    hitPos  = params.cam + dir * tv;
                    tMaxV.z += tDelta.z;
                    vCell.z += stepDir.z;
                }
            } // voxel loop
        } // if chunk occupied

        // Advance chunk DDA
        if (tMaxChunk.x <= tMaxChunk.y && tMaxChunk.x <= tMaxChunk.z) {
            t = tMaxChunk.x; tMaxChunk.x += tDeltaC.x; chunkCell.x += stepDir.x;
        } else if (tMaxChunk.y <= tMaxChunk.z) {
            t = tMaxChunk.y; tMaxChunk.y += tDeltaC.y; chunkCell.y += stepDir.y;
        } else {
            t = tMaxChunk.z; tMaxChunk.z += tDeltaC.z; chunkCell.z += stepDir.z;
        }
    }

    // Sky blend
    float a = opacAccum;
    int r = int(float((SKY >> 16) & 0xFF) * a + float((hitColor >> 16) & 0xFF) * (1.0f - a));
    int g = int(float((SKY >>  8) & 0xFF) * a + float((hitColor >>  8) & 0xFF) * (1.0f - a));
    int b = int(float( SKY        & 0xFF) * a + float( hitColor        & 0xFF) * (1.0f - a));
    pixels[idx] = (0xFF << 24) | (r << 16) | (g << 8) | b;
}
