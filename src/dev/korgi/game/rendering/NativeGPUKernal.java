package dev.korgi.game.rendering;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import dev.korgi.game.physics.WorldStorage;
import dev.korgi.jni.KorgiJNI;
import dev.korgi.math.Vector3;
import dev.korgi.math.Vector4;
import dev.korgi.math.VectorConstants;
import dev.korgi.utils.Time;

public class NativeGPUKernal {

    private static int[] pixels;
    private static int width, height;

    private static int[] vcolor;
    public static TextureAtlas textureAtlas;
    private static int[] textureLocation;
    private static float[] opacity;
    private static int[] voxelGrid;
    private static Camera camera;

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

    public static void execute(WorldStorage world, Camera camera) {
        NativeGPUKernal.camera = camera;
        Time.time(() -> {
            precompute(world.getFlat());
        }, 0.05, "Warning High Kernal Latency: %f");
    }

    private static String path = System.getProperty("user.dir");

    private static String os = System.getProperty("os.name").toLowerCase();

    static {
        if (os.contains("mac")) {
            path += "/natives/mac/build/Shaders.metallib";
        } else if (os.contains("win")) {
            path += "\\natives\\win\\src\\Shaders.comp.glsl";
        }
    }

    private static void precompute(List<Voxel> voxels) {
        if (voxels.isEmpty()) {
            for (int i = 0; i < pixels.length; i++) {
                pixels[i] = 0xFF87CEEB;
            }
            return;
        }

        Vector3 min = new Vector3(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        Vector3 max = new Vector3(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);

        for (Voxel v : voxels) {
            int x = (int) v.position.x;
            int y = (int) v.position.y;
            int z = (int) v.position.z;
            if (x < min.x)
                min.x = x;
            if (y < min.y)
                min.y = y;
            if (z < min.z)
                min.z = z;
            if (x > max.x)
                max.x = x;
            if (y > max.y)
                max.y = y;
            if (z > max.z)
                max.z = z;
        }

        Vector3 size = new Vector3(max.x - min.x + 1, max.y - min.y + 1, max.z - min.z + 1)
                .add(VectorConstants.ONE);

        float tanFov = (float) Math.tan(Math.toRadians(camera.fov * 0.5f));

        Vector3 forward = camera.getForward();

        Vector3 right = camera.getRight();

        Vector3 up = right.cross(forward).normalizeHere();

        int voxelCount = voxels.size();
        if (vcolor == null || voxelCount != vcolor.length) {
            vcolor = new int[voxelCount];
            opacity = new float[voxelCount];
            textureLocation = new int[voxelCount];
        }

        if (voxelGrid == null || voxelGrid.length != size.multiplyComp())
            voxelGrid = new int[(int) size.multiplyComp()];

        Time.time(() -> {

            Arrays.fill(voxelGrid, -1);

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

        }, 0.05, "High Precompute Latency: %f");

        Time.time(() -> {
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
        }, 0.05, "High Render Latency: %f");

    }

}
