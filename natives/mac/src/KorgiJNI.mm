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

    const char *utf8Chars = (env)->GetStringUTFChars(javaString, NULL);
    if (utf8Chars == NULL) {
        return nil;
    }

    NSString *objectiveCString = [NSString stringWithUTF8String:utf8Chars];


    (env)->ReleaseStringUTFChars(javaString, utf8Chars);

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
    jstring path
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

    // Buffers
    id<MTLBuffer> pixelsBuffer = [device newBufferWithBytes:pixelsPtr
                                                     length:sizeof(jint)*width*height
                                                    options:MTLResourceStorageModeShared];

    id<MTLBuffer> voxelBuffer = [device newBufferWithBytes:voxelGridPtr
                                                    length:sizeof(jint)*worldSize[0]*worldSize[1]*worldSize[2]
                                                   options:MTLResourceStorageModeShared];

    id<MTLBuffer> colorBuffer = [device newBufferWithBytes:colorPtr
                                                   length:sizeof(jint)*voxCount
                                                  options:MTLResourceStorageModeShared];

    id<MTLBuffer> opacityBuffer = [device newBufferWithBytes:opacityPtr
                                                     length:sizeof(float)*voxCount
                                                    options:MTLResourceStorageModeShared];

    RayParams params;
    params.cam = simd_make_float3(camPtr[0], camPtr[1], camPtr[2]);
    params.forward = simd_make_float3(forwardPtr[0], forwardPtr[1], forwardPtr[2]);
    params.right = simd_make_float3(rightPtr[0], rightPtr[1], rightPtr[2]);
    params.up = simd_make_float3(upPtr[0], upPtr[1], upPtr[2]);
    params.tanFov = tanFov;
    params.voxelCount = voxCount;
    params.worldMin = simd_make_int3(worldMin[0], worldMin[1], worldMin[2]);
    params.worldSize = simd_make_int3(worldSize[0], worldSize[1], worldSize[2]);

    id<MTLBuffer> paramsBuffer = [device newBufferWithBytes:&params
                                                     length:sizeof(RayParams)
                                                    options:MTLResourceStorageModeShared];

    int32_t w = width;
    int32_t h = height;
    id<MTLBuffer> widthBuffer = [device newBufferWithBytes:&w length:sizeof(int32_t) options:MTLResourceStorageModeShared];
    id<MTLBuffer> heightBuffer = [device newBufferWithBytes:&h length:sizeof(int32_t) options:MTLResourceStorageModeShared];

    // Encode & dispatch
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

    MTLSize threadsPerThreadgroup = MTLSizeMake(8,8,1);
    MTLSize threadgroups = MTLSizeMake((width+7)/8, (height+7)/8,1);

    [encoder dispatchThreadgroups:threadgroups threadsPerThreadgroup:threadsPerThreadgroup];
    [encoder endEncoding];
    [commandBuffer commit];
    [commandBuffer waitUntilCompleted];

    memcpy(pixelsPtr, [pixelsBuffer contents], sizeof(jint)*width*height);

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
}