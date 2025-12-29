package dev.korgi.math;

public class Vector3 {
    public double x;
    public double y;
    public double z;

    public Vector3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vector3() {
        this.x = 0;
        this.y = 0;
        this.z = 0;
    }

    public double length() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    public Vector3 subtract(Vector3 other) {
        return new Vector3(x - other.x, y - other.y, z - other.z);
    }

    public Vector3 subtract(double x, double y, double z) {
        return new Vector3(this.x - x, this.y - y, this.z - z);
    }

    public Vector3 subtractFrom(Vector3 other) {
        this.x -= other.x;
        this.y -= other.y;
        this.z -= other.z;
        return this;
    }

    public Vector3 subtractFrom(double x, double y, double z) {
        this.x -= x;
        this.y -= y;
        this.z -= z;
        return this;
    }

    public Vector3 add(Vector3 other) {
        return new Vector3(x + other.x, y + other.y, z + other.z);
    }

    public Vector3 add(double x, double y, double z) {
        return new Vector3(this.x + x, this.y + y, this.z + z);
    }

    public Vector3 addTo(Vector3 other) {
        this.x += other.x;
        this.y += other.y;
        this.z += other.z;
        return this;
    }

    public Vector3 addTo(double x, double y, double z) {
        this.x += x;
        this.y += y;
        this.z += z;
        return this;
    }

    public Vector3 multiply(double factor) {
        return new Vector3(x * factor, y * factor, z * factor);
    }

    public Vector3 multiplyBy(double factor) {
        this.x *= factor;
        this.y *= factor;
        this.z *= factor;
        return this;
    }

    public Vector3 multiply(Vector3 other) {
        return new Vector3(x * other.x, y * other.y, z * other.z);
    }

    public Vector3 multiply(double x, double y, double z) {
        return new Vector3(this.x * x, this.y * y, this.z * z);
    }

    public Vector3 multiplyBy(Vector3 other) {
        this.x *= other.x;
        this.y *= other.y;
        this.z *= other.z;
        return this;
    }

    public Vector3 multiplyBy(double x, double y, double z) {
        this.x *= x;
        this.y *= y;
        this.z *= z;
        return this;
    }

    public double dot(Vector3 other) {
        return x * other.x + y * other.y + z * other.z;
    }

    public double dot(double x, double y, double z) {
        return this.x * x + this.y * y + this.z * z;
    }

    public Vector3 cross(Vector3 other) {
        return new Vector3(
                y * other.z - z * other.y,
                z * other.x - x * other.z,
                x * other.y - y * other.x);
    }

    public Vector3 cross(double x, double y, double z) {
        return new Vector3(
                this.y * z - this.z * y,
                this.z * x - this.x * z,
                this.x * y - this.y * x);
    }

    public Vector3 crossOf(Vector3 other) {
        double x = this.y * other.z - this.z * other.y;
        double y = this.z * other.x - this.x * other.z;
        double z = this.x * other.y - this.y * other.x;
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    public Vector3 crossOf(double x, double y, double z) {
        this.x = this.y * z - this.z * y;
        this.y = this.z * x - this.x * z;
        this.z = this.x * y - this.y * x;
        return this;
    }

    public Vector3 normalize() {
        double len = length();
        if (len == 0)
            return new Vector3(0, 0, 0);
        return new Vector3(x / len, y / len, z / len);
    }

    public Vector3 normalizeHere() {
        double len = length();
        if (len == 0) {
            this.x = 0;
            this.y = 0;
            this.z = 0;
        }
        this.x /= len;
        this.y /= len;
        this.z /= len;
        return this;
    }

    public int[] toIntArray() {
        return new int[] {
                (int) x,
                (int) y,
                (int) z
        };
    }

    public float[] toFloatArray() {
        return new float[] {
                (float) x,
                (float) y,
                (float) z
        };
    }

    public double[] toArray() {
        return new double[] {
                x,
                y,
                z
        };
    }

    public double multiplyComp() {
        return x * y * z;
    }

}
