/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.loadbalancer;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Statistical computation algorithms for streaming data with near O(1) memory requirement.
 */
public final class StatUtils {

    private StatUtils() {
        // no instances
    }

    /**
     * Computes the Exponentially Weighted Moving Average in an Unevenly Spaced Time Series based
     * on work by Andreas Eckner.
     *
     * <p>
     * Exponential moving averages (EMAs) can be used for summarizing the average value of a time series over a certain
     * time horizon, except that more weight is given to more recent observations.
     * <p>
     *
     * @see <a href="http://www.eckner.com/papers/Algorithms%20for%20Unevenly%20Spaced%20Time%20Series.pdf"> Eckner
     * (2019) Algorithms for Unevenly Spaced Time Series: Moving Averages and Other Rolling Operators (4.2 pp. 10)</a>
     */
    public static class UnevenExpWeightedMovingAvg {

        /**
         * Mean lifetime, exponential decay.
         */
        private final float tau;
        /**
         * Last inserted value to compute weight.
         */
        private long lastTime;
        /**
         * Current weighted average.
         */
        private volatile float value;

        /**
         * Creates a new instance to compute the EWMA in an Unevenly Spaced Time Series.
         * <p>
         * @param halfLife time till the previously observed value decays to 50% of its initial value.
         */
        public UnevenExpWeightedMovingAvg(final Duration halfLife) {
            tau = (long) (halfLife.toNanos() / Math.log(2));
        }

        /**
         * Time source in ns, visible for testing purposes.
         * @return current time in ms.
         */
        long currentTimeNs() {
            return System.nanoTime();
        }

        /**
         * Computes a new weighted average with the provided {@code value}.
         * <p>
         * @param value the observed value to compute a new weighted average with.
         */
        public void observe(final float value) {
            long now = currentTimeNs();
            synchronized (this) {
                float tmp = (now - lastTime) / tau;
                float w = (float) Math.exp(-tmp);
                this.value = this.value * w + value * (1f - w);
                lastTime = now;
            }
        }

        /**
         * Sets the weighted average to the provided {@code value}.
         * <p>
         * This may be used to override the average value, eg. Service Discovery indicates a host is down.
         * @param value the new weighted average.
         */
        public void set(final float value) {
            Long now = currentTimeNs();
            synchronized (this) {
                this.lastTime = now;
                this.value = value;
            }
        }

        /**
         * Returns the current weighted average.
         * <p>
         * @return the current weighted average.
         */
        public float value() {
            return value;
        }

        /**
         * Returns the weighted average decayed until now.
         * <p>
         * @return the weighted average decayed until now.
         */
        public float valueDecayed() {
            long now = currentTimeNs();
            long lastTime;
            float decayedValue = this.value;
            synchronized (this) {
                lastTime = this.lastTime;
            }
            float tmp = (now - lastTime) / tau;
            float w = (float) Math.exp(-tmp);
            return decayedValue * w;
        }
    }

    /**
     * Estimates the quantile value from a stream with O(2) memory based on work by Qiang, Muthukrishnan and Sandler.
     * <p>
     * <u>nb:</u> The accuracy of the estimates improves a lot past the 1000 sample size for 50th %-ile and at the 5000
     * mark for 90th and higher %-iles. For smaller stream sizes this algorithm is perhaps not accurate enough and a
     * classic non streaming approach with O(n) memory may be more appropriate.
     *
     * @see <a href="https://arxiv.org/pdf/1407.1121.pdf">Ma, Muthukrishnan, Sandler (2014) Frugal Streaming for
     * Estimating Quantiles:One (or two) memory suffices (Algorithm 3 Frugal-2U, pp. 5)</a>
     */
    public static class StreamingQuantile {

        /**
         * Quantile to estimate, designated by {@code h/k} in the paper.
         */
        private final float hk;

        /**
         * Current estimated value for the quantile {@link #hk}, typically initialized with {@code 0} or with the first
         * observed value to increase convergence speed.
         */
        private float m;

        /**
         * Varying step size allows increased convergence speed over the Frugal-1U algorithm.
         */
        private float step = 1;

        /**
         * The direction of the last step.
         * <p>
         * This {@code 0}  value is different from the algorithm in the paper (value: {@code 1}) to indicate an
         * uninitialized value which we initialized with the first observation to increase convergence speed.
         */
        private int sign;

        /**
         * Estimates quantiles using a Frugal Streaming Quantile Estimation sketch.
         *
         * @param quantile the value for the quantile to estimate
         */
        public StreamingQuantile(final float quantile) {
            hk = quantile;
        }

        /**
         * Adds an observed data point {@code s} from the stream and recomputes the value of the quantile.
         * <p>
         * This implementation is based on Algorithm 3 Frugal-2U in the paper.
         * @param s the newly observed value, computes a new quantile estimate.
         */
        public synchronized void observe(final float s) {
            // This initializes m with the first observed value to increasing convergence speed, this conditional
            // block is not part of the original algorithm that starts with: {sign = 1, m = 0}.
            if (sign == 0) {
                m = s;
                sign = 1;
                return;
            }

            if (s > m && randomGenerator().nextFloat() > (1f - hk)) {
                step += (sign > 0) ? f(step) : -f(step);
                m += (step > 0) ? step : 1;
                if (m > s) {
                    step += (s - m);
                    m = s;
                }
                if (sign < 0) {
                    step = 1;
                }
                sign = 1;
            } else if (s < m && randomGenerator().nextFloat() > hk) {
                step -= (sign > 0) ? f(step) : -f(step);
                m -= (step > 0) ? step : 1;
                if (m < s) {
                    step += (m - s);
                    m = s;
                }
                if (sign > 0) {
                    step = 1;
                }
                sign = -1;
            }
        }

        /**
         * Step size function.
         *
         * <p>
         * The paper only researched a "constant factor additive update of step size 1", but other values may help
         * increase convergence speed for some use-cases.
         *
         * @param step the step value.
         * @return the computed step value.
         */
        private static float f(final float step) {
            return 1;
        }

        /**
         * Random number generator, visible for testing purposes.
         * <p>
         * @return the random number generator to use.
         */

        Random randomGenerator() {
            return ThreadLocalRandom.current();
        }

        /**
         * Returns the current estimation for the quantile.
         * <p>
         * @return the current estimation for the quantile.
         */
        public synchronized float estimation() {
            return m;
        }
    }
}
