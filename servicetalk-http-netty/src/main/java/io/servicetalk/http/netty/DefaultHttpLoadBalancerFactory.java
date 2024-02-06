/*
 * Copyright © 2020-2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.netty;

import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.api.LoadBalancerFactory;
import io.servicetalk.client.api.ReservableRequestConcurrencyController;
import io.servicetalk.client.api.ScoreSupplier;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TerminalSignalConsumer;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.FilterableStreamingHttpLoadBalancedConnection;
import io.servicetalk.http.api.HttpConnectionContext;
import io.servicetalk.http.api.HttpEventKey;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpLoadBalancerFactory;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.ReservedBlockingHttpConnection;
import io.servicetalk.http.api.ReservedBlockingStreamingHttpConnection;
import io.servicetalk.http.api.ReservedHttpConnection;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.utils.BeforeFinallyHttpOperator;
import io.servicetalk.loadbalancer.ErrorClass;
import io.servicetalk.loadbalancer.RequestTracker;
import io.servicetalk.loadbalancer.RoundRobinLoadBalancers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.ConnectException;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.HttpApiConversions.toReservedBlockingConnection;
import static io.servicetalk.http.api.HttpApiConversions.toReservedBlockingStreamingConnection;
import static io.servicetalk.http.api.HttpApiConversions.toReservedConnection;
import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.SERVER_ERROR_5XX;
import static io.servicetalk.http.api.HttpResponseStatus.TOO_MANY_REQUESTS;
import static io.servicetalk.loadbalancer.ErrorClass.LOCAL_ORIGIN_CONNECT_FAILED;
import static io.servicetalk.loadbalancer.ErrorClass.LOCAL_ORIGIN_REQUEST_FAILED;
import static io.servicetalk.loadbalancer.RequestTracker.REQUEST_TRACKER_KEY;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

/**
 * Default implementation of {@link HttpLoadBalancerFactory}.
 *
 * @param <ResolvedAddress> The type of address after resolution.
 */
