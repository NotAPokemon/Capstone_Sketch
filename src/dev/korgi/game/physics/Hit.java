package dev.korgi.game.physics;

import dev.korgi.game.rendering.Voxel;
import dev.korgi.math.Vector3;

public class Hit {
    public Voxel hit;
    public int face;
    public double dist;

    public Vector3 getFaceOffset() {
        return new Vector3(
                (face == 0 ? 1 : face == 1 ? -1 : 0),
                (face == 2 ? 1 : face == 3 ? -1 : 0),
                (face == 4 ? 1 : face == 5 ? -1 : 0));
    }

    public Vector3 getFaceWithOffset() {
        return hit.position.add(getFaceOffset());
    }

    public Vector3 getVoxelPos() {
        return hit.position;
    }
}
