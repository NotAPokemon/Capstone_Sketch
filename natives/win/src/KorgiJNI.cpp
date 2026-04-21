#include <glad/glad.h>
#include <GLFW/glfw3.h>
#include <jni.h>
#include "dev_korgi_jni_KorgiJNI.h"
#include <vector>
#include <iostream>
#include <fstream>
#include <sstream>
#include <cstring>
#include <cstdint>

static GLuint computeProgram = 0;
static GLuint pixelsBuffer = 0;
static GLuint voxelBuffer = 0;
static GLuint colorBuffer = 0;
static GLuint opacityBuffer = 0;
static GLuint textureLocationBuffer = 0;
static GLuint overlayLocationBuffer = 0;
static GLuint textureAtlasBuffer = 0;
static GLuint chunkGridBuffer = 0;
static GLuint chunkSizeBuffer = 0;
static GLuint tBuffer = 0;

static GLuint entityProgram = 0;
static GLuint entHeaderBuffer = 0;
static GLuint bvHotBuffer = 0;
static GLuint bvColdBuffer = 0;
static GLuint bvhNodeBuffer = 0;
static GLuint entityPixelsBuffer = 0;
static GLuint entityAtlasBuffer = 0;
static GLuint entityParamsBuffer = 0;

static bool initGL(int width, int height)
{
    static bool initialized = false;
    if (initialized)
        return true;

    if (!glfwInit())
    {
        std::cerr << "GLFW Init failed" << std::endl;
        return false;
    }

    glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
    glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);

    GLFWwindow *window = glfwCreateWindow(width, height, "Offscreen", NULL, NULL);
    if (!window)
    {
        std::cerr << "GLFW Window creation failed" << std::endl;
        glfwTerminate();
        return false;
    }

    glfwMakeContextCurrent(window);

    if (!gladLoadGLLoader((GLADloadproc)glfwGetProcAddress))
    {
        std::cerr << "GLAD Init failed" << std::endl;
        return false;
    }

    initialized = true;
    return true;
}

static GLuint compileComputeShader(const std::string &path)
{
    std::ifstream file(path);
    if (!file)
    {
        std::cerr << "Shader not found: " << path << std::endl;
        return 0;
    }
    std::stringstream ss;
    ss << file.rdbuf();
    std::string src = ss.str();
    const char *csrc = src.c_str();

    GLuint shader = glCreateShader(GL_COMPUTE_SHADER);
    glShaderSource(shader, 1, &csrc, nullptr);
    glCompileShader(shader);

    GLint status;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &status);
    if (!status)
    {
        char log[1024];
        glGetShaderInfoLog(shader, 1024, nullptr, log);
        std::cerr << "Compute shader compile error:\n"
                  << log << std::endl;
        return 0;
    }

    GLuint program = glCreateProgram();
    glAttachShader(program, shader);
    glLinkProgram(program);

    glGetProgramiv(program, GL_LINK_STATUS, &status);
    if (!status)
    {
        char log[1024];
        glGetProgramInfoLog(program, 1024, nullptr, log);
        std::cerr << "Shader link error:\n"
                  << log << std::endl;
        return 0;
    }

    glDeleteShader(shader);
    return program;
}

static std::string toString(JNIEnv *env, jstring jstr)
{
    if (jstr == nullptr)
        return {};
    const char *utf = env->GetStringUTFChars(jstr, nullptr);
    if (!utf)
        return {};
    std::string result(utf);
    env->ReleaseStringUTFChars(jstr, utf);
    return result;
}

static void uploadSSBO(GLuint &buf, GLsizeiptr byteSize, const void *data)
{
    if (!buf)
        glGenBuffers(1, &buf);
    glBindBuffer(GL_SHADER_STORAGE_BUFFER, buf);
    glBufferData(GL_SHADER_STORAGE_BUFFER, byteSize, data, GL_DYNAMIC_DRAW);
}

struct EntityHeaderC
{
    float wx, wy, wz;
    int32_t voxelOffset;
    int32_t voxelCount;
    float rot[9];
    float radius;
    int32_t bvhOffset;
    int32_t pad1;
};

struct BVHotC
{
    float lx, ly, lz;
    float size;
};

