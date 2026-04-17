package dev.korgi.utils;

import java.util.HashMap;
import java.util.Map;

public class Time {

    private static final Map<String, Long> lastTime = new HashMap<>();
    private static final Map<String, Long> cd = new HashMap<>();
    private static long startTime;

    public static void createCooldown(String name, double timeSeconds) {
        lastTime.put(name, System.nanoTime());
        cd.put(name, (long) (timeSeconds * 1_000_000_000L));
    }

    public static void ensure(String name, double time) {
        if (cd.get(name) == null || Math.abs(cd.get(name) - time) < 0.001) {
            createCooldown(name, time);
        }
    }

    public static boolean check(String name) {
        if (!lastTime.containsKey(name) || !cd.containsKey(name))
            return false;

        long elapsed = System.nanoTime() - lastTime.get(name);
        return elapsed > cd.get(name);
    }

    public static void use(String name, Runnable action) {
        if (check(name)) {
            lastTime.put(name, System.nanoTime());
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

    public static void staticTime() {
        startTime = System.nanoTime();
    }

    public static void staticTime(String output) {
        System.out.println(output.formatted((System.nanoTime() - startTime) / 1e9));
    }

    public static void staticTime(String output, double threashold) {
        double time = (System.nanoTime() - startTime) / 1e9;
        if (time > threashold)
            System.out.println(output.formatted(time));
    }

}
