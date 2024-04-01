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

import java.util.concurrent.atomic.AtomicLong;
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
            return new Observer.RequestTrackerExchangeObserver();
        }

        private final class RequestTrackerExchangeObserver implements GrpcLifecycleObserver.GrpcExchangeObserver,
                GrpcLifecycleObserver.GrpcResponseObserver {

            // TODO: cleanup.
            private final AtomicLong startTime = new AtomicLong(Long.MIN_VALUE);

            @Override
            public void onConnectionSelected(ConnectionInfo info) {
                // noop
            }

            @Override
            public GrpcLifecycleObserver.GrpcRequestObserver onRequest(HttpRequestMetaData requestMetaData) {
                startTime.set(tracker.beforeRequestStart());
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
                final long startTime = finish();
                if (checkOnce(startTime)) {
                    tracker.onRequestSuccess(startTime);
                }
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
                // noop: covered by `onExchangeFinally`.
            }

            private long finish() {
                return this.startTime.getAndSet(Long.MAX_VALUE);
            }

            private boolean checkOnce(long startTime) {
                return startTime != Long.MAX_VALUE && startTime != Long.MIN_VALUE;
            }
        }
    }

    private static final class NoopGrpcRequestObserver implements GrpcLifecycleObserver.GrpcRequestObserver {
        @Override
        public void onRequestTrailers(HttpHeaders trailers) {

        }

        @Override
        public void onRequestData(Buffer data) {

        }

        @Override
        public void onRequestComplete() {

        }

        @Override
        public void onRequestError(Throwable cause) {

        }

        @Override
        public void onRequestCancel() {

        }
    }
}