struct BVColdC
{
    int32_t color;
    float opacity;
    int32_t textureId;
    int32_t pad;
};

struct BVHNodeC
{
    float aabbMinX, aabbMinY, aabbMinZ;
    int32_t leftChild;
    float aabbMaxX, aabbMaxY, aabbMaxZ;
    int32_t rightChild;
    int32_t voxelIndex;
    int32_t pad0, pad1, pad2;
};

struct EntityParams
{
    float cam[4];
    float forward[4];
    float right[4];
    float up[4];
    float misc0[4];
    float misc1[4];
};

extern "C" JNIEXPORT void JNICALL Java_dev_korgi_jni_KorgiJNI_executeKernal(
    JNIEnv *env, jclass cls,
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
    jintArray overlayLocation,
    jintArray textureAtlas,
    jintArray chunkGrid,
    jintArray chunkSize)
{
    if (!initGL(width, height))
        return;

    if (!computeProgram)
    {
        computeProgram = compileComputeShader(toString(env, path));
        if (!computeProgram)
            return;
    }

    jint *pixelsPtr = env->GetIntArrayElements(pixels, nullptr);
    jfloat *camPtr = env->GetFloatArrayElements(cam, nullptr);
    jfloat *forwardPtr = env->GetFloatArrayElements(forward, nullptr);
    jfloat *rightPtr = env->GetFloatArrayElements(right, nullptr);
    jfloat *upPtr = env->GetFloatArrayElements(up, nullptr);
    jint *colorPtr = env->GetIntArrayElements(color, nullptr);
    jfloat *opacityPtr = env->GetFloatArrayElements(opacity, nullptr);
    jint *worldMin = env->GetIntArrayElements(worldMinArray, nullptr);
    jint *worldSize = env->GetIntArrayElements(worldSizeArray, nullptr);
    jint *voxelGridPtr = env->GetIntArrayElements(voxelGrid, nullptr);
    jint *textureLocationPtr = env->GetIntArrayElements(textureLocation, nullptr);
    jint *overlayLocationPtr = env->GetIntArrayElements(overlayLocation, nullptr);
    jint *textureAtlasPtr = env->GetIntArrayElements(textureAtlas, nullptr);
    jint *chunkGridPtr = env->GetIntArrayElements(chunkGrid, nullptr);
    jint *chunkSizePtr = env->GetIntArrayElements(chunkSize, nullptr);

    jsize atlasLen = env->GetArrayLength(textureAtlas);
    jsize chunkGridLen = (jsize)chunkSizePtr[0] * chunkSizePtr[1] * chunkSizePtr[2];

    uploadSSBO(pixelsBuffer, sizeof(jint) * width * height, pixelsPtr);
    uploadSSBO(voxelBuffer, sizeof(jint) * worldSize[0] * worldSize[1] * worldSize[2], voxelGridPtr);
    uploadSSBO(colorBuffer, sizeof(jint) * voxCount, colorPtr);
    uploadSSBO(opacityBuffer, sizeof(float) * voxCount, opacityPtr);
    uploadSSBO(textureLocationBuffer, sizeof(jint) * voxCount, textureLocationPtr);
    uploadSSBO(overlayLocationBuffer, sizeof(jint) * voxCount, overlayLocationPtr);
    uploadSSBO(textureAtlasBuffer, sizeof(jint) * atlasLen, textureAtlasPtr);
    uploadSSBO(chunkGridBuffer, sizeof(jint) * chunkGridLen, chunkGridPtr);
    uploadSSBO(chunkSizeBuffer, sizeof(jint) * 3, chunkSizePtr);

    {
        jsize pixelCount = width * height;
        if (!tBuffer)
            glGenBuffers(1, &tBuffer);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, tBuffer);
        glBufferData(GL_SHADER_STORAGE_BUFFER, sizeof(float) * pixelCount, nullptr, GL_DYNAMIC_DRAW);
    }

    glUseProgram(computeProgram);

    glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, pixelsBuffer);
    glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, voxelBuffer);
    glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, colorBuffer);
    glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 3, opacityBuffer);
    glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 4, textureLocationBuffer);
    glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 5, overlayLocationBuffer);
    glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 6, textureAtlasBuffer);
    glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 7, chunkGridBuffer);
    glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 8, chunkSizeBuffer);
    glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 9, tBuffer);

    glUniform3f(glGetUniformLocation(computeProgram, "cam"), camPtr[0], camPtr[1], camPtr[2]);
    glUniform3f(glGetUniformLocation(computeProgram, "forward"), forwardPtr[0], forwardPtr[1], forwardPtr[2]);
    glUniform3f(glGetUniformLocation(computeProgram, "right"), rightPtr[0], rightPtr[1], rightPtr[2]);
    glUniform3f(glGetUniformLocation(computeProgram, "up"), upPtr[0], upPtr[1], upPtr[2]);
    glUniform1f(glGetUniformLocation(computeProgram, "tanFov"), tanFov);
    glUniform1i(glGetUniformLocation(computeProgram, "voxCount"), voxCount);
    glUniform3i(glGetUniformLocation(computeProgram, "worldMin"), worldMin[0], worldMin[1], worldMin[2]);
    glUniform3i(glGetUniformLocation(computeProgram, "worldSize"), worldSize[0], worldSize[1], worldSize[2]);
    glUniform1i(glGetUniformLocation(computeProgram, "width"), width);
    glUniform1i(glGetUniformLocation(computeProgram, "height"), height);

    GLuint groupsX = (width + 7) / 8;
    GLuint groupsY = (height + 7) / 8;
    glDispatchCompute(groupsX, groupsY, 1);
    glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);

    glBindBuffer(GL_SHADER_STORAGE_BUFFER, pixelsBuffer);
    void *ptr = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
    memcpy(pixelsPtr, ptr, sizeof(jint) * width * height);
    glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);

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
    env->ReleaseIntArrayElements(overlayLocation, overlayLocationPtr, 0);
    env->ReleaseIntArrayElements(textureAtlas, textureAtlasPtr, 0);
    env->ReleaseIntArrayElements(chunkGrid, chunkGridPtr, 0);
    env->ReleaseIntArrayElements(chunkSize, chunkSizePtr, 0);
}

