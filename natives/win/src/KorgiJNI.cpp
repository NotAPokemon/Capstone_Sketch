#include <glad/glad.h>
#include <GLFW/glfw3.h>
#include <jni.h>
#include "dev_korgi_jni_KorgiJNI.h"
#include <vector>
#include <iostream>
#include <fstream>
#include <sstream>

static GLuint computeProgram = 0;
static GLuint pixelsBuffer = 0;
static GLuint voxelBuffer = 0;
static GLuint colorBuffer = 0;
static GLuint opacityBuffer = 0;
static GLuint textureLocationBuffer = 0;
static GLuint textureAtlasBuffer = 0;

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

GLuint compileComputeShader(const std::string &path)
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

std::string toString(JNIEnv *env, jstring jstr)
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
    jintArray textureAtlas)
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
    jint *colorPtr = env->GetIntArrayElements(color, nullptr);
    jfloat *opacityPtr = env->GetFloatArrayElements(opacity, nullptr);
    jint *worldMin = env->GetIntArrayElements(worldMinArray, nullptr);
    jint *worldSize = env->GetIntArrayElements(worldSizeArray, nullptr);
    jint *voxelGridPtr = env->GetIntArrayElements(voxelGrid, nullptr);
    jfloat *camPtr = env->GetFloatArrayElements(cam, nullptr);
    jfloat *forwardPtr = env->GetFloatArrayElements(forward, nullptr);
    jfloat *rightPtr = env->GetFloatArrayElements(right, nullptr);
    jfloat *upPtr = env->GetFloatArrayElements(up, nullptr);
    jint *textureLocationPtr = env->GetIntArrayElements(textureLocation, nullptr);
    jint *textureAtlasPtr = env->GetIntArrayElements(textureAtlas, nullptr);

    if (!pixelsBuffer)
        glGenBuffers(1, &pixelsBuffer);
    glBindBuffer(GL_SHADER_STORAGE_BUFFER, pixelsBuffer);
    glBufferData(GL_SHADER_STORAGE_BUFFER, sizeof(jint) * width * height, pixelsPtr, GL_DYNAMIC_DRAW);

    if (!voxelBuffer)
        glGenBuffers(1, &voxelBuffer);
    glBindBuffer(GL_SHADER_STORAGE_BUFFER, voxelBuffer);
    glBufferData(GL_SHADER_STORAGE_BUFFER, sizeof(jint) * worldSize[0] * worldSize[1] * worldSize[2], voxelGridPtr, GL_STATIC_DRAW);

    if (!colorBuffer)
        glGenBuffers(1, &colorBuffer);
    glBindBuffer(GL_SHADER_STORAGE_BUFFER, colorBuffer);
    glBufferData(GL_SHADER_STORAGE_BUFFER, sizeof(jint) * voxCount, colorPtr, GL_STATIC_DRAW);

    if (!opacityBuffer)
        glGenBuffers(1, &opacityBuffer);
    glBindBuffer(GL_SHADER_STORAGE_BUFFER, opacityBuffer);
    glBufferData(GL_SHADER_STORAGE_BUFFER, sizeof(float) * voxCount, opacityPtr, GL_STATIC_DRAW);

    if (!textureLocationBuffer)
        glGenBuffers(1, &textureLocationBuffer);
    glBindBuffer(GL_SHADER_STORAGE_BUFFER, textureLocationBuffer);
    glBufferData(GL_SHADER_STORAGE_BUFFER, sizeof(jint) * voxCount, textureLocationPtr, GL_STATIC_DRAW);

    jsize length = env->GetArrayLength(textureAtlas);

    if (!textureAtlasBuffer)
        glGenBuffers(1, &textureAtlasBuffer);
    glBindBuffer(GL_SHADER_STORAGE_BUFFER, textureAtlasBuffer);
    glBufferData(GL_SHADER_STORAGE_BUFFER, sizeof(jint) * length, textureAtlasPtr, GL_STATIC_DRAW);

    glUseProgram(computeProgram);

    glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, pixelsBuffer);
    glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, voxelBuffer);
    glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, colorBuffer);
    glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 3, opacityBuffer);
    glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 4, textureLocationBuffer);
    glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 5, textureAtlasBuffer);

    GLint loc_cam = glGetUniformLocation(computeProgram, "cam");
    GLint loc_forward = glGetUniformLocation(computeProgram, "forward");
    GLint loc_right = glGetUniformLocation(computeProgram, "right");
    GLint loc_up = glGetUniformLocation(computeProgram, "up");
    GLint loc_tanFov = glGetUniformLocation(computeProgram, "tanFov");
    GLint loc_voxCount = glGetUniformLocation(computeProgram, "voxCount");
    GLint loc_worldMin = glGetUniformLocation(computeProgram, "worldMin");
    GLint loc_worldSize = glGetUniformLocation(computeProgram, "worldSize");
    GLint loc_width = glGetUniformLocation(computeProgram, "width");
    GLint loc_height = glGetUniformLocation(computeProgram, "height");

    glUniform3f(loc_cam, camPtr[0], camPtr[1], camPtr[2]);
    glUniform3f(loc_forward, forwardPtr[0], forwardPtr[1], forwardPtr[2]);
    glUniform3f(loc_right, rightPtr[0], rightPtr[1], rightPtr[2]);
    glUniform3f(loc_up, upPtr[0], upPtr[1], upPtr[2]);
    glUniform1f(loc_tanFov, tanFov);
    glUniform1i(loc_voxCount, voxCount);
    glUniform3i(loc_worldMin, worldMin[0], worldMin[1], worldMin[2]);
    glUniform3i(loc_worldSize, worldSize[0], worldSize[1], worldSize[2]);
    glUniform1i(loc_width, width);
    glUniform1i(loc_height, height);

    GLuint workGroupSizeX = 8;
    GLuint workGroupSizeY = 8;
    GLuint groupsX = (width + workGroupSizeX - 1) / workGroupSizeX;
    GLuint groupsY = (height + workGroupSizeY - 1) / workGroupSizeY;
    glDispatchCompute(groupsX, groupsY, 1);
    glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);

    glBindBuffer(GL_SHADER_STORAGE_BUFFER, pixelsBuffer);
    void *ptr = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
    memcpy(pixelsPtr, ptr, sizeof(jint) * width * height);
    glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);

    env->ReleaseIntArrayElements(pixels, pixelsPtr, 0);
    env->ReleaseIntArrayElements(color, colorPtr, 0);
    env->ReleaseFloatArrayElements(opacity, opacityPtr, 0);
    env->ReleaseIntArrayElements(worldMinArray, worldMin, 0);
    env->ReleaseIntArrayElements(worldSizeArray, worldSize, 0);
    env->ReleaseIntArrayElements(voxelGrid, voxelGridPtr, 0);
    env->ReleaseFloatArrayElements(cam, camPtr, 0);
    env->ReleaseFloatArrayElements(forward, forwardPtr, 0);
    env->ReleaseFloatArrayElements(right, rightPtr, 0);
    env->ReleaseFloatArrayElements(up, upPtr, 0);
    env->ReleaseIntArrayElements(textureLocation, textureLocationPtr, 0);
    env->ReleaseIntArrayElements(textureAtlas, textureAtlasPtr, 0);
}
