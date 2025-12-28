#include <jni.h>
#include "dev_korgi_jni_KorgiJNI.h"
#import <Metal/Metal.h>
#import <QuartzCore/CAMetalLayer.h>
#include <vector>
#include <iostream>

static id<MTLDevice> device = nil;
static id<MTLCommandQueue> commandQueue = nil;
static id<MTLComputePipelineState> pipelineState = nil;

// Helper: load Metal shader
void initMetal() {
    if (device) return;
    
    printf("test");

    device = MTLCreateSystemDefaultDevice();
    commandQueue = [device newCommandQueue];

    // Load shader from default library
    NSError* error = nil;
    id<MTLLibrary> library = [device newDefaultLibrary];
    id<MTLFunction> kernelFunc = [library newFunctionWithName:@"raytraceKernel"];
    pipelineState = [device newComputePipelineStateWithFunction:kernelFunc error:&error];
    if (error) {
        std::cerr << "Failed to create pipeline: " << [[error localizedDescription] UTF8String] << "\n";
    }
}

extern "C"
JNIEXPORT void JNICALL Java_dev_korgi_game_rendering_NativeGPUKernal_execute(
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
    jintArray voxelGrid)
{
    initMetal();
    printf("testing");
    fflush(stdout);

    // Convert Java arrays to C pointers
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

    // --- Metal buffer creation ---
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

    // Create command buffer
    id<MTLCommandBuffer> commandBuffer = [commandQueue commandBuffer];

    id<MTLComputeCommandEncoder> encoder = [commandBuffer computeCommandEncoder];
    [encoder setComputePipelineState:pipelineState];

    [encoder setBuffer:pixelsBuffer offset:0 atIndex:0];
    [encoder setBuffer:voxelBuffer offset:0 atIndex:1];
    [encoder setBuffer:colorBuffer offset:0 atIndex:2];
    [encoder setBuffer:opacityBuffer offset:0 atIndex:3];
    // TODO: pass cam/forward/right/up/tanFov/worldMin/worldSize as Metal buffers or structs

    MTLSize threadsPerThreadgroup = MTLSizeMake(8, 8, 1);
    MTLSize threadgroups = MTLSizeMake((width+7)/8, (height+7)/8, 1);

    [encoder dispatchThreadgroups:threadgroups threadsPerThreadgroup:threadsPerThreadgroup];
    [encoder endEncoding];
    [commandBuffer commit];
    [commandBuffer waitUntilCompleted];

    // Copy back results
    memcpy(pixelsPtr, [pixelsBuffer contents], sizeof(jint)*width*height);

    // Release arrays
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
