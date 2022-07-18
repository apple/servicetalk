/*
 * Copyright © 2020-2022 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.LoadBalancerFactory;
import io.servicetalk.client.api.ReservableRequestConcurrencyController;
import io.servicetalk.client.api.ScoreSupplier;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;

import java.lang.reflect.Method;

import static io.servicetalk.http.api.HttpApiConversions.toReservedBlockingConnection;
import static io.servicetalk.http.api.HttpApiConversions.toReservedBlockingStreamingConnection;
import static io.servicetalk.http.api.HttpApiConversions.toReservedConnection;
import static java.lang.Integer.MAX_VALUE;
import static java.util.Objects.requireNonNull;

/**
 * A {@link LoadBalancerFactory} for HTTP clients.
 *
 * @param <ResolvedAddress> The type of address after resolution.
 */
public interface HttpLoadBalancerFactory<ResolvedAddress>
        extends LoadBalancerFactory<ResolvedAddress, FilterableStreamingHttpLoadBalancedConnection> {

    /**
     * Converts the passed {@link FilterableStreamingHttpConnection} to a
     * {@link FilterableStreamingHttpLoadBalancedConnection}.
     *
     * @param connection {@link FilterableStreamingHttpConnection} to convert
     * @return {@link FilterableStreamingHttpLoadBalancedConnection} for the passed
     * {@link FilterableStreamingHttpConnection}
     * @deprecated Implement and use {@link #toLoadBalancedConnection(FilterableStreamingHttpConnection,
     * ReservableRequestConcurrencyController)}
     */
    @Deprecated // FIXME: 0.43 - remove deprecated method
    default FilterableStreamingHttpLoadBalancedConnection toLoadBalancedConnection(
            FilterableStreamingHttpConnection connection) {
        throw new UnsupportedOperationException(
                "HttpLoadBalancerFactory#toLoadBalancedConnection(FilterableStreamingHttpConnection) " +
                "is not implemented by " + getClass() + ". This method is deprecated, consider migrating to " +
                "HttpLoadBalancerFactory#toLoadBalancedConnection(FilterableStreamingHttpConnection, " +
                "ReservableRequestConcurrencyController) or implement this method if it's required temporarily.");
    }

    /**
     * Converts the passed {@link FilterableStreamingHttpConnection} to a
     * {@link FilterableStreamingHttpLoadBalancedConnection}.
     *
     * @param connection {@link FilterableStreamingHttpConnection} to convert
     * @param concurrencyController {@link ReservableRequestConcurrencyController} to control access to the connection
     * @return {@link FilterableStreamingHttpLoadBalancedConnection} for the passed
     * {@link FilterableStreamingHttpConnection}
     */
    default FilterableStreamingHttpLoadBalancedConnection toLoadBalancedConnection(
            FilterableStreamingHttpConnection connection,
            ReservableRequestConcurrencyController concurrencyController) {
        boolean defaultOriginalMethod;
        try {
            Method originalMethod = this.getClass().getMethod("toLoadBalancedConnection",
                    FilterableStreamingHttpConnection.class);
            defaultOriginalMethod = originalMethod.getDeclaringClass() == HttpLoadBalancerFactory.class &&
                    originalMethod.isDefault();
        } catch (NoSuchMethodException impossible) {
            defaultOriginalMethod = true;
        }
        if (defaultOriginalMethod) {
            return new DefaultFilterableStreamingHttpLoadBalancedConnection(connection, concurrencyController);
        }
        final FilterableStreamingHttpLoadBalancedConnection original = toLoadBalancedConnection(connection);
        return new DefaultFilterableStreamingHttpLoadBalancedConnection(original, concurrencyController, original);
    }

    @Override
    default HttpExecutionStrategy requiredOffloads() {
        // "safe" default -- implementations are expected to override
        return HttpExecutionStrategies.offloadAll();
    }

    /**
     * Default implementation of {@link FilterableStreamingHttpLoadBalancedConnection}.
     */
    final class DefaultFilterableStreamingHttpLoadBalancedConnection
            implements FilterableStreamingHttpLoadBalancedConnection {

        private final FilterableStreamingHttpConnection delegate;
        private final ReservableRequestConcurrencyController controller;
        private final ScoreSupplier scoreSupplier;

        /**
         * Create a new instance without support for {@link #score()}.
         *
         * @param delegate {@link FilterableStreamingHttpConnection} to delegate to
         * @param controller {@link ReservableRequestConcurrencyController} to control concurrent access to the delegate
         */
        public DefaultFilterableStreamingHttpLoadBalancedConnection(
                final FilterableStreamingHttpConnection delegate,
                final ReservableRequestConcurrencyController controller) {
            this(delegate, controller, () -> MAX_VALUE);
        }

        /**
         * Create a new instance.
         *
         * @param delegate {@link FilterableStreamingHttpConnection} to delegate to
         * @param controller {@link ReservableRequestConcurrencyController} to control concurrent access to the delegate
         * @param scoreSupplier {@link ScoreSupplier} to query the {@link #score()}
         */
        public DefaultFilterableStreamingHttpLoadBalancedConnection(
                final FilterableStreamingHttpConnection delegate,
                final ReservableRequestConcurrencyController controller,
                final ScoreSupplier scoreSupplier) {
            this.delegate = requireNonNull(delegate);
            this.controller = requireNonNull(controller);
            this.scoreSupplier = requireNonNull(scoreSupplier);
        }

        @Override
        public int score() {
            return scoreSupplier.score();
        }

        @Override
        public HttpConnectionContext connectionContext() {
            return delegate.connectionContext();
        }

        @Override
        public <T> Publisher<? extends T> transportEventStream(final HttpEventKey<T> eventKey) {
            return delegate.transportEventStream(eventKey);
        }

        @Override
        public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
            return delegate.request(request);
        }

        @Override
        public HttpExecutionContext executionContext() {
            return delegate.executionContext();
        }

        @Override
        public StreamingHttpResponseFactory httpResponseFactory() {
            return delegate.httpResponseFactory();
        }

        @Override
        public Completable onClose() {
            return delegate.onClose();
        }

        @Override
        public Completable closeAsync() {
            return delegate.closeAsync();
        }

        @Override
        public Completable closeAsyncGracefully() {
            return delegate.closeAsyncGracefully();
        }

        @Override
        public StreamingHttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
            return delegate.newRequest(method, requestTarget);
        }

        @Override
        public Result tryRequest() {
            return controller.tryRequest();
        }

        @Override
        public void requestFinished() {
            controller.requestFinished();
        }

        @Override
        public boolean tryReserve() {
            return controller.tryReserve();
        }

        @Override
        public Completable releaseAsync() {
            return controller.releaseAsync();
        }

        // Client filters will also be applied for reserved connections that are returned to the user, see
        // ClientFilterToReservedConnectionFilter. Therefore, we should take the finally computed strategy from the
        // ExecutionContext that takes into account all filters.
        @Override
        public ReservedHttpConnection asConnection() {
            return toReservedConnection(this, executionContext().executionStrategy());
        }

        @Override
        public ReservedBlockingStreamingHttpConnection asBlockingStreamingConnection() {
            return toReservedBlockingStreamingConnection(this, executionContext().executionStrategy());
        }

        @Override
        public ReservedBlockingHttpConnection asBlockingConnection() {
            return toReservedBlockingConnection(this, executionContext().executionStrategy());
        }
    }
}
