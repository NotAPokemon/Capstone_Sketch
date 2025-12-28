package dev.korgi.jni;

public class KorgiJNI {

    static {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("mac")) {
            System.loadLibrary("korgikompute-mac");
        } else if (os.contains("win")) {
            System.load(
                    "C:\\Users\\every\\Documents\\Github\\Capstone_Sketch\\natives\\win\\build\\korgikompute-win.dll");

        } else if (os.contains("nux")) {
            System.loadLibrary("korgikompute-linux");
        } else {
            throw new UnsatisfiedLinkError("Unsupported OS I suggest you switch to mac to windows");
        }
    }

    public static native void executeKernal(int[] pixels, int w, int h, float[] cam, float[] foward, float[] right,
            float[] up, float tanFov, int voxCount, int[] color, float[] opacity, int[] worldMin, int[] worldSize,
            int[] voxelGrid);

}
