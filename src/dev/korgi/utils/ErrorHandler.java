package dev.korgi.utils;

import dev.korgi.game.ui.Screen;
import dev.korgi.json.JSONObject;

public class ErrorHandler {

    private ErrorHandler() {
    };

    public static Error error(String message, Object... format) {
        JSONObject style = Screen.errorMsg.getStyle();
        JSONObject canavsLocal = style.getJSONObject("error_main_canvas");
        Screen screen = Screen.getInstance();
        canavsLocal.set("x", screen.width / 2.0f);
        canavsLocal.set("y", screen.height / 2.0f);
        JSONObject textLocal = style.getJSONObject("display");
        textLocal.set("bg", 0xFFFF0000);
        String error = "ERROR: %s".formatted(message).formatted(format);
        style.set("display.value", error);
        Thread thread = Thread.currentThread();
        StackTraceElement[] stackTrace = thread.getStackTrace();
        System.err.println(error);
        System.err.println("Occured at " + Time.now());
        printStackTrace(stackTrace);
        return new Error(error);
    }

    public static Error error(String message, double time, Object... format) {
        JSONObject style = Screen.errorMsg.getStyle();
        style.set("time", time);
        style.set("updated", System.nanoTime());
        return error(message, format);
    }

    public static void warn(String message, Object... format) {
        JSONObject style = Screen.errorMsg.getStyle();
        JSONObject canavsLocal = style.getJSONObject("error_main_canvas");
        Screen screen = Screen.getInstance();
        canavsLocal.set("x", screen.width / 2.0f);
        canavsLocal.set("y", screen.height / 2.0f);
        JSONObject textLocal = style.getJSONObject("display");
        textLocal.set("bg", 0xFFE9D502);
        String error = "WARNING: %s".formatted(message).formatted(format);
        style.set("display.value", error);
    }

    public static void warn(String message, double time, Object... format) {
        JSONObject style = Screen.errorMsg.getStyle();
        style.set("time", time);
        style.set("updated", System.nanoTime());
        warn(message, format);
    }

    public static void loòp() {
        JSONObject style = Screen.errorMsg.getStyle();
        Double time = style.getDouble("time");
        if (time != null && !Double.isFinite(time)) {
            long updateTime = style.getLong("updated");
            if (((System.nanoTime() - updateTime) / 1e9) >= time) {
                style.set("time", Double.NaN);
                style.set("display.value", "");
            }
        }
    }

    public static void printStackTrace(StackTraceElement[] stackTrace) {
        for (StackTraceElement traceElement : stackTrace)
            System.err.println("\tat " + traceElement);
    }

}
