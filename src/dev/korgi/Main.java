package dev.korgi;

import java.io.IOException;

import dev.korgi.game.rendering.Screen;
import dev.korgi.utils.InstallConstants;
import processing.core.PApplet;

public class Main {

    @SuppressWarnings("unused")
    public static void main(String args[]) throws IOException {
        if (!InstallConstants.dev && !InstallConstants.installed) {
            PApplet.runSketch(new String[] { "" }, InstallerGUI.getInstance());
            InstallerGUI.getInstance().windowResizable(true);
            // install on the user's computer by running an installer

            return;
        }
        PApplet.runSketch(new String[] { "" }, Screen.getInstance());
        Screen.getInstance().windowResizable(true);
    }

}
