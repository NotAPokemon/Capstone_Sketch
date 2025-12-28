package dev.korgi.jni;

public class KorgiJNI {

    static {
        System.loadLibrary("korgigl");
    }

    public static native void executeKernal(int[] pixels, int w, int h, float[] cam, float[] foward, float[] right,
            float[] up, float tanFov, int voxCount, int[] color, float[] opacity, int[] worldMin, int[] worldSize,
            int[] voxelGrid);

}
