package dev.korgi.jni;

public class KorgiJNI {

    static {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("mac")) {
            System.loadLibrary("korgikompute-mac");
        } else if (os.contains("win")) {
            System.loadLibrary("korgikompute-win");
        } else if (os.contains("nux")) {
            System.loadLibrary("korgikompute-linux");
        } else {
            throw new UnsatisfiedLinkError("Unsupported OS I suggest you switch to mac to windows");
        }
    }

    public static native void executeKernal(int[] pixels, int w, int h, float[] cam, float[] foward, float[] right,
            float[] up, float tanFov, int voxCount, int[] color, float[] opacity, int[] worldMin, int[] worldSize,
            int[] voxelGrid, String path, int[] textureLocation, int[] textureAtlas, int[] chunkGrid, int[] chunckSize);

    public static native void executeEntityKernal(int[] pixels, int width, int height, float[] floatArray,
            float[] floatArray2,
            float[] floatArray3, float[] floatArray4, float tanFov, float[] entityPositions, float[] entityRotations,
            float[] entityRadii,
            int[] entityVoxelOffsets, int entityCount, float[] bvPositions, float[] bvSizes, int[] bvColors,
            float[] bvOpacities, int[] bvTextureIds, int totalVoxels, int[] textureAtlas, String path);

}
