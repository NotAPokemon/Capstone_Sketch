#include <metal_stdlib>
using namespace metal;

struct EntityParams {
    float3 cam;
    float3 forward;
    float3 right;
    float3 up;
    float tanFov;
    int entityCount;
    int totalVoxels;
};

struct EntityHeader {
    packed_float3 worldPos;
    int voxelOffset;
    int voxelCount;
    float rot[9];
    float radius;
    int bvhOffset;
    int pad1;
};


struct BVHot {
    packed_float3 localPos;
    float size;
};

struct BVCold {
    int color;
    float opacity;
    int textureId;
    int pad;
};

struct BVHNode {
    packed_float3 aabbMin;
    int leftChild;
    packed_float3 aabbMax;
    int rightChild;
    int voxelIndex;
    int pad0, pad1, pad2;
};


inline float3 unpackRGB(int packed) {
    return float3(
        float((packed >> 16) & 0xFF) / 255.0,
        float((packed >>  8) & 0xFF) / 255.0,
        float( packed & 0xFF) / 255.0
    );
}

inline int packARGB(float3 c, float a) {
    return (int(clamp(a,   0.0, 1.0) * 255) << 24)
         | (int(clamp(c.r, 0.0, 1.0) * 255) << 16)
         | (int(clamp(c.g, 0.0, 1.0) * 255) <<  8)
         | int(clamp(c.b, 0.0, 1.0) * 255);
}

// Transpose multiply — brings world vector into entity local space
inline float3 rotateInv(const float rot[9], float3 v) {
    return float3(
        rot[0]*v.x + rot[1]*v.y + rot[2]*v.z,
        rot[3]*v.x + rot[4]*v.y + rot[5]*v.z,
        rot[6]*v.x + rot[7]*v.y + rot[8]*v.z
    );
}

struct HitResult {
    float t;
    int face;
};

inline HitResult rayBox(float3 ro, float3 rd, float3 center, float halF) {
    float3 invD  = 1.0 / rd;
    float3 t0 = (center - halF - ro) * invD;
    float3 t1 = (center + halF - ro) * invD;
    float3 tNear = min(t0, t1);
    float3 tFar  = max(t0, t1);

    float tMin = max(max(tNear.x, tNear.y), tNear.z);
    float tMax = min(min(tFar.x,  tFar.y),  tFar.z);

    HitResult r = { INFINITY, -1 };
    if (tMin > tMax || tMax < 0.0) return r;

    r.t = (tMin >= 0.0) ? tMin : tMax;

    if (tNear.x >= tNear.y && tNear.x >= tNear.z) r.face = (invD.x > 0.0) ? 1 : 0;
    else if (tNear.y >= tNear.z) r.face = (invD.y > 0.0) ? 3 : 2;
    else r.face = (invD.z > 0.0) ? 5 : 4;

    return r;
}

inline HitResult rayAABB(float3 ro, float3 rd, float3 aabbMin, float3 aabbMax) {
    float3 invD = 1.0 / rd;
    float3 t0 = (aabbMin - ro) * invD;
    float3 t1 = (aabbMax - ro) * invD;
    float3 tNear = min(t0, t1);
    float3 tFar  = max(t0, t1);

    float tMin = max(max(tNear.x, tNear.y), tNear.z);
    float tMax = min(min(tFar.x,  tFar.y),  tFar.z);

    HitResult r = { INFINITY, -1 };
    if (tMin > tMax || tMax < 0.0) return r;
    r.t = (tMin >= 0.0) ? tMin : tMax;
    return r;
}

inline float faceLighting(int face) {
    const float lut[6] = { 0.80, 0.70, 1.00, 0.55, 0.75, 0.65 };
    return (face >= 0 && face < 6) ? lut[face] : 1.0;
}

