package dev.korgi.game.rendering;

import dev.korgi.math.Vector3;

public class Camera {
    public Vector3 position = new Vector3();
    public Vector3 rotation = new Vector3();
    public float fov = 50;

    public Vector3 getForward() {
        return new Vector3(
                Math.sin(rotation.y) * Math.cos(rotation.x),
                Math.sin(rotation.x),
                Math.cos(rotation.y) * Math.cos(rotation.x)).normalizeHere();
    }

    public Vector3 getRight() {
        return new Vector3(
                Math.sin(rotation.y - Math.PI / 2.0),
                0,
                Math.cos(rotation.y - Math.PI / 2.0)).normalizeHere();
    }

    public Vector3 getUp() {
        return getRight().cross(getForward()).normalizeHere();
    }

}
