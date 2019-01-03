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
package io.servicetalk.http.api;

import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.api.LoadBalancerReadyEvent;
import io.servicetalk.client.api.RetryableException;
import io.servicetalk.client.internal.LoadBalancerReadySubscriber;
import io.servicetalk.concurrent.api.BiIntFunction;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;

import static io.servicetalk.concurrent.api.Completable.error;

/**
 * A {@link StreamingHttpClient} filter that will account for transient failures introduced by a {@link LoadBalancer}
 * not being ready for {@link #request(StreamingHttpRequest)} and retry/delay requests until the {@link LoadBalancer}
 * is ready.
 */
public final class LoadBalancerReadyStreamingHttpClient extends StreamingHttpClientFilter {
    private final LoadBalancerReadySubscriber loadBalancerReadySubscriber;
    private final int maxRetryCount;

    /**
     * Create a new instance.
     *
     * @param maxRetryCount The maximum number of retries when requests fail with a {@link RetryableException}.
     * @param loadBalancerEvents See {@link LoadBalancer#eventStream()}. This filter will listen for
     * {@link LoadBalancerReadyEvent} events to trigger retries.
     * @param next The next {@link StreamingHttpClient} in the filter chain.
     */
    public LoadBalancerReadyStreamingHttpClient(int maxRetryCount,
                                                Publisher<Object> loadBalancerEvents,
                                                StreamingHttpClient next) {
        super(next);
        if (maxRetryCount <= 0) {
            throw new IllegalArgumentException("maxRetryCount " + maxRetryCount + " (expected >0)");
        }
        this.maxRetryCount = maxRetryCount;
        loadBalancerReadySubscriber = new LoadBalancerReadySubscriber();
        loadBalancerEvents.subscribe(loadBalancerReadySubscriber);
    }

    @Override
    public Single<? extends ReservedStreamingHttpConnection> reserveConnection(final HttpExecutionStrategy strategy,
                                                                               final StreamingHttpRequest request) {
        return delegate().reserveConnection(strategy, request).retryWhen(retryWhenFunction());
    }

    @Override
    public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                 final StreamingHttpRequest request) {
        return delegate().request(strategy, request).retryWhen(retryWhenFunction());
    }

    private BiIntFunction<Throwable, Completable> retryWhenFunction() {
        return (count, cause) -> count <= maxRetryCount && cause instanceof RetryableException ?
                loadBalancerReadySubscriber.onHostsAvailable() : error(cause);
    }
}
