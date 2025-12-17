package dev.korgi.gui;

import dev.korgi.Game;
import processing.core.PApplet;

public class Screen extends PApplet {

    private static Screen mInstance = null;

    private Screen() {
    }

    public static Screen getInstance() {
        mInstance = mInstance == null ? new Screen() : mInstance;
        return mInstance;
    }

    @Override
    public void draw() {
        if (Game.isInitialized()) {
            Game.loop();
        }
    }

}
