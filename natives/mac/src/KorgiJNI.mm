#import <Metal/Metal.h>
#import <QuartzCore/CAMetalLayer.h>
#include <jni.h>
#include "dev_korgi_jni_KorgiJNI.h"
#include <simd/simd.h>
#include <vector>

struct RayParams {
    simd_float3 cam;
    simd_float3 forward;
    simd_float3 right;
    simd_float3 up;
    simd_int3 worldMin;
    simd_int3 worldSize;
    float tanFov;
    int32_t voxelCount;
};

struct EntityHeaderC {
    float wx, wy, wz;
    int32_t voxelOffset;
    int32_t voxelCount;
    float rot[9];
    float radius;
    int32_t pad0, pad1;
};

struct BVHotC {
    float lx, ly, lz;
    float size;
};

struct BVColdC {
    int32_t color;
    float opacity;
    int32_t textureId;
    int32_t pad;
};

struct EntityParams {
    simd_float3 cam;
    simd_float3 forward;
    simd_float3 right;
    simd_float3 up;
    float tanFov;
    int32_t entityCount;
    int32_t totalVoxels;
};


static id<MTLDevice> device = nil;
static id<MTLCommandQueue> commandQueue = nil;
static id<MTLComputePipelineState> pipelineState = nil;

static id<MTLDevice> entityDevice = nil;
static id<MTLCommandQueue> entityCommandQueue = nil;
static id<MTLComputePipelineState> entityPipelineState = nil;

static id<MTLBuffer> pixelsBuffer = nil;
static id<MTLBuffer> voxelBuffer = nil;
static id<MTLBuffer> colorBuffer = nil;
static id<MTLBuffer> opacityBuffer = nil;
static id<MTLBuffer> textureLocationBuffer = nil;
static id<MTLBuffer> textureAtlasBuffer = nil;
static id<MTLBuffer> paramsBuffer = nil;
static id<MTLBuffer> widthBuffer = nil;
static id<MTLBuffer> heightBuffer = nil;
static id<MTLBuffer> chunkGridBuffer = nil;
static id<MTLBuffer> chunkSizeBuffer = nil;

static id<MTLBuffer> tBuffer = nil;

static id<MTLBuffer> entHeaderBuffer = nil;
static id<MTLBuffer> bvHotBuffer = nil;
static id<MTLBuffer> bvColdBuffer = nil;
static id<MTLBuffer> entityPixelsBuffer = nil;
static id<MTLBuffer> entityWidthBuffer = nil;
static id<MTLBuffer> entityHeightBuffer = nil;
static id<MTLBuffer> entityCountBuffer = nil;
static id<MTLBuffer> entityAtlasBuffer = nil;


static void ensureBuffer(id<MTLBuffer> __strong *buf, NSUInteger byteSize) {
    if (*buf == nil || (*buf).length < byteSize) {
        *buf = [device newBufferWithLength:byteSize options:MTLResourceStorageModeShared];
    }
}

static size_t jintSize = sizeof(jint);


void initMetal(NSString* path) {
    if (device) return;

    device = MTLCreateSystemDefaultDevice();
    commandQueue = [device newCommandQueue];

    NSError* error = nil;
    id<MTLLibrary> library = [device newLibraryWithFile:path error:&error];
    id<MTLFunction> kernelFunc = [library newFunctionWithName:@"raytraceKernel"];
    pipelineState = [device newComputePipelineStateWithFunction:kernelFunc error:&error];
    if (error) {
        NSLog(@"Failed to create pipeline: %@", error);
    }
}

static void ensureEntityBuffer(id<MTLBuffer> __strong *buf, NSUInteger byteSize) {
    if (*buf == nil || (*buf).length < byteSize) {
        *buf = [entityDevice newBufferWithLength:byteSize options:MTLResourceStorageModeShared];
    }
}

