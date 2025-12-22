package dev.korgi.game.rendering;

import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

import dev.korgi.math.Vector4;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.glBindBufferBase;
import static org.lwjgl.opengl.GL42.glMemoryBarrier;
import static org.lwjgl.opengl.GL43.*;

public class GridRaytraceKernel {

    private int width, height;
    private int[] pixels;

    // OpenGL handles
    private int program;
    private int pixelSSBO;
    private int cameraSSBO;
    private int voxelSSBO;
    private boolean glInitialized = false;
    private long hiddenWindow;

    // Camera + voxel data
    private float camX, camY, camZ;
    private float forwardX, forwardY, forwardZ;
    private float rightX, rightY, rightZ;
    private float upX, upY, upZ;

    private int worldMinX, worldMinY, worldMinZ;
    private int worldSizeX, worldSizeY, worldSizeZ;
    private int voxelCount;
    private int[] vcolor;
    private float[] opacity;
    private int[] voxelGrid;

    public GridRaytraceKernel(int[] pixels, int width, int height) {
        this.pixels = pixels;
        this.width = width;
        this.height = height;
    }

    // -------------------------------
    // High-level interface
    // -------------------------------
    public void precompute(List<Voxel> voxels, Camera cam) {
        if (voxels.isEmpty())
            return;

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        for (Voxel v : voxels) {
            int x = (int) v.position.x;
            int y = (int) v.position.y;
            int z = (int) v.position.z;
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }

        camX = (float) cam.position.x;
        camY = (float) cam.position.y;
        camZ = (float) cam.position.z;

        worldMinX = minX;
        worldMinY = minY;
        worldMinZ = minZ;
        worldSizeX = maxX - minX + 1;
        worldSizeY = maxY - minY + 1;
        worldSizeZ = maxZ - minZ + 1;

        computeCameraBasis((float) cam.rotation.x, (float) cam.rotation.y);

        voxelCount = voxels.size();
        if (vcolor == null || vcolor.length != voxelCount) {
            vcolor = new int[voxelCount];
            opacity = new float[voxelCount];
        }

        if (voxelGrid == null || voxelGrid.length != worldSizeX * worldSizeY * worldSizeZ)
            voxelGrid = new int[worldSizeX * worldSizeY * worldSizeZ];

        for (int i = 0; i < voxelGrid.length; i++)
            voxelGrid[i] = -1;

        for (int i = 0; i < voxelCount; i++) {
            Voxel v = voxels.get(i);
            int gx = (int) v.position.x - worldMinX;
            int gy = (int) v.position.y - worldMinY;
            int gz = (int) v.position.z - worldMinZ;

            if (gx < 0 || gx >= worldSizeX || gy < 0 || gy >= worldSizeY || gz >= worldSizeZ)
                continue;

            Vector4 color = v.getMaterial().getColor();
            vcolor[i] = rgbToARGB((float) color.x, (float) color.y, (float) color.z, 1);
            opacity[i] = (float) v.getMaterial().getOpacity();

            voxelGrid[gx + gy * worldSizeX + gz * worldSizeX * worldSizeY] = i;
        }

        initGL();
        uploadVoxelGrid();
    }

    public void execute() {
        uploadCamera();
        dispatchCompute();
        readPixels();
    }

