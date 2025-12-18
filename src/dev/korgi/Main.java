package dev.korgi;

import java.io.IOException;

import dev.korgi.gui.Screen;
import processing.core.PApplet;

public class Main {

    public static void main(String args[]) throws IOException {

        PApplet.runSketch(new String[] { "" }, Screen.getInstance());
        Screen.getInstance().windowResizable(true);
    }

}
