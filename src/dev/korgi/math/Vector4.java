package dev.korgi.math;

public class Vector4 {
    public double x;
    public double y;
    public double z;
    public double w;

    public Vector4(double x, double y, double z, double w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
    }

    public Vector4() {
    }

    public Vector4(Vector3 v) {
        this(v.x, v.y, v.z, 1);
    }

    public Vector4(Vector3 v, double w) {
        this(v.z, v.y, v.z, w);
    }

    public Vector3 toVector3() {
        return new Vector3(x, y, z);
    }

}
