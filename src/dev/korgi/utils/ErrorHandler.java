package dev.korgi.utils;

import dev.korgi.game.ui.ErrorMessage;
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
        Screen.errorMsg.hide();
        Screen.errorMsg = new ErrorMessage(error, 3);
        Screen.errorMsg.setError(true);
        Screen.errorMsg.show();
        Thread thread = Thread.currentThread();
        StackTraceElement[] stackTrace = thread.getStackTrace();
        System.err.println(error);
        System.err.println("Occured at " + Time.now());
        printStackTrace(stackTrace);
        return new Error(error);
    }

    public static void warn(String message, Object... format) {
        JSONObject style = Screen.errorMsg.getStylesheet();
        JSONObject textLocal = style.getJSONObject("txt");
        textLocal.set("bg", StyleConstants.WARN);
        String error = "WARNING: %s".formatted(message).formatted(format);
        Screen.errorMsg.hide();
        Screen.errorMsg = new ErrorMessage(error, 3);
        Screen.errorMsg.setError(false);
        Screen.errorMsg.show();
    }

    public static void printStackTrace(StackTraceElement[] stackTrace) {
        for (StackTraceElement traceElement : stackTrace)
            System.err.println("\tat " + traceElement);
    }

}
