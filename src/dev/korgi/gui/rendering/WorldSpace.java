package dev.korgi.gui.rendering;

import java.util.ArrayList;
import java.util.List;

import dev.korgi.gui.Screen;
import dev.korgi.math.Vector3;
import dev.korgi.math.Vector4;

public class WorldSpace {

    private static List<Voxel> objects = new ArrayList<>();
    public static Camera camera = new Camera();
    private static final double VOXEL_SIZE = 5; // size of the cube

    public static void init() {
        // example voxels
        Voxel v1 = new Voxel();
        v1.position = new Vector3(0, 0, 200);
        v1.color = new Vector4(1, 0, 0, 1);
        objects.add(v1);

        Voxel v2 = new Voxel();
        v2.position = new Vector3(50, 50, 300);
        v2.color = new Vector4(0, 1, 0, 1);
        objects.add(v2);

        camera.position = new Vector3(0, 0, -50);
        camera.rotation = new Vector3(0, 0, 0);
        camera.fov = 500;
    }

    public static void execute() {
        Screen screen = Screen.getInstance();
        screen.background(0, 0, 255);

    }

}
