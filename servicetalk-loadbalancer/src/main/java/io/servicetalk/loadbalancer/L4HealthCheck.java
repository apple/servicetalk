package io.servicetalk.loadbalancer;

import java.time.Duration;

import static io.servicetalk.utils.internal.DurationUtils.ensureNonNegative;
import static io.servicetalk.utils.internal.DurationUtils.ensurePositive;
import static io.servicetalk.utils.internal.DurationUtils.isPositive;
import static java.time.Duration.ofSeconds;

final class L4HealthCheck {

    private L4HealthCheck() {
        // no instances.
    }
    static final Duration DEFAULT_HEALTH_CHECK_INTERVAL = ofSeconds(5);
    static final Duration DEFAULT_HEALTH_CHECK_JITTER = ofSeconds(3);
    static final Duration DEFAULT_HEALTH_CHECK_RESUBSCRIBE_INTERVAL = ofSeconds(10);
    static final int DEFAULT_HEALTH_CHECK_FAILED_CONNECTIONS_THRESHOLD = 5; // higher than default for AutoRetryStrategy

    static void validateHealthCheckIntervals(Duration interval, Duration jitter) {
        ensurePositive(interval, "interval");
        ensureNonNegative(jitter, "jitter");
        final Duration lowerBound = interval.minus(jitter);
        if (!isPositive(lowerBound)) {
            throw new IllegalArgumentException("interval (" + interval + ") minus jitter (" + jitter +
                    ") must be greater than 0, current=" + lowerBound);
        }
        final Duration upperBound = interval.plus(jitter);
        if (!isPositive(upperBound)) {
            throw new IllegalArgumentException("interval (" + interval + ") plus jitter (" + jitter +
                    ") must not overflow, current=" + upperBound);
        }
    }
}
