package com.aseubel.yusi.common.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LowSensitivityLogSummaryTest {

    @Test
    void summarizesOnlyFixedLengthBuckets() {
        assertEquals("empty", LowSensitivityLogSummary.lengthBucket(null));
        assertEquals("empty", LowSensitivityLogSummary.lengthBucket(" \n"));
        assertEquals("short", LowSensitivityLogSummary.lengthBucket("海边🌊"));
        assertEquals("short", LowSensitivityLogSummary.lengthBucket("x".repeat(32)));
        assertEquals("medium", LowSensitivityLogSummary.lengthBucket("x".repeat(33)));
        assertEquals("medium", LowSensitivityLogSummary.lengthBucket("x".repeat(256)));
        assertEquals("long", LowSensitivityLogSummary.lengthBucket("x".repeat(257)));
        assertFalse(LowSensitivityLogSummary.lengthBucket("fixture-log-sensitive-query-7f3c")
                .contains("fixture-log-sensitive-query-7f3c"));
    }

    @Test
    void returnsOnlyExceptionType() {
        Throwable error = new IllegalStateException("fixture-log-sensitive-query-7f3c");

        assertEquals("IllegalStateException", LowSensitivityLogSummary.exceptionType(error));
        assertEquals("unknown", LowSensitivityLogSummary.exceptionType(null));
    }
}
