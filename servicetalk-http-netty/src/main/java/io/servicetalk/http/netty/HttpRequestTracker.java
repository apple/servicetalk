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
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpLifecycleObserver;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.loadbalancer.ErrorClass;
import io.servicetalk.loadbalancer.RequestTracker;
import io.servicetalk.transport.api.ConnectionInfo;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.function.Function;

final class HttpRequestTracker {

    private HttpRequestTracker() {
        // no instances
    }

    static FilterableStreamingHttpConnection observe(
            Function<HttpResponseMetaData, ErrorClass> peerResponseErrorClassifier,
            Function<Throwable, ErrorClass> errorClassFunction,
            RequestTracker requestTracker, FilterableStreamingHttpConnection connection) {
        HttpLifecycleObserverRequesterFilter filter = new HttpLifecycleObserverRequesterFilter(
                new Observer(peerResponseErrorClassifier, errorClassFunction, requestTracker));
        return filter.create(connection);
    }

    private static class Observer implements HttpLifecycleObserver {

        private final Function<HttpResponseMetaData, ErrorClass> peerResponseErrorClassifier;
        private final Function<Throwable, ErrorClass> errorClassFunction;
        private final RequestTracker tracker;

        Observer(final Function<HttpResponseMetaData, ErrorClass> peerResponseErrorClassifier,
                 final Function<Throwable, ErrorClass> errorClassFunction, final RequestTracker tracker) {
            this.peerResponseErrorClassifier = peerResponseErrorClassifier;
            this.errorClassFunction = errorClassFunction;
            this.tracker = tracker;
        }

        @Override
        public HttpExchangeObserver onNewExchange() {
            return new RequestTrackerExchangeObserver(peerResponseErrorClassifier, errorClassFunction, tracker);
        }

        private static final class RequestTrackerExchangeObserver implements HttpLifecycleObserver.HttpExchangeObserver,
                HttpLifecycleObserver.HttpResponseObserver {

            private static final AtomicLongFieldUpdater<RequestTrackerExchangeObserver> START_TIME_UPDATER =
                    AtomicLongFieldUpdater.newUpdater(RequestTrackerExchangeObserver.class, "startTime");

            private final Function<HttpResponseMetaData, ErrorClass> peerResponseErrorClassifier;
            private final Function<Throwable, ErrorClass> errorClassFunction;
            private final RequestTracker tracker;
            @SuppressWarnings("unused")
            private volatile long startTime = Long.MIN_VALUE;

            RequestTrackerExchangeObserver(
                    final Function<HttpResponseMetaData, ErrorClass> peerResponseErrorClassifier,
                    final Function<Throwable, ErrorClass> errorClassFunction, final RequestTracker tracker) {
                this.peerResponseErrorClassifier = peerResponseErrorClassifier;
                this.errorClassFunction = errorClassFunction;
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
                ErrorClass error = peerResponseErrorClassifier.apply(responseMetaData);
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
                    tracker.onRequestError(startTime, errorClassFunction.apply(cause));
                }
            }

            @Override
            public void onResponseCancel() {
                final long startTime = finish();
                if (checkOnce(startTime)) {
                    tracker.onRequestError(startTime, ErrorClass.CANCELLED);
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
}
