package com.aseubel.yusi.observability.alert;

import java.time.Duration;
import java.time.Instant;

public interface AlertStateStore {

    boolean claim(String fingerprint, Instant now, Duration suppressionWindow);

    void markFiring(String fingerprint, Instant now);

    boolean markRecovered(String fingerprint, Instant now);

    boolean isRootSuppressionActive(Instant now);
}
