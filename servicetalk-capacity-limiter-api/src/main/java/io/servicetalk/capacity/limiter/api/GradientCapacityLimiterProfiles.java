/*
 * Copyright © 2024 Apple Inc. and the ServiceTalk project authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.servicetalk.capacity.limiter.api;

import io.servicetalk.capacity.limiter.api.LatencyTracker.EMA;

import java.time.Duration;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

import static io.servicetalk.capacity.limiter.api.GradientCapacityLimiterUtils.blastRadius;
import static io.servicetalk.capacity.limiter.api.GradientCapacityLimiterUtils.occupancyFactor;
import static java.lang.Integer.MAX_VALUE;
import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;

/**
 * Default supported gradient profiles.
 */
final class GradientCapacityLimiterProfiles {

    static final int DEFAULT_INITIAL_LIMIT = 100;
    static final int DEFAULT_MIN_LIMIT = 1;
    static final int DEFAULT_MAX_LIMIT = MAX_VALUE;
    static final float DEFAULT_ON_DROP = 0.5f;
    static final float DEFAULT_ON_LIMIT = 0.2f;
    static final float DEFAULT_MIN_GRADIENT = 0.2f;
    static final float DEFAULT_MAX_GRADIENT = 1.2f;
    static final float GREEDY_MAX_GRADIENT = 1.8f;
    static final float GREEDY_ON_LIMIT = 0.9f;
    static final float GREEDY_ON_DROP = 0.95f;
    static final float GREEDY_MIN_GRADIENT = 0.90f;
    static final Duration DEFAULT_LIMIT_UPDATE_INTERVAL = ofSeconds(1);
    static final BiPredicate<Integer, Double> DEFAULT_SUSPEND_INCR = blastRadius(2);
    static final BiPredicate<Integer, Double> DEFAULT_SUSPEND_DEC = (__, ___) -> false;
    static final BiPredicate<Integer, Double> SUSPEND_DEC = occupancyFactor(.9f);
    static final BiFunction<Double, Double, Double> DEFAULT_HEADROOM = (__, ___) -> 0.0;
    static final BiFunction<Double, Double, Double> GREEDY_HEADROOM = (grad, limit) -> Math.sqrt(grad * limit);
    static final Duration MIN_SAMPLING_DURATION = Duration.ofMillis(50);
    static final LatencyTracker SHORT_LATENCY_CALMER_TRACKER = new EMA(Duration.ofMillis(500).toNanos());
    static final LatencyTracker LONG_LATENCY_CALMER_TRACKER = new EMA(ofSeconds(1).toNanos());
    static final BiFunction<Double, Double, Float> CALMER_RATIO =
            (tracker, calmer) -> calmer < (tracker / 2) ? .90f : -1f;
    static final LatencyTracker DEFAULT_SHORT_LATENCY_TRACKER = new LatencyTracker.LastSample();
    static final LatencyTracker SHORT_LATENCY_TRACKER = new EMA(ofSeconds(10).toNanos(),
            SHORT_LATENCY_CALMER_TRACKER, CALMER_RATIO);
    static final LatencyTracker DEFAULT_LONG_LATENCY_TRACKER = new EMA(ofMinutes(10).toNanos(),
            LONG_LATENCY_CALMER_TRACKER, CALMER_RATIO);

    private GradientCapacityLimiterProfiles() {
        // No instances
    }

    /**
     * The settings applied from this profile demonstrate cautious behaviour of the {@link CapacityLimiter},
     * that tries to keep the limit lower to avoid increasing the latency of requests.
     * This is a suggested setting for latency sensitive applications, but be aware that it may start throttling much
     * earlier when even small gradients are noticed in the response times.
     * @return Settings for the {@link GradientCapacityLimiterBuilder} for a cautious Gradient {@link CapacityLimiter}.
     */
    static Consumer<GradientCapacityLimiterBuilder> latencyDefaults() {
        return builder ->
                builder.minGradient(DEFAULT_MIN_GRADIENT)
                        .maxGradient(DEFAULT_MAX_GRADIENT)
                        .shortLatencyTracker(new LatencyTracker.LastSample())
                        .headroom(DEFAULT_HEADROOM);
    }

    /**
     * The settings applied from this profile demonstrate aggressive behaviour of the {@link CapacityLimiter},
     * that tries to push the limit higher until a significant gradient change is noticed. It will allow limit increases
     * while latency is changing, favouring throughput overall, so latency sensitive application may not want to use
     * this profile.
     *
     * @return Settings for the {@link GradientCapacityLimiterBuilder} for an aggressive Gradient
     * {@link CapacityLimiter}.
     */
    static Consumer<GradientCapacityLimiterBuilder> throughputDefaults() {
        return builder ->
                builder.minGradient(GREEDY_MIN_GRADIENT)
                        .maxGradient(GREEDY_MAX_GRADIENT)
                        .backoffRatio(GREEDY_ON_DROP, GREEDY_ON_LIMIT)
                        .shortLatencyTracker(SHORT_LATENCY_TRACKER)
                        .suspendLimitDecrease(SUSPEND_DEC)
                        .headroom(GREEDY_HEADROOM);
    }
}
