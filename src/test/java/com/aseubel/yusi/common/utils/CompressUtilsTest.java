package com.aseubel.yusi.common.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompressUtilsTest {

    @Test
    void leavesNullEmptyAndSmallPayloadsUntouched() {
        assertEquals(null, CompressUtils.compress(null));
        assertEquals("", CompressUtils.compress(""));

        String small = "short text";

        assertSame(small, CompressUtils.compress(small));
        assertEquals(small, CompressUtils.decompress(small));
    }

    @Test
    void compressesAndDecompressesLargeRepetitivePayload() {
        String payload = "memory:".repeat(200) + "重要内容".repeat(120);

        String compressed = CompressUtils.compress(payload);

        assertTrue(compressed.startsWith("COMPRESSED:"));
        assertTrue(compressed.length() < payload.length());
        assertEquals(payload, CompressUtils.decompress(compressed));
    }

    @Test
    void corruptedCompressedPayloadIsReturnedUnchanged() {
        String corrupted = "COMPRESSED:this-is-not-valid-deflate-data";

        assertEquals(corrupted, CompressUtils.decompress(corrupted));
    }

    @Test
    void compressionRatioUsesCompressedStringLength() {
        assertEquals(1.0, CompressUtils.compressionRatio("", "anything"));
        assertEquals(0.5, CompressUtils.compressionRatio("1234567890", "12345"));
    }
}
