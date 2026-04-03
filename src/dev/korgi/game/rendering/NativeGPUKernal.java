package dev.korgi.game.rendering;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
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
    private static int[] chunkGrid;
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

        textureAtlas.build();
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
            precompute(world);
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

    public static int render_dist = 50;
    private static final List<Voxel> voxels = new ArrayList<>();
    private static final Vector3 min = new Vector3();
    private static final Vector3 max = new Vector3();
    private static float tanFov = -1;

    private static void precompute(WorldStorage world) {
        if (world.voxels.values().isEmpty()) {
            Arrays.fill(pixels, 0xFF87CEEB);
            return;
        }

        min.copyFrom(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        max.copyFrom(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);

        int radius = render_dist;

        Vector3 forward = camera.getForward();

        Vector3 right = camera.getRight();

        Vector3 up = right.cross(forward).normalizeHere();

        voxels.clear();
        if (world.voxels.values().size() < radius * radius * radius * 8) {
            for (Voxel v : world.voxels.values()) {
                double dx = v.position.x - camera.position.x;
                double dy = v.position.y - camera.position.y;
                double dz = v.position.z - camera.position.z;
                if (Math.abs(dx) > radius || Math.abs(dy) > radius || Math.abs(dz) > radius)
                    continue;
                int xv = (int) v.position.x;
                int yv = (int) v.position.y;
                int zv = (int) v.position.z;
                if (xv < min.x)
                    min.x = xv;
                if (yv < min.y)
                    min.y = yv;
                if (zv < min.z)
                    min.z = zv;
                if (xv > max.x)
                    max.x = xv;
                if (yv > max.y)
                    max.y = yv;
                if (zv > max.z)
                    max.z = zv;
                voxels.add(v);
            }
        } else {
            for (int x = -radius; x < radius; x++) {
                for (int y = -radius; y < radius; y++) {
                    for (int z = -radius; z < radius; z++) {
                        int xv = (int) camera.position.x + x;
                        int yv = (int) camera.position.y + y;
                        int zv = (int) camera.position.z + z;
                        Voxel v = world.voxels.get(WorldStorage.voxelKey(xv, yv, zv));
                        if (v == null)
                            continue;

                        if (xv < min.x)
                            min.x = xv;
                        if (yv < min.y)
                            min.y = yv;
                        if (zv < min.z)
                            min.z = zv;
                        if (xv > max.x)
                            max.x = xv;
                        if (yv > max.y)
                            max.y = yv;
                        if (zv > max.z)
                            max.z = zv;
                        voxels.add(v);
                    }
                }
            }
        }

        if (voxels.isEmpty()) {
            Arrays.fill(pixels, 0xFF87CEEB);
            return;
        }

        min.subtractFrom(VectorConstants.ONE);
        max.addTo(VectorConstants.ONE);
        Vector3 size = max.subtract(min).addTo(VectorConstants.ONE);

        if (tanFov < 0) {
            tanFov = (float) Math.tan(Math.toRadians(camera.fov * 0.5f));
        }

        int voxelCount = voxels.size();

        Vector3 chunckSize = size.add(7).multiplyBy(1.0 / 8.0);

        if (chunkGrid == null || chunkGrid.length != (int) chunckSize.multiplyComp())
            chunkGrid = new int[(int) chunckSize.multiplyComp()];
        Arrays.fill(chunkGrid, 0);

        if (vcolor == null || vcolor.length != voxelCount) {
            vcolor = new int[voxelCount];
            opacity = new float[voxelCount];
            textureLocation = new int[voxelCount];
        }

        if (voxelGrid == null || voxelGrid.length != (int) size.multiplyComp())
            voxelGrid = new int[(int) size.multiplyComp()];

        Arrays.fill(voxelGrid, -1);
        for (int i = 0; i < voxelCount; i++) {
            Voxel v = voxels.get(i);
            Vector3 g = v.position.subtract(min);

            if (g.x < 0 || g.x >= size.x ||
                    g.y < 0 || g.y >= size.y ||
                    g.z < 0 || g.z >= size.z)
                continue;

            textureLocation[i] = v.getMaterial().getTextureLocation();
            if (textureLocation[i] == -1) {
                Vector4 color = v.getMaterial().getColor();
                vcolor[i] = rgbToARGB((float) color.x, (float) color.y, (float) color.z, 1);
                opacity[i] = (float) v.getMaterial().getOpacity();
            }

            voxelGrid[(int) ((int) g.x + (int) g.y * (int) size.x + (int) g.z * (int) size.x * (int) size.y)] = i;

            int cx = (int) g.x / 8;
            int cy = (int) g.y / 8;
            int cz = (int) g.z / 8;
            chunkGrid[cx + cy * (int) chunckSize.x + cz * (int) chunckSize.x * (int) chunckSize.y] = 1;
        }

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
                    textureAtlas.getAtlas(),
                    chunkGrid,
                    chunckSize.toIntArray());
        }, 0.05, "High Render Latency: %f");
    }

}
