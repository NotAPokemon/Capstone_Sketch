#version 430

layout(local_size_x = 16, local_size_y = 16) in;

// Output pixel buffer
layout(std430, binding = 0) buffer Pixels {
    uint pixels[];
};

// Camera data
layout(std430, binding = 1) buffer Camera {
    vec4 camPos;       // xyz = position
    vec4 forward;      // xyz = forward vector
    vec4 right;        // xyz = right vector
    vec4 up;           // xyz = up vector
    float tanFov;      // x
};

// Voxel data
layout(std430, binding = 2) buffer Voxels {
    int voxelGrid[];
    int worldMinX;
    int worldMinY;
    int worldMinZ;
    int worldSizeX;
    int worldSizeY;
    int worldSizeZ;
    int voxelCount;
    uint vcolor[];     // ARGB packed
    float opacity[];   // 0..1 per voxel
};

uniform int width;
uniform int height;
uniform float renderDistance;
uniform uint skyColor;

bool inBounds(int x, int y, int z) {
    return x >= worldMinX && y >= worldMinY && z >= worldMinZ &&
           x < worldMinX + worldSizeX &&
           y < worldMinY + worldSizeY &&
           z < worldMinZ + worldSizeZ;
}

int flattenIndex(int x, int y, int z) {
    return (x - worldMinX) +
           (y - worldMinY) * worldSizeX +
           (z - worldMinZ) * worldSizeX * worldSizeY;
}

uint blend(uint bg, uint fg, float alpha) {
    alpha = clamp(alpha, 0.0, 1.0);

    uint rBg = (bg >> 16) & 0xFF;
    uint gBg = (bg >> 8) & 0xFF;
    uint bBg = bg & 0xFF;

    uint rFg = (fg >> 16) & 0xFF;
    uint gFg = (fg >> 8) & 0xFF;
    uint bFg = fg & 0xFF;

    uint r = uint(rFg * alpha + rBg * (1.0 - alpha));
    uint g = uint(gFg * alpha + gBg * (1.0 - alpha));
    uint b = uint(bFg * alpha + bBg * (1.0 - alpha));

    return (0xFF << 24) | (r << 16) | (g << 8) | b;
}

void main() {
    uint px = gl_GlobalInvocationID.x;
    uint py = gl_GlobalInvocationID.y;

    if (px >= uint(width) || py >= uint(height)) return;

    uint id = py * uint(width) + px;

    float aspect = float(width) / float(height);
    float ndcX = 2.0 * (float(px) + 0.5) / float(width) - 1.0;
    float ndcY = 1.0 - 2.0 * (float(py) + 0.5) / float(height);

    // Ray direction
    float dx = forward.x + ndcX * aspect * tanFov * right.x + ndcY * tanFov * up.x;
    float dy = forward.y + ndcX * aspect * tanFov * right.y + ndcY * tanFov * up.y;
    float dz = forward.z + ndcX * aspect * tanFov * right.z + ndcY * tanFov * up.z;

    float invLen = inversesqrt(dx*dx + dy*dy + dz*dz);
    dx *= invLen; dy *= invLen; dz *= invLen;

    float invDx = dx != 0.0 ? 1.0/dx : 0.0;
    float invDy = dy != 0.0 ? 1.0/dy : 0.0;
    float invDz = dz != 0.0 ? 1.0/dz : 0.0;

    // Grid bounds
    float tMin = 0.0;
    float tMax = renderDistance;

    float gridMinX = float(worldMinX);
    float gridMinY = float(worldMinY);
    float gridMinZ = float(worldMinZ);
    float gridMaxX = float(worldMinX + worldSizeX);
    float gridMaxY = float(worldMinY + worldSizeY);
    float gridMaxZ = float(worldMinZ + worldSizeZ);

    if(dx != 0.0) {
        float tx1 = (gridMinX - camPos.x) * invDx;
        float tx2 = (gridMaxX - camPos.x) * invDx;
        tMin = max(tMin, min(tx1, tx2));
        tMax = min(tMax, max(tx1, tx2));
    } else if(camPos.x < gridMinX || camPos.x > gridMaxX) { pixels[id] = skyColor; return; }

    if(dy != 0.0) {
        float ty1 = (gridMinY - camPos.y) * invDy;
        float ty2 = (gridMaxY - camPos.y) * invDy;
        tMin = max(tMin, min(ty1, ty2));
        tMax = min(tMax, max(ty1, ty2));
    } else if(camPos.y < gridMinY || camPos.y > gridMaxY) { pixels[id] = skyColor; return; }

    if(dz != 0.0) {
        float tz1 = (gridMinZ - camPos.z) * invDz;
        float tz2 = (gridMaxZ - camPos.z) * invDz;
        tMin = max(tMin, min(tz1, tz2));
        tMax = min(tMax, max(tz1, tz2));
    } else if(camPos.z < gridMinZ || camPos.z > gridMaxZ) { pixels[id] = skyColor; return; }

    if(tMin > tMax) { pixels[id] = skyColor; return; }

    // Starting point in voxel grid
    float startX = camPos.x + dx * tMin;
    float startY = camPos.y + dy * tMin;
    float startZ = camPos.z + dz * tMin;

    int cellX = int(floor(startX));
    int cellY = int(floor(startY));
    int cellZ = int(floor(startZ));

    float tMaxX = tMin + ((dx > 0.0 ? float(cellX+1)-startX : float(cellX)-startX) * invDx);
    float tMaxY = tMin + ((dy > 0.0 ? float(cellY+1)-startY : float(cellY)-startY) * invDy);
    float tMaxZ = tMin + ((dz > 0.0 ? float(cellZ+1)-startZ : float(cellZ)-startZ) * invDz);

    float tDeltaX = dx != 0.0 ? abs(invDx) : 1e30;
    float tDeltaY = dy != 0.0 ? abs(invDy) : 1e30;
    float tDeltaZ = dz != 0.0 ? abs(invDz) : 1e30;

    int stepX = dx > 0.0 ? 1 : -1;
    int stepY = dy > 0.0 ? 1 : -1;
    int stepZ = dz > 0.0 ? 1 : -1;

    uint hitColor = 0u;
    float currentOpacity = 1.0;
    float dist = tMin;

    while(dist < renderDistance && currentOpacity > 0.01) {
        if(inBounds(cellX, cellY, cellZ)) {
            int i = voxelGrid[flattenIndex(cellX, cellY, cellZ)];
            if(i >= 0) {
                hitColor = blend(hitColor, vcolor[i], opacity[i]*currentOpacity);
                currentOpacity *= (1.0 - opacity[i]);
            }
        }

        if(tMaxX < tMaxY && tMaxX < tMaxZ) {
            cellX += stepX;
            dist = tMaxX;
            tMaxX += tDeltaX;
        } else if(tMaxY < tMaxZ) {
            cellY += stepY;
            dist = tMaxY;
            tMaxY += tDeltaY;
        } else {
            cellZ += stepZ;
            dist = tMaxZ;
            tMaxZ += tDeltaZ;
        }

        if(cellX < worldMinX && stepX < 0) break;
        if(cellY < worldMinY && stepY < 0) break;
        if(cellZ < worldMinZ && stepZ < 0) break;
        if(cellX >= worldMinX + worldSizeX && stepX > 0) break;
        if(cellY >= worldMinY + worldSizeY && stepY > 0) break;
        if(cellZ >= worldMinZ + worldSizeZ && stepZ > 0) break;
    }

    // Blend remaining opacity with sky
    hitColor = blend(hitColor, skyColor, currentOpacity);

    pixels[id] = hitColor;
}