kernel void entityKernel(
    device int* pixels [[buffer(0)]],
    constant int& screenW [[buffer(1)]],
    constant int& screenH [[buffer(2)]],
    constant EntityParams& params [[buffer(3)]],
    device const EntityHeader* entities [[buffer(4)]],
    device const BVHot* bvHot [[buffer(5)]],
    device const BVCold* bvCold [[buffer(6)]],
    device const int* atlas [[buffer(7)]],
    device const float* tBuffer [[buffer(8)]],
    device const BVHNode* bvhNodes [[buffer(9)]],
    uint2 gid [[thread_position_in_grid]]
) {
    if ((int)gid.x >= screenW || (int)gid.y >= screenH) return;

    float aspect = float(screenW) / float(screenH);
    float ndcX = ((float(gid.x) + 0.5) / float(screenW) * 2.0 - 1.0) * params.tanFov * aspect;
    float ndcY = -((float(gid.y) + 0.5) / float(screenH) * 2.0 - 1.0) * params.tanFov;
    float3 worldRd = normalize(params.forward + ndcX * params.right + ndcY * params.up);

    int pixIdx = (int)gid.y * screenW + (int)gid.x;
    int bg = pixels[pixIdx];
    float3 outColor = unpackRGB(bg);
    float outAlpha = float((bg >> 24) & 0xFF) / 255.0;

    float tVal = INFINITY;

    for (int e = 0; e < params.entityCount; e++) {
        EntityHeader ent = entities[e];

        float3 localRo = rotateInv(ent.rot, params.cam - float3(ent.worldPos));
        float3 localRd = rotateInv(ent.rot, worldRd);
        float3 oc = localRo;
        float b = dot(oc, localRd);
        float c = dot(oc, oc) - ent.radius * ent.radius;
        if (b * b - c < 0.0) continue;

        float bestT = INFINITY;
        int bestFace = -1;
        int bestVox = -1;

        int stack[32];
        int stackTop = 0;
        stack[stackTop++] = 0;

        while (stackTop > 0) {
            BVHNode node = bvhNodes[ent.bvhOffset + stack[--stackTop]];

            HitResult h = rayAABB(localRo, localRd, float3(node.aabbMin), float3(node.aabbMax));
            if (h.t == INFINITY || h.t >= bestT) continue;

            if (node.leftChild == -1) {
                BVHot hot = bvHot[ent.voxelOffset + node.voxelIndex];
                HitResult vh = rayBox(localRo, localRd, float3(hot.localPos), hot.size * 0.5);
                if (vh.t < bestT) {
                    bestT = vh.t;
                    bestFace = vh.face;
                    bestVox = node.voxelIndex;
                }
            } else {
                stack[stackTop++] = node.leftChild;
                stack[stackTop++] = node.rightChild;
            }
        }

        if (bestVox < 0) continue;

        float3 hitLocal = localRo + localRd * bestT;
        float3 hitWorld = float3(ent.worldPos) + float3(
            ent.rot[0]*hitLocal.x + ent.rot[3]*hitLocal.y + ent.rot[6]*hitLocal.z,
            ent.rot[1]*hitLocal.x + ent.rot[4]*hitLocal.y + ent.rot[7]*hitLocal.z,
            ent.rot[2]*hitLocal.x + ent.rot[5]*hitLocal.y + ent.rot[8]*hitLocal.z
        );
        float worldT = dot(hitWorld - params.cam, worldRd);
        if (worldT < tVal) {
            tVal = worldT;
        }

        BVCold cold = bvCold[ent.voxelOffset + bestVox];
        float3 voxColor = (cold.textureId < 0) ? unpackRGB(cold.color) : float3(1.0);
        voxColor *= faceLighting(bestFace);
        float a = cold.opacity;

        outColor = voxColor * a + outColor * (1.0 - a);
        outAlpha = a + outAlpha * (1.0 - a);
    }

    int skyVal = 0xFF87CEEB;
    if (pixels[pixIdx] != skyVal && tBuffer[pixIdx] < tVal){
        return;
    }
    pixels[pixIdx] = packARGB(outColor, outAlpha);
}