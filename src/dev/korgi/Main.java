package dev.korgi;

import java.io.File;
import java.io.IOException;

import dev.korgi.game.rendering.Screen;
import processing.core.PApplet;

public class Main {

    public static void main(String args[]) throws IOException {
        File file = new File("./internal_config.json");
        if (!file.exists()) {
            PApplet.runSketch(new String[] { "" }, InstallerGUI.getInstance());
            InstallerGUI.getInstance().windowResizable(true);
            // install on the user's computer by running an installer

            return;
        }
        PApplet.runSketch(new String[] { "" }, Screen.getInstance());
        Screen.getInstance().windowResizable(true);
    }

}