void initEntityMetal(NSString* path) {
    if (entityDevice) return;

    entityDevice = MTLCreateSystemDefaultDevice();
    entityCommandQueue = [entityDevice newCommandQueue];

    NSError* error = nil;
    id<MTLLibrary> library = [entityDevice newLibraryWithFile:path error:&error];
    if (error || !library) {
        NSLog(@"Failed to load entity library at path %@: %@", path, error);
        return;
    }

    id<MTLFunction> kernelFunc = [library newFunctionWithName:@"entityKernel"];
    if (!kernelFunc) {
        NSLog(@"entityKernel not found in library. Available functions:");
        for (NSString* name in library.functionNames) {
            NSLog(@"  - %@", name);
        }
        return;
    }

    error = nil;
    entityPipelineState = [entityDevice newComputePipelineStateWithFunction:kernelFunc error:&error];
    if (error) {
        NSLog(@"Failed to create entity pipeline: %@", error);
    }
}

NSString* JString_to_NSString(JNIEnv* env, jstring javaString) {
    if (javaString == NULL) {
        return nil;
    }

    const char *utf8Chars = env->GetStringUTFChars(javaString, NULL);
    if (utf8Chars == NULL) {
        return nil;
    }

    NSString *objectiveCString = [NSString stringWithUTF8String:utf8Chars];


    env->ReleaseStringUTFChars(javaString, utf8Chars);

    return objectiveCString;
}

jint* loadArray(JNIEnv* env, jintArray array){
    return env->GetIntArrayElements(array, nullptr);
}

jfloat* loadArray(JNIEnv* env, jfloatArray array){
    return env->GetFloatArrayElements(array, nullptr);
}

static void releaseArray(JNIEnv* env, jfloatArray array, jfloat* ptr){
    env->ReleaseFloatArrayElements(array, ptr, 0);
}

static void releaseArray(JNIEnv* env, jintArray array, jint* ptr){
    env->ReleaseIntArrayElements(array, ptr, 0);
}

