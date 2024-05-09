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
package io.servicetalk.apple.capacity.limiter.api;

import java.util.function.BiPredicate;

public final class GradientCapacityLimiterUtils {

    private GradientCapacityLimiterUtils() {
        // No instances
    }

    /**
     * Helper function to calculate a maximum limit growth when current consumption is below the current limit.
     * Limits when not disturbed can grow indefinitely (to their maximum setting), which has the negative effect of
     * taking more time to react to bad situations and shrink down to a healthy figure. This "blast-radius" concept
     * allows the limit to grow in a controlled way (e.g., grow 2x the current need) which still allows for traffic
     * spikes to happen uninterrupted, but also faster reaction times.
     * @param scale the scaling factor to use a multiplier of the current consumption.
     * @return A {@link BiPredicate} where the first argument represents the current consumption of the Gradient,
     * and the second argument is the current limit.
     */
    public static BiPredicate<Integer, Double> blastRadius(final int scale) {
        return (inFlight, limit) -> (inFlight * scale) < limit;
    }

    /**
     * Helper function to calculate a minimum target for limit decrease when current consumption is not reaching the
     * set limit. Limits can shrink to they minimum setting, when the RTT deviation is significant, even though that
     * doesn't cause buildups.
     * The "occupancy-factor" concept allows the limit to stay put even in presence of negative gradients, when its not
     * utilised.
     * @param factor the target utilization of the limit before it should start decreasing.
     * @return A {@link BiPredicate} where the first argument represents the current consumption of the Gradient,
     * and the second argument is the current limit.
     */
    public static BiPredicate<Integer, Double> occupancyFactor(final float factor) {
        return (inFlight, limit) -> inFlight <= (limit * factor);
    }
}
