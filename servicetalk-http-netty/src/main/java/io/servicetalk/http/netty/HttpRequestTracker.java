package io.servicetalk.http.netty;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.DelegatingFilterableStreamingHttpLoadBalancedConnection;
import io.servicetalk.http.api.FilterableStreamingHttpLoadBalancedConnection;
import io.servicetalk.http.api.HttpLifecycleObserver;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.loadbalancer.ErrorClass;
import io.servicetalk.loadbalancer.RequestTracker;
import io.servicetalk.transport.api.ConnectionInfo;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

final class HttpRequestTracker {

    private HttpRequestTracker() {
        // no instances
    }

    static FilterableStreamingHttpLoadBalancedConnection observe(
            Function<HttpResponseMetaData, ErrorClass> peerResponseErrorClassifier,
            Function<Throwable, ErrorClass> errorClassFunction,
            RequestTracker requestTracker, FilterableStreamingHttpLoadBalancedConnection cxn) {
        HttpRequestLifecycleTracker filter = new HttpRequestLifecycleTracker(
                new Observer(peerResponseErrorClassifier, errorClassFunction, requestTracker));
        return filter.observe(cxn);
    }

    // We need to extend this class so we can get access to the `trackLifecycle` method. We could move it to a more
    // accessible place if we really wanted to.
    private static class HttpRequestLifecycleTracker extends AbstractLifecycleObserverHttpFilter {
        HttpRequestLifecycleTracker(HttpLifecycleObserver observer) {
            super(observer, true);
        }

        FilterableStreamingHttpLoadBalancedConnection observe(FilterableStreamingHttpLoadBalancedConnection delegate) {
            return new DelegatingFilterableStreamingHttpLoadBalancedConnection(delegate) {
                @Override
                public Single<StreamingHttpResponse> request(StreamingHttpRequest request) {
                    return trackLifecycle(delegate.connectionContext(), request, r -> delegate.request(r));
                }
            };
        }
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
