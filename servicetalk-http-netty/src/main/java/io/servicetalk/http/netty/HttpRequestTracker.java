package io.servicetalk.http.netty;

import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpLifecycleObserver;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.loadbalancer.ErrorClass;
import io.servicetalk.loadbalancer.RequestTracker;
import io.servicetalk.transport.api.ConnectionInfo;

import java.util.concurrent.atomic.AtomicLong;
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
            return new RequestTrackerExchangeObserver();
        }

        private final class RequestTrackerExchangeObserver implements HttpLifecycleObserver.HttpExchangeObserver {

            // TODO: cleanup.
            private final AtomicLong startTime = new AtomicLong(Long.MIN_VALUE);

            @Override
            public void onConnectionSelected(ConnectionInfo info) {
                // noop
            }

            @Override
            public HttpLifecycleObserver.HttpRequestObserver onRequest(HttpRequestMetaData requestMetaData) {
                startTime.set(tracker.beforeRequestStart());
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
                return NoopHttpLifecycleObserver.NoopHttpResponseObserver.INSTANCE;
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

            private long finish() {
                return this.startTime.getAndSet(Long.MAX_VALUE);
            }

            private boolean checkOnce(long startTime) {
                return startTime != Long.MAX_VALUE && startTime != Long.MIN_VALUE;
            }
        }
    }
}
