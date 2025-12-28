#include <metal_stdlib>
using namespace metal;

// Struct to hold camera and ray parameters
struct RayParams {
    float3 cam;
    float3 forward;
    float3 right;
    float3 up;
    float tanFov;
    uint voxelCount;
};

// Kernel function
kernel void raytraceKernel(
    device int* pixels        [[buffer(0)]],
    device int* voxelGrid     [[buffer(1)]],
    device int* colors        [[buffer(2)]],
    device float* opacity     [[buffer(3)]],
    constant uint &gridWidth  [[buffer(4)]], // pass total width of grid
    uint2 gid                 [[thread_position_in_grid]]
) {
    // Compute 1D index from 2D thread position
    uint idx = gid.y * gridWidth + gid.x;

    // Simple test output: solid green
    pixels[idx] = 0xFF00FF00;

    // TODO: implement raytracing using voxelGrid, colors, opacity, and camera parameters
}
