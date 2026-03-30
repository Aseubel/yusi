package com.aseubel.yusi.common.utils;

import java.util.Locale;

public class ModelUtils {
    public static String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) {
            return "zh";
        }
        String value = language.toLowerCase(Locale.ROOT);
        if (value.startsWith("zh")) {
            return "zh";
        }
        if (value.startsWith("ja")) {
            return "ja";
        }
        if (value.startsWith("en")) {
            return "en";
        }
        return "zh";
    }
}
