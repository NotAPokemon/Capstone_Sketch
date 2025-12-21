package dev.korgi.game.physics;

import java.util.ArrayList;
import java.util.List;

import dev.korgi.game.rendering.Voxel;
import dev.korgi.game.rendering.WorldSpace;
import dev.korgi.math.Vector3;

public class WorldEngine {
    private static List<Voxel> voxels = new ArrayList<>();
    private static List<Entity> entities = new ArrayList<>();

    public static void init() {
        Voxel v1 = new Voxel(0, 0, 20, 1, 0, 0, 1);
        voxels.add(v1);

        Voxel v2 = new Voxel(5, 5, 30, 0, 1, 0, 1);
        voxels.add(v2);

        Voxel v3 = new Voxel(0, 5, 6, 0, 1, 0, 1);
        voxels.add(v3);

        Voxel v4 = new Voxel(0, 5, 7, 0, 1, 0, 1);
        v4.getMaterial().setOpacity(0.5);
        voxels.add(v4);
    }

    public static void execute() {
        for (Entity entity : entities) {
            for (Voxel bodyVoxel : entity.body) {
                Vector3 voxPos = bodyVoxel.position.add(entity.position);
                for (Voxel worldVoxel : voxels) {
                    if (voxelIntersects(voxPos, worldVoxel.position)) {
                        if (worldVoxel.getMaterial().isRigid() && bodyVoxel.getMaterial().isRigid()) {
                            resolveRigidCollision(entity, voxPos, worldVoxel.position);
                        }
                        entity.onCollide(worldVoxel);
                    }
                }
            }
        }

        for (int i = 0; i < entities.size(); i++) {
            Entity a = entities.get(i);
            for (int j = i + 1; j < entities.size(); j++) {
                Entity b = entities.get(j);
                if (entitiesIntersect(a, b)) {
                    a.onCollide(b);
                }
            }
        }
        WorldSpace.execute();
    }

    public static List<Entity> getEntities() {
        return entities;
    }

    public static List<Voxel> getVoxels() {
        return voxels;
    }

    private static boolean entitiesIntersect(Entity a, Entity b) {
        for (Voxel va : a.body) {
            Vector3 voxPos = va.position.add(a.position);
            for (Voxel vb : b.body) {
                if (voxelIntersects(voxPos, vb.position.add(b.position))) {
                    if (va.getMaterial().isRigid() && vb.getMaterial().isRigid()) {
                        resolveRigidCollision(a, voxPos, vb.position.add(b.position));
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean voxelIntersects(Vector3 a, Vector3 b) {
        return a.x < b.x + 1 &&
                a.x + 1 > b.x &&
                a.y < b.y + 1 &&
                a.y + 1 > b.y &&
                a.z < b.z + 1 &&
                a.z + 1 > b.z;
    }

    private static Vector3 getPenetration(Vector3 a, Vector3 b) {
        double dx = Math.min(a.x + 1, b.x + 1) - Math.max(a.x, b.x);
        double dy = Math.min(a.y + 1, b.y + 1) - Math.max(a.y, b.y);
        double dz = Math.min(a.z + 1, b.z + 1) - Math.max(a.z, b.z);

        if (dx <= 0 || dy <= 0 || dz <= 0)
            return null;

        return new Vector3(dx, dy, dz);
    }

    private static void resolveRigidCollision(Entity entity, Vector3 bodyWorld, Vector3 worldVoxel) {
        Vector3 pen = getPenetration(bodyWorld, worldVoxel);
        if (pen == null)
            return;

        if (pen.x <= pen.y && pen.x <= pen.z) {
            entity.position.x += bodyWorld.x < worldVoxel.x ? -pen.x : pen.x;
        } else if (pen.y <= pen.x && pen.y <= pen.z) {
            entity.position.y += bodyWorld.y < worldVoxel.y ? -pen.y : pen.y;
        } else {
            entity.position.z += bodyWorld.z < worldVoxel.z ? -pen.z : pen.z;
        }
    }

}
