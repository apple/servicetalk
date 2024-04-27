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

import io.servicetalk.apple.capacity.limiter.api.CapacityLimiter.Ticket;

import java.util.List;
import java.util.function.Consumer;

/**
 * A static factory for creating instances of {@link CapacityLimiter}s.
 */
public final class CapacityLimiters {
    private CapacityLimiters() {
        // no instances
    }

    /**
     * A composite {@link CapacityLimiter} that is composed of multiple {@link CapacityLimiter}s.
     * All capacity limiters need to grant a {@link Ticket} for the request to be allowed.
     *
     * @param providers The individual {@link CapacityLimiter} that form the composite result.
     * @return A {@link CapacityLimiter} that is composed by the sum of all the {@link CapacityLimiter}s passed as
     * arguments.
     */
    public static CapacityLimiter composite(final List<CapacityLimiter> providers) {
        return new CompositeCapacityLimiter(providers);
    }

    /**
     * Returns a {@link CapacityLimiter} that will reject all requests till the current pending request count is equal
     * or less to the passed {@code capacity}.
     * This {@link CapacityLimiter} takes into consideration the {@link Classification} of a given request and will
     * variate the effective {@code capacity} according to the {@link Classification#weight() weight} before
     * attempting to grant access to the request. The effective {@code capacity} will never be more than the given
     * {@code capacity}.
     * <p>
     * Requests with {@link Classification#weight() weight} equal to or greater than {@code 100} will enjoy
     * the full capacity (100%), while requests with {@link Classification#weight() weight} less than {@code 100}
     * will be mapped to a percentage point of the given {@code capacity} and be granted access only if the {@code
     * consumed capacity} is less than that percentage.
     * <br>
     * Example: With a {@code capacity} = 10, and incoming {@link Classification#weight()} = 70, then the effective
     * target limit for this request will be 70% of the 10 = 7. If current consumption is less than 7, the request
     * will be permitted.
     *
     * @return A {@link CapacityLimiter} builder to configure the available parameters.
     */
    public static FixedCapacityLimiterBuilder fixedCapacity() {
        return new FixedCapacityLimiterBuilder();
    }

    /**
     * AIMD is a request drop based dynamic {@link CapacityLimiter} for clients,
     * that adapts its limit based on a configurable range of concurrency and re-evaluates this limit upon
     * a {@link Ticket#dropped() request-drop event (eg. timeout or rejection due to capacity)}.
     * <p>
     * The limit translates to a concurrency figure, e.g. how many requests can be in-flight simultaneously and doesn't
     * represent a constant rate (i.e. has no notion of time). Requests per second when that limit is met will be
     * equal to the exit rate of the queue.
     * <p>
     * The solution is based on the <a href="https://en.wikipedia.org/wiki/Additive_increase/multiplicative_decrease">
     * AIMD feedback control algorithm</a>
     *
     * @return A client side dynamic {@link CapacityLimiter capacity limiter builder}.
     */
    public static AimdCapacityLimiterBuilder dynamicAIMD() {
        return new AimdCapacityLimiterBuilder();
    }

    /**
     * Gradient is a dynamic concurrency limit algorithm used for clients.
     * <p>
     * Gradient's basic concept is that it tracks two Round Trip Time (RTT) figures, one of long period and another one
     * of a shorter period. These two figures are then compared, and a gradient value is produced, representing the
     * change between the two. That gradient value can in-turn be used to signify load; i.e. a positive gradient can
     * mean that the RTTs are decreasing, whereas a negative value means that RTTs are increasing.
     * This figure can be used to deduce a new limit (lower or higher accordingly) to follow the observed load pattern.
     * <p>
     * The algorithm is heavily influenced by the following prior-art
     * @return A client side dynamic {@link CapacityLimiter capacity limiter builder}.
     * @see
     * <a
     * href="https://www.envoyproxy.io/docs/envoy/latest/configuration/http/http_filters/adaptive_concurrency_filter">
     * Envoy Adaptive Concurrency</a>
     * @see <a href="https://github.com/Netflix/concurrency-limits">Netflix Concurrency Limits</a>
     */
    public static GradientCapacityLimiterBuilder dynamicGradient() {
        return new GradientCapacityLimiterBuilder();
    }

    /**
     * Gradient is a dynamic concurrency limit algorithm used for clients.
     * <p>
     * Gradient's basic concept is that it tracks two Round Trip Time (RTT) figures, one of long period and another one
     * of a shorter period. These two figures are then compared, and a gradient value is produced, representing the
     * change between the two. That gradient value can in-turn be used to signify load; i.e. a positive gradient can
     * mean that the RTTs are decreasing, whereas a negative value means that RTTs are increasing.
     * This figure can be used to deduce a new limit (lower or higher accordingly) to follow the observed load pattern.
     * @param profile The behaviour profile to apply to the builder instead of using the normal defaults.
     * @return A client side dynamic {@link CapacityLimiter capacity limiter builder}.
     */
    public static GradientCapacityLimiterBuilder dynamicGradient(
            final Consumer<GradientCapacityLimiterBuilder> profile) {
        final GradientCapacityLimiterBuilder builder = new GradientCapacityLimiterBuilder();
        profile.accept(builder);
        return builder;
    }

    /**
     * Returns a NO-OP {@link CapacityLimiter} that has no logic around acquiring or releasing a permit for a request;
     * thus it allows everything to go through, similarly to a non-existing {@link CapacityLimiter}.
     * This {@link CapacityLimiter} allows for situations where partitioned configurations are in use through
     * a resilience filter, and you want to limit one partition but not necessary the other.
     *
     * @return A NO-OP {@link CapacityLimiter capacity limiter}.
     */
    public static CapacityLimiter allowAll() {
        return AllowAllCapacityLimiter.INSTANCE;
    }
}