public final class DefaultHttpLoadBalancerFactory<ResolvedAddress>
        implements HttpLoadBalancerFactory<ResolvedAddress> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultHttpLoadBalancerFactory.class);
    private final LoadBalancerFactory<ResolvedAddress, FilterableStreamingHttpLoadBalancedConnection> rawFactory;
    private final Function<Throwable, ErrorClass> errorClassFunction;
    private final Function<HttpResponseMetaData, ErrorClass> peerResponseErrorClassifier;
    private final HttpExecutionStrategy strategy;

    DefaultHttpLoadBalancerFactory(
            final LoadBalancerFactory<ResolvedAddress, FilterableStreamingHttpLoadBalancedConnection> rawFactory,
            final Function<Throwable, ErrorClass> errorClassFunction,
            final Function<HttpResponseMetaData, ErrorClass> peerResponseErrorClassifier,
            final HttpExecutionStrategy strategy) {
        this.rawFactory = rawFactory;
        this.errorClassFunction = errorClassFunction;
        this.peerResponseErrorClassifier = peerResponseErrorClassifier;
        this.strategy = strategy;
    }

    @SuppressWarnings("deprecation")
    @Override
    public <T extends FilterableStreamingHttpLoadBalancedConnection> LoadBalancer<T> newLoadBalancer(
            final String targetResource,
            final Publisher<? extends Collection<? extends ServiceDiscovererEvent<ResolvedAddress>>> eventPublisher,
            final ConnectionFactory<ResolvedAddress, T> connectionFactory) {
        return rawFactory.newLoadBalancer(targetResource, eventPublisher, connectionFactory);
    }

    @Override
    public LoadBalancer<FilterableStreamingHttpLoadBalancedConnection> newLoadBalancer(
            final Publisher<? extends Collection<? extends ServiceDiscovererEvent<ResolvedAddress>>> eventPublisher,
            final ConnectionFactory<ResolvedAddress, FilterableStreamingHttpLoadBalancedConnection> connectionFactory,
            final String targetResource) {
        return rawFactory.newLoadBalancer(eventPublisher, connectionFactory, targetResource);
    }

    @Override   // FIXME: 0.43 - remove deprecated method
    public FilterableStreamingHttpLoadBalancedConnection toLoadBalancedConnection(
            final FilterableStreamingHttpConnection connection) {
        return new DefaultFilterableStreamingHttpLoadBalancedConnection(connection);
    }

    @Override
    public FilterableStreamingHttpLoadBalancedConnection toLoadBalancedConnection(
            final FilterableStreamingHttpConnection connection,
            final ReservableRequestConcurrencyController concurrencyController,
            @Nullable final ContextMap context) {

        RequestTracker hostHealthIndicator = null;
        if (context == null) {
            LOGGER.debug("Context is null. In order for " + DefaultHttpLoadBalancerFactory.class.getSimpleName() +
                    ":toLoadBalancedConnection to get access to the " + RequestTracker.class.getSimpleName() +
                    ", health-monitor of this connection, the context must not be null.");
        } else {
            hostHealthIndicator = context.get(REQUEST_TRACKER_KEY);
            if (hostHealthIndicator == null) {
                LOGGER.debug(REQUEST_TRACKER_KEY.name() + " is not set in context. " +
                        "In order for " + DefaultHttpLoadBalancerFactory.class.getSimpleName() +
                        ":toLoadBalancedConnection to get access to the " + RequestTracker.class.getSimpleName() +
                        ", health-monitor of this connection, the context must be properly wired.");
            }
        }

        if (hostHealthIndicator == null) {
            return new HttpLoadBalancerFactory.DefaultFilterableStreamingHttpLoadBalancedConnection(connection,
                    concurrencyController);
        }

        return new DefaultHttpLoadBalancedConnection(connection, concurrencyController,
                errorClassFunction, peerResponseErrorClassifier, hostHealthIndicator);
    }

    @Override
    public HttpExecutionStrategy requiredOffloads() {
        return strategy;
    }

    /**
     * A builder for creating instances of {@link DefaultHttpLoadBalancerFactory}.
     *
     * @param <ResolvedAddress> The type of address after resolution for the {@link HttpLoadBalancerFactory} built by
     * this builder.
     */
    public static final class Builder<ResolvedAddress> {
        private final LoadBalancerFactory<ResolvedAddress, FilterableStreamingHttpLoadBalancedConnection> rawFactory;
        private final HttpExecutionStrategy strategy;
        private final Function<Throwable, ErrorClass> errorClassifier = t -> t instanceof ConnectException ?
                LOCAL_ORIGIN_CONNECT_FAILED : LOCAL_ORIGIN_REQUEST_FAILED;
        private final Function<HttpResponseMetaData, ErrorClass> peerResponseErrorClassifier = resp ->
                (resp.status().statusClass() == SERVER_ERROR_5XX || TOO_MANY_REQUESTS.equals(resp.status())) ?
                        ErrorClass.EXT_ORIGIN_REQUEST_FAILED : null;

        private Builder(
                final LoadBalancerFactory<ResolvedAddress, FilterableStreamingHttpLoadBalancedConnection> rawFactory,
                final HttpExecutionStrategy strategy) {
            this.rawFactory = rawFactory;
            this.strategy = strategy;
        }

        /**
         * Builds a {@link DefaultHttpLoadBalancerFactory} using the properties configured on this builder.
         *
         * @return A {@link DefaultHttpLoadBalancerFactory}.
         */
        public DefaultHttpLoadBalancerFactory<ResolvedAddress> build() {
            return new DefaultHttpLoadBalancerFactory<>(rawFactory, errorClassifier, peerResponseErrorClassifier,
                    strategy);
        }

        /**
         * Creates a new {@link Builder} instance using the default {@link LoadBalancer} implementation.
         *
         * @param <ResolvedAddress> The type of address after resolution for the {@link LoadBalancerFactory} built by
         * the returned builder.
         * @return A new {@link Builder}.
         */
        public static <ResolvedAddress> Builder<ResolvedAddress> fromDefaults() {
            return from(RoundRobinLoadBalancers
                    .<ResolvedAddress, FilterableStreamingHttpLoadBalancedConnection>builder(
                            DefaultHttpLoadBalancerFactory.class.getSimpleName()).build());
        }

        /**
         * Creates a new {@link Builder} using the passed {@link LoadBalancerFactory}.
         *
         * @param rawFactory {@link LoadBalancerFactory} to use for creating a {@link HttpLoadBalancerFactory} from the
         * returned {@link Builder}.
         * @param <ResolvedAddress> The type of address after resolution for a {@link HttpLoadBalancerFactory} created
         * by the returned {@link Builder}.
         * @return A new {@link Builder}.
         */
        @SuppressWarnings("deprecation")
        public static <ResolvedAddress> Builder<ResolvedAddress> from(
                final LoadBalancerFactory<ResolvedAddress, FilterableStreamingHttpLoadBalancedConnection> rawFactory) {
            final HttpExecutionStrategy strategy = HttpExecutionStrategy.from(rawFactory.requiredOffloads());
            return new Builder<>(rawFactory, strategy);
        }
    }

    private static final class DefaultFilterableStreamingHttpLoadBalancedConnection
            implements FilterableStreamingHttpLoadBalancedConnection {

        private final FilterableStreamingHttpConnection delegate;

        DefaultFilterableStreamingHttpLoadBalancedConnection(final FilterableStreamingHttpConnection delegate) {
            this.delegate = requireNonNull(delegate);
        }

        @Override
        public int score() {
            throw new UnsupportedOperationException(
                    DefaultFilterableStreamingHttpLoadBalancedConnection.class.getName() +
                            " doesn't support scoring. " + ScoreSupplier.class.getName() +
                            " is only available through " + HttpLoadBalancerFactory.class.getSimpleName() +
                            " implementations that support scoring.");
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
        public Completable onClosing() {
            return delegate.onClosing();
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
        public String toString() {
            return delegate.toString();
        }
    }

    private static final class DefaultHttpLoadBalancedConnection
            implements FilterableStreamingHttpLoadBalancedConnection {
        private final FilterableStreamingHttpConnection delegate;
        private final ReservableRequestConcurrencyController concurrencyController;
        private final Function<Throwable, ErrorClass> errorClassFunction;
        private final Function<HttpResponseMetaData, ErrorClass> peerResponseErrorClassifier;
        @Nullable
        private final RequestTracker tracker;

        DefaultHttpLoadBalancedConnection(final FilterableStreamingHttpConnection delegate,
                                          final ReservableRequestConcurrencyController concurrencyController,
                                          final Function<Throwable, ErrorClass> errorClassFunction,
                                          final Function<HttpResponseMetaData, ErrorClass> peerResponseErrorClassifier,
                                          @Nullable final RequestTracker tracker) {
            this.delegate = delegate;
            this.concurrencyController = concurrencyController;
            this.errorClassFunction = errorClassFunction;
            this.peerResponseErrorClassifier = peerResponseErrorClassifier;
            this.tracker = tracker;
        }

        @Override
        public int score() {
            return 1;
        }

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

        @Override
        public Completable releaseAsync() {
            return concurrencyController.releaseAsync();
        }

        @Override
        public Completable closeAsyncGracefully() {
            return delegate.closeAsyncGracefully();
        }

        @Override
        public Result tryRequest() {
            return concurrencyController.tryRequest();
        }

        @Override
        public boolean tryReserve() {
            return concurrencyController.tryReserve();
        }

        @Override
        public void requestFinished() {
            concurrencyController.requestFinished();
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
            if (tracker == null) {
                return delegate.request(request).shareContextOnSubscribe();
            }

            return Single.defer(() -> {
                final RequestTracker theTracker = new AtMostOnceDeliveryRequestTracker(tracker);
                final long startTime = theTracker.beforeRequestStart();

                return delegate.request(request)
                        .liftSync(new BeforeFinallyHttpOperator(new TerminalSignalConsumer() {
                            @Override
                            public void onComplete() {
                                theTracker.onRequestSuccess(startTime);
                            }

                            @Override
                            public void onError(final Throwable throwable) {
                                theTracker.onRequestError(startTime, errorClassFunction.apply(throwable));
                            }

                            @Override
                            public void cancel() {
                                theTracker.onRequestError(startTime, ErrorClass.CANCELLED);
                            }
                        }, /*discardEventsAfterCancel*/ true))

                        // BeforeFinallyHttpOperator conditionally outputs a Single<Meta> with a failed
                        // Publisher<Data> instead of the real Publisher<Data> in case a cancel signal is observed
                        // before completion of Meta. It also transforms the original Publisher<Data> to discard
                        // signals after cancel. So in order for downstream operators to get a consistent view of the
                        // data path map() needs to be applied last.
                        .map(response -> {
                            final ErrorClass eClass = peerResponseErrorClassifier.apply(response);
                            if (eClass != null) {
                                // The onError is triggered before the body is actually consumed.
                                theTracker.onRequestError(startTime, eClass);
                            }
                            return response;
                        })
                        .shareContextOnSubscribe();
            });
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
        public Completable onClosing() {
            return delegate.onClosing();
        }

        @Override
        public Completable closeAsync() {
            return delegate.closeAsync();
        }

        @Override
        public StreamingHttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
            return delegate.newRequest(method, requestTarget);
        }

        @Override
        public String toString() {
            return delegate.toString();
        }

        private static final class AtMostOnceDeliveryRequestTracker implements RequestTracker {

            private static final AtomicIntegerFieldUpdater<AtMostOnceDeliveryRequestTracker> doneUpdater =
                    newUpdater(AtMostOnceDeliveryRequestTracker.class, "done");

            private final RequestTracker original;
            private volatile int done;

            private AtMostOnceDeliveryRequestTracker(final RequestTracker original) {
                this.original = original;
            }

            @Override
            public long beforeRequestStart() {
                return original.beforeRequestStart();
            }

            @Override
            public void onRequestSuccess(final long beforeStartTimeNs) {
                if (doneUpdater.compareAndSet(this, 0, 1)) {
                    original.onRequestSuccess(beforeStartTimeNs);
                }
            }

            @Override
            public void onRequestError(final long beforeStartTimeNs, final ErrorClass errorClass) {
                if (doneUpdater.compareAndSet(this, 0, 1)) {
                    original.onRequestError(beforeStartTimeNs, errorClass);
                }
            }
        }
    }
}
