package io.servicetalk.http.netty;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.DelegatingFilterableStreamingHttpLoadBalancedConnection;
import io.servicetalk.http.api.FilterableStreamingHttpLoadBalancedConnection;
import io.servicetalk.http.api.HttpLifecycleObserver;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.loadbalancer.RequestTracker;

final class HttpRequestTracker {

    private HttpRequestTracker() {
        // no instances
    }

    static FilterableStreamingHttpLoadBalancedConnection observe(RequestTracker requestTracker,
                                                                 FilterableStreamingHttpLoadBalancedConnection cxn) {
        Foo filter = new Foo(new Observer(requestTracker));
        return filter.observe(cxn);
    }

    private static class Observer implements HttpLifecycleObserver {

        private final RequestTracker tracker;

        Observer(RequestTracker tracker) {
            this.tracker = tracker;
        }

        @Override
        public HttpExchangeObserver onNewExchange() {
            return null;
        }
    }

    private static class Foo extends AbstractLifecycleObserverHttpFilter {
        Foo(HttpLifecycleObserver observer) {
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
}
