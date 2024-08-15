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

import io.servicetalk.utils.internal.NumberUtils;

import java.util.function.BiFunction;
import javax.annotation.Nullable;

import static java.lang.Math.exp;
import static java.lang.Math.log;
import static java.util.Objects.requireNonNull;

/**
 * A tracker of latency values at certain points in time.
 * <p>
 * This helps observe latency behavior and different implementation can offer their own interpretation of the latency
 * tracker, allowing a way to enhance the algorithm's behavior according to the observations.
 * Implementations must provide thread-safety guarantees.
 */
@FunctionalInterface
interface LatencyTracker {

    /**
     * Observe a latency figure at a certain point in time.
     *
     * @param timestampNs The time this latency was observed (in Nanoseconds).
     * @param latencyMillis The observed latency (in Milliseconds).
     * @return the result of the observations so far.
     */
    double observe(long timestampNs, long latencyMillis);

    /**
     * A {@link LatencyTracker} that keeps track of the latest observed latency.
     */
    final class LastSample implements LatencyTracker {
        @Override
        public double observe(long timestampNs, final long latency) {
            return latency;
        }
    }

    /**
     * Exponential weighted moving average based on the work by Andreas Eckner.
     *
     * @see <a href="http://www.eckner.com/papers/Algorithms%20for%20Unevenly%20Spaced%20Time%20Series.pdf"> Eckner
     * (2019) Algorithms for Unevenly Spaced Time Series: Moving Averages and Other Rolling Operators (4.2 pp. 10)</a>
     */
    final class EMA implements LatencyTracker {

        private final double invTau;
        @Nullable
        private final LatencyTracker calmTracker;
        @Nullable
        private final BiFunction<Double, Double, Float> calmRatio;

        private double ewma;
        private long lastTimeNanos;

        /**
         * Constructs an Exponential Moving Average {@link LatencyTracker} with decay window of halfLifeNs.
         * @param halfLifeNs The decay window of the EMA.
         */
        EMA(final long halfLifeNs) {
            this.invTau = 1.0 / (halfLifeNs / log(2));
            this.calmTracker = null;
            this.calmRatio = null;
        }

        /**
         * Constructs an Exponential Moving Average {@link LatencyTracker} with decay window of halfLifeNs.
         * It offers additional support of shrinking the observed latency when the calmTracker observations
         * are different enough to trigger the predicate.
         * @param halfLifeNs The decay window of the EMA.
         * @param calmTracker A {@link LatencyTracker} that is used to help shrink the observations
         * of this {@link LatencyTracker} faster. The observations of this tracker and the calmTracker,
         * are compared in the {@link BiFunction}.
         * @param calmRatio A {@link BiFunction} that evaluates the observation of this tracker against the calmer,
         * and decides on shrink ration of this observation. Any negative value, results in no calming.
         */
        EMA(final long halfLifeNs, final LatencyTracker calmTracker,
            final BiFunction<Double, Double, Float> calmRatio) {
            NumberUtils.ensurePositive(halfLifeNs, "halfLifeNs");
            this.invTau = 1.0 / (halfLifeNs / log(2));
            this.calmTracker = requireNonNull(calmTracker);
            this.calmRatio = requireNonNull(calmRatio);
        }

        @Override
        public double observe(long timestampNs, final long latency) {
            if (calmTracker != null) {
                assert calmRatio != null;
                final double calmedEwma = calmTracker.observe(timestampNs, latency);
                final float ratio = calmRatio.apply(ewma, calmedEwma);
                if (ewma > 0 && ratio > 0f) {
                    ewma *= ratio;
                    lastTimeNanos = timestampNs;
                    return ewma;
                }
            }

            final double tmp = (timestampNs - lastTimeNanos) * invTau;
            final double w = exp(-tmp);
            ewma = ewma * w + latency * (1d - w);
            lastTimeNanos = timestampNs;
            return ewma;
        }
    }
}
