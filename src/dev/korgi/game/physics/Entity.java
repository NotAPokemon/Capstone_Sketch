package dev.korgi.game.physics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import dev.korgi.game.rendering.Voxel;
import dev.korgi.json.JSONObject;
import dev.korgi.math.Vector3;
import dev.korgi.math.VectorConstants;
import dev.korgi.networking.NetworkObject;

public abstract class Entity extends NetworkObject {

    protected Vector3 velocity;
    protected Vector3 position;
    protected Vector3 rotation;
    protected List<Voxel> body;

    private static Map<String, List<Voxel>> cache = new HashMap<>();

    public Entity() {
        this.position = new Vector3();
        this.rotation = new Vector3();
        this.velocity = new Vector3();
        this.body = createBody();
    }

    protected abstract List<Voxel> createBody();

    protected static List<Voxel> fromModel(String name) {

        if (cache.get(name) != null) {
            return cache.get(name);
        }

        JSONObject object = JSONObject.fromResource(name);
        int amt = object.getInt("amt");
        List<Voxel> voxels = new ArrayList<>();
        for (int i = 0; i < amt; i++) {
            Voxel v = new Voxel();
            object.getJSONObject("voxel%d".formatted(i)).fillObject(v);
            voxels.add(v);
        }

        cache.put(name, voxels);

        return voxels;
    }

    String c() {
        JSONObject object = new JSONObject();
        object.set("amt", body.size());
        for (int i = 0; i < body.size(); i++) {
            object.set("voxel%d".formatted(i), new JSONObject(body.get(i)));
        }
        return object.toString();
    }

    public void onCollide(Entity other, Vector3 bodyPart, Vector3 otherBodyPart, Vector3 penetration) {
    }

    public void onCollide(Voxel other, Vector3 bodyPart, Vector3 penetration) {
    }

    public void addToWorld() {
        WorldEngine.addEntity(this);
    }

    public Vector3 getPosition() {
        return position;
    }

    public Vector3 getVelocity() {
        return velocity;
    }

    public List<Voxel> getBody() {
        return body;
    }

    protected void withHit(Consumer<Hit> action, double maxDist) {
        Vector3 dir = new Vector3(
                Math.sin(rotation.y) * Math.cos(rotation.x),
                Math.sin(rotation.x),
                Math.cos(rotation.y) * Math.cos(rotation.x)).normalizeHere();
        Vector3 origin = position.add(VectorConstants.HALF);
        Hit hit = WorldEngine.trace(origin, dir, maxDist);
        if (hit != null) {
            action.accept(hit);
        }
    }

}
