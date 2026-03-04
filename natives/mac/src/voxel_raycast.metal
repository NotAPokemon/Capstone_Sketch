//  voxel_raycast.metal
//  =============================================================================
//  Voxel Ray Tracer — Majercik et al. 2018
//  "A Ray-Box Intersection Algorithm and Efficient Dynamic Voxel Rendering"
//  https://jcgt.org/published/0007/03/04/
//
//  Buffer bindings — must match C++ encoder setBuffer calls exactly:
//    0  pixels           device int*         packed ARGB output (width × height)
//    1  voxelGrid        device int*         flat [x*y*z]: -1=empty, else voxel idx
//    2  voxColor         device int*         packed ARGB tint per voxel
//    3  voxOpacity       device float*       opacity [0,1] per voxel
//    4  params           device RayParams*   camera + world info
//    5  width            device int*         render width
//    6  height           device int*         render height
//    7  textureLocation  device int*         atlas texture ID per voxel (1 int each)
//    8  textureAtlas     device int*         flat packed-ARGB atlas pixels
//
//  TextureAtlas memory layout (matches dev.korgi.game.rendering.TextureAtlas):
//
//    getAtlas() produces a flat int[] where each texture occupies 192*32 = 6144 ints.
//    Inside one texture, 6 x 32-pixel faces are packed side-by-side in X:
//
//      face 0 (+X / right)  cols [  0 .. 31]
//      face 1 (-X / left)   cols [ 32 .. 63]
//      face 2 (+Y / top)    cols [ 64 .. 95]
//      face 3 (-Y / bottom) cols [ 96 ..127]
//      face 4 (+Z / front)  cols [128 ..159]
//      face 5 (-Z / back)   cols [160 ..191]
//
//    Pixel address:
//      index = texId * 192 * 32   +   row * 192   +   face * 32   +   col
//              (row, col ∈ [0,31])
//  =============================================================================

#include <metal_stdlib>
using namespace metal;

// =============================================================================
// RayParams
//
// simd_float3 / simd_int3 in a C++ struct are 16-byte aligned (4 floats wide).
// Metal's float3 / int3 match this — both are 16 bytes in a struct.
// So the layout lines up naturally when using float3/int3 here.
// =============================================================================
struct RayParams {
    float3  cam;          // 16 bytes (xyz + 4-byte pad)
    float3  forward;
    float3  right;
    float3  up;
    int3    worldMin;     // 16 bytes (xyz + 4-byte pad)
    int3    worldSize;
    float   tanFov;
    int     voxelCount;
};

// =============================================================================
// Utility
// =============================================================================

inline int packARGB(float4 c) {
    int4 ci = int4(clamp(c, 0.0f, 1.0f) * 255.0f + 0.5f);
    return (ci.w << 24) | (ci.x << 16) | (ci.y << 8) | ci.z;
}

inline float4 unpackARGB(int v) {
    return float4(
        float((v >> 16) & 0xFF),
        float((v >>  8) & 0xFF),
        float( v        & 0xFF),
        float((v >> 24) & 0xFF)
    ) / 255.0f;
}

inline float maxComp(float3 v) { return max(max(v.x, v.y), v.z); }
inline float minComp(float3 v) { return min(min(v.x, v.y), v.z); }

// =============================================================================
// Majercik et al. Ray-Box Intersection  (Listing 5 in the paper)
// Branchless bitmask avoids warp divergence.
// =============================================================================
inline bool intersectBox(
    float3         rayOrigin,
    float3         rayDir,
    float3         invRayDir,
    float3         boxCenter,
    float3         boxRadius,
    bool           canStartInBox,
    thread float&  dist,
    thread float3& normal)
{
    float3 ro      = rayOrigin - boxCenter;
    float  winding = (canStartInBox && maxComp(abs(ro) / boxRadius) < 1.0f) ? -1.0f : 1.0f;
    float3 sgn     = -sign(rayDir);

    float3 d = (boxRadius * winding * sgn - ro) * invRayDir;

    bool3 hit = bool3(
        (d.x >= 0.0f) && (abs(ro.y + rayDir.y * d.x) < boxRadius.y) && (abs(ro.z + rayDir.z * d.x) < boxRadius.z),
        (d.y >= 0.0f) && (abs(ro.z + rayDir.z * d.y) < boxRadius.z) && (abs(ro.x + rayDir.x * d.y) < boxRadius.x),
        (d.z >= 0.0f) && (abs(ro.x + rayDir.x * d.z) < boxRadius.x) && (abs(ro.y + rayDir.y * d.z) < boxRadius.y)
    );

    sgn    = hit.x ? float3(sgn.x, 0, 0) : hit.y ? float3(0, sgn.y, 0) : float3(0, 0, hit.z ? sgn.z : 0);
    dist   = hit.x ? d.x : (hit.y ? d.y : d.z);
    normal = sgn;

    return (sgn.x != 0.0f) || (sgn.y != 0.0f) || (sgn.z != 0.0f);
}

