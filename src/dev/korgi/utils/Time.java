package dev.korgi.utils;

import java.util.HashMap;
import java.util.Map;

public class Time {

    private static final Map<String, Double> lastTime = new HashMap<>();
    private static final Map<String, Double> cd = new HashMap<>();

    public static void createCooldown(String name, double time) {
        lastTime.put(name, (System.nanoTime() / 1e9) - time);
        cd.put(name, time);
    }

    public static void ensure(String name, double time) {
        if (cd.get(name) == null || Math.abs(cd.get(name) - time) < 0.001) {
            createCooldown(name, time);
        }
    }

    public static boolean check(String name) {
        double last = lastTime.get(name);
        if (((System.nanoTime() - last) / 1e9) > cd.get(name)) {
            return true;
        }
        return false;
    }

    public static void use(String name, Runnable action) {
        if (check(name)) {
            lastTime.put(name, System.nanoTime() / 1e9);
            action.run();
        }
    }

    public static void time(Runnable runnable, double threashold, String output) {
        long time = System.nanoTime();
        runnable.run();
        if ((System.nanoTime() - time) / 1e9 > threashold) {
            System.out.println(output.formatted((System.nanoTime() - time) / 1e9));
        }
    }

}
