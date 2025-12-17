package dev.korgi;

import java.io.IOException;
import java.util.Scanner;

import dev.korgi.gui.Screen;
import processing.core.PApplet;

public class Main {

    public static void main(String args[]) throws IOException {
        Scanner scanner = new Scanner(System.in);
        int clientServer = scanner.nextInt();
        scanner.close();
        Game.isClient = clientServer == 0;
        Game.init();
        PApplet.runSketch(new String[] { "" }, Screen.getInstance());
        Screen.getInstance().windowResizable(true);
    }

}
