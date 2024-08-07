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

import io.servicetalk.capacity.limiter.api.CapacityLimiter.Ticket;

import java.util.List;

import static io.servicetalk.capacity.limiter.api.GradientCapacityLimiterProfiles.latencyDefaults;
import static io.servicetalk.capacity.limiter.api.GradientCapacityLimiterProfiles.throughputDefaults;

/**
 * A static factory for creating instances of {@link CapacityLimiter}s.
 */
public final class CapacityLimiters {
    private CapacityLimiters() {
        // no instances
    }

    /**
     * Returns a NO-OP {@link CapacityLimiter} that has no logic around acquiring or releasing a permit for a request;
     * thus it allows everything to go through, similar to a non-existing {@link CapacityLimiter}.
     * <p>
     * This {@link CapacityLimiter} allows for situations where partitioned configurations are in use through
     * a resilience filter, and you want to limit one partition but not necessary the other.
     *
     * @return A NO-OP {@link CapacityLimiter capacity limiter}.
     */
    public static CapacityLimiter allowAll() {
        return AllowAllCapacityLimiter.INSTANCE;
    }

    /**
     * A composite {@link CapacityLimiter} that is composed of multiple {@link CapacityLimiter}s.
     * <p>
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
     * A {@link CapacityLimiter} that will reject all requests till the current pending request count is equal or less
     * to the passed {@code capacity}.
     * <p>
     * This {@link CapacityLimiter} takes into consideration the {@link Classification} of a given request and will
     * variate the effective {@code capacity} according to the {@link Classification#priority() priority} before
     * attempting to grant access to the request. The effective {@code capacity} will never be more than the given
     * {@code capacity}.
     * <p>
     * Requests with {@link Classification#priority() priority} equal to or greater than {@code 100} will enjoy
     * the full capacity (100%), while requests with {@link Classification#priority() priority} less than {@code 100}
     * will be mapped to a percentage point of the given {@code capacity} and be granted access only if the {@code
     * consumed capacity} is less than that percentage.
     * <p>
     * Example: With a {@code capacity} = 10, and incoming {@link Classification#priority()} = 70, then the effective
     * target limit for this request will be 70% of the 10 = 7. If current consumption is less than 7, the request
     * will be permitted.
     *
     * @param capacity The fixed capacity value for this limiter.
     * @return A {@link FixedCapacityLimiterBuilder} to configure the available parameters.
     */
    public static FixedCapacityLimiterBuilder fixedCapacity(final int capacity) {
        return new FixedCapacityLimiterBuilder(capacity);
    }

    /**
     * AIMD is a request drop based dynamic {@link CapacityLimiter} for clients,
     * that adapts its limit based on a configurable range of concurrency and re-evaluates this limit upon
     * a {@link Ticket#dropped() request-drop event (eg. timeout or rejection due to capacity)}.
     * <p>
     * It's not ideal for server-side solutions, due to the slow recover mechanism it offers, which can lead in
     * significant traffic loss during the recovery window.
     * <p>
     * The limit translates to a concurrency figure, e.g. how many requests can be in-flight simultaneously and doesn't
     * represent a constant rate (i.e. has no notion of time). Requests per second when that limit is met will be
     * equal to the exit rate of the queue.
     * <p>
     * The solution is based on the <a href="https://en.wikipedia.org/wiki/Additive_increase/multiplicative_decrease">
     * AIMD feedback control algorithm</a>.
     *
     * @return An {@link AimdCapacityLimiterBuilder} to configure the available parameters.
     */
    public static AimdCapacityLimiterBuilder dynamicAIMD() {
        return new AimdCapacityLimiterBuilder();
    }