    // -------------------------------
    // OpenGL backend
    // -------------------------------
    private void initGL() {
        if (glInitialized)
            return;

        // Hidden GLFW window for context
        GLFW.glfwInit();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        hiddenWindow = GLFW.glfwCreateWindow(1, 1, "", MemoryUtil.NULL, MemoryUtil.NULL);
        if (hiddenWindow == MemoryUtil.NULL)
            throw new RuntimeException("Failed to create hidden GLFW window");
        GLFW.glfwMakeContextCurrent(hiddenWindow);
        GL.createCapabilities();

        program = createComputeProgram(loadShader("/raytrace.glsl"));

        pixelSSBO = glGenBuffers();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, pixelSSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, (long) width * height * 4, GL_DYNAMIC_DRAW);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, pixelSSBO);

        cameraSSBO = glGenBuffers();
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, cameraSSBO);

        voxelSSBO = glGenBuffers();
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, voxelSSBO);

        glInitialized = true;
    }

    private int createComputeProgram(String glslSource) {
        int shader = glCreateShader(GL_COMPUTE_SHADER);
        glShaderSource(shader, glslSource);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE)
            throw new RuntimeException("Shader compile failed: " + glGetShaderInfoLog(shader));

        int program = glCreateProgram();
        glAttachShader(program, shader);
        glLinkProgram(program);
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE)
            throw new RuntimeException("Program link failed: " + glGetProgramInfoLog(program));

        glDetachShader(program, shader);
        glDeleteShader(shader);
        return program;
    }

    private static String loadShader(String resourcePath) {
        StringBuilder sb = new StringBuilder();
        try (InputStream in = GridRaytraceKernel.class.getResourceAsStream(resourcePath);
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null)
                sb.append(line).append("\n");
        } catch (Exception e) {
            throw new RuntimeException("Failed to load shader: " + resourcePath, e);
        }
        return sb.toString();
    }

    private void uploadCamera() {
        ByteBuffer buf = BufferUtils.createByteBuffer(64);
        buf.putFloat(camX).putFloat(camY).putFloat(camZ).putFloat(0);
        buf.putFloat(forwardX).putFloat(forwardY).putFloat(forwardZ).putFloat(0);
        buf.putFloat(rightX).putFloat(rightY).putFloat(rightZ).putFloat(0);
        buf.putFloat(upX).putFloat(upY).putFloat(upZ).putFloat(0);
        buf.flip();

        glBindBuffer(GL_SHADER_STORAGE_BUFFER, cameraSSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, buf, GL_DYNAMIC_DRAW);
    }

    private void uploadVoxelGrid() {
        IntBuffer buf = BufferUtils.createIntBuffer(voxelGrid.length);
        buf.put(voxelGrid).flip();

        glBindBuffer(GL_SHADER_STORAGE_BUFFER, voxelSSBO);
        glBufferData(GL_SHADER_STORAGE_BUFFER, buf, GL_DYNAMIC_DRAW);
    }

    private void dispatchCompute() {
        glUseProgram(program);
        int gx = (width + 15) / 16;
        int gy = (height + 15) / 16;
        glDispatchCompute(gx, gy, 1);
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
    }

    private void readPixels() {
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, pixelSSBO);
        ByteBuffer buf = glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
        for (int i = 0; i < pixels.length; i++) {
            int r = buf.get() & 0xFF;
            int g = buf.get() & 0xFF;
            int b = buf.get() & 0xFF;
            int a = buf.get() & 0xFF;
            pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }
        glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
    }

    // -------------------------------
    // Utility
    // -------------------------------
    private void computeCameraBasis(float pitchRad, float yawRad) {
        forwardX = (float) (Math.sin(yawRad) * Math.cos(pitchRad));
        forwardY = (float) Math.sin(pitchRad);
        forwardZ = (float) (Math.cos(yawRad) * Math.cos(pitchRad));

        rightX = (float) Math.sin(yawRad - Math.PI / 2.0);
        rightY = 0;
        rightZ = (float) Math.cos(yawRad - Math.PI / 2.0);

        upX = rightY * forwardZ - rightZ * forwardY;
        upY = rightZ * forwardX - rightX * forwardZ;
        upZ = rightX * forwardY - rightY * forwardX;

        float fLen = (float) Math.sqrt(forwardX * forwardX + forwardY * forwardY + forwardZ * forwardZ);
        forwardX /= fLen;
        forwardY /= fLen;
        forwardZ /= fLen;

        float rLen = (float) Math.sqrt(rightX * rightX + rightY * rightY + rightZ * rightZ);
        rightX /= rLen;
        rightY /= rLen;
        rightZ /= rLen;

        float uLen = (float) Math.sqrt(upX * upX + upY * upY + upZ * upZ);
        upX /= uLen;
        upY /= uLen;
        upZ /= uLen;
    }

    private int rgbToARGB(float r, float g, float b, int a) {
        int ri = (int) (Math.min(r, 1f) * 255);
        int gi = (int) (Math.min(g, 1f) * 255);
        int bi = (int) (Math.min(b, 1f) * 255);
        return (a << 24) | (ri << 16) | (gi << 8) | bi;
    }

    // Cleanup context
    public void cleanup() {
        glDeleteProgram(program);
        glDeleteBuffers(pixelSSBO);
        glDeleteBuffers(cameraSSBO);
        glDeleteBuffers(voxelSSBO);
        GLFW.glfwDestroyWindow(hiddenWindow);
        GLFW.glfwTerminate();
    }
}
