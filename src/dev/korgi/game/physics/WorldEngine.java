package dev.korgi.game.physics;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.korgi.game.Game;
import dev.korgi.game.rendering.Voxel;
import dev.korgi.json.JSONObject;
import dev.korgi.math.Vector3;
import dev.korgi.math.Vector4;
import dev.korgi.math.VectorConstants;
import dev.korgi.networking.NetworkStream;
import dev.korgi.networking.Packet;

public class WorldEngine {

    private static final WorldStorage world = new WorldStorage();
    public static final double g = 9.80665;

    public static void init() {
        Vector4 color = new Vector4(Vector3.random());
        double opacity = Math.random();
        for (int x = 0; x < 10; x++) {
            for (int z = 0; z < 10; z++) {
                Voxel v = new Voxel(x, -5, z, color);
                v.getMaterial().setOpacity(opacity);
                addVoxel(v);
            }
        }
    }

    public static void addEntity(Entity e) {
        world.entities.add(e);
        world.updated = true;
    }

    public static void addVoxel(Voxel v) {
        world.voxels.add(v);
        world.updated = true;
    }

    public static void addRandomVoxel(Vector3 pos) {
        world.voxels.add(new Voxel(pos, new Vector4(Vector3.random())));
        world.updated = true;
    }

    public static void updateClient() {
        Packet in = NetworkStream.getPacket("world", true);
        if (in != null) {
            in.getData().fillObject(world);
        }
    }

    public static boolean canPlaceVoxel(Vector3 pos) {
        for (Entity entity : world.entities) {
            for (Voxel body : entity.body) {
                if (voxelIntersects(entity.position.add(body.position), pos)) {
                    return false;
                }
            }

        }
        if (voxelAt(pos) != null) {
            return false;
        }

        return true;
    }

    public static void removeVoxel(Voxel v) {
        if (v != null) {
            world.voxels.remove(v);
            world.updated = true;
            Game.getPlayers().forEach((p) -> {
                p.checkGravity();
            });
        }
    }

    public static void execute() {
        List<Packet> in = NetworkStream.getAllPackets("world", false);
        for (Packet p : in) {
            JSONObject data = p.getData();
            if (!data.hasKey("voxel"))
                continue;
            Voxel v = new Voxel();
            data.getJSONObject("voxel").fillObject(v);
            world.voxels.add(v);
        }

        for (Entity entity : world.entities) {
            for (Voxel bodyVoxel : entity.body) {
                Vector3 bodyWorldPos = bodyVoxel.position.add(entity.position);

                for (Voxel worldVoxel : world.voxels) {
                    if (voxelIntersects(bodyWorldPos, worldVoxel.position)) {
                        if (worldVoxel.getMaterial().isRigid() && bodyVoxel.getMaterial().isRigid()) {
                            Vector3 penetration = getPenetration(
                                    bodyWorldPos,
                                    bodyWorldPos.add(VectorConstants.ONE),
                                    worldVoxel.position,
                                    worldVoxel.position.add(VectorConstants.ONE));

                            resolveRigidCollision(entity, bodyVoxel, worldVoxel);

                            entity.onCollide(worldVoxel, bodyWorldPos, penetration);
                        }
                    }
                }
            }
        }

        for (int i = 0; i < world.entities.size(); i++) {
            Entity a = world.entities.get(i);
            for (int j = i + 1; j < world.entities.size(); j++) {
                Entity b = world.entities.get(j);
                entitiesIntersect(a, b);
            }
        }

        if (world.updated) {
            world.updated = false;
            JSONObject outData = new JSONObject(world);
            Packet out = new Packet("world", NetworkStream.CLIENT, NetworkStream.BROADCAST, outData);
            NetworkStream.sendPacket(out);
        }
    }

    public static WorldStorage getWorld() {
        return world;
    }

    public static Voxel voxelAt(Vector3 pos) {
        Vector3 posInt = pos.floor();

        for (Voxel v : world.voxels) {
            Vector3 posInt2 = v.position.floor();

            if (posInt.equals(posInt2)) {
                return v;
            }
        }
        return null;
    }

