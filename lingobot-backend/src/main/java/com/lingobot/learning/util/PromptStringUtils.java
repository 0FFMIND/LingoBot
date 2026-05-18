package com.lingobot.learning.util;

public final class PromptStringUtils {

    private PromptStringUtils() {}

    public static String textOrUnknown(String value) {
        return value != null && !value.isBlank() ? value : "unknown";
    }

    public static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
