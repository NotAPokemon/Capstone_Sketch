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

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Vector4 v) {
            return v.x == x && v.y == y && v.z == z;
        } else if (obj instanceof Vector3 v) {
            return v.x == x && v.y == y && v.z == z;
        } else if (obj instanceof Vector2 v) {
            return v.x == x && v.y == y;
        }
        return false;
    }

    public Vector3 copy() {
        return new Vector3(x, y, z);
    }

    public Vector3 floor() {
        return new Vector3(Math.floor(x), Math.floor(y), Math.floor(z));
    }

    public Vector3 floorMe() {
        x = Math.floor(x);
        y = Math.floor(y);
        z = Math.floor(z);
        return this;
    }

    public Vector3 abs() {
        return new Vector3(Math.abs(x), Math.abs(y), Math.abs(z));
    }

    public Vector3 absValue() {
        x = Math.abs(x);
        y = Math.abs(y);
        z = Math.abs(z);
        return this;
    }

    public Vector3 copyFrom(Vector3 other) {
        this.x = other.x;
        this.y = other.y;
        this.z = other.z;
        return this;
    }

    public Vector3 copyFrom(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    public double min() {
        return Math.min(Math.min(x, y), z);
    }

    public double max() {
        return Math.max(Math.max(x, y), z);
    }

    public double compare(double value, Vector4 results) {
        if (x == value) {
            return results.x;
        } else if (y == value) {
            return results.y;
        } else if (z == value) {
            return results.z;
        } else {
            return results.w;
        }
    }

    public static Vector3 min(Vector3 a, Vector3 b) {
        return new Vector3(Math.min(a.x, b.x), Math.min(a.y, b.y), Math.min(a.z, b.z));
    }

    public static Vector3 max(Vector3 a, Vector3 b) {
        return new Vector3(Math.max(a.x, b.x), Math.max(a.y, b.y), Math.max(a.z, b.z));
    }

    public static Vector3 random() {
        return new Vector3(Math.random(), Math.random(), Math.random());
    }

    @Override
    public String toString() {
        return "(%.3f, %.3f, %.3f)".formatted(x, y, z);
    }

}
