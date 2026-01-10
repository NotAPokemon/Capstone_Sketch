package dev.korgi.game.rendering;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import dev.korgi.jni.KorgiJNI;
import dev.korgi.math.Vector3;
import dev.korgi.math.Vector4;
import dev.korgi.math.VectorConstants;

public class NativeGPUKernal {

    private static int[] pixels;
    private static int width, height;

    private static int[] vcolor;
    public static TextureAtlas textureAtlas;
    private static int[] textureLocation;
    private static float[] opacity;
    private static int[] voxelGrid;

    public static void loadTextureMap() {

        File dir = new File(System.getProperty("user.dir") + "/texture");

        Pattern pattern = Pattern.compile("(.+)_([0-9]+)\\.(png|jpg|jpeg)");
        Map<Integer, BufferedImage> textures = new HashMap<>();

        for (File file : Objects.requireNonNull(dir.listFiles())) {
            Matcher m = pattern.matcher(file.getName());
            if (!m.matches())
                continue;

            int id = Integer.parseInt(m.group(2));

            try {
                textures.put(id, ImageIO.read(file));
            } catch (Exception e) {
                throw new RuntimeException("Failed to load " + file.getName(), e);
            }
        }

        textureAtlas = new TextureAtlas(textures.size());

        for (Map.Entry<Integer, BufferedImage> entry : textures.entrySet()) {
            int id = entry.getKey();
            BufferedImage img = entry.getValue();
            textureAtlas.addTexture(id, img);
        }

    }

    public static void resetSpecs(int[] pixels, int width, int height) {
        NativeGPUKernal.pixels = pixels;
        NativeGPUKernal.width = width;
        NativeGPUKernal.height = height;
    }

    private static int rgbToARGB(float r, float g, float b, int a) {
        int ri = (int) (Math.min(r, 1f) * 255);
        int gi = (int) (Math.min(g, 1f) * 255);
        int bi = (int) (Math.min(b, 1f) * 255);
        return (a << 24) | (ri << 16) | (gi << 8) | bi;
    }

    public static void execute(List<Voxel> voxels, Camera camera) {
        precompute(voxels, camera);
    }

    private static void precompute(List<Voxel> voxels, Camera camera) {
        if (voxels.isEmpty()) {
            for (int i = 0; i < pixels.length; i++) {
                pixels[i] = 0xFF87CEEB;
            }
            return;
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (Voxel v : voxels) {
            int x = (int) v.position.x;
            int y = (int) v.position.y;
            int z = (int) v.position.z;
            if (x < minX)
                minX = x;
            if (y < minY)
                minY = y;
            if (z < minZ)
                minZ = z;
            if (x > maxX)
                maxX = x;
            if (y > maxY)
                maxY = y;
            if (z > maxZ)
                maxZ = z;
        }

        Vector3 min = new Vector3(minX, minY, minZ);

        Vector3 size = new Vector3(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1).add(VectorConstants.ONE);

        float tanFov = (float) Math.tan(Math.toRadians(camera.fov * 0.5f));

        double yawRad = camera.rotation.y;
        double pitchRad = camera.rotation.x;

        Vector3 forward = new Vector3(
                Math.sin(yawRad) * Math.cos(pitchRad),
                Math.sin(pitchRad),
                Math.cos(yawRad) * Math.cos(pitchRad)).normalizeHere();

        Vector3 right = new Vector3(
                Math.sin(yawRad - Math.PI / 2.0),
                0,
                Math.cos(yawRad - Math.PI / 2.0)).normalizeHere();

        Vector3 up = right.cross(forward).normalizeHere();

        int voxelCount = voxels.size();
        if (vcolor == null || voxelCount != vcolor.length) {
            vcolor = new int[voxelCount];
            opacity = new float[voxelCount];
            textureLocation = new int[voxelCount];
        }

        if (voxelGrid == null || voxelGrid.length != size.multiplyComp())
            voxelGrid = new int[(int) size.multiplyComp()];
        for (int i = 0; i < voxelGrid.length; i++)
            voxelGrid[i] = -1;

        for (int i = 0; i < voxelCount; i++) {
            Voxel v = voxels.get(i);
            Vector3 g = v.position.subtract(min);

            if (g.x < 0 || g.x >= size.x ||
                    g.y < 0 || g.y >= size.y ||
                    g.z < 0 || g.z >= size.z)
                continue;

            Vector4 color = v.getMaterial().getColor();
            vcolor[i] = rgbToARGB((float) color.x, (float) color.y, (float) color.z, 1);
            opacity[i] = (float) v.getMaterial().getOpacity();
            textureLocation[i] = v.getMaterial().getTextureLocation();

            voxelGrid[(int) ((int) g.x + (int) g.y * (int) size.x + (int) g.z * (int) size.x * (int) size.y)] = i;
        }

        String path = System.getProperty("user.dir");

        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("mac")) {
            path += "/natives/mac/build/Shaders.metallib";
        } else if (os.contains("win")) {
            path += "\\natives\\win\\src\\Shaders.comp.glsl";
        }

        KorgiJNI.executeKernal(pixels, width, height, camera.position.toFloatArray(), forward.toFloatArray(),
                right.toFloatArray(),
                up.toFloatArray(), tanFov,
                voxelCount,
                vcolor, opacity,
                min.toIntArray(),
                size.toIntArray(),
                voxelGrid,
                path,
                textureLocation,
                textureAtlas.getAtlas());

    }

}