    private static void entitiesIntersect(Entity a, Entity b) {
        for (Voxel va : a.body) {
            Vector3 vaWorld = va.position.add(a.position);
            for (Voxel vb : b.body) {
                Vector3 vbWorld = vb.position.add(b.position);
                if (voxelIntersects(vaWorld, vbWorld)) {
                    if (va.getMaterial().isRigid() && vb.getMaterial().isRigid()) {
                        resolveRigidCollision(a, va, vb);
                    }
                    Vector3 penetration = getPenetration(
                            vaWorld,
                            vaWorld.add(VectorConstants.ONE),
                            vbWorld,
                            vbWorld.add(VectorConstants.ONE));
                    a.onCollide(b, vaWorld, vbWorld, penetration);
                }
            }
        }
    }

    public static boolean voxelIntersects(Vector3 a, Vector3 b) {
        return a.x < b.x + 1 &&
                a.x + 1 > b.x &&
                a.y < b.y + 1 &&
                a.y + 1 > b.y &&
                a.z < b.z + 1 &&
                a.z + 1 > b.z;
    }

    private static Vector3 getPenetration(Vector3 aMin, Vector3 aMax, Vector3 bMin, Vector3 bMax) {
        Vector3 min = Vector3.min(aMax, bMax);
        Vector3 max = Vector3.max(aMin, bMin);
        Vector3 d = min.subtract(max);

        if (!d.equals(d.abs()) || d.equals(VectorConstants.ZERO))
            return null;
        return d;
    }

    private static void resolveRigidCollision(Entity entity, Voxel bodyVoxel, Voxel worldVoxel) {
        Vector3 bodyWorld = bodyVoxel.position.add(entity.position);
        Vector3 bodyMin = bodyWorld;
        Vector3 bodyMax = bodyWorld.add(VectorConstants.ONE);

        Vector3 worldMin = worldVoxel.position;
        Vector3 worldMax = worldVoxel.position.add(VectorConstants.ONE);

        Vector3 pen = getPenetration(bodyMin, bodyMax, worldMin, worldMax);
        if (pen == null)
            return;

        if (pen.x <= pen.y && pen.x <= pen.z) {
            entity.position.x += bodyWorld.x < worldVoxel.position.x ? -pen.x : pen.x;
        } else if (pen.y <= pen.x && pen.y <= pen.z) {
            entity.position.y += bodyWorld.y < worldVoxel.position.y ? -pen.y : pen.y;
            entity.velocity.y = 0;
        } else {
            entity.position.z += bodyWorld.z < worldVoxel.position.z ? -pen.z : pen.z;
        }
    }

    private static long voxelKey(int x, int y, int z) {
        return (((long) (x & 0xFFFFF)) << 40)
                | (((long) (y & 0xFFFFF)) << 20)
                | ((long) (z & 0xFFFFF));
    }

    public static Hit trace(Vector3 origin, Vector3 dir, double maxDist) {
        double stepSize = 0.05;
        double traveled = 0.0;

        Map<Long, Voxel> voxelMap = new HashMap<>();
        for (Voxel v : world.voxels) {
            voxelMap.put(voxelKey((int) v.position.x, (int) v.position.y, (int) v.position.z), v);
        }

        Vector3 pos = origin.copy();
        Vector3 prev = pos.copy();

        while (traveled <= maxDist) {
            Vector3 voxPos = pos.floor();

            Voxel v = voxelMap.get(voxelKey((int) voxPos.x, (int) voxPos.y, (int) voxPos.z));
            if (v != null) {
                Hit hit = new Hit();
                hit.hit = v;
                hit.dist = traveled;

                Vector3 dMin = prev.subtract(voxPos).absValue();
                Vector3 dMax = prev.subtract(voxPos.add(VectorConstants.ONE)).absValue();

                double min = Vector3.min(dMin, dMax).min();

                hit.face = (int) dMin.compare(min, new Vector4(1, 3, 5, hit.face));
                hit.face = (int) dMax.compare(min, new Vector4(0, 2, 4, hit.face));

                return hit;
            }

            prev.copyFrom(pos);
            pos.addTo(dir.multiply(stepSize));
            traveled += stepSize;
        }

        return null;
    }

}
