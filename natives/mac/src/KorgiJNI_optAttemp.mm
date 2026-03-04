#import <Metal/Metal.h>
#import <QuartzCore/CAMetalLayer.h>
#include <jni.h>
#include "dev_korgi_jni_KorgiJNI.h"
#include <simd/simd.h>
#include <vector>
#include <cstring>

struct RayParams {
    simd_float3 cam;
    simd_float3 forward;
    simd_float3 right;
    simd_float3 up;
    simd_int3   worldMin;
    simd_int3   worldSize;
    simd_int3   chunkGridSize;  // NEW
    float       tanFov;
    int32_t     voxelCount;
};

static id<MTLDevice>              device        = nil;
static id<MTLCommandQueue>        commandQueue  = nil;
static id<MTLComputePipelineState> pipelineState = nil;

void initMetal(NSString* path) {
    if (device) return;
    device       = MTLCreateSystemDefaultDevice();
    commandQueue = [device newCommandQueue];

    NSError* error = nil;
    id<MTLLibrary>  library    = [device newLibraryWithFile:path error:&error];
    id<MTLFunction> kernelFunc = [library newFunctionWithName:@"raytraceKernel"];
    pipelineState = [device newComputePipelineStateWithFunction:kernelFunc error:&error];
    if (error) NSLog(@"Failed to create pipeline: %@", error);
}

NSString* JString_to_NSString(JNIEnv* env, jstring javaString) {
    if (!javaString) return nil;
    const char* utf8 = env->GetStringUTFChars(javaString, nullptr);
    if (!utf8) return nil;
    NSString* s = [NSString stringWithUTF8String:utf8];
    env->ReleaseStringUTFChars(javaString, utf8);
    return s;
}

// ── Build chunk occupancy bitfield ────────────────────────────────────────────
// Returns a packed uint32 array; one bit per chunk, 1 = at least one solid voxel.
// chunkGridSizeOut receives the chunk grid dimensions (ceil(worldSize / 8)).
static std::vector<uint32_t> buildChunkGrid(
    const jint* voxelGrid,
    const jint* worldSize,         // [3]
    simd_int3&  chunkGridSizeOut)
{
    const int CHUNK = 8;

    int cgW = (worldSize[0] + CHUNK - 1) / CHUNK;
    int cgH = (worldSize[1] + CHUNK - 1) / CHUNK;
    int cgD = (worldSize[2] + CHUNK - 1) / CHUNK;
    chunkGridSizeOut = simd_make_int3(cgW, cgH, cgD);

    int totalChunks = cgW * cgH * cgD;
    std::vector<uint32_t> bits((totalChunks + 31) / 32, 0u);

    int wx = worldSize[0];
    int wy = worldSize[1];
    int wz = worldSize[2];

    for (int z = 0; z < wz; ++z) {
        for (int y = 0; y < wy; ++y) {
            for (int x = 0; x < wx; ++x) {
                int voxIdx = x + y * wx + z * wx * wy;
                if (voxelGrid[voxIdx] < 0) continue;   // -1 = empty

                int cx = x / CHUNK;
                int cy = y / CHUNK;
                int cz = z / CHUNK;
                int flatIdx = cx + cy * cgW + cz * cgW * cgH;
                bits[flatIdx >> 5] |= (1u << (flatIdx & 31));
            }
        }
    }

    return bits;
}

