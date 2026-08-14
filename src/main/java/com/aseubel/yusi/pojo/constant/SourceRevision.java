package com.aseubel.yusi.pojo.constant;

/** Version policy for user-authored sources and their derived data. */
public final class SourceRevision {

    public static final long INITIAL = 1L;

    private SourceRevision() {
    }

    public static long initialOrCurrent(Long revision) {
        return revision == null || revision < INITIAL ? INITIAL : revision;
    }

    public static long next(Long revision) {
        if (revision == null || revision < INITIAL) {
            return INITIAL;
        }
        long current = revision;
        if (current == Long.MAX_VALUE) {
            throw new IllegalStateException("Source revision overflow");
        }
        return current + 1;
    }
}
