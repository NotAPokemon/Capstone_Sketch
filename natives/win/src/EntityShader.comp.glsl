#version 430

layout(local_size_x = 8, local_size_y = 8) in;

layout(std430, binding = 0) buffer Pixels { int pixels[]; };

layout(std430, binding = 1) buffer Params {
    vec3 cam; float tanFov;
    vec3 fwd; int entityCount;
    vec3 right; int totalVoxels;
    vec3 up; int screenW;
    int screenH;
    int pad0; intpad1;
};

layout(std430, binding = 2) buffer Headers {
    int headerData[];
};

layout(std430, binding = 3) buffer BVHotBuf {
    float bvHotData[];
};

layout(std430, binding = 4) buffer BVColdBuf {
    int bvColdData[];
};

layout(std430, binding = 5) buffer Atlas { int atlas[]; };
layout(std430, binding = 6) buffer TBuf { float tBuffer[]; };

layout(std430, binding = 7) buffer BVHNodes {
    int nodeData[];
};


const int ENT_STRIDE = 16;
const int NODE_STRIDE = 12;
const int BVHOT_STRIDE = 4;
const int BVCOLD_STRIDE = 4;

vec3 entWorldPos(int e) { int b=e*ENT_STRIDE; return vec3(intBitsToFloat(headerData[b]), intBitsToFloat(headerData[b+1]), intBitsToFloat(headerData[b+2])); }
int entVoxOffset(int e) { return headerData[e*ENT_STRIDE + 3]; }
int entVoxCount(int e) { return headerData[e*ENT_STRIDE + 4]; }
float entRot(int e, int i) { return intBitsToFloat(headerData[e*ENT_STRIDE + 5 + i]); } 
float entRadius(int e) { return intBitsToFloat(headerData[e*ENT_STRIDE + 14]); }
int entBvhOffset(int e) { return headerData[e*ENT_STRIDE + 15]; }

vec3 nodeAabbMin(int n) { int b=n*NODE_STRIDE; return vec3(intBitsToFloat(nodeData[b]), intBitsToFloat(nodeData[b+1]), intBitsToFloat(nodeData[b+2])); }
int nodeLeft(int n) { return nodeData[n*NODE_STRIDE + 3]; }
vec3 nodeAabbMax(int n) { int b=n*NODE_STRIDE; return vec3(intBitsToFloat(nodeData[b+4]), intBitsToFloat(nodeData[b+5]), intBitsToFloat(nodeData[b+6])); }
int nodeRight(int n) { return nodeData[n*NODE_STRIDE + 7]; }
int nodeVoxIdx(int n) { return nodeData[n*NODE_STRIDE + 8]; }

vec3 hotPos(int base, int vi)  { int b=(base+vi)*BVHOT_STRIDE; return vec3(bvHotData[b], bvHotData[b+1], bvHotData[b+2]); }
float hotSize(int base, int vi) { return bvHotData[(base+vi)*BVHOT_STRIDE + 3]; }

int coldColor(int base, int vi) { return bvColdData[(base+vi)*BVCOLD_STRIDE]; }
float coldOpacity(int base, int vi) { return intBitsToFloat(bvColdData[(base+vi)*BVCOLD_STRIDE + 1]); }
int coldTexId(int base, int vi) { return bvColdData[(base+vi)*BVCOLD_STRIDE + 2]; }

vec3 unpackRGB(int packed) {
    return vec3(
        float((packed >> 16) & 0xFF) / 255.0,
        float((packed >> 8) & 0xFF) / 255.0,
        float(packed & 0xFF) / 255.0
    );
}

int packARGB(vec3 c, float a) {
    return (int(clamp(a, 0.0, 1.0) * 255.0) << 24)
         | (int(clamp(c.r, 0.0, 1.0) * 255.0) << 16)
         | (int(clamp(c.g, 0.0, 1.0) * 255.0) <<  8)
         |  int(clamp(c.b, 0.0, 1.0) * 255.0);
}

vec3 rotateInv(int e, vec3 v) {
    return vec3(
        entRot(e,0)*v.x + entRot(e,3)*v.y + entRot(e,6)*v.z,
        entRot(e,1)*v.x + entRot(e,4)*v.y + entRot(e,7)*v.z,
        entRot(e,2)*v.x + entRot(e,5)*v.y + entRot(e,8)*v.z
    );
}


vec2 rayAABB(vec3 ro, vec3 rd, vec3 aabbMin, vec3 aabbMax) {
    vec3 invD  = 1.0 / rd;
    vec3 t0 = (aabbMin - ro) * invD;
    vec3 t1 = (aabbMax - ro) * invD;
    vec3 tNear = min(t0, t1);
    vec3 tFar = max(t0, t1);
    float tMin = max(max(tNear.x, tNear.y), tNear.z);
    float tMax = min(min(tFar.x,  tFar.y),  tFar.z);
    if (tMin > tMax || tMax < 0.0) return vec2(1e20, -1.0);
    return vec2((tMin >= 0.0) ? tMin : tMax, 0.0);
}


