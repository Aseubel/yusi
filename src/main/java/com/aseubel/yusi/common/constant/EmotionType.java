package com.aseubel.yusi.common.constant;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Emotion codes returned by the model and stored on diary/plaza records. */
public enum EmotionType {
    JOY("Joy"),
    SADNESS("Sadness"),
    ANXIETY("Anxiety"),
    LOVE("Love"),
    ANGER("Anger"),
    FEAR("Fear"),
    HOPE("Hope"),
    CALM("Calm"),
    CONFUSION("Confusion"),
    NEUTRAL("Neutral");

    private static final Set<String> CODES = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.stream(values()).map(EmotionType::code).toList()));

    private final String code;

    EmotionType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static Set<String> codes() {
        return CODES;
    }

    public static EmotionType fromModelValue(String value) {
        if (value == null) {
            return NEUTRAL;
        }
        String normalized = value.trim();
        for (EmotionType emotion : values()) {
            if (emotion.code.equalsIgnoreCase(normalized)
                    || normalized.toLowerCase(Locale.ROOT).contains(emotion.code.toLowerCase(Locale.ROOT))) {
                return emotion;
            }
        }
        return NEUTRAL;
    }
}
