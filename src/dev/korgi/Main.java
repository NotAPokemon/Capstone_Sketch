package dev.korgi;

import java.io.File;
import java.io.IOException;

import dev.korgi.game.ui.Screen;
import dev.korgi.json.JSONObject;
import dev.korgi.utils.InstallConstants;
import processing.core.PApplet;

public class Main {

    public static void main(String args[]) throws IOException, InterruptedException {
        File file = new File("./internal_config.json");
        if (!file.exists()) {
            if (InstallConstants.dev) {
                Installer.runInstall();
                System.out.println("installed rerun the command");
                return;
            }
            PApplet.runSketch(new String[] { "" }, InstallerGUI.getInstance());
            InstallerGUI.getInstance().windowResizable(true);
            return;
        } else if (!new File("./korgi_main.jar").exists()) {
            JSONObject obj = JSONObject.fromFile(file);
            String path = obj.getString("path");
            ProcessBuilder pb = new ProcessBuilder("java", "-jar", "./korgi_main.jar",
                    "-Djava.library.path=./", "--enable-native-access=ALL-UNNAMED");
            pb = pb.directory(new File(path));
            pb.inheritIO();
            Process p = pb.start();
            p.waitFor();
            return;
        }
        PApplet.runSketch(new String[] { "" }, Screen.getInstance());
        Screen.getInstance().windowResizable(true);
    }

}
