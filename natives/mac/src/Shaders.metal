#include <metal_stdlib>
using namespace metal;

struct RayParams {
    float3 cam;
    float3 forward;
    float3 right;
    float3 up;
    float tanFov;
    uint voxelCount;
};

kernel void raytraceKernel(
    device int* pixels        [[buffer(0)]],
    device int* voxelGrid     [[buffer(1)]],
    device int* colors        [[buffer(2)]],
    device float* opacity     [[buffer(3)]],
    uint2 gid                 [[thread_position_in_grid]]
) {
    // Compute pixel index
    uint idx = gid.y * grid_size().x + gid.x;
    // Simple test output
    pixels[idx] = 0xFF00FF00; // green
}
