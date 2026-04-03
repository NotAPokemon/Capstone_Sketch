#import <Metal/Metal.h>
#import <QuartzCore/CAMetalLayer.h>
#include <jni.h>
#include "dev_korgi_jni_KorgiJNI.h"
#include <simd/simd.h>

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

static id<MTLDevice> device = nil;
static id<MTLCommandQueue> commandQueue = nil;
static id<MTLComputePipelineState> pipelineState = nil;

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