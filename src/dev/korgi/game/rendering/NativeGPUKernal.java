package dev.korgi.game.rendering;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import dev.korgi.game.Game;
import dev.korgi.game.entites.Entity;
import dev.korgi.game.physics.WorldStorage;
import dev.korgi.jni.KorgiJNI;
import dev.korgi.json.JSONObject;
import dev.korgi.math.Vector3;
import dev.korgi.math.Vector4;
import dev.korgi.math.VectorConstants;
import dev.korgi.player.Player;
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

        File dir = new File("./texture/packs/" + Game.config.getString("pack") + "/layout.json");
        JSONObject packLayout = JSONObject.fromFile(dir);
        int amt = packLayout.getInt("count");
        Map<Integer, BufferedImage> textures = new HashMap<>();

        Path textureDir = Path.of("./texture/packs/", Game.config.getString("pack"));

        for (int i = 0; i < amt; i++) {
            File file = textureDir.resolve(packLayout.getString("" + i)).toFile();
            try {
                textures.put(i, ImageIO.read(file));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        textureAtlas = new TextureAtlas(amt);

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
            precomputeEntites(world);
        }, 0.05, "Warning High Kernal Latency: %f");
    }

    private static String path = System.getProperty("user.dir");
    private static String path2 = System.getProperty("user.dir");

    private static String os = System.getProperty("os.name").toLowerCase();

    static {
        if (os.contains("mac")) {
            path += "/Shaders.metallib";
            path2 += "/EntityShader.metallib";
        } else if (os.contains("win")) {
            path += "\\Shaders.comp.glsl";
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

    private static float[] entityPositions;
    private static float[] entityRotations;
    private static int[] entityVoxelOffsets;
    private static int[] entityBvhOffsets;
    private static float[] entityRadii;
    private static float[] bvPositions;
    private static float[] bvSizes;
    private static int[] bvColors;
    private static float[] bvOpacities;
    private static int[] bvTextureIds;
    private static float[] bvhMins;
    private static float[] bvhMaxs;
    private static int[] bvhLinks;
    private static int lastEntitySize;
    private static int lastVoxCount;

    private static int buildBVH(int[] localIndices, int start, int end,
            float[] bvPositions, float[] bvSizes, int globalOffset,
            List<float[]> mins, List<float[]> maxs, List<int[]> links) {
        int nodeIdx = mins.size();

        if (end - start == 1) {
            int gi = globalOffset + localIndices[start];
            float x = bvPositions[gi * 3], y = bvPositions[gi * 3 + 1], z = bvPositions[gi * 3 + 2];
            float s = bvSizes[gi] * 0.5f;
            mins.add(new float[] { x - s, y - s, z - s });
            maxs.add(new float[] { x + s, y + s, z + s });
            links.add(new int[] { -1, -1, localIndices[start] });
            return nodeIdx;
        }

        float aMinX = Float.MAX_VALUE, aMinY = Float.MAX_VALUE, aMinZ = Float.MAX_VALUE;
        float aMaxX = -Float.MAX_VALUE, aMaxY = -Float.MAX_VALUE, aMaxZ = -Float.MAX_VALUE;
        float cMinX = Float.MAX_VALUE, cMinY = Float.MAX_VALUE, cMinZ = Float.MAX_VALUE;
        float cMaxX = -Float.MAX_VALUE, cMaxY = -Float.MAX_VALUE, cMaxZ = -Float.MAX_VALUE;

        for (int i = start; i < end; i++) {
            int gi = globalOffset + localIndices[i];
            float x = bvPositions[gi * 3], y = bvPositions[gi * 3 + 1], z = bvPositions[gi * 3 + 2];
            float s = bvSizes[gi] * 0.5f;
            aMinX = Math.min(aMinX, x - s);
            aMaxX = Math.max(aMaxX, x + s);
            aMinY = Math.min(aMinY, y - s);
            aMaxY = Math.max(aMaxY, y + s);
            aMinZ = Math.min(aMinZ, z - s);
            aMaxZ = Math.max(aMaxZ, z + s);
            cMinX = Math.min(cMinX, x);
            cMaxX = Math.max(cMaxX, x);
            cMinY = Math.min(cMinY, y);
            cMaxY = Math.max(cMaxY, y);
            cMinZ = Math.min(cMinZ, z);
            cMaxZ = Math.max(cMaxZ, z);
        }

        float dx = cMaxX - cMinX, dy = cMaxY - cMinY, dz = cMaxZ - cMinZ;
        final int axis = (dx >= dy && dx >= dz) ? 0 : (dy >= dz) ? 1 : 2;

        Integer[] sub = new Integer[end - start];
        for (int i = 0; i < sub.length; i++)
            sub[i] = localIndices[start + i];
        Arrays.sort(sub, (a, b) -> Float.compare(bvPositions[(globalOffset + a) * 3 + axis],
                bvPositions[(globalOffset + b) * 3 + axis]));
        for (int i = 0; i < sub.length; i++)
            localIndices[start + i] = sub[i];

        mins.add(new float[] { aMinX, aMinY, aMinZ });
        maxs.add(new float[] { aMaxX, aMaxY, aMaxZ });
        links.add(new int[] { -1, -1, -1 });

        int left = buildBVH(localIndices, start, (start + end) / 2, bvPositions, bvSizes, globalOffset, mins, maxs,
                links);
        int right = buildBVH(localIndices, (start + end) / 2, end, bvPositions, bvSizes, globalOffset, mins, maxs,
                links);

        links.get(nodeIdx)[0] = left;
        links.get(nodeIdx)[1] = right;

        return nodeIdx;
    }

    private static void precomputeEntites(WorldStorage world) {
        List<Entity> entities = world.entities;
        Player client = Game.getClient();
        entities.remove(client);

        if (entities.isEmpty()) {
            return;
        }

        int totalVoxels = 0;

        if (lastEntitySize == entities.size()) {
            totalVoxels = lastVoxCount;
        } else {
            for (Entity entity : entities) {
                totalVoxels += entity.getBody().size();
            }
        }
        lastVoxCount = totalVoxels;

        int entityCount = entities.size();

        if (entityPositions == null || entityPositions.length != entityCount * 3) {
            entityPositions = new float[entityCount * 3];
            entityRotations = new float[entityCount * 9];
            entityVoxelOffsets = new int[entityCount * 2];
            entityBvhOffsets = new int[entityCount];
            entityRadii = new float[entityCount];
        }

        if (bvPositions == null || bvPositions.length < totalVoxels * 3) {
            bvPositions = new float[totalVoxels * 3];
            bvSizes = new float[totalVoxels];
            bvColors = new int[totalVoxels];
            bvOpacities = new float[totalVoxels];
            bvTextureIds = new int[totalVoxels];
        }

        int voxelCursor = 0;

        for (int i = 0; i < entityCount; i++) {
            Entity entity = entities.get(i);
            List<Voxel> bodyVoxels = entity.getBody();

            int ep = i * 3;
            Vector3 ePos = entity.getPosition();
            entityPositions[ep] = (float) ePos.x;
            entityPositions[ep + 1] = (float) ePos.y;
            entityPositions[ep + 2] = (float) ePos.z;

            float[] rot = entity.getRotationMatrix();
            int rp = i * 9;
            System.arraycopy(rot, 0, entityRotations, rp, 9);

            entityVoxelOffsets[i * 2] = voxelCursor;
            entityVoxelOffsets[i * 2 + 1] = bodyVoxels.size();

            float maxDist = 0;
            for (Voxel bv : bodyVoxels) {
                float d = (float) bv.position.length() + (float) bv.getMaterial().getSize() * 0.5f;
                if (d > maxDist)
                    maxDist = d;

                Material mat = bv.getMaterial();
                int vp = voxelCursor * 3;
                Vector3 bvPos = bv.position;
                bvPositions[vp] = (float) bvPos.x;
                bvPositions[vp + 1] = (float) bvPos.y;
                bvPositions[vp + 2] = (float) bvPos.z;

                bvSizes[voxelCursor] = (float) mat.getSize();

                int texLoc = mat.getTextureLocation();
                bvTextureIds[voxelCursor] = texLoc;

                if (texLoc == -1) {
                    Vector4 color = mat.getColor();
                    bvColors[voxelCursor] = rgbToARGB(
                            (float) color.x,
                            (float) color.y,
                            (float) color.z,
                            1);
                } else {
                    bvColors[voxelCursor] = -1;
                }

                bvOpacities[voxelCursor] = (float) mat.getOpacity();

                voxelCursor++;
            }
            entityRadii[i] = maxDist;
        }

        List<float[]> bvhMinsList = new ArrayList<>();
        List<float[]> bvhMaxsList = new ArrayList<>();
        List<int[]> bvhLinksList = new ArrayList<>();

        voxelCursor = 0;
        for (int i = 0; i < entityCount; i++) {
            List<Voxel> bodyVoxels = entities.get(i).getBody();
            int voxelCount = bodyVoxels.size();
            entityBvhOffsets[i] = bvhMinsList.size();

            if (voxelCount > 0) {
                int[] localIndices = new int[voxelCount];
                for (int j = 0; j < voxelCount; j++)
                    localIndices[j] = j;
                buildBVH(localIndices, 0, voxelCount, bvPositions, bvSizes, voxelCursor, bvhMinsList, bvhMaxsList,
                        bvhLinksList);
            }

            voxelCursor += voxelCount;
        }

        int totalBvhNodes = bvhMinsList.size();
        if (bvhMins == null || bvhMins.length < totalBvhNodes * 3) {
            bvhMins = new float[totalBvhNodes * 3];
            bvhMaxs = new float[totalBvhNodes * 3];
            bvhLinks = new int[totalBvhNodes * 3];
        }

        for (int i = 0; i < totalBvhNodes; i++) {
            float[] mn = bvhMinsList.get(i), mx = bvhMaxsList.get(i);
            int[] lk = bvhLinksList.get(i);
            bvhMins[i * 3] = mn[0];
            bvhMins[i * 3 + 1] = mn[1];
            bvhMins[i * 3 + 2] = mn[2];
            bvhMaxs[i * 3] = mx[0];
            bvhMaxs[i * 3 + 1] = mx[1];
            bvhMaxs[i * 3 + 2] = mx[2];
            bvhLinks[i * 3] = lk[0];
            bvhLinks[i * 3 + 1] = lk[1];
            bvhLinks[i * 3 + 2] = lk[2];
        }

        Time.staticTime();
        KorgiJNI.executeEntityKernal(
                pixels, width, height,
                camera.position.toFloatArray(),
                camera.getForward().toFloatArray(),
                camera.getRight().toFloatArray(),
                camera.getUp().toFloatArray(),
                tanFov,
                entityPositions,
                entityRotations,
                entityRadii,
                entityVoxelOffsets,
                entityBvhOffsets,
                entityCount,
                bvPositions,
                bvSizes,
                bvColors,
                bvOpacities,
                bvTextureIds,
                totalVoxels,
                bvhMins,
                bvhMaxs,
                bvhLinks,
                totalBvhNodes,
                textureAtlas.getAtlas(),
                path2);
        Time.staticTime("High Entity render: %f", 0.02f);
        entities.add(client);
    }

}