extern "C"
JNIEXPORT void JNICALL Java_dev_korgi_jni_KorgiJNI_executeKernal(
    JNIEnv* env, jclass cls,
    jintArray   pixels,
    jint        width,  jint height,
    jfloatArray cam,
    jfloatArray forward,
    jfloatArray right,
    jfloatArray up,
    jfloat      tanFov,
    jint        voxCount,
    jintArray   color,
    jfloatArray opacity,
    jintArray   worldMinArray,
    jintArray   worldSizeArray,
    jintArray   voxelGrid,
    jstring     path,
    jintArray   textureLocation,
    jintArray   textureAtlas
) {
    initMetal(JString_to_NSString(env, path));

    // ── Pin Java arrays ───────────────────────────────────────────────────────
    jint*   pixelsPtr          = env->GetIntArrayElements(pixels,          nullptr);
    jfloat* camPtr             = env->GetFloatArrayElements(cam,           nullptr);
    jfloat* forwardPtr         = env->GetFloatArrayElements(forward,       nullptr);
    jfloat* rightPtr           = env->GetFloatArrayElements(right,         nullptr);
    jfloat* upPtr              = env->GetFloatArrayElements(up,            nullptr);
    jint*   colorPtr           = env->GetIntArrayElements(color,           nullptr);
    jfloat* opacityPtr         = env->GetFloatArrayElements(opacity,       nullptr);
    jint*   worldMinPtr        = env->GetIntArrayElements(worldMinArray,   nullptr);
    jint*   worldSizePtr       = env->GetIntArrayElements(worldSizeArray,  nullptr);
    jint*   voxelGridPtr       = env->GetIntArrayElements(voxelGrid,       nullptr);
    jint*   textureLocationPtr = env->GetIntArrayElements(textureLocation, nullptr);
    jint*   textureAtlasPtr    = env->GetIntArrayElements(textureAtlas,    nullptr);

    // ── Build chunk grid on CPU ───────────────────────────────────────────────
    simd_int3 chunkGridSize;
    std::vector<uint32_t> chunkBits = buildChunkGrid(voxelGridPtr, worldSizePtr, chunkGridSize);

    // ── Metal buffers ─────────────────────────────────────────────────────────
    int voxelGridLen = worldSizePtr[0] * worldSizePtr[1] * worldSizePtr[2];

    id<MTLBuffer> pixelsBuffer = [device newBufferWithBytes:pixelsPtr
        length:sizeof(jint)*width*height options:MTLResourceStorageModeShared];

    id<MTLBuffer> voxelBuffer = [device newBufferWithBytes:voxelGridPtr
        length:sizeof(jint)*voxelGridLen options:MTLResourceStorageModeShared];

    id<MTLBuffer> colorBuffer = [device newBufferWithBytes:colorPtr
        length:sizeof(jint)*voxCount options:MTLResourceStorageModeShared];

    id<MTLBuffer> opacityBuffer = [device newBufferWithBytes:opacityPtr
        length:sizeof(float)*voxCount options:MTLResourceStorageModeShared];

    id<MTLBuffer> textureLocationBuffer = [device newBufferWithBytes:textureLocationPtr
        length:sizeof(jint)*voxCount options:MTLResourceStorageModeShared];

    jsize atlasLen = env->GetArrayLength(textureAtlas);
    id<MTLBuffer> textureAtlasBuffer = [device newBufferWithBytes:textureAtlasPtr
        length:sizeof(jint)*atlasLen options:MTLResourceStorageModeShared];

    id<MTLBuffer> chunkGridBuffer = [device newBufferWithBytes:chunkBits.data()
        length:sizeof(uint32_t)*chunkBits.size() options:MTLResourceStorageModeShared];

    // ── RayParams ─────────────────────────────────────────────────────────────
    RayParams params;
    params.cam           = simd_make_float3(camPtr[0],      camPtr[1],      camPtr[2]);
    params.forward       = simd_make_float3(forwardPtr[0],  forwardPtr[1],  forwardPtr[2]);
    params.right         = simd_make_float3(rightPtr[0],    rightPtr[1],    rightPtr[2]);
    params.up            = simd_make_float3(upPtr[0],       upPtr[1],       upPtr[2]);
    params.worldMin      = simd_make_int3(worldMinPtr[0],   worldMinPtr[1], worldMinPtr[2]);
    params.worldSize     = simd_make_int3(worldSizePtr[0],  worldSizePtr[1],worldSizePtr[2]);
    params.chunkGridSize = chunkGridSize;   // NEW
    params.tanFov        = tanFov;
    params.voxelCount    = voxCount;

    id<MTLBuffer> paramsBuffer = [device newBufferWithBytes:&params
        length:sizeof(RayParams) options:MTLResourceStorageModeShared];

    int32_t w = width, h = height;
    id<MTLBuffer> widthBuffer  = [device newBufferWithBytes:&w
        length:sizeof(int32_t) options:MTLResourceStorageModeShared];
    id<MTLBuffer> heightBuffer = [device newBufferWithBytes:&h
        length:sizeof(int32_t) options:MTLResourceStorageModeShared];

    // ── Encode & dispatch ─────────────────────────────────────────────────────
    id<MTLCommandBuffer>      commandBuffer = [commandQueue commandBuffer];
    id<MTLComputeCommandEncoder> encoder    = [commandBuffer computeCommandEncoder];
    [encoder setComputePipelineState:pipelineState];

    [encoder setBuffer:pixelsBuffer          offset:0 atIndex:0];
    [encoder setBuffer:voxelBuffer           offset:0 atIndex:1];
    [encoder setBuffer:colorBuffer           offset:0 atIndex:2];
    [encoder setBuffer:opacityBuffer         offset:0 atIndex:3];
    [encoder setBuffer:paramsBuffer          offset:0 atIndex:4];
    [encoder setBuffer:widthBuffer           offset:0 atIndex:5];
    [encoder setBuffer:heightBuffer          offset:0 atIndex:6];
    [encoder setBuffer:textureLocationBuffer offset:0 atIndex:7];
    [encoder setBuffer:textureAtlasBuffer    offset:0 atIndex:8];
    [encoder setBuffer:chunkGridBuffer       offset:0 atIndex:9]; // NEW

    MTLSize threadsPerThreadgroup = MTLSizeMake(8, 8, 1);
    MTLSize threadgroups = MTLSizeMake((width + 7) / 8, (height + 7) / 8, 1);
    [encoder dispatchThreadgroups:threadgroups threadsPerThreadgroup:threadsPerThreadgroup];
    [encoder endEncoding];
    [commandBuffer commit];
    [commandBuffer waitUntilCompleted];

    memcpy(pixelsPtr, [pixelsBuffer contents], sizeof(jint) * width * height);

    // ── Release Java arrays ───────────────────────────────────────────────────
    env->ReleaseIntArrayElements(pixels,          pixelsPtr,          0);
    env->ReleaseFloatArrayElements(cam,           camPtr,             0);
    env->ReleaseFloatArrayElements(forward,       forwardPtr,         0);
    env->ReleaseFloatArrayElements(right,         rightPtr,           0);
    env->ReleaseFloatArrayElements(up,            upPtr,              0);
    env->ReleaseIntArrayElements(color,           colorPtr,           0);
    env->ReleaseFloatArrayElements(opacity,       opacityPtr,         0);
    env->ReleaseIntArrayElements(worldMinArray,   worldMinPtr,        0);
    env->ReleaseIntArrayElements(worldSizeArray,  worldSizePtr,       0);
    env->ReleaseIntArrayElements(voxelGrid,       voxelGridPtr,       0);
    env->ReleaseIntArrayElements(textureLocation, textureLocationPtr, 0);
    env->ReleaseIntArrayElements(textureAtlas,    textureAtlasPtr,    0);
}