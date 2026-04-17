#version 430

layout(local_size_x = 8, local_size_y = 8) in;

struct EntityParams {
    vec3 cam;
    float tanFov;

    vec3 forward;
    int entityCount;

    vec3 right;
    int totalVoxels;

    vec3 up;
    int width;

    int height;
    int pad0, pad1, pad2;
};

struct EntityHeader {
    vec3 worldPos;
    int voxelOffset;

    int voxelCount;
    float rot[9];

    float radius;
    int bvhOffset;
    int pad1;
};

struct BVHot {
    vec3 localPos;
    float size;
};

struct BVCold {
    int color;
    float opacity;
    int textureId;
    int pad;
};

struct BVHNode {
    vec3 aabbMin;
    int leftChild;

    vec3 aabbMax;
    int rightChild;

    int voxelIndex;
    int pad0, pad1, pad2;
};

layout(std430, binding = 0) buffer Pixels { int pixels[]; };
layout(std430, binding = 1) buffer Params { EntityParams params; };
layout(std430, binding = 2) buffer Headers { EntityHeader entities[]; };
layout(std430, binding = 3) buffer BVHotBuf { BVHot bvHot[]; };
layout(std430, binding = 4) buffer BVColdBuf { BVCold bvCold[]; };
layout(std430, binding = 5) buffer Atlas { int atlas[]; };
layout(std430, binding = 6) buffer TBuffer { float tBuffer[]; };
layout(std430, binding = 7) buffer BVHBuf { BVHNode bvhNodes[]; };


int packARGB(vec3 c, float a) {
    return (int(clamp(a, 0.0, 1.0) * 255.0) << 24) |
           (int(clamp(c.r, 0.0, 1.0) * 255.0) << 16) |
           (int(clamp(c.g, 0.0, 1.0) * 255.0) << 8) |
           int(clamp(c.b, 0.0, 1.0) * 255.0);
}

vec3 rotateInv(float rot[9], vec3 v) {
    return vec3(
        rot[0]*v.x + rot[3]*v.y + rot[6]*v.z,
        rot[1]*v.x + rot[4]*v.y + rot[7]*v.z,
        rot[2]*v.x + rot[5]*v.y + rot[8]*v.z
    );
}

struct HitResult {
    float t;
    int face;
};

HitResult rayBox(vec3 ro, vec3 rd, vec3 center, float halfF) {
    vec3 invD = 1.0 / rd;
    vec3 t0 = (center - halfF - ro) * invD;
    vec3 t1 = (center + halfF - ro) * invD;

    vec3 tNear = min(t0, t1);
    vec3 tFar  = max(t0, t1);

    float tMin = max(max(tNear.x, tNear.y), tNear.z);
    float tMax = min(min(tFar.x, tFar.y), tFar.z);

    HitResult r;
    r.t = 1e30;
    r.face = -1;

    if (tMin > tMax || tMax < 0.0) return r;

    r.t = (tMin >= 0.0) ? tMin : tMax;

    if (tNear.x >= tNear.y && tNear.x >= tNear.z)
        r.face = (invD.x > 0.0) ? 1 : 0;
    else if (tNear.y >= tNear.z)
        r.face = (invD.y > 0.0) ? 3 : 2;
    else
        r.face = (invD.z > 0.0) ? 5 : 4;

    return r;
}

HitResult rayAABB(vec3 ro, vec3 rd, vec3 mn, vec3 mx) {
    vec3 invD = 1.0 / rd;
    vec3 t0 = (mn - ro) * invD;
    vec3 t1 = (mx - ro) * invD;

    vec3 tNear = min(t0, t1);
    vec3 tFar  = max(t0, t1);

    float tMin = max(max(tNear.x, tNear.y), tNear.z);
    float tMax = min(min(tFar.x, tFar.y), tFar.z);

    HitResult r;
    r.t = 1e30;
    r.face = -1;

    if (tMin > tMax || tMax < 0.0) return r;
    r.t = (tMin >= 0.0) ? tMin : tMax;
    return r;
}