extern "C"
JNIEXPORT void JNICALL Java_dev_korgi_jni_KorgiJNI_executeKernal(
    JNIEnv* env, jclass cls,
    jintArray pixels,
    jint width, jint height,
    jfloatArray cam,
    jfloatArray forward,
    jfloatArray right,
    jfloatArray up,
    jfloat tanFov,
    jint voxCount,
    jintArray color,
    jfloatArray opacity,
    jintArray worldMinArray,
    jintArray worldSizeArray,
    jintArray voxelGrid,
    jstring path,
    jintArray textureLocation,
    jintArray textureAtlas,
    jintArray chunkGrid,
    jintArray chunkSize
) {

    initMetal(JString_to_NSString(env, path));

    jint* pixelsPtr = env->GetIntArrayElements(pixels, nullptr);
    jfloat* camPtr = env->GetFloatArrayElements(cam, nullptr);
    jfloat* forwardPtr = env->GetFloatArrayElements(forward, nullptr);
    jfloat* rightPtr = env->GetFloatArrayElements(right, nullptr);
    jfloat* upPtr = env->GetFloatArrayElements(up, nullptr);
    jint* colorPtr = env->GetIntArrayElements(color, nullptr);
    jfloat* opacityPtr = env->GetFloatArrayElements(opacity, nullptr);
    jint* worldMin = env->GetIntArrayElements(worldMinArray, nullptr);
    jint* worldSize = env->GetIntArrayElements(worldSizeArray, nullptr);
    jint* voxelGridPtr = env->GetIntArrayElements(voxelGrid, nullptr);
    jint* textureLocationPtr = env->GetIntArrayElements(textureLocation, nullptr);
    jint* textureAtlasPtr = env->GetIntArrayElements(textureAtlas, nullptr);
    jint* chunkGridPtr = env->GetIntArrayElements(chunkGrid, nullptr);
    jint* chunkSizePtr = env->GetIntArrayElements(chunkSize, nullptr);


    jsize length = env->GetArrayLength(textureAtlas);

    size_t voxCountSize = jintSize * voxCount;
    int32_t w = width, h = height;
    
    ensureBuffer(&pixelsBuffer, jintSize * width * height);
    ensureBuffer(&tBuffer, sizeof(float) * width * height);
    ensureBuffer(&voxelBuffer, jintSize * worldSize[0] * worldSize[1] * worldSize[2]);
    ensureBuffer(&colorBuffer, voxCountSize);
    ensureBuffer(&opacityBuffer, sizeof(float) * voxCount);
    ensureBuffer(&textureLocationBuffer, voxCountSize);
    ensureBuffer(&textureAtlasBuffer, jintSize * length);
    ensureBuffer(&widthBuffer, sizeof(int32_t));
    ensureBuffer(&heightBuffer, sizeof(int32_t));
    ensureBuffer(&chunkGridBuffer, jintSize * chunkSizePtr[0] * chunkSizePtr[1] * chunkSizePtr[2]);
    ensureBuffer(&chunkSizeBuffer, jintSize * 3);

    memcpy(pixelsBuffer.contents, pixelsPtr, jintSize  * width * height);
    memcpy(voxelBuffer.contents, voxelGridPtr, jintSize  * worldSize[0] * worldSize[1] * worldSize[2]);
    memcpy(colorBuffer.contents, colorPtr, voxCountSize);
    memcpy(opacityBuffer.contents, opacityPtr, voxCountSize); // sizeof(jint) == sizeof(float)
    memcpy(textureLocationBuffer.contents, textureLocationPtr, voxCountSize);
    memcpy(textureAtlasBuffer.contents, textureAtlasPtr, jintSize * length);
    memcpy(widthBuffer.contents,  &w, sizeof(int32_t));
    memcpy(heightBuffer.contents, &h, sizeof(int32_t));
    memcpy(chunkGridBuffer.contents, chunkGridPtr, jintSize * chunkSizePtr[0] * chunkSizePtr[1] * chunkSizePtr[2]);
    memcpy(chunkSizeBuffer.contents, chunkSizePtr, jintSize * 3);
                                                
    ensureBuffer(&paramsBuffer, sizeof(RayParams));

    RayParams params;
    params.cam = simd_make_float3(camPtr[0], camPtr[1], camPtr[2]);
    params.forward = simd_make_float3(forwardPtr[0], forwardPtr[1], forwardPtr[2]);
    params.right = simd_make_float3(rightPtr[0], rightPtr[1], rightPtr[2]);
    params.up = simd_make_float3(upPtr[0], upPtr[1], upPtr[2]);
    params.tanFov = tanFov;
    params.voxelCount = voxCount;
    params.worldMin = simd_make_int3(worldMin[0], worldMin[1], worldMin[2]);
    params.worldSize = simd_make_int3(worldSize[0], worldSize[1], worldSize[2]);

    memcpy(paramsBuffer.contents, &params, sizeof(RayParams));


    id<MTLCommandBuffer> commandBuffer = [commandQueue commandBuffer];
    id<MTLComputeCommandEncoder> encoder = [commandBuffer computeCommandEncoder];
    [encoder setComputePipelineState:pipelineState];

    [encoder setBuffer:pixelsBuffer offset:0 atIndex:0];
    [encoder setBuffer:voxelBuffer offset:0 atIndex:1];
    [encoder setBuffer:colorBuffer offset:0 atIndex:2];
    [encoder setBuffer:opacityBuffer offset:0 atIndex:3];
    [encoder setBuffer:paramsBuffer offset:0 atIndex:4];
    [encoder setBuffer:widthBuffer offset:0 atIndex:5];
    [encoder setBuffer:heightBuffer offset:0 atIndex:6];
    [encoder setBuffer:textureLocationBuffer offset:0 atIndex:7];
    [encoder setBuffer:textureAtlasBuffer offset:0 atIndex:8];
    [encoder setBuffer:chunkGridBuffer offset:0 atIndex: 9];
    [encoder setBuffer:chunkSizeBuffer offset:0 atIndex: 10];
    [encoder setBuffer:tBuffer offset:0 atIndex: 11];

    NSUInteger tw = pipelineState.threadExecutionWidth;
    NSUInteger th = pipelineState.maxTotalThreadsPerThreadgroup / tw;
    MTLSize threadsPerThreadgroup = MTLSizeMake(tw, th, 1);
    MTLSize totalThreads = MTLSizeMake(width, height, 1);

    [encoder dispatchThreads:totalThreads threadsPerThreadgroup:threadsPerThreadgroup];
    [encoder endEncoding];
    [commandBuffer commit];
    [commandBuffer waitUntilCompleted];

    memcpy(pixelsPtr, pixelsBuffer.contents, jintSize * width * height);

    env->ReleaseIntArrayElements(pixels, pixelsPtr, 0);
    env->ReleaseFloatArrayElements(cam, camPtr, 0);
    env->ReleaseFloatArrayElements(forward, forwardPtr, 0);
    env->ReleaseFloatArrayElements(right, rightPtr, 0);
    env->ReleaseFloatArrayElements(up, upPtr, 0);
    env->ReleaseIntArrayElements(color, colorPtr, 0);
    env->ReleaseFloatArrayElements(opacity, opacityPtr, 0);
    env->ReleaseIntArrayElements(worldMinArray, worldMin, 0);
    env->ReleaseIntArrayElements(worldSizeArray, worldSize, 0);
    env->ReleaseIntArrayElements(voxelGrid, voxelGridPtr, 0);
    env->ReleaseIntArrayElements(textureLocation, textureLocationPtr, 0);
    env->ReleaseIntArrayElements(textureAtlas, textureAtlasPtr, 0);
    env->ReleaseIntArrayElements(chunkGrid, chunkGridPtr, 0);
    env->ReleaseIntArrayElements(chunkSize, chunkSizePtr, 0);
}


