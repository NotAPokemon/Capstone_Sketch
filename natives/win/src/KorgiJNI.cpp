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
static GLuint chunkGridBuffer = 0;
static GLuint chunkSizeBuffer = 0;

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
    uploadSSBO(textureAtlasBuffer, sizeof(jint) * atlasLen, textureAtlasPtr);
    uploadSSBO(chunkGridBuffer, sizeof(jint) * chunkGridLen, chunkGridPtr);
    uploadSSBO(chunkSizeBuffer, sizeof(jint) * 3, chunkSizePtr);

    glUseProgram(computeProgram);

    glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, pixelsBuffer);
    glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, voxelBuffer);
    glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, colorBuffer);
    glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 3, opacityBuffer);
    glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 4, textureLocationBuffer);
    glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 5, textureAtlasBuffer);
    glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 6, chunkGridBuffer);
    glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 7, chunkSizeBuffer);

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
    env->ReleaseIntArrayElements(textureAtlas, textureAtlasPtr, 0);
    env->ReleaseIntArrayElements(chunkGrid, chunkGridPtr, 0);
    env->ReleaseIntArrayElements(chunkSize, chunkSizePtr, 0);
}
