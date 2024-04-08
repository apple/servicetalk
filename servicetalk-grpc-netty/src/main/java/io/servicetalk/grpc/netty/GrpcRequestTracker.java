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
package io.servicetalk.grpc.netty;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.client.api.ConnectionFactoryFilter;
import io.servicetalk.client.api.DelegatingConnectionFactory;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.grpc.api.GrpcLifecycleObserver;
import io.servicetalk.grpc.api.GrpcStatus;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.netty.HttpLifecycleObserverRequesterFilter;
import io.servicetalk.loadbalancer.ErrorClass;
import io.servicetalk.loadbalancer.RequestTracker;
import io.servicetalk.transport.api.ConnectionInfo;
import io.servicetalk.transport.api.TransportObserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.loadbalancer.RequestTracker.REQUEST_TRACKER_KEY;

final class GrpcRequestTracker {

    private static final Logger LOGGER = LoggerFactory.getLogger(GrpcRequestTracker.class);
    private static final GrpcLifecycleObserver.GrpcRequestObserver NOOP_REQUEST_OBSERVER =
            new NoopGrpcRequestObserver();

    private static final Function<GrpcStatus, ErrorClass> PEER_RESPONSE_ERROR_CLASSIFIER = (status) -> {
        // TODO: this needs to be gone over with more detail.
        switch (status.code()) {
            case OK:
                return null;
            case CANCELLED:
                return ErrorClass.CANCELLED;
            default:
                return ErrorClass.EXT_ORIGIN_REQUEST_FAILED;
        }
    };

    // TODO: this needs to be gone over with more detail.
    private static final Function<Throwable, ErrorClass> ERROR_CLASS_FUNCTION = (exn) ->
            ErrorClass.EXT_ORIGIN_REQUEST_FAILED;

    private GrpcRequestTracker() {
        // no instances
    }

    static <ResolvedAddress> ConnectionFactoryFilter<ResolvedAddress, FilterableStreamingHttpConnection> filter() {
        return (connection) -> new ConnectionFactoryWrapper<>(connection);
    }

    private static class ConnectionFactoryWrapper<ResolvedAddress>
            extends DelegatingConnectionFactory<ResolvedAddress, FilterableStreamingHttpConnection> {

        ConnectionFactoryWrapper(
                ConnectionFactory<ResolvedAddress, FilterableStreamingHttpConnection> delegate) {
            super(delegate);
        }

        @Override
        public Single<FilterableStreamingHttpConnection> newConnection(
                ResolvedAddress resolvedAddress, @Nullable ContextMap context, @Nullable TransportObserver observer) {
            Single<FilterableStreamingHttpConnection> result = delegate()
                    .newConnection(resolvedAddress, context, observer);
            if (context == null) {
                LOGGER.debug("Context is null. In order for {} to get access to the {}" +
                                ", health-monitor of this connection, the context must not be null.",
                        GrpcRequestTracker.class.getSimpleName(), RequestTracker.class.getSimpleName());
            } else {
                result = result.map(connection -> transformConnection(connection, context));
            }
            return result;
        }

        private FilterableStreamingHttpConnection transformConnection(
                FilterableStreamingHttpConnection connection, ContextMap context) {
            RequestTracker requestTracker = context.remove(REQUEST_TRACKER_KEY);
            if (requestTracker == null) {
                LOGGER.debug("{} is not set in context. In order for {} to get access to the {}" +
                                ", health-monitor of this connection, the context must be properly wired.",
                        REQUEST_TRACKER_KEY.name(), GrpcRequestTracker.class.getSimpleName(),
                        RequestTracker.class.getSimpleName());
                return connection;
            } else {
                LOGGER.debug("Added request tracker to connection {}.", connection.connectionContext());
                HttpLifecycleObserverRequesterFilter filter = new GrpcLifecycleObserverRequesterFilter(
                        new Observer(requestTracker));
                return filter.create(connection);
            }
        }
    }

    private static class Observer implements GrpcLifecycleObserver {
        private final RequestTracker tracker;

        Observer(final RequestTracker tracker) {
            this.tracker = tracker;
        }

        @Override
        public GrpcExchangeObserver onNewExchange() {
            return new Observer.RequestTrackerExchangeObserver(tracker);
        }

        private static final class RequestTrackerExchangeObserver implements GrpcLifecycleObserver.GrpcExchangeObserver,
                GrpcLifecycleObserver.GrpcResponseObserver {

            private static final AtomicLongFieldUpdater<RequestTrackerExchangeObserver> START_TIME_UPDATER =
                    AtomicLongFieldUpdater.newUpdater(RequestTrackerExchangeObserver.class, "startTime");
            private final RequestTracker tracker;
            @SuppressWarnings("unused")
            private volatile long startTime = Long.MIN_VALUE;

            RequestTrackerExchangeObserver(final RequestTracker tracker) {
                this.tracker = tracker;
            }

            @Override
            public void onConnectionSelected(ConnectionInfo info) {
                // noop
            }

            @Override
            public GrpcLifecycleObserver.GrpcRequestObserver onRequest(HttpRequestMetaData requestMetaData) {
                START_TIME_UPDATER.set(this, tracker.beforeRequestStart());
                return NOOP_REQUEST_OBSERVER;
            }

            @Override
            public GrpcLifecycleObserver.GrpcResponseObserver onResponse(HttpResponseMetaData responseMetaData) {
                // TODO: should we _also_ check the HttpResponseMetadata?
                return this;
            }

            @Override
            public void onResponseError(Throwable cause) {
                final long startTime = finish();
                if (checkOnce(startTime)) {
                    tracker.onRequestError(startTime, ERROR_CLASS_FUNCTION.apply(cause));
                }
            }

            @Override
            public void onResponseCancel() {
                final long startTime = finish();
                if (checkOnce(startTime)) {
                    tracker.onRequestError(startTime, ErrorClass.CANCELLED);
                }
            }

            @Override
            public void onExchangeFinally() {
                // noop
            }

            @Override
            public void onGrpcStatus(GrpcStatus status) {
                ErrorClass error = PEER_RESPONSE_ERROR_CLASSIFIER.apply(status);
                if (error != null) {
                    final long startTime = finish();
                    if (checkOnce(startTime)) {
                        tracker.onRequestError(startTime, error);
                    }
                }
            }

            @Override
            public void onResponseData(Buffer data) {
                // noop
            }

            @Override
            public void onResponseTrailers(HttpHeaders trailers) {
                // noop: this is called right before `onGrpcStatus` in the GrpcToHttpLifecycleObserverBridge.
            }

            @Override
            public void onResponseComplete() {
                final long startTime = finish();
                if (checkOnce(startTime)) {
                    tracker.onRequestSuccess(startTime);
                }
            }

            private long finish() {
                return START_TIME_UPDATER.getAndSet(this, Long.MAX_VALUE);
            }

            private boolean checkOnce(long startTime) {
                return startTime != Long.MAX_VALUE && startTime != Long.MIN_VALUE;
            }
        }
    }

    private static final class NoopGrpcRequestObserver implements GrpcLifecycleObserver.GrpcRequestObserver {
        @Override
        public void onRequestTrailers(HttpHeaders trailers) {
            // noop
        }

        @Override
        public void onRequestData(Buffer data) {
            // noop
        }

        @Override
        public void onRequestComplete() {
            // noop
        }

        @Override
        public void onRequestError(Throwable cause) {
            // noop
        }

        @Override
        public void onRequestCancel() {
            // noop
        }
    }
}
