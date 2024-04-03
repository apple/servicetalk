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

import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.function.Function;

final class GrpcRequestTracker {

    private static final GrpcLifecycleObserver.GrpcRequestObserver NOOP_REQUEST_OBSERVER =
            new NoopGrpcRequestObserver();

    private GrpcRequestTracker() {
        // no instances
    }

    static FilterableStreamingHttpConnection observe(
            Function<GrpcStatus, ErrorClass> peerResponseErrorClassifier,
            Function<Throwable, ErrorClass> errorClassFunction,
            RequestTracker requestTracker, FilterableStreamingHttpConnection connection) {
        HttpLifecycleObserverRequesterFilter filter = new GrpcLifecycleObserverRequesterFilter(
                new Observer(peerResponseErrorClassifier, errorClassFunction, requestTracker));
        return filter.create(connection);
    }

    private static class Observer implements GrpcLifecycleObserver {

        private final Function<GrpcStatus, ErrorClass> peerResponseErrorClassifier;
        private final Function<Throwable, ErrorClass> errorClassFunction;
        private final RequestTracker tracker;

        Observer(final Function<GrpcStatus, ErrorClass> peerResponseErrorClassifier,
                 final Function<Throwable, ErrorClass> errorClassFunction, final RequestTracker tracker) {
            this.peerResponseErrorClassifier = peerResponseErrorClassifier;
            this.errorClassFunction = errorClassFunction;
            this.tracker = tracker;
        }

        @Override
        public GrpcExchangeObserver onNewExchange() {
            return new Observer.RequestTrackerExchangeObserver(
                    peerResponseErrorClassifier, errorClassFunction, tracker);
        }

        private static final class RequestTrackerExchangeObserver implements GrpcLifecycleObserver.GrpcExchangeObserver,
                GrpcLifecycleObserver.GrpcResponseObserver {

            private static final AtomicLongFieldUpdater<RequestTrackerExchangeObserver> START_TIME_UPDATER =
                    AtomicLongFieldUpdater.newUpdater(RequestTrackerExchangeObserver.class, "startTime");

            private final Function<GrpcStatus, ErrorClass> peerResponseErrorClassifier;
            private final Function<Throwable, ErrorClass> errorClassFunction;
            private final RequestTracker tracker;
            @SuppressWarnings("unused")
            private volatile long startTime = Long.MIN_VALUE;

            RequestTrackerExchangeObserver(final Function<GrpcStatus, ErrorClass> peerResponseErrorClassifier,
                    final Function<Throwable, ErrorClass> errorClassFunction, final RequestTracker tracker) {
                this.peerResponseErrorClassifier = peerResponseErrorClassifier;
                this.errorClassFunction = errorClassFunction;
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

            @Override
            public void onExchangeFinally() {
                // noop
            }

            @Override
            public void onGrpcStatus(GrpcStatus status) {
                ErrorClass error = peerResponseErrorClassifier.apply(status);
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
