/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.redis.api;

import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.concurrent.api.Publisher;

import java.util.function.UnaryOperator;

import static java.util.Objects.requireNonNull;

/**
 * A factory that applies filters to {@link RedisClient}s.
 */
@FunctionalInterface
public interface RedisClientFilterFactory {
    /**
     * Apply filters to a {@link RedisClient}.
     * @param client The {@link RedisClient} before filtering.
     * @param subscribeLoadBalancerEvents The {@link LoadBalancer#eventStream()} for the load balancer responsible
     * for pub/sub commands.
     * @param pipelinedLoadBalancerEvents The {@link LoadBalancer#eventStream()} for the load balancer responsible
     * for non-pub/sub commands.
     * @return The filtered {@link RedisClient}.
     */
    RedisClient apply(RedisClient client,
                      Publisher<Object> subscribeLoadBalancerEvents,
                      Publisher<Object> pipelinedLoadBalancerEvents);

    /**
     * Returns a composed function that first applies the {@code before} function to its input, and then applies
     * this function to the result.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     filter1.append(filter2).append(filter3)
     * </pre>
     * making a request to a client wrapped by this filter chain the order of invocation of these filters will be:
     * <pre>
     *     filter1 =&gt; filter2 =&gt; filter3 =&gt; client
     * </pre>
     * @param before the function to apply before this function is applied
     * @return a composed function that first applies the {@code before}
     * function and then applies this function
     */
    default RedisClientFilterFactory append(RedisClientFilterFactory before) {
        requireNonNull(before);
        return (client, subscribeLBEvents, pipelinedLBEvents) -> apply(
                before.apply(client, subscribeLBEvents, pipelinedLBEvents), subscribeLBEvents, pipelinedLBEvents);
    }

    /**
     * Returns a function that always returns its input {@link RedisClient}.
     *
     * @return a function that always returns its input {@link RedisClient}.
     */
    static RedisClientFilterFactory identity() {
        return (client, subscribeLoadBalancerEvents, pipelinedLoadBalancerEvents) -> client;
    }

    /**
     * Returns a function that adapts from the {@link UnaryOperator}&lt;{@link RedisClient}&gt; function type to the
     * {@link RedisClientFilterFactory}.
     *
     * @param function the function that is applied to the input {@link RedisClient}
     * @return the filtered {@link RedisClient}
     */
    static RedisClientFilterFactory from(UnaryOperator<RedisClient> function) {
        requireNonNull(function);
        return (client, subscribeLBEvents, pipelinedLBEvents) -> function.apply(client);
    }
}