float faceLighting(int face) {
    float lut[6] = float[6](0.80, 0.70, 1.00, 0.55, 0.75, 0.65);
    return (face >= 0 && face < 6) ? lut[face] : 1.0;
}

void main() {
    ivec2 gid = ivec2(gl_GlobalInvocationID.xy);
    if (gid.x >= params.width || gid.y >= params.height) return;

    float aspect = float(params.width) / float(params.height);

    float ndcX = ((float(gid.x) + 0.5) / float(params.width) * 2.0 - 1.0)
                 * params.tanFov * aspect;

    float ndcY = -((float(gid.y) + 0.5) / float(params.height) * 2.0 - 1.0)
                 * params.tanFov;

    vec3 worldRd = normalize(params.forward + ndcX * params.right + ndcY * params.up);

    int pixIdx = gid.y * params.width + gid.x;

    int bg = pixels[pixIdx];
    vec3 outColor = vec3(
        float((bg >> 16) & 0xFF) / 255.0,
        float((bg >> 8) & 0xFF) / 255.0,
        float(bg & 0xFF) / 255.0
    );
    float outAlpha = float((bg >> 24) & 0xFF) / 255.0;

    float tVal = 1e30;

    for (int e = 0; e < params.entityCount; e++) {
        EntityHeader ent = entities[e];

        vec3 localRo = rotateInv(ent.rot, params.cam - ent.worldPos);
        vec3 localRd = rotateInv(ent.rot, worldRd);

        vec3 oc = localRo;
        float b = dot(oc, localRd);
        float c = dot(oc, oc) - ent.radius * ent.radius;

        if (b * b - c < 0.0) continue;

        float bestT = 1e30;
        int bestFace = -1;
        int bestVox = -1;

        int stack[32];
        int stackTop = 0;
        stack[stackTop++] = ent.bvhOffset;

        while (stackTop > 0) {
            BVHNode node = bvhNodes[stack[--stackTop]];

            HitResult h = rayAABB(localRo, localRd, node.aabbMin, node.aabbMax);
            if (h.t >= bestT) continue;

            if (node.leftChild == -1) {
                BVHot hot = bvHot[ent.voxelOffset + node.voxelIndex];
                HitResult vh = rayBox(localRo, localRd, hot.localPos, hot.size * 0.5);

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

        vec3 hitLocal = localRo + localRd * bestT;

        vec3 hitWorld = ent.worldPos + vec3(
            ent.rot[0]*hitLocal.x + ent.rot[3]*hitLocal.y + ent.rot[6]*hitLocal.z,
            ent.rot[1]*hitLocal.x + ent.rot[4]*hitLocal.y + ent.rot[7]*hitLocal.z,
            ent.rot[2]*hitLocal.x + ent.rot[5]*hitLocal.y + ent.rot[8]*hitLocal.z
        );

        float worldT = dot(hitWorld - params.cam, worldRd);

        if (worldT < tVal) {
            tVal = worldT;

            BVCold cold = bvCold[ent.voxelOffset + bestVox];

            vec3 voxColor = (cold.textureId < 0)
                ? vec3(
                    float((cold.color >> 16) & 0xFF) / 255.0,
                    float((cold.color >> 8) & 0xFF) / 255.0,
                    float(cold.color & 0xFF) / 255.0
                )
                : vec3(1.0);

            voxColor *= faceLighting(bestFace);
            float a = cold.opacity;

            outColor = voxColor * a + outColor * (1.0 - a);
            outAlpha = a + outAlpha * (1.0 - a);
        }
    }

    int skyVal = 0xFF87CEEB;

    if (pixels[pixIdx] != skyVal && tBuffer[pixIdx] < tVal) {
        return;
    }

    pixels[pixIdx] = packARGB(outColor, outAlpha);
}