package dev.korgi.game.physics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.korgi.game.rendering.Voxel;
import dev.korgi.json.JSONFillOverride;
import dev.korgi.json.JSONObject;
import dev.korgi.math.Vector3;

public class WorldStorage {

    @JSONFillOverride("overrideVoxelFill")
    public HashMap<Long, Voxel> voxels = new HashMap<>();

    public List<Entity> entities = new ArrayList<>();
    public boolean updated = true;

    public void add(Voxel v) {
        voxels.put(voxelKey(v.position), v);
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
                long key = Long.parseLong(entry.getKey().toString());

                Object value = entry.getValue();
                if (!(value instanceof JSONObject json))
                    continue;

                Voxel v = new Voxel();
                json.fillObject(v);

                voxels.put(key, v);
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
