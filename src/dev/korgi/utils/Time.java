package dev.korgi.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class Time {

    private static final Map<String, Long> lastTime = new HashMap<>();
    private static final Map<String, Long> cd = new HashMap<>();
    private static long startTime;

    private static void createCooldown(String name, double timeSeconds) {
        lastTime.put(name, System.nanoTime());
        cd.put(name, (long) (timeSeconds * 1_000_000_000L));
    }

    public static boolean check(String name) {
        if (!lastTime.containsKey(name) || !cd.containsKey(name))
            return false;

        long elapsed = System.nanoTime() - lastTime.get(name);
        return elapsed > cd.get(name);
    }

    public static void cooldown(String name, Runnable action, double timeSeconds) {
        boolean shouldUse = false;

        if (cd.get(name) == null || Math.abs(cd.get(name) - timeSeconds) < 0.001) {
            createCooldown(name, timeSeconds);
            shouldUse = true;
        }
        if (shouldUse || check(name)) {
            lastTime.put(name, System.nanoTime());
            action.run();
        }
    }

    public static void startTimer() {
        startTime = System.nanoTime();
    }

    public static void stopTimer(String output, double threashold) {
        double time = (System.nanoTime() - startTime) / 1e9;
        if (time > threashold)
            System.out.println(output.formatted(time));
    }

    public static String now() {
        return LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("HH:mm:ss, d MMM uuuu"));
    }

}