static EntityHeaderC* headerScratch = nullptr;
static BVHotC* voxelHotScratch = nullptr;
static BVColdC* voxelColdScratch = nullptr;
static int headerScratchCount = 0;
static int voxelScratchCount = 0;
static int lastEntityCount = -1;
static int lastTotalVoxels = -1;


extern "C"
JNIEXPORT void JNICALL Java_dev_korgi_jni_KorgiJNI_executeEntityKernal(
    JNIEnv* env, jclass cls,
    jintArray pixels, jint w, jint h,
    jfloatArray camPos, jfloatArray camFwd, jfloatArray camRight, jfloatArray camUp,
    jfloat tanFov,
    jfloatArray entPositions, jfloatArray entRotations, jfloatArray entRaddi, jintArray entVoxelOffsets,
    jint entityCount,
    jfloatArray bvPositions, jfloatArray bvSizes,
    jintArray bvColors, jfloatArray bvOpacities, jintArray bvTextureIds,
    jint totalVoxels,
    jintArray textureAtlas,
    jstring path
) {
    initEntityMetal(JString_to_NSString(env, path));

    if (headerScratchCount < entityCount) {
        delete[] headerScratch;
        headerScratch = new EntityHeaderC[entityCount];
        headerScratchCount = entityCount;
    }
    if (voxelScratchCount < totalVoxels) {
        delete[] voxelHotScratch;
        delete[] voxelColdScratch;
        voxelHotScratch  = new BVHotC[totalVoxels];
        voxelColdScratch = new BVColdC[totalVoxels];
        voxelScratchCount = totalVoxels;
    }

    jint* pixelsPtr = (jint*) env->GetPrimitiveArrayCritical(pixels, nullptr);
    jfloat* camPosPtr = (jfloat*) env->GetPrimitiveArrayCritical(camPos, nullptr);
    jfloat* camFwdPtr = (jfloat*) env->GetPrimitiveArrayCritical(camFwd, nullptr);
    jfloat* camRightPtr = (jfloat*) env->GetPrimitiveArrayCritical(camRight, nullptr);
    jfloat* camUpPtr = (jfloat*) env->GetPrimitiveArrayCritical(camUp, nullptr);

    bool dirty = (entityCount != lastEntityCount || totalVoxels != lastTotalVoxels);
    lastEntityCount = entityCount;
    lastTotalVoxels = totalVoxels;

    if (dirty) {
        jfloat* entPosPtr = (jfloat*) env->GetPrimitiveArrayCritical(entPositions, nullptr);
        jfloat* entRotPtr = (jfloat*) env->GetPrimitiveArrayCritical(entRotations, nullptr);
        jfloat* entRaddiPtr  = (jfloat*) env->GetPrimitiveArrayCritical(entRaddi, nullptr);
        jint* entOffsetPtr = (jint*) env->GetPrimitiveArrayCritical(entVoxelOffsets, nullptr);
        jfloat* bvPosPtr = (jfloat*) env->GetPrimitiveArrayCritical(bvPositions, nullptr);
        jfloat* bvSizePtr = (jfloat*) env->GetPrimitiveArrayCritical(bvSizes, nullptr);
        jint* bvColorPtr = (jint*) env->GetPrimitiveArrayCritical(bvColors, nullptr);
        jfloat* bvOpacityPtr = (jfloat*) env->GetPrimitiveArrayCritical(bvOpacities, nullptr);
        jint* bvTexIdPtr = (jint*) env->GetPrimitiveArrayCritical(bvTextureIds, nullptr);

        for (int i = 0; i < entityCount; i++) {
            EntityHeaderC& h = headerScratch[i];
            h.wx = entPosPtr[i * 3];
            h.wy = entPosPtr[i * 3 + 1];
            h.wz = entPosPtr[i * 3 + 2];
            h.voxelOffset = entOffsetPtr[i * 2];
            h.voxelCount  = entOffsetPtr[i * 2 + 1];
            memcpy(h.rot, &entRotPtr[i * 9], sizeof(float) * 9);
            h.radius = entRaddiPtr[i];
            h.pad0 = h.pad1 = 0;

        }

        for (int i = 0; i < totalVoxels; i++) {
            BVHotC& hot = voxelHotScratch[i];
            hot.lx = bvPosPtr[i * 3];
            hot.ly = bvPosPtr[i * 3 + 1];
            hot.lz = bvPosPtr[i * 3 + 2];
            hot.size = bvSizePtr[i];

            BVColdC& cold = voxelColdScratch[i];
            cold.color = bvColorPtr[i];
            cold.opacity = bvOpacityPtr[i];
            cold.textureId = bvTexIdPtr[i];
            cold.pad = 0;
        }

        env->ReleasePrimitiveArrayCritical(entPositions, entPosPtr, JNI_ABORT);
        env->ReleasePrimitiveArrayCritical(entRotations, entRotPtr, JNI_ABORT);
        env->ReleasePrimitiveArrayCritical(entRaddi, entRaddiPtr, JNI_ABORT);
        env->ReleasePrimitiveArrayCritical(entVoxelOffsets, entOffsetPtr, JNI_ABORT);
        env->ReleasePrimitiveArrayCritical(bvPositions, bvPosPtr, JNI_ABORT);
        env->ReleasePrimitiveArrayCritical(bvSizes, bvSizePtr, JNI_ABORT);
        env->ReleasePrimitiveArrayCritical(bvColors, bvColorPtr, JNI_ABORT);
        env->ReleasePrimitiveArrayCritical(bvOpacities, bvOpacityPtr, JNI_ABORT);
        env->ReleasePrimitiveArrayCritical(bvTextureIds, bvTexIdPtr, JNI_ABORT);

        ensureEntityBuffer(&entHeaderBuffer, sizeof(EntityHeaderC) * entityCount);
        ensureEntityBuffer(&bvHotBuffer,  sizeof(BVHotC)  * totalVoxels);
        ensureEntityBuffer(&bvColdBuffer, sizeof(BVColdC) * totalVoxels);

        memcpy(entHeaderBuffer.contents, headerScratch, sizeof(EntityHeaderC) * entityCount);
        memcpy(bvHotBuffer.contents,  voxelHotScratch,  sizeof(BVHotC)  * totalVoxels);
        memcpy(bvColdBuffer.contents, voxelColdScratch, sizeof(BVColdC) * totalVoxels);
    }

    jsize atlasLength = env->GetArrayLength(textureAtlas);
    jint* atlasPtr = (jint*) env->GetPrimitiveArrayCritical(textureAtlas, nullptr);

    EntityParams params;
    params.cam = simd_make_float3(camPosPtr[0], camPosPtr[1], camPosPtr[2]);
    params.forward = simd_make_float3(camFwdPtr[0], camFwdPtr[1], camFwdPtr[2]);
    params.right = simd_make_float3(camRightPtr[0], camRightPtr[1], camRightPtr[2]);
    params.up = simd_make_float3(camUpPtr[0], camUpPtr[1], camUpPtr[2]);
    params.tanFov = tanFov;
    params.entityCount = (int32_t) entityCount;
    params.totalVoxels = (int32_t) totalVoxels;

    int32_t w32 = (int32_t) w;
    int32_t h32 = (int32_t) h;

    ensureEntityBuffer(&entityPixelsBuffer, jintSize * w * h);
    ensureEntityBuffer(&entityAtlasBuffer,  jintSize * atlasLength);
    ensureEntityBuffer(&entityWidthBuffer,  sizeof(int32_t));
    ensureEntityBuffer(&entityHeightBuffer, sizeof(int32_t));
    ensureEntityBuffer(&entityCountBuffer,  sizeof(EntityParams));

    memcpy(entityPixelsBuffer.contents, pixelsPtr, jintSize * w * h);
    memcpy(entityAtlasBuffer.contents, atlasPtr, jintSize * atlasLength);
    memcpy(entityWidthBuffer.contents, &w32, sizeof(int32_t));
    memcpy(entityHeightBuffer.contents, &h32, sizeof(int32_t));
    memcpy(entityCountBuffer.contents, &params, sizeof(EntityParams));

    env->ReleasePrimitiveArrayCritical(textureAtlas, atlasPtr, JNI_ABORT);
    env->ReleasePrimitiveArrayCritical(camPos, camPosPtr, JNI_ABORT);
    env->ReleasePrimitiveArrayCritical(camFwd, camFwdPtr, JNI_ABORT);
    env->ReleasePrimitiveArrayCritical(camRight, camRightPtr,JNI_ABORT);
    env->ReleasePrimitiveArrayCritical(camUp, camUpPtr, JNI_ABORT);

    id<MTLCommandBuffer> commandBuffer = [entityCommandQueue commandBuffer];
    id<MTLComputeCommandEncoder> encoder = [commandBuffer computeCommandEncoder];

    [encoder setComputePipelineState:entityPipelineState];
    [encoder setBuffer:entityPixelsBuffer offset:0 atIndex:0];
    [encoder setBuffer:entityWidthBuffer offset:0 atIndex:1];
    [encoder setBuffer:entityHeightBuffer offset:0 atIndex:2];
    [encoder setBuffer:entityCountBuffer offset:0 atIndex:3];
    [encoder setBuffer:entHeaderBuffer offset:0 atIndex:4];
    [encoder setBuffer:bvHotBuffer offset:0 atIndex:5];
    [encoder setBuffer:bvColdBuffer offset:0 atIndex:6];
    [encoder setBuffer:entityAtlasBuffer offset:0 atIndex:7];
    [encoder setBuffer:tBuffer offset:0 atIndex:8];

    NSUInteger tw = entityPipelineState.threadExecutionWidth;
    NSUInteger th = entityPipelineState.maxTotalThreadsPerThreadgroup / tw;
    [encoder dispatchThreads:MTLSizeMake(w, h, 1) threadsPerThreadgroup:MTLSizeMake(tw, th, 1)];
    [encoder endEncoding];
    [commandBuffer commit];
    [commandBuffer waitUntilCompleted];

    memcpy(pixelsPtr, entityPixelsBuffer.contents, jintSize * w * h);
    env->ReleasePrimitiveArrayCritical(pixels, pixelsPtr, 0);
}