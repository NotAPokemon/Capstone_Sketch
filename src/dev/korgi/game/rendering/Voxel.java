package dev.korgi.game.rendering;

import dev.korgi.math.Vector3;
import dev.korgi.math.Vector4;

public class Voxel {
    public Vector3 position;
    private Material mat = new Material();

    public Voxel() {
        this.position = new Vector3();
    }

    public Voxel(Vector3 pos) {
        this.position = pos;
    }

    public Voxel(Vector4 color) {
        this();
        this.mat.setColor(color);
    }

    public Voxel(Vector3 pos, Vector4 color) {
        this.position = pos;
        this.mat.setColor(color);
    }

    public Voxel(double x, double y, double z) {
        this.position = new Vector3(x, y, z);
    }

    public Voxel(double x, double y, double z, Vector4 color) {
        this.position = new Vector3(x, y, z);
        this.mat.setColor(color);
    }

    public Voxel(Vector3 pos, double r, double g, double b, double a) {
        this.position = pos;
        this.mat.setColor(new Vector4(r, g, b, a));

    }

    public Voxel(double x, double y, double z, double r, double g, double b, double a) {
        this.position = new Vector3(x, y, z);
        this.mat.setColor(new Vector4(r, g, b, a));
    }

    public void setMaterial(Material m) {
        this.mat = m;
    }

    public Material getMaterial() {
        return mat;
    }
}