vec2 rayBox(vec3 ro, vec3 rd, vec3 center, float half_size) {
    vec3 invD  = 1.0 / rd;
    vec3 t0 = (center - half_size - ro) * invD;
    vec3 t1 = (center + half_size - ro) * invD;
    vec3 tNear = min(t0, t1);
    vec3 tFar = max(t0, t1);
    float tMin = max(max(tNear.x, tNear.y), tNear.z);
    float tMax = min(min(tFar.x,  tFar.y),  tFar.z);
    if (tMin > tMax || tMax < 0.0) return vec2(1e20, -1.0);

    float t = (tMin >= 0.0) ? tMin : tMax;

    int face;
    if (tNear.x >= tNear.y && tNear.x >= tNear.z) face = (invD.x > 0.0) ? 1 : 0;
    else if (tNear.y >= tNear.z) face = (invD.y > 0.0) ? 3 : 2;
    else face = (invD.z > 0.0) ? 5 : 4;

    return vec2(t, float(face));
}

float faceLighting(int face) {
    if (face == 0) return 0.80;
    if (face == 1) return 0.70;
    if (face == 2) return 1.00;
    if (face == 3) return 0.55;
    if (face == 4) return 0.75;
    if (face == 5) return 0.65;
    return 1.0;
}


void main() {
    ivec2 gid = ivec2(gl_GlobalInvocationID.xy);
    if (gid.x >= screenW || gid.y >= screenH) return;

    float aspect = float(screenW) / float(screenH);
    float ndcX = ((float(gid.x) + 0.5) / float(screenW) * 2.0 - 1.0) * tanFov * aspect;
    float ndcY = -((float(gid.y) + 0.5) / float(screenH) * 2.0 - 1.0) * tanFov;
    vec3 worldRd = normalize(fwd + ndcX * right + ndcY * up);

    int pixIdx = gid.y * screenW + gid.x;
    int bg = pixels[pixIdx];
    vec3 outColor = unpackRGB(bg);
    float outAlpha = float((bg >> 24) & 0xFF) / 255.0;

    float tVal = 1e20;

    for (int e = 0; e < entityCount; e++) {
        vec3 entPos  = entWorldPos(e);
        float radius  = entRadius(e);
        int voxBase = entVoxOffset(e);
        int bvhBase = entBvhOffset(e);

        vec3 localRo = rotateInv(e, cam - entPos);
        vec3 localRd = rotateInv(e, worldRd);
        float b = dot(localRo, localRd);
        float c = dot(localRo, localRo) - radius * radius;
        if (b * b - c < 0.0) continue;

        float bestT = 1e20;
        int bestFace = -1;
        int bestVox = -1;

        int stack[32];
        int stackTop = 0;
        stack[stackTop++] = bvhBase;

        while (stackTop > 0) {
            int nodeIdx = stack[--stackTop];
            vec3 nMin = nodeAabbMin(nodeIdx);
            vec3 nMax = nodeAabbMax(nodeIdx);
            int left = nodeLeft(nodeIdx);
            int right2= nodeRight(nodeIdx);
            int vIdx = nodeVoxIdx(nodeIdx);
 
            vec2 aabbH = rayAABB(localRo, localRd, nMin, nMax);
            if (aabbH.x == 1e20 || aabbH.x >= bestT) continue;

            if (left == -1) {
                vec3 lp = hotPos(voxBase, vIdx);
                float hs = hotSize(voxBase, vIdx) * 0.5;
                vec2 vh = rayBox(localRo, localRd, lp, hs);
                if (vh.x < bestT) {
                    bestT = vh.x;
                    bestFace = int(vh.y);
                    bestVox = vIdx;
                }
            } else {
                stack[stackTop++] = left;
                stack[stackTop++] = right2;
            }
        }

        if (bestVox < 0) continue;

        vec3 hitLocal = localRo + localRd * bestT;
        vec3 hitWorld = entPos + vec3(
            entRot(e,0)*hitLocal.x + entRot(e,3)*hitLocal.y + entRot(e,6)*hitLocal.z,
            entRot(e,1)*hitLocal.x + entRot(e,4)*hitLocal.y + entRot(e,7)*hitLocal.z,
            entRot(e,2)*hitLocal.x + entRot(e,5)*hitLocal.y + entRot(e,8)*hitLocal.z
        );
        float worldT = dot(hitWorld - cam, worldRd);

        if (worldT < tVal) {
            tVal = worldT;

            vec3 voxColor;
            float alpha;
            int texId = coldTexId(voxBase, bestVox);
            int col = coldColor(voxBase, bestVox);
            alpha = coldOpacity(voxBase, bestVox);

            voxColor = (texId < 0) ? unpackRGB(col) : vec3(1.0);
            voxColor *= faceLighting(bestFace);

            outColor = voxColor * alpha + outColor * (1.0 - alpha);
            outAlpha = alpha + outAlpha * (1.0 - alpha);
        }
    }

    if (pixels[pixIdx] != 0xFF87CEEB && tBuffer[pixIdx] < tVal) return;

    pixels[pixIdx] = packARGB(outColor, outAlpha);
}
