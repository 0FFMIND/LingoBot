package com.lingobot.learning.util;

import com.lingobot.infrastructure.common.exception.ChatException;

public class UserInputSanitizer {

    public static final int MAX_USER_MEANING_LENGTH = 100;
    public static final int MAX_USER_SENTENCE_LENGTH = 500;

    private static final String CONTROL_CHARS_PATTERN = "[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]";
    private static final String ZERO_WIDTH_CHARS_PATTERN = "[\\u200B-\\u200F\\u202A-\\u202E\\uFEFF\\u00AD]";

    private UserInputSanitizer() {
    }

    public static String sanitizeUserMeaning(String input) {
        return sanitize(input, "释义", MAX_USER_MEANING_LENGTH);
    }

    public static String sanitizeUserSentence(String input) {
        return sanitize(input, "造句", MAX_USER_SENTENCE_LENGTH);
    }

    private static String sanitize(String input, String fieldName, int maxLength) {
        if (input == null) {
            throw ChatException.badRequest(fieldName + "不能为空");
        }

        String cleaned = input
                .replaceAll(CONTROL_CHARS_PATTERN, "")
                .replaceAll(ZERO_WIDTH_CHARS_PATTERN, "")
                .trim();

        if (cleaned.isEmpty()) {
            throw ChatException.badRequest(fieldName + "不能为空或仅包含空白字符");
        }

        if (cleaned.codePointCount(0, cleaned.length()) > maxLength) {
            throw ChatException.badRequest(fieldName + "长度不能超过 " + maxLength + " 字符");
        }

        return cleaned;
    }

    public static String markAsUntrusted(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return "";
        }
        return "（不可信用户输入）" + userInput;
    }
}
