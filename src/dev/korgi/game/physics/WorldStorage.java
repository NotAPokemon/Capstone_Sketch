package dev.korgi.game.physics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.korgi.game.entites.Entity;
import dev.korgi.game.rendering.Voxel;
import dev.korgi.json.JSONFillOverride;
import dev.korgi.json.JSONIgnore;
import dev.korgi.json.JSONObject;
import dev.korgi.math.Vector3;

public class WorldStorage {

    @JSONFillOverride("overrideVoxelFill")
    public HashMap<Long, Voxel> voxels = new LinkedHashMap<>();

    @JSONIgnore
    public List<Entity> entities = new ArrayList<>();

    public boolean updated = true;
    @JSONIgnore
    public List<Voxel> updates = new ArrayList<>();
    @JSONIgnore
    public List<Vector3> removes = new ArrayList<>();
    private final Vector3 minBounds = new Vector3();
    private final Vector3 maxBounds = new Vector3();

    public void add(Voxel v) {
        voxels.put(voxelKey(v.position), v);
        if (v.position.x > maxBounds.x) {
            maxBounds.copyFrom(v.position.x, maxBounds.y, maxBounds.z);
        }
        if (v.position.y > maxBounds.y) {
            maxBounds.copyFrom(maxBounds.x, v.position.y, maxBounds.z);
        }
        if (v.position.z > maxBounds.z) {
            maxBounds.copyFrom(maxBounds.x, maxBounds.y, v.position.z);
        }
        if (v.position.x < minBounds.x) {
            minBounds.copyFrom(v.position.x, minBounds.y, minBounds.z);
        }
        if (v.position.y < minBounds.y) {
            minBounds.copyFrom(minBounds.x, v.position.y, minBounds.z);
        }
        if (v.position.z < minBounds.z) {
            minBounds.copyFrom(minBounds.x, minBounds.y, v.position.z);
        }

    }

    public boolean inBounds(Vector3 pos) {
        return pos.x < maxBounds.x && pos.y < maxBounds.y && pos.z < maxBounds.z && pos.x > minBounds.x
                && pos.y > minBounds.y && pos.z > minBounds.z;
    }

    public List<Voxel> getFlat() {
        return new ArrayList<>(voxels.values());
    }

    public Voxel at(Vector3 pos) {
        return voxels.get(voxelKey(pos));
    }

    public void overrideVoxelFill(Object serialized) {
        voxels.clear();

        if (!(serialized instanceof JSONObject map)) {
            return;
        }

        for (Map.Entry<?, ?> entry : map.getValues().entrySet()) {
            try {
                Object value = entry.getValue();

                if (!(value instanceof JSONObject json)) {
                    System.out.println(value);
                    continue;
                }

                Voxel v = new Voxel();
                json.fillObject(v);

                add(v);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    public static long voxelKey(int x, int y, int z) {
        return (((long) (x & 0xFFFFF)) << 40)
                | (((long) (y & 0xFFFFF)) << 20)
                | ((long) (z & 0xFFFFF));
    }

    public static long voxelKey(Vector3 pos) {
        return (((long) (((int) pos.x) & 0xFFFFF)) << 40)
                | (((long) (((int) pos.y) & 0xFFFFF)) << 20)
                | ((long) (((int) pos.z) & 0xFFFFF));
    }

}
