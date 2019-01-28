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
import io.servicetalk.client.api.LoadBalancerReadyEvent;
import io.servicetalk.client.api.NoAvailableHostException;
import io.servicetalk.client.api.RetryableException;
import io.servicetalk.client.internal.LoadBalancerReadySubscriber;
import io.servicetalk.concurrent.api.BiIntFunction;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.redis.api.RedisProtocolSupport.Command;

import static io.servicetalk.concurrent.api.Completable.error;

/**
 * A {@link RedisClient} filter that will account for transient failures introduced by a {@link LoadBalancer} not being
 * ready for {@link #request(RedisRequest)} and retry/delay requests until the {@link LoadBalancer} is ready.
 */
public final class LoadBalancerReadyRedisClient extends RedisClientFilter {
    private final LoadBalancerReadySubscriber loadBalancerReadySubscriber;
    private final int maxRetryCount;

    /**
     * Create a new instance.
     *
     * @param maxRetryCount The maximum number of retries when requests fail with a {@link RetryableException}.
     * @param loadBalancerEvents See {@link LoadBalancer#eventStream()}. This filter will listen for
     * {@link LoadBalancerReadyEvent} events to trigger retries.
     * @param next The next {@link RedisClient} in the filter chain.
     */
    public LoadBalancerReadyRedisClient(int maxRetryCount,
                                        Publisher<Object> loadBalancerEvents,
                                        RedisClient next) {
        super(next);
        if (maxRetryCount <= 0) {
            throw new IllegalArgumentException("maxRetryCount " + maxRetryCount + " (expected >0)");
        }
        this.maxRetryCount = maxRetryCount;
        loadBalancerReadySubscriber = new LoadBalancerReadySubscriber();
        loadBalancerEvents.subscribe(loadBalancerReadySubscriber);
    }

    @Override
    public Single<? extends ReservedRedisConnection> reserveConnection(RedisExecutionStrategy strategy,
                                                                       Command command) {
        return delegate().reserveConnection(strategy, command).retryWhen(retryWhenFunction());
    }

    @Override
    public Publisher<RedisData> request(final RedisExecutionStrategy strategy, final RedisRequest request) {
        return delegate().request(strategy, request).retryWhen(retryWhenFunction());
    }

    private BiIntFunction<Throwable, Completable> retryWhenFunction() {
        return (count, cause) -> count <= maxRetryCount && cause instanceof NoAvailableHostException ?
                loadBalancerReadySubscriber.onHostsAvailable() : error(cause);
    }
}
