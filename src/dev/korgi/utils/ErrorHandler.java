package dev.korgi.utils;

import dev.korgi.game.ui.Screen;
import dev.korgi.json.JSONObject;

public class ErrorHandler {

    private ErrorHandler() {
    };

    public static Error error(String message, Object... format) {
        JSONObject style = Screen.errorMsg.getStylesheet();
        JSONObject textLocal = style.getJSONObject("txt");
        textLocal.set("bg", StyleConstants.RED);
        String error = "ERROR: %s".formatted(message).formatted(format);
        Screen.errorMsg.setValue(error);
        Thread thread = Thread.currentThread();
        StackTraceElement[] stackTrace = thread.getStackTrace();
        System.err.println(error);
        System.err.println("Occured at " + Time.now());
        printStackTrace(stackTrace);
        return new Error(error);
    }

    public static Error error(String message, double time, Object... format) {
        JSONObject style = Screen.errorMsg.getStylesheet();
        style.set("time", time);
        style.set("updated", System.nanoTime());
        return error(message, format);
    }

    public static void warn(String message, Object... format) {
        JSONObject style = Screen.errorMsg.getStylesheet();
        JSONObject textLocal = style.getJSONObject("txt");
        textLocal.set("bg", StyleConstants.WARN);
        String error = "WARNING: %s".formatted(message).formatted(format);
        Screen.errorMsg.setValue(error);
    }

    public static void warn(String message, double time, Object... format) {
        JSONObject style = Screen.errorMsg.getStylesheet();
        style.set("time", time);
        style.set("updated", System.nanoTime());
        warn(message, format);
    }

    public static void loòp() {
        JSONObject style = Screen.errorMsg.getStylesheet();
        Double time = style.getDouble("time");
        if (time != null && !Double.isFinite(time)) {
            long updateTime = style.getLong("updated");
            if (((System.nanoTime() - updateTime) / 1e9) >= time) {
                style.set("time", Double.NaN);
                Screen.errorMsg.setValue("");
            }
        }
    }

    public static void printStackTrace(StackTraceElement[] stackTrace) {
        for (StackTraceElement traceElement : stackTrace)
            System.err.println("\tat " + traceElement);
    }

}
