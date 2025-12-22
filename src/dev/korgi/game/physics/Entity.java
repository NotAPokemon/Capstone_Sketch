package dev.korgi.game.physics;

import java.util.List;

import dev.korgi.game.rendering.Voxel;
import dev.korgi.math.Vector3;
import dev.korgi.networking.NetworkObject;

public abstract class Entity extends NetworkObject {

    protected Vector3 velocity;
    protected Vector3 position;
    protected Vector3 rotation;
    protected List<Voxel> body;

    public Entity() {
        this.position = new Vector3();
        this.rotation = new Vector3();
        this.velocity = new Vector3();
        this.body = createBody();
    }

    public abstract List<Voxel> createBody();

    public void onCollide(Entity other, Vector3 bodyPart, Vector3 otherBodyPart, Vector3 penetration) {
    }

    public void onCollide(Voxel other, Vector3 bodyPart, Vector3 penetration) {
    }

    public void addToWorld() {
        WorldEngine.getEntities().add(this);
    }

}
