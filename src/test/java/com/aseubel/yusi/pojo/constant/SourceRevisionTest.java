package com.aseubel.yusi.pojo.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SourceRevisionTest {

    @Test
    void firstTrackedEditOfHistoricalSourceStartsAtInitialRevision() {
        assertEquals(SourceRevision.INITIAL, SourceRevision.next(null));
    }

    @Test
    void trackedSourceIncrementsMonotonically() {
        assertEquals(2L, SourceRevision.next(1L));
    }
}
