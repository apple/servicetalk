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
import io.servicetalk.transport.api.ExecutionContext;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Completable.error;
import static java.util.Objects.requireNonNull;

/**
 * A {@link HttpClient} filter that will account for transient failures introduced by a {@link LoadBalancer} not being
 * ready for {@link #request(HttpRequest)} and retry/delay requests until the {@link LoadBalancer} is ready.
 */
public final class LoadBalancerReadyHttpClient<I, O> extends HttpClient<I, O> {
    private final LoadBalancerReadySubscriber loadBalancerReadySubscriber;
    private final HttpClient<I, O> next;
    private final int maxRetryCount;

    /**
     * Create a new instance.
     * @param maxRetryCount The maximum number of retries when requests fail with a {@link RetryableException}.
     * @param loadBalancerEvents See {@link LoadBalancer#getEventStream()}. This filter will listen for
     * {@link LoadBalancerReadyEvent} events to trigger retries.
     * @param next The next {@link HttpClient} in the filter chain.
     */
    public LoadBalancerReadyHttpClient(int maxRetryCount,
                                       Publisher<Object> loadBalancerEvents,
                                       HttpClient<I, O> next) {
        if (maxRetryCount <= 0) {
            throw new IllegalArgumentException("maxRetryCount " + maxRetryCount + " (expected >0)");
        }
        this.next = requireNonNull(next);
        this.maxRetryCount = maxRetryCount;
        loadBalancerReadySubscriber = new LoadBalancerReadySubscriber();
        loadBalancerEvents.subscribe(loadBalancerReadySubscriber);
    }

    @Override
    public Single<? extends ReservedHttpConnection<I, O>> reserveConnection(final HttpRequest<I> request) {
        return next.reserveConnection(request).retryWhen(retryWhenFunction());
    }

    @Override
    public Single<? extends UpgradableHttpResponse<I, O>> upgradeConnection(final HttpRequest<I> request) {
        return next.upgradeConnection(request).retryWhen(retryWhenFunction());
    }

    @Override
    public Single<HttpResponse<O>> request(final HttpRequest<I> request) {
        return next.request(request).retryWhen(retryWhenFunction());
    }

    @Override
    public ExecutionContext getExecutionContext() {
        return next.getExecutionContext();
    }

    @Override
    public Completable onClose() {
        return next.onClose();
    }

    @Override
    public Completable closeAsync() {
        return next.closeAsync();
    }

    private BiIntFunction<Throwable, Completable> retryWhenFunction() {
        return (count, cause) -> {
            if (count <= maxRetryCount && cause instanceof RetryableException) {
                Completable onHostsAvailable = loadBalancerReadySubscriber.onHostsAvailable();
                return onHostsAvailable != null ? onHostsAvailable : completed();
            }
            return error(cause);
        };
    }
}
