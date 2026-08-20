package com.aseubel.yusi.observability.alert;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded state store for test profile and single-instance fallback. */
public class InMemoryAlertStateStore implements AlertStateStore {

    private final int capacity;
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public InMemoryAlertStateStore(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    @Override
    public synchronized boolean claim(String fingerprint, Instant now, Duration suppressionWindow) {
        validate(fingerprint, now);
        purge(now, suppressionWindow);
        Entry entry = entries.get(fingerprint);
        if (entry != null && entry.lastClaimedAt() != null
                && Duration.between(entry.lastClaimedAt(), now).compareTo(suppressionWindow) < 0) {
            return false;
        }
        ensureCapacity();
        entries.put(fingerprint, new Entry(entry != null && entry.active(), now,
                entry != null && entry.recovered()));
        return true;
    }

    @Override
    public synchronized void markFiring(String fingerprint, Instant now) {
        validate(fingerprint, now);
        Entry previous = entries.get(fingerprint);
        ensureCapacity();
        entries.put(fingerprint, new Entry(true,
                previous == null ? null : previous.lastClaimedAt(), false));
    }

    @Override
    public synchronized boolean markRecovered(String fingerprint, Instant now) {
        validate(fingerprint, now);
        Entry previous = entries.get(fingerprint);
        if (previous == null || !previous.active() || previous.recovered()) {
            return false;
        }
        entries.put(fingerprint, new Entry(false, previous.lastClaimedAt(), true));
        return true;
    }

    @Override
    public synchronized boolean isRootSuppressionActive(Instant now) {
        for (Map.Entry<String, Entry> entry : entries.entrySet()) {
            if (entry.getKey().startsWith("service_unavailable|") && entry.getValue().active()) {
                return true;
            }
        }
        return false;
    }

    private void purge(Instant now, Duration window) {
        Iterator<Map.Entry<String, Entry>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next().getValue();
            if (entry.lastClaimedAt() != null
                    && Duration.between(entry.lastClaimedAt(), now).compareTo(window.multipliedBy(2)) > 0
                    && !entry.active()) {
                iterator.remove();
            }
        }
    }

    private void ensureCapacity() {
        while (entries.size() >= capacity) {
            entries.remove(entries.keySet().iterator().next());
        }
    }

    private void validate(String fingerprint, Instant now) {
        if (fingerprint == null || fingerprint.isBlank() || fingerprint.length() > 256 || now == null) {
            throw new IllegalArgumentException("invalid alert state");
        }
    }

    private record Entry(boolean active, Instant lastClaimedAt, boolean recovered) {
    }
}