// =============================================================================
// Grid helpers
// =============================================================================

inline int gridIndex(int3 cell, int3 sz) {
    return cell.x + sz.x * (cell.y + sz.y * cell.z);
}

// =============================================================================
// TextureAtlas face selection
//
// Maps outward surface normal → face index matching TextureAtlas convention:
//   0 = +X (right)   1 = -X (left)
//   2 = +Y (top)     3 = -Y (bottom)
//   4 = +Z (front)   5 = -Z (back)
// =============================================================================
inline int normalToFace(float3 n) {
    if (n.x >  0.5f) return 0;
    if (n.x < -0.5f) return 1;
    if (n.y >  0.5f) return 2;
    if (n.y < -0.5f) return 3;
    if (n.z >  0.5f) return 4;
    return 5;
}

// Compute UV ∈ [0,1] for the face determined by the surface normal.
inline float2 faceUV(float3 hitPos, float3 normal, float3 boxCenter) {
    float3 local = hitPos - boxCenter;  // ∈ [-0.5, 0.5]
    if (abs(normal.x) > 0.5f) return fract(float2( local.z,  local.y) + 0.5f);
    if (abs(normal.y) > 0.5f) return fract(float2( local.x,  local.z) + 0.5f);
                               return fract(float2( local.x,  local.y) + 0.5f);
}

// Sample the flat atlas array.
// index = texId * 192 * 32  +  row * 192  +  face * 32  +  col
inline float4 sampleAtlas(
    int               texId,
    int               face,
    float2            uv,
    const device int* atlasData,
    int               atlasTexCount)
{
    if (atlasTexCount <= 0 || texId < 0 || texId >= atlasTexCount)
        return float4(1.0f);

    const int FACE_PX    = 32;
    const int ATLAS_W    = 192;          // 6 * 32
    const int TEX_PIXELS = ATLAS_W * FACE_PX;   // 6144

    int col = clamp(int(uv.x * float(FACE_PX - 1) + 0.5f), 0, FACE_PX - 1);
    int row = clamp(int(uv.y * float(FACE_PX - 1) + 0.5f), 0, FACE_PX - 1);

    int idx = texId * TEX_PIXELS  +  row * ATLAS_W  +  face * FACE_PX  +  col;
    return unpackARGB(atlasData[idx]);
}

// =============================================================================
// Simple diffuse shading
// =============================================================================
inline float3 shade(float3 albedo, float3 normal) {
    float3 lightDir = normalize(float3(0.6f, 1.0f, 0.4f));
    float  diff     = max(dot(normal, lightDir), 0.0f);
    return albedo * 0.25f + albedo * diff * 0.75f;
}

