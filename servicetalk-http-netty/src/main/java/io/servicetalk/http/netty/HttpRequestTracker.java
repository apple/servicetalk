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
package io.servicetalk.http.netty;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.client.api.ConnectionFactoryFilter;
import io.servicetalk.client.api.DelegatingConnectionFactory;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpLifecycleObserver;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.loadbalancer.RequestTracker;
import io.servicetalk.transport.api.ConnectionInfo;
import io.servicetalk.transport.api.ExecutionStrategy;
import io.servicetalk.transport.api.TransportObserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.SERVER_ERROR_5XX;
import static io.servicetalk.http.api.HttpResponseStatus.TOO_MANY_REQUESTS;
import static io.servicetalk.loadbalancer.RequestTracker.ErrorClass.EXT_ORIGIN_TIMEOUT;
import static io.servicetalk.loadbalancer.RequestTracker.ErrorClass.LOCAL_ORIGIN_REQUEST_FAILED;
import static io.servicetalk.loadbalancer.RequestTracker.REQUEST_TRACKER_KEY;

final class HttpRequestTracker {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpRequestTracker.class);

    private HttpRequestTracker() {
        // no instances
    }

    static <ResolvedAddress> ConnectionFactoryFilter<ResolvedAddress, FilterableStreamingHttpConnection> filter() {
        return new ConnectionFactoryFilterImpl<>();
    }

    private static final class ConnectionFactoryFilterImpl<ResolvedAddress>
            implements ConnectionFactoryFilter<ResolvedAddress, FilterableStreamingHttpConnection> {

        @Override
        public ConnectionFactory<ResolvedAddress, FilterableStreamingHttpConnection> create(
                ConnectionFactory<ResolvedAddress, FilterableStreamingHttpConnection> original) {
            return new RequestTrackerConnectionFactory<>(original);
        }

        @Override
        public ExecutionStrategy requiredOffloads() {
            return ExecutionStrategy.offloadNone();
        }
    }

    private static final class RequestTrackerConnectionFactory<ResolvedAddress>
            extends DelegatingConnectionFactory<ResolvedAddress, FilterableStreamingHttpConnection> {

        RequestTrackerConnectionFactory(
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
                        HttpRequestTracker.class.getSimpleName(), RequestTracker.class.getSimpleName());
            } else {
                result = result.map(connection -> transformConnection(connection, context));
            }
            return result;
        }
    }

    private static FilterableStreamingHttpConnection transformConnection(
            FilterableStreamingHttpConnection connection, ContextMap context) {
        RequestTracker requestTracker = context.remove(REQUEST_TRACKER_KEY);
        if (requestTracker == null) {
            LOGGER.debug("{} is not set in context. In order for {} to get access to the {}" +
                    ", health-monitor of this connection, the context must be properly wired.",
                    REQUEST_TRACKER_KEY.name(), HttpRequestTracker.class.getSimpleName(),
                    RequestTracker.class.getSimpleName());
            return connection;
        } else {
            LOGGER.debug("Added request tracker to connection {}.", connection.connectionContext());
            HttpLifecycleObserverRequesterFilter filter = new HttpLifecycleObserverRequesterFilter(
                    new Observer(requestTracker));
            return filter.create(connection);
        }
    }

    private static class Observer implements HttpLifecycleObserver {
        private final RequestTracker tracker;

        Observer(final RequestTracker tracker) {
            this.tracker = tracker;
        }

        @Override
        public HttpExchangeObserver onNewExchange() {
            return new RequestTrackerExchangeObserver(tracker);
        }

        private static final class RequestTrackerExchangeObserver implements HttpLifecycleObserver.HttpExchangeObserver,
                HttpLifecycleObserver.HttpResponseObserver {

            private static final AtomicLongFieldUpdater<RequestTrackerExchangeObserver> START_TIME_UPDATER =
                    AtomicLongFieldUpdater.newUpdater(RequestTrackerExchangeObserver.class, "startTime");

            private final RequestTracker tracker;
            @SuppressWarnings("unused")
            private volatile long startTime = Long.MIN_VALUE;

            RequestTrackerExchangeObserver(final RequestTracker tracker) {
                this.tracker = tracker;
            }

            // HttpExchangeObserver methods

            @Override
            public void onConnectionSelected(ConnectionInfo info) {
                // noop
            }

            @Override
            public HttpLifecycleObserver.HttpRequestObserver onRequest(HttpRequestMetaData requestMetaData) {
                START_TIME_UPDATER.set(this, tracker.beforeRequestStart());
                return NoopHttpLifecycleObserver.NoopHttpRequestObserver.INSTANCE;
            }

            @Override
            public HttpLifecycleObserver.HttpResponseObserver onResponse(HttpResponseMetaData responseMetaData) {
                RequestTracker.ErrorClass error = classifyResponse(responseMetaData);
                if (error != null) {
                    final long startTime = finish();
                    if (checkOnce(startTime)) {
                        tracker.onRequestError(startTime, error);
                    }
                }
                return this;
            }

            @Override
            public void onExchangeFinally() {
                // noop
            }

            // Shared interface methods

            @Override
            public void onResponseError(Throwable cause) {
                final long startTime = finish();
                if (checkOnce(startTime)) {
                    tracker.onRequestError(startTime, classifyThrowable(cause));
                }
            }

            @Override
            public void onResponseCancel() {
                final long startTime = finish();
                if (checkOnce(startTime)) {
                    tracker.onRequestError(startTime, RequestTracker.ErrorClass.CANCELLED);
                }
            }

            // HttpResponseObserver methods

            @Override
            public void onResponseData(Buffer data) {
                // noop
            }

            @Override
            public void onResponseTrailers(HttpHeaders trailers) {
                // noop
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

    @Nullable
    private static RequestTracker.ErrorClass classifyResponse(HttpResponseMetaData resp) {
        return (resp.status().statusClass() == SERVER_ERROR_5XX || TOO_MANY_REQUESTS.equals(resp.status())) ?
                        RequestTracker.ErrorClass.EXT_ORIGIN_REQUEST_FAILED : null;
    }

    private static RequestTracker.ErrorClass classifyThrowable(Throwable error) {
        return error instanceof TimeoutException ? EXT_ORIGIN_TIMEOUT : LOCAL_ORIGIN_REQUEST_FAILED;
    }
}
