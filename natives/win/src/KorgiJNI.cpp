#include <jni.h>
#include <iostream>
#include <vector>
#include <fstream>
#include <sstream>

#include <glad/glad.h>
#include <GLFW/glfw3.h>

#define GLFW_EXPOSE_NATIVE_WIN32
#include <GLFW/glfw3native.h>

#include "dev_korgi_jni_KorgiJNI.h"

// Helper: read shader source
std::string readFile(const char* path) {
    std::ifstream file(path);
    if (!file.is_open()) {
        std::cerr << "Failed to open shader file: " << path << std::endl;
        return "";
    }
    std::stringstream ss;
    ss << file.rdbuf();
    return ss.str();
}

// Helper: compile compute shader
GLuint compileComputeShader(const char* path) {
    std::string srcStr = readFile(path);
    const char* src = srcStr.c_str();

    GLuint shader = glCreateShader(GL_COMPUTE_SHADER);
    glShaderSource(shader, 1, &src, nullptr);
    glCompileShader(shader);

    GLint success;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &success);
    if (!success) {
        char info[512];
        glGetShaderInfoLog(shader, 512, nullptr, info);
        std::cerr << "Compute shader compile error: " << info << std::endl;
    }

    GLuint program = glCreateProgram();
    glAttachShader(program, shader);
    glLinkProgram(program);

    glGetProgramiv(program, GL_LINK_STATUS, &success);
    if (!success) {
        char info[512];
        glGetProgramInfoLog(program, 512, nullptr, info);
        std::cerr << "Program link error: " << info << std::endl;
    }

    glDeleteShader(shader);
    return program;
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
    jintArray voxelGrid
) {
    // -------------------------
    // 1. Get pointers from Java
    // -------------------------
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

    // -------------------------
    // 2. Init GLFW + hidden window
    // -------------------------
    if (!glfwInit()) {
        std::cerr << "Failed to init GLFW" << std::endl;
        return;
    }

    glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
    glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);

    GLFWwindow* window = glfwCreateWindow(1, 1, "Hidden", nullptr, nullptr);
    if (!window) {
        std::cerr << "Failed to create GLFW window" << std::endl;
        glfwTerminate();
        return;
    }

    glfwMakeContextCurrent(window);

    if (!gladLoadGLLoader((GLADloadproc)glfwGetProcAddress)) {
        std::cerr << "Failed to init GLAD" << std::endl;
        glfwDestroyWindow(window);
        glfwTerminate();
        return;
    }

    // -------------------------
    // 3. Compile compute shader
    // -------------------------
    GLuint computeProgram = compileComputeShader("Shaders.comp.glsl");
    glUseProgram(computeProgram);

    // -------------------------
    // 4. Create SSBOs
    // -------------------------
    GLuint pixelsBuffer, voxelBuffer, colorBuffer, opacityBuffer;
    glGenBuffers(1, &pixelsBuffer);
    glBindBuffer(GL_SHADER_STORAGE_BUFFER, pixelsBuffer);
    glBufferData(GL_SHADER_STORAGE_BUFFER, width*height*sizeof(int), pixelsPtr, GL_DYNAMIC_COPY);
    glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, pixelsBuffer);

    int voxelGridSize = worldSize[0]*worldSize[1]*worldSize[2];
    glGenBuffers(1, &voxelBuffer);
    glBindBuffer(GL_SHADER_STORAGE_BUFFER, voxelBuffer);
    glBufferData(GL_SHADER_STORAGE_BUFFER, voxelGridSize*sizeof(int), voxelGridPtr, GL_STATIC_DRAW);
    glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, voxelBuffer);

    glGenBuffers(1, &colorBuffer);
    glBindBuffer(GL_SHADER_STORAGE_BUFFER, colorBuffer);
    glBufferData(GL_SHADER_STORAGE_BUFFER, voxCount*sizeof(int), colorPtr, GL_STATIC_DRAW);
    glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, colorBuffer);

    glGenBuffers(1, &opacityBuffer);
    glBindBuffer(GL_SHADER_STORAGE_BUFFER, opacityBuffer);
    glBufferData(GL_SHADER_STORAGE_BUFFER, voxCount*sizeof(float), opacityPtr, GL_STATIC_DRAW);
    glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 3, opacityBuffer);

    // -------------------------
    // 5. Set uniforms
    // -------------------------
    GLint loc = glGetUniformLocation(computeProgram, "width"); glUniform1i(loc, width);
    loc = glGetUniformLocation(computeProgram, "height"); glUniform1i(loc, height);
    loc = glGetUniformLocation(computeProgram, "tanFov"); glUniform1f(loc, tanFov);
    loc = glGetUniformLocation(computeProgram, "voxCount"); glUniform1i(loc, voxCount);

    loc = glGetUniformLocation(computeProgram, "cam"); glUniform3f(loc, camPtr[0], camPtr[1], camPtr[2]);
    loc = glGetUniformLocation(computeProgram, "forward"); glUniform3f(loc, forwardPtr[0], forwardPtr[1], forwardPtr[2]);
    loc = glGetUniformLocation(computeProgram, "right"); glUniform3f(loc, rightPtr[0], rightPtr[1], rightPtr[2]);
    loc = glGetUniformLocation(computeProgram, "up"); glUniform3f(loc, upPtr[0], upPtr[1], upPtr[2]);

    loc = glGetUniformLocation(computeProgram, "worldMin"); glUniform3i(loc, worldMin[0], worldMin[1], worldMin[2]);
    loc = glGetUniformLocation(computeProgram, "worldSize"); glUniform3i(loc, worldSize[0], worldSize[1], worldSize[2]);

    // -------------------------
    // 6. Dispatch compute shader
    // -------------------------
    int workgroupX = (width + 7)/8;
    int workgroupY = (height + 7)/8;
    glDispatchCompute(workgroupX, workgroupY, 1);

    glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);

    // -------------------------
    // 7. Copy pixels back
    // -------------------------
    glBindBuffer(GL_SHADER_STORAGE_BUFFER, pixelsBuffer);
    int* ptr = (int*)glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
    memcpy(pixelsPtr, ptr, width*height*sizeof(int));
    glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);

    // -------------------------
    // 8. Cleanup
    // -------------------------
    glDeleteBuffers(1, &pixelsBuffer);
    glDeleteBuffers(1, &voxelBuffer);
    glDeleteBuffers(1, &colorBuffer);
    glDeleteBuffers(1, &opacityBuffer);
    glDeleteProgram(computeProgram);

    glfwDestroyWindow(window);
    glfwTerminate();

    // -------------------------
    // 9. Release Java arrays
    // -------------------------
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