// =============================================================================
// DDA voxel traversal (Amanatides & Woo) + Majercik intersection per cell
// =============================================================================
float4 traceRay(
    float3               rayOrigin,
    float3               rayDir,
    const device int*    voxelGrid,
    const device int*    voxColor,
    const device float*  voxOpacity,
    const device int*    textureLocation,
    const device int*    atlasData,
    int3                 orig,
    int3                 sz,
    int                  voxCount,
    int                  atlasTexCount)
{
    float3 invDir = 1.0f / (rayDir + 1e-20f);
    float3 fOrig  = float3(orig);

    // World AABB early-out
    float3 t0     = (fOrig              - rayOrigin) * invDir;
    float3 t1     = (fOrig + float3(sz) - rayOrigin) * invDir;
    float  tStart = max(maxComp(min(t0, t1)), 0.0f);
    float  tEnd   = minComp(max(t0, t1));
    if (tStart > tEnd) return float4(0.0f);

    // Step into the grid
    float3 entry = rayOrigin + rayDir * (tStart + 1e-4f);
    int3   cell  = clamp(int3(floor(entry)) - orig, int3(0), sz - 1);

    int3   step   = int3(sign(rayDir));
    float3 tDelta = abs(invDir);
    float3 tMax   = (float3(orig + cell + max(step, int3(0))) - rayOrigin) * invDir;

    for (int i = 0; i < 512; ++i) {
        if (any(cell < int3(0)) || any(cell >= sz)) break;

        int voxIdx = voxelGrid[gridIndex(cell, sz)];

        if (voxIdx >= 0 && voxIdx < voxCount) {
            float opac = voxOpacity[voxIdx];

            if (opac > 0.01f) {
                float3 boxCenter = float3(orig + cell) + 0.5f;
                float  dist;
                float3 nrm;

                if (intersectBox(rayOrigin, rayDir, invDir,
                                 boxCenter, float3(0.5f),
                                 false, dist, nrm)) {

                    float3 hitPos = rayOrigin + rayDir * dist;
                    float2 uv     = faceUV(hitPos, nrm, boxCenter);
                    int    face   = normalToFace(nrm);
                    int    texId  = textureLocation[voxIdx];   // single int per voxel

                    float4 texCol = sampleAtlas(texId, face, uv, atlasData, atlasTexCount);
                    float4 tint   = unpackARGB(voxColor[voxIdx]);
                    float4 albedo = texCol * tint;

                    return float4(shade(albedo.rgb, nrm), albedo.a * opac);
                }
            }
        }

        // Advance DDA
        if (tMax.x < tMax.y) {
            if (tMax.x < tMax.z) { cell.x += step.x; tMax.x += tDelta.x; }
            else                  { cell.z += step.z; tMax.z += tDelta.z; }
        } else {
            if (tMax.y < tMax.z) { cell.y += step.y; tMax.y += tDelta.y; }
            else                  { cell.z += step.z; tMax.z += tDelta.z; }
        }
    }

    return float4(0.0f);   // miss → transparent black
}

// =============================================================================
// Kernel entry point
// =============================================================================
kernel void voxelRaycast(
    device  int*         pixels           [[ buffer(0) ]],
    device  int*         voxelGrid        [[ buffer(1) ]],
    device  int*         voxColor         [[ buffer(2) ]],
    device  float*       voxOpacity       [[ buffer(3) ]],
    device  RayParams*   params           [[ buffer(4) ]],
    device  int*         widthBuf         [[ buffer(5) ]],
    device  int*         heightBuf        [[ buffer(6) ]],
    device  int*         textureLocation  [[ buffer(7) ]],
    device  int*         textureAtlas     [[ buffer(8) ]],
    uint2                gid              [[ thread_position_in_grid ]])
{
    int W = widthBuf[0];
    int H = heightBuf[0];
    if ((int)gid.x >= W || (int)gid.y >= H) return;

    // Camera
    float3 camPos = params->cam;
    float3 fwd    = normalize(params->forward);
    float3 right  = normalize(params->right);
    float3 up     = normalize(params->up);

    // Primary ray (pinhole)
    float aspect = float(W) / float(H);
    float ndcX   = (float(gid.x) + 0.5f) / float(W) * 2.0f - 1.0f;
    float ndcY   = 1.0f - (float(gid.y) + 0.5f) / float(H) * 2.0f;

    float3 rayDir = normalize(fwd
                            + right * (ndcX * params->tanFov * aspect)
                            + up    * (ndcY * params->tanFov));

    // TextureAtlas.amt — bump this when you add new block types
    const int atlasTexCount = 3;   // MINECRAFT_GRASS_BLOCK, DUNGEON_BLOCK, GALAXY_BLOCK

    float4 color = traceRay(
        camPos, rayDir,
        voxelGrid, voxColor, voxOpacity,
        textureLocation, textureAtlas,
        params->worldMin, params->worldSize,
        params->voxelCount, atlasTexCount);

    pixels[gid.y * W + gid.x] = packARGB(color);
}