extern "C" JNIEXPORT void JNICALL Java_dev_korgi_jni_KorgiJNI_executeEntityKernal(
    JNIEnv *env, jclass cls,
    jintArray pixels, jint w, jint h,
    jfloatArray camPos, jfloatArray camFwd, jfloatArray camRight, jfloatArray camUp,
    jfloat tanFov,
    jfloatArray entPositions, jfloatArray entRotations, jfloatArray entRaddi,
    jintArray entVoxelOffsets, jintArray entBvhOffsets,
    jint entityCount,
    jfloatArray bvPositions, jfloatArray bvSizes,
    jintArray bvColors, jfloatArray bvOpacities, jintArray bvTextureIds,
    jint totalVoxels,
    jfloatArray bvhMins, jfloatArray bvhMaxs, jintArray bvhLinks,
    jint totalBvhNodes,
    jintArray textureAtlas,
    jstring path)
{
    if (!initGL(w, h))
        return;

    if (!entityProgram)
    {
        entityProgram = compileComputeShader(toString(env, path));
        if (!entityProgram)
            return;
    }

    jint *pixelsPtr = (jint *)env->GetPrimitiveArrayCritical(pixels, nullptr);

    jfloat *camPosPtr = (jfloat *)env->GetPrimitiveArrayCritical(camPos, nullptr);
    jfloat *camFwdPtr = (jfloat *)env->GetPrimitiveArrayCritical(camFwd, nullptr);
    jfloat *camRightPtr = (jfloat *)env->GetPrimitiveArrayCritical(camRight, nullptr);
    jfloat *camUpPtr = (jfloat *)env->GetPrimitiveArrayCritical(camUp, nullptr);

    jfloat *entPosPtr = (jfloat *)env->GetPrimitiveArrayCritical(entPositions, nullptr);
    jfloat *entRotPtr = (jfloat *)env->GetPrimitiveArrayCritical(entRotations, nullptr);
    jfloat *entRadPtr = (jfloat *)env->GetPrimitiveArrayCritical(entRaddi, nullptr);

    jint *entVoxelOffPtr = (jint *)env->GetPrimitiveArrayCritical(entVoxelOffsets, nullptr);
    jint *entBvhOffPtr = (jint *)env->GetPrimitiveArrayCritical(entBvhOffsets, nullptr);

    jfloat *bvPosPtr = (jfloat *)env->GetPrimitiveArrayCritical(bvPositions, nullptr);
    jfloat *bvSizePtr = (jfloat *)env->GetPrimitiveArrayCritical(bvSizes, nullptr);
    jint *bvColorPtr = (jint *)env->GetPrimitiveArrayCritical(bvColors, nullptr);
    jfloat *bvOpPtr = (jfloat *)env->GetPrimitiveArrayCritical(bvOpacities, nullptr);
    jint *bvTexPtr = (jint *)env->GetPrimitiveArrayCritical(bvTextureIds, nullptr);

    jfloat *bvhMinPtr = (jfloat *)env->GetPrimitiveArrayCritical(bvhMins, nullptr);
    jfloat *bvhMaxPtr = (jfloat *)env->GetPrimitiveArrayCritical(bvhMaxs, nullptr);
    jint *bvhLinkPtr = (jint *)env->GetPrimitiveArrayCritical(bvhLinks, nullptr);

    jint *atlasPtr = (jint *)env->GetPrimitiveArrayCritical(textureAtlas, nullptr);
    jsize atlasLength = env->GetArrayLength(textureAtlas);

    std::vector<int> headers(entityCount * 16);

    for (int i = 0; i < entityCount; i++)
    {
        int base = i * 16;

        headers[base + 0] = *(int *)&entPosPtr[i * 3 + 0];
        headers[base + 1] = *(int *)&entPosPtr[i * 3 + 1];
        headers[base + 2] = *(int *)&entPosPtr[i * 3 + 2];

        headers[base + 3] = entVoxelOffPtr[i * 2];
        headers[base + 4] = entVoxelOffPtr[i * 2 + 1];

        for (int r = 0; r < 9; r++)
            headers[base + 5 + r] = *(int *)&entRotPtr[i * 9 + r];

        headers[base + 14] = *(int *)&entRadPtr[i];
        headers[base + 15] = entBvhOffPtr[i];
    }

    std::vector<float> hot(totalVoxels * 4);
    for (int i = 0; i < totalVoxels; i++)
    {
        hot[i * 4 + 0] = bvPosPtr[i * 3 + 0];
        hot[i * 4 + 1] = bvPosPtr[i * 3 + 1];
        hot[i * 4 + 2] = bvPosPtr[i * 3 + 2];
        hot[i * 4 + 3] = bvSizePtr[i];
    }

    std::vector<int> cold(totalVoxels * 4);
    for (int i = 0; i < totalVoxels; i++)
    {
        cold[i * 4 + 0] = bvColorPtr[i];
        cold[i * 4 + 1] = *(int *)&bvOpPtr[i];
        cold[i * 4 + 2] = bvTexPtr[i];
        cold[i * 4 + 3] = 0;
    }

    std::vector<int> nodes(totalBvhNodes * 12);
    for (int i = 0; i < totalBvhNodes; i++)
    {
        int base = i * 12;

        nodes[base + 0] = *(int *)&bvhMinPtr[i * 3 + 0];
        nodes[base + 1] = *(int *)&bvhMinPtr[i * 3 + 1];
        nodes[base + 2] = *(int *)&bvhMinPtr[i * 3 + 2];

        nodes[base + 3] = bvhLinkPtr[i * 3 + 0];

        nodes[base + 4] = *(int *)&bvhMaxPtr[i * 3 + 0];
        nodes[base + 5] = *(int *)&bvhMaxPtr[i * 3 + 1];
        nodes[base + 6] = *(int *)&bvhMaxPtr[i * 3 + 2];

        nodes[base + 7] = bvhLinkPtr[i * 3 + 1];
        nodes[base + 8] = bvhLinkPtr[i * 3 + 2];

        nodes[base + 9] = 0;
        nodes[base + 10] = 0;
        nodes[base + 11] = 0;
    }

    EntityParams params{};
    params.cam[0] = camPosPtr[0];
    params.cam[1] = camPosPtr[1];
    params.cam[2] = camPosPtr[2];

    params.forward[0] = camFwdPtr[0];
    params.forward[1] = camFwdPtr[1];
    params.forward[2] = camFwdPtr[2];

    params.right[0] = camRightPtr[0];
    params.right[1] = camRightPtr[1];
    params.right[2] = camRightPtr[2];

    params.up[0] = camUpPtr[0];
    params.up[1] = camUpPtr[1];
    params.up[2] = camUpPtr[2];

    params.misc0[0] = tanFov;
    params.misc0[1] = (float)entityCount;
    params.misc0[2] = (float)totalVoxels;

    params.misc1[0] = (float)w;
    params.misc1[1] = (float)h;

    env->ReleasePrimitiveArrayCritical(pixels, pixelsPtr, 0);

    env->ReleasePrimitiveArrayCritical(camPos, camPosPtr, JNI_ABORT);
    env->ReleasePrimitiveArrayCritical(camFwd, camFwdPtr, JNI_ABORT);
    env->ReleasePrimitiveArrayCritical(camRight, camRightPtr, JNI_ABORT);
    env->ReleasePrimitiveArrayCritical(camUp, camUpPtr, JNI_ABORT);

    env->ReleasePrimitiveArrayCritical(entPositions, entPosPtr, JNI_ABORT);
    env->ReleasePrimitiveArrayCritical(entRotations, entRotPtr, JNI_ABORT);
    env->ReleasePrimitiveArrayCritical(entRaddi, entRadPtr, JNI_ABORT);

    env->ReleasePrimitiveArrayCritical(entVoxelOffsets, entVoxelOffPtr, JNI_ABORT);
    env->ReleasePrimitiveArrayCritical(entBvhOffsets, entBvhOffPtr, JNI_ABORT);

    env->ReleasePrimitiveArrayCritical(bvPositions, bvPosPtr, JNI_ABORT);
    env->ReleasePrimitiveArrayCritical(bvSizes, bvSizePtr, JNI_ABORT);
    env->ReleasePrimitiveArrayCritical(bvColors, bvColorPtr, JNI_ABORT);
    env->ReleasePrimitiveArrayCritical(bvOpacities, bvOpPtr, JNI_ABORT);
    env->ReleasePrimitiveArrayCritical(bvTextureIds, bvTexPtr, JNI_ABORT);

    env->ReleasePrimitiveArrayCritical(bvhMins, bvhMinPtr, JNI_ABORT);
    env->ReleasePrimitiveArrayCritical(bvhMaxs, bvhMaxPtr, JNI_ABORT);
    env->ReleasePrimitiveArrayCritical(bvhLinks, bvhLinkPtr, JNI_ABORT);

    env->ReleasePrimitiveArrayCritical(textureAtlas, atlasPtr, JNI_ABORT);

    uploadSSBO(entityPixelsBuffer, sizeof(jint) * w * h, pixelsPtr);
    uploadSSBO(entHeaderBuffer, sizeof(int) * headers.size(), headers.data());
    uploadSSBO(bvHotBuffer, sizeof(float) * hot.size(), hot.data());
    uploadSSBO(bvColdBuffer, sizeof(int) * cold.size(), cold.data());
    uploadSSBO(bvhNodeBuffer, sizeof(int) * nodes.size(), nodes.data());
    uploadSSBO(entityAtlasBuffer, sizeof(jint) * atlasLength, atlasPtr);
    uploadSSBO(entityParamsBuffer, sizeof(EntityParams), &params);

    glUseProgram(entityProgram);
    glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, entityPixelsBuffer);
    glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, entityParamsBuffer);
    glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, entHeaderBuffer);
    glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 3, bvHotBuffer);
    glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 4, bvColdBuffer);
    glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 5, entityAtlasBuffer);
    glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 6, tBuffer);
    glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 7, bvhNodeBuffer);

    GLuint gx = (w + 7) / 8;
    GLuint gy = (h + 7) / 8;

    glDispatchCompute(gx, gy, 1);
    glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);

    glBindBuffer(GL_SHADER_STORAGE_BUFFER, entityPixelsBuffer);
    void *mapped = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
    memcpy(pixelsPtr, mapped, sizeof(jint) * w * h);
    glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);

    env->ReleasePrimitiveArrayCritical(pixels, pixelsPtr, 0);
}