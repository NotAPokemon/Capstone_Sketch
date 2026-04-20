package dev.korgi.game.entites;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

import dev.korgi.game.physics.Hit;
import dev.korgi.game.physics.WorldEngine;
import dev.korgi.game.rendering.Voxel;
import dev.korgi.json.JSONIgnore;
import dev.korgi.json.JSONObject;
import dev.korgi.math.Vector3;
import dev.korgi.math.VectorConstants;
import dev.korgi.networking.NetworkObject;
import dev.korgi.utils.ClientSide;
import dev.korgi.utils.ServerSide;
import dev.korgi.utils.VoxTranslator;

public abstract class Entity extends NetworkObject {

    @ServerSide
    protected Vector3 velocity;
    @ServerSide
    protected Vector3 position;
    protected Vector3 rotation;
    @JSONIgnore
    protected List<Voxel> body;
    @JSONIgnore
    protected List<Voxel> hitbox;
    @JSONIgnore
    protected Vector3 displayAxis = VectorConstants.DOWN;

    @JSONIgnore
    private static Map<Class<? extends Entity>, List<Voxel>> cache = new HashMap<>();
    @JSONIgnore
    private static Map<String, Supplier<? extends Entity>> constructors = new HashMap<>();

    @SuppressWarnings("unused")
    private String name = getClass().getSimpleName();

    @ServerSide
    protected boolean gravityEnabled = true;
    @ServerSide
    protected boolean onGround = false;

    public Entity() {
        this.position = new Vector3();
        this.rotation = new Vector3();
        this.velocity = new Vector3();
        this.body = cache.get(this.getClass());
        if (body == null) {
            body = createBody();
            cache.put(this.getClass(), VoxTranslator.copyModel(body));
        } else {
            body = VoxTranslator.copyModel(body);
        }
        this.hitbox = createHitbox();
        internal_id = UUID.randomUUID().toString();
    }

    public Entity(JSONObject prebuildparams) {
        prebuld(prebuildparams);
        this.position = new Vector3();
        this.rotation = new Vector3();
        this.velocity = new Vector3();
        this.body = cache.get(this.getClass());
        if (body == null) {
            body = createBody();
            cache.put(this.getClass(), VoxTranslator.copyModel(body));
        } else {
            body = VoxTranslator.copyModel(body);
        }
        this.hitbox = createHitbox();
        internal_id = UUID.randomUUID().toString();
    }

    protected void prebuld(JSONObject params) {
    }

    public static void register(String className, Supplier<? extends Entity> constructor) {
        if (constructors.get(className) == null) {
            constructors.put(className, constructor);
        }
    }

    public static Entity construct(String name) {
        Supplier<? extends Entity> c = constructors.get(name);
        if (c != null) {
            return c.get();
        }
        return null;
    }

    protected abstract List<Voxel> createBody();

    protected abstract List<Voxel> createHitbox();

    protected void scale(float scalar) {
        if (body != null) {
            for (Voxel voxel : body) {
                voxel.getMaterial().setSize(scalar);
                voxel.position.multiplyBy(scalar);
            }
        } else {
            System.out.println("This method cannot be called at this time");
        }
    }

    protected void scaleHitbox(float scalar) {
        if (hitbox != null) {
            for (Voxel voxel : hitbox) {
                voxel.getMaterial().setSize(scalar);
                voxel.position.multiplyBy(scalar);
            }
        } else {
            System.out.println("This method cannot be called at this time");
        }
    }

    protected void setOpacity(float value) {
        if (value > 1) {
            value = 1;
        }
        if (value < 0) {
            value = 0;
        }
        if (body != null) {
            for (Voxel voxel : body) {
                voxel.getMaterial().setOpacity(value);
            }
        } else {
            System.out.println("This method cannot be called at this time");
        }
    }

    protected void modelOffset(Vector3 offset) {
        if (body != null) {
            for (Voxel voxel : body) {
                voxel.position.addTo(offset);
            }
        } else {
            System.out.println("This method cannot be called at this time");
        }
    }

    protected void hitboxOffset(Vector3 offset) {
        if (hitbox != null) {
            for (Voxel voxel : hitbox) {
                voxel.position.addTo(offset);
            }
        } else {
            System.out.println("This method cannot be called at this time");
        }
    }

    @ServerSide
    public void onCollide(Entity other, Vector3 bodyPart, Vector3 otherBodyPart, Vector3 penetration) {
    }

    @ServerSide
    public void onCollide(Voxel other, Vector3 bodyVoxelWorldPos, Vector3 penetration) {
        if (penetration != null && penetration.y > 0 && other.position.y < position.y) {
            onGround = true;
            velocity.y = 0;
        }
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

    public List<Voxel> getHitbox() {
        return hitbox;
    }

    public Vector3 getForward() {
        return new Vector3(
                Math.sin(rotation.y) * Math.cos(rotation.x),
                Math.sin(rotation.x),
                Math.cos(rotation.y) * Math.cos(rotation.x)).normalizeHere();
    }

    protected void withHit(Consumer<Hit> action, double maxDist) {
        Vector3 dir = getForward();
        Vector3 origin = position.add(VectorConstants.HALF);
        Hit hit = WorldEngine.trace(origin, dir, maxDist);
        if (hit != null) {
            action.accept(hit);
        }
    }

    @ServerSide
    protected void handlePhysics(double dt) {
        if (gravityEnabled && !onGround)
            velocity.addTo(VectorConstants.DOWN.multiply(WorldEngine.g * dt));

        position.addTo(velocity.multiply(dt));
        WorldEngine.validatePosition(this);

        if (position.y < -70 && WorldEngine.getWorld().voxels.size() > 0) {
            position.copyFrom(
                    WorldEngine.getWorld().voxels.values().stream().toList().get(0).position.add(VectorConstants.HALF)
                            .addTo(VectorConstants.UP.multiply(3)));
            velocity.copyFrom(VectorConstants.ZERO);
            rotation.copyFrom(VectorConstants.ZERO);
        }
    }

    @Override
    @ClientSide
    protected void client(double dt) {
    }

    @Override
    @ServerSide
    protected void server(double dt) {
        checkGravity();
        handlePhysics(dt);
    }

    public Vector3 getRotation() {
        return rotation;
    }

    @ClientSide
    public float[] getRotationMatrix() {
        Vector3 rot = rotation.multiply(displayAxis);
        double cx = Math.cos(rot.x), sx = Math.sin(rot.x);
        double cy = Math.cos(rot.y), sy = Math.sin(rot.y);
        double cz = Math.cos(rot.z), sz = Math.sin(rot.z);

        return new float[] {

                (float) (cy * cz + sy * sx * sz),
                (float) (cx * sz),
                (float) (-sy * cz + cy * sx * sz),

                (float) (-cy * sz + sy * sx * cz),
                (float) (cx * cz),
                (float) (sy * sz + cy * sx * cz),

                (float) (sy * cx),
                (float) (-sx),
                (float) (cy * cx)
        };
    }

    @ServerSide
    public void checkGravity() {
        onGround = WorldEngine.voxelAt(position.add(VectorConstants.DOWN)) != null;
    }

}
