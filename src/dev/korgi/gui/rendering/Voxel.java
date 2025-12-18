package dev.korgi.gui.rendering;

import dev.korgi.math.Vector3;
import dev.korgi.math.Vector4;

public class Voxel {
    public Vector3 position;
    public Vector4 color;

    public Voxel() {

    }

    public Voxel(Vector3 pos, Vector4 color) {
        this.position = pos;
        this.color = color;
    }

    public Voxel(double x, double y, double z, Vector4 color) {
        this.position = new Vector3(x, y, z);
        this.color = color;
    }

    public Voxel(Vector3 pos, double r, double g, double b, double a) {
        this.position = pos;
        this.color = new Vector4(r, g, b, a);

    }

    public Voxel(double x, double y, double z, double r, double g, double b, double a) {
        this.position = new Vector3(x, y, z);
        this.color = new Vector4(r, g, b, a);
    }
}
