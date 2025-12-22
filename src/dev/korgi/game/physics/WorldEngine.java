package dev.korgi.game.physics;

import java.util.List;

import dev.korgi.game.rendering.Voxel;
import dev.korgi.json.JSONObject;
import dev.korgi.math.Vector3;
import dev.korgi.math.Vector4;
import dev.korgi.networking.NetworkStream;
import dev.korgi.networking.Packet;

public class WorldEngine {

    private static final WorldStorage world = new WorldStorage();

    public static void init() {
        Vector4 color = new Vector4(Math.random(), Math.random(), Math.random(), 1);
        double opacity = Math.random();
        for (int x = 0; x < 10; x++) {
            for (int z = 0; z < 10; z++) {
                Voxel v = new Voxel(x, -5, z, color);
                v.getMaterial().setOpacity(opacity);
                world.voxels.add(v);
            }
        }
    }

    public static void updateClient() {
        Packet in = NetworkStream.getPacket("world", true);
        if (in != null) {
            in.getData().fillObject(world);
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
                                    bodyWorldPos.add(1, 1, 1),
                                    worldVoxel.position,
                                    worldVoxel.position.add(1, 1, 1));

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

        JSONObject outData = new JSONObject(world);
        Packet out = new Packet("world", NetworkStream.CLIENT, NetworkStream.BROADCAST, outData);
        NetworkStream.sendPacket(out);
    }

    public static List<Entity> getEntities() {
        return world.entities;
    }

    public static List<Voxel> getVoxels() {
        return world.voxels;
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
                            vaWorld.add(1, 1, 1),
                            vbWorld,
                            vbWorld.add(1, 1, 1));
                    a.onCollide(b, vaWorld, vbWorld, penetration);
                }
            }
        }
    }

    private static boolean voxelIntersects(Vector3 a, Vector3 b) {
        return a.x < b.x + 1 &&
                a.x + 1 > b.x &&
                a.y < b.y + 1 &&
                a.y + 1 > b.y &&
                a.z < b.z + 1 &&
                a.z + 1 > b.z;
    }

    private static Vector3 getPenetration(Vector3 aMin, Vector3 aMax, Vector3 bMin, Vector3 bMax) {
        double dx = Math.min(aMax.x, bMax.x) - Math.max(aMin.x, bMin.x);
        double dy = Math.min(aMax.y, bMax.y) - Math.max(aMin.y, bMin.y);
        double dz = Math.min(aMax.z, bMax.z) - Math.max(aMin.z, bMin.z);

        if (dx <= 0 || dy <= 0 || dz <= 0)
            return null;
        return new Vector3(dx, dy, dz);
    }

    private static void resolveRigidCollision(Entity entity, Voxel bodyVoxel, Voxel worldVoxel) {
        Vector3 bodyWorld = bodyVoxel.position.add(entity.position);
        Vector3 bodyMin = bodyWorld;
        Vector3 bodyMax = bodyWorld.add(1, 1, 1);

        Vector3 worldMin = worldVoxel.position;
        Vector3 worldMax = worldVoxel.position.add(1, 1, 1);

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
}
