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
package io.servicetalk.client.api;

import io.servicetalk.concurrent.api.AsyncCloseable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.transport.api.RetryableException;

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.AsyncCloseables.emptyAsyncCloseable;
import static io.servicetalk.concurrent.api.AsyncCloseables.toAsyncCloseable;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Completable.failed;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;

/**
 * Default implementation for {@link AutoRetryStrategyProvider}.
 * @deprecated The capabilities of the auto-retry have been introduced under a new universal retrying filter,
 * available from {@code io.servicetalk.http.netty.RetryingHttpRequesterFilter}.
 */
@Deprecated
public final class DefaultAutoRetryStrategyProvider implements AutoRetryStrategyProvider {
    private final int maxRetryCount;
    private final boolean waitForLb;
    private final boolean ignoreSdErrors;
    private final boolean retryAllRetryableExceptions;

    private DefaultAutoRetryStrategyProvider(final int maxRetryCount, final boolean waitForLb,
                                             final boolean ignoreSdErrors,
                                             final boolean retryAllRetryableExceptions) {
        this.maxRetryCount = maxRetryCount;
        this.waitForLb = waitForLb;
        this.ignoreSdErrors = ignoreSdErrors;
        this.retryAllRetryableExceptions = retryAllRetryableExceptions;
    }

    @Override
    public AutoRetryStrategy newStrategy(final Publisher<Object> lbEventStream, final Completable sdStatus) {
        if (!waitForLb && !retryAllRetryableExceptions) {
            return (count, cause) -> failed(cause);
        }
        return new DefaultAutoRetryStrategy(maxRetryCount, waitForLb, retryAllRetryableExceptions,
                lbEventStream, ignoreSdErrors ? null : sdStatus);
    }

    /**
     * A builder for {@link DefaultAutoRetryStrategyProvider}.
     * @deprecated The capabilities of the auto-retry have been introduced under a new universal retrying filter,
     * available from {@code io.servicetalk.http.netty.RetryingHttpRequesterFilter}.
     */
    @Deprecated
    public static final class Builder {
        private boolean waitForLb = true;
        private boolean ignoreSdErrors;
        private boolean retryAllRetryableExceptions = true;
        private int maxRetries = 4;

        /**
         * By default, automatic retries wait for the associated {@link LoadBalancer} to be ready before triggering a
         * retry for requests. This behavior may add latency to requests till the time the load balancer is ready
         * instead of failing fast. This method allows controlling that behavior.
         * @param waitForLb Whether to wait for the {@link LoadBalancer} to be ready before retrying requests.
         * @return {@code this}.
         */
        public Builder waitForLoadBalancer(final boolean waitForLb) {
            this.waitForLb = waitForLb;
            return this;
        }

        /**
         * By default, {@link AutoRetryStrategy auto-retry strategies} fail a request if the last signal from the
         * associated {@link ServiceDiscoverer} was an error. This method disables that behavior.
         *
         * @return {@code this}.
         */
        public Builder ignoreServiceDiscovererErrors() {
            ignoreSdErrors = true;
            return this;
        }

        /**
         * Connection closures (by the peer or locally) and new requests may happen concurrently. This means that it is
         * possible for a {@link LoadBalancer} to select a connection which is already closed (concurrently) but the
         * close signal has not yet been seen by the {@link LoadBalancer}. In such cases, requests fail with a
         * {@link RetryableException}. By default, automatic retries always retries these {@link RetryableException}s.
         * This method allows controlling that behaviour.
         * @param retryAllRetryableExceptions Whether to retry all {@link RetryableException}s.
         * @return {@code this}.
         */
        public Builder retryAllRetryableExceptions(final boolean retryAllRetryableExceptions) {
            this.retryAllRetryableExceptions = retryAllRetryableExceptions;
            return this;
        }

        /**
         * Updates maximum number of automatic retries done for any request.
         *
         * @param maxRetries Maximum number of automatic retries done for any request.
         * @return {@code this}.
         */
        public Builder maxRetries(int maxRetries) {
            if (maxRetries <= 0) {
                throw new IllegalArgumentException("maxRetries " + maxRetries + " (expected >0)");
            }
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * Builds a new {@link AutoRetryStrategyProvider}.
         *
         * @return A new {@link AutoRetryStrategyProvider}.
         */
        public AutoRetryStrategyProvider build() {
            return new DefaultAutoRetryStrategyProvider(maxRetries, waitForLb, ignoreSdErrors,
                    retryAllRetryableExceptions);
        }
    }

    private static final class DefaultAutoRetryStrategy implements AutoRetryStrategy {
        @Nullable
        private final LoadBalancerReadySubscriber loadBalancerReadySubscriber;
        @Nullable
        private final Completable sdStatus;
        private final AsyncCloseable closeAsync;
        private final int maxRetryCount;
        private final boolean retryAllRetryableExceptions;

        DefaultAutoRetryStrategy(final int maxRetryCount, final boolean waitForLb,
                                 final boolean retryAllRetryableExceptions,
                                 final Publisher<Object> lbEventStream, @Nullable final Completable sdStatus) {
            this.maxRetryCount = maxRetryCount;
            this.sdStatus = sdStatus;
            this.retryAllRetryableExceptions = retryAllRetryableExceptions;
            if (waitForLb) {
                loadBalancerReadySubscriber = new LoadBalancerReadySubscriber();
                closeAsync = toAsyncCloseable(__ -> {
                    loadBalancerReadySubscriber.cancel();
                    return completed();
                });
                toSource(lbEventStream).subscribe(loadBalancerReadySubscriber);
            } else {
                loadBalancerReadySubscriber = null;
                closeAsync = emptyAsyncCloseable();
            }
        }

        @Override
        public Completable apply(final int count, final Throwable cause) {
            if (count > maxRetryCount) {
                return failed(cause);
            }
            if (loadBalancerReadySubscriber != null && cause instanceof NoAvailableHostException) {
                final Completable onHostsAvailable = loadBalancerReadySubscriber.onHostsAvailable();
                return sdStatus == null ? onHostsAvailable : onHostsAvailable.ambWith(sdStatus);
            }
            if (retryAllRetryableExceptions && cause instanceof RetryableException) {
                return completed();
            }
            return failed(cause);
        }

        @Override
        public Completable closeAsync() {
            return closeAsync.closeAsync();
        }

        @Override
        public Completable closeAsyncGracefully() {
            return closeAsync.closeAsyncGracefully();
        }
    }
}
