package dev.korgi;

import dev.korgi.gui.Screen;
import processing.core.PApplet;

public class Main {

    public static void main(String args[]) {
        PApplet.runSketch(new String[] { "" }, Screen.getInstance());
        Screen.getInstance().windowResizable(true);
    }

}