    /**
     * Gradient is a dynamic concurrency limit algorithm used for clients or servers.
     * <p>
     * Gradient's basic concept is that it tracks two Round Trip Time (RTT) figures, one of long period and another one
     * of a shorter period. These two figures are then compared, and a gradient value is produced, representing the
     * change between the two. That gradient value can in-turn be used to signify load; i.e. a positive gradient can
     * mean that the RTTs are decreasing, whereas a negative value means that RTTs are increasing.
     * This figure can be used to deduce a new limit (lower or higher accordingly) to follow the observed load pattern.
     * <p>
     * The algorithm is heavily influenced by the following prior-art:
     * <ol>
     * <li><a
     * href="https://www.envoyproxy.io/docs/envoy/latest/configuration/http/http_filters/adaptive_concurrency_filter">
     * Envoy Adaptive Concurrency</a></li>
     * <li><a href="https://github.com/Netflix/concurrency-limits">Netflix Concurrency Limits</a></li>
     * </ol>
     *
     * @return A {@link GradientCapacityLimiterBuilder} to configure the available parameters.
     * @see #dynamicGradientOptimizeForLatency()
     * @see #dynamicGradientOptimizeForThroughput()
     */
    public static GradientCapacityLimiterBuilder dynamicGradient() {
        return new GradientCapacityLimiterBuilder();
    }

    /**
     * Gradient is a dynamic concurrency limit algorithm used for clients or servers.
     * <p>
     * Gradient's basic concept is that it tracks two Round Trip Time (RTT) figures, one of long period and another one
     * of a shorter period. These two figures are then compared, and a gradient value is produced, representing the
     * change between the two. That gradient value can in-turn be used to signify load; i.e. a positive gradient can
     * mean that the RTTs are decreasing, whereas a negative value means that RTTs are increasing.
     * This figure can be used to deduce a new limit (lower or higher accordingly) to follow the observed load pattern.
     * <p>
     * The default settings applied on this version, demonstrate aggressive behaviour of the {@link CapacityLimiter},
     * that tries to push the limit higher until a significant gradient change is noticed. It will allow limit increases
     * while latency is changing, favouring throughput overall, so latency sensitive application may not want to use
     * this profile.
     * <p>
     * The algorithm is heavily influenced by the following prior-art:
     * <ol>
     * <li><a
     * href="https://www.envoyproxy.io/docs/envoy/latest/configuration/http/http_filters/adaptive_concurrency_filter">
     * Envoy Adaptive Concurrency</a></li>
     * <li><a href="https://github.com/Netflix/concurrency-limits">Netflix Concurrency Limits</a></li>
     * </ol>
     *
     * @return A {@link GradientCapacityLimiterBuilder} to configure the available parameters.
     * @see #dynamicGradient()
     * @see #dynamicGradientOptimizeForLatency()
     */
    public static GradientCapacityLimiterBuilder dynamicGradientOptimizeForThroughput() {
        final GradientCapacityLimiterBuilder builder = new GradientCapacityLimiterBuilder();
        throughputDefaults().accept(builder);
        return builder;
    }

    /**
     * Gradient is a dynamic concurrency limit algorithm used for clients or servers.
     * <p>
     * Gradient's basic concept is that it tracks two Round Trip Time (RTT) figures, one of long period and another one
     * of a shorter period. These two figures are then compared, and a gradient value is produced, representing the
     * change between the two. That gradient value can in-turn be used to signify load; i.e. a positive gradient can
     * mean that the RTTs are decreasing, whereas a negative value means that RTTs are increasing.
     * This figure can be used to deduce a new limit (lower or higher accordingly) to follow the observed load pattern.
     * <p>
     * The default settings applied from this version, demonstrate cautious behaviour of the {@link CapacityLimiter},
     * that tries to keep the limit lower to avoid increasing the latency of requests.
     * This is a suggested setting for latency sensitive applications, but be aware that it may start throttling much
     * earlier when even small gradients are noticed in the response times.
     * <p>
     * The algorithm is heavily influenced by the following prior-art:
     * <ol>
     * <li><a
     * href="https://www.envoyproxy.io/docs/envoy/latest/configuration/http/http_filters/adaptive_concurrency_filter">
     * Envoy Adaptive Concurrency</a></li>
     * <li><a href="https://github.com/Netflix/concurrency-limits">Netflix Concurrency Limits</a></li>
     * </ol>
     *
     * @return A {@link GradientCapacityLimiterBuilder} to configure the available parameters.
     * @see #dynamicGradient()
     * @see #dynamicGradientOptimizeForThroughput()
     */
    public static GradientCapacityLimiterBuilder dynamicGradientOptimizeForLatency() {
        final GradientCapacityLimiterBuilder builder = new GradientCapacityLimiterBuilder();
        latencyDefaults().accept(builder);
        return builder;
    }
}
