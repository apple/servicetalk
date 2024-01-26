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

package io.servicetalk.loadbalancer;

import io.servicetalk.client.api.ReservableRequestConcurrencyController;
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.SingleSource.Subscriber;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.FilterableStreamingHttpLoadBalancedConnection;
import io.servicetalk.http.api.HttpConnectionContext;
import io.servicetalk.http.api.HttpEventKey;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.ReservedBlockingHttpConnection;
import io.servicetalk.http.api.ReservedBlockingStreamingHttpConnection;
import io.servicetalk.http.api.ReservedHttpConnection;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.HttpApiConversions.toReservedBlockingConnection;
import static io.servicetalk.http.api.HttpApiConversions.toReservedBlockingStreamingConnection;
import static io.servicetalk.http.api.HttpApiConversions.toReservedConnection;
import static io.servicetalk.loadbalancer.ErrorClass.LOCAL_ORIGIN_REQUEST_FAILED;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

final class DefaultHttpLoadBalancedConnection
        implements FilterableStreamingHttpLoadBalancedConnection {
    private final RootSwayingLeafRequestTracker tracker;
    private final Function<HttpResponseMetaData, ErrorClass> responseErrorMapper;
    private final FilterableStreamingHttpConnection delegate;
    private final ReservableRequestConcurrencyController concurrencyController;

    DefaultHttpLoadBalancedConnection(final Function<HttpResponseMetaData, ErrorClass> responseErrorMapper,
                                      final FilterableStreamingHttpConnection delegate,
                                      final ReservableRequestConcurrencyController concurrencyController,
                                      final DefaultHost parent,
                                      final Duration requestLatencyHalfLife) {
        this(responseErrorMapper, delegate, concurrencyController,
                new RootSwayingLeafRequestTracker(requireNonNull(parent).healthIndicator(),
                        new DefaultAddressLatencyRequestTracker(requestLatencyHalfLife.toNanos())));
    }

    DefaultHttpLoadBalancedConnection(final Function<HttpResponseMetaData, ErrorClass> responseErrorMapper,
                                      final FilterableStreamingHttpConnection delegate,
                                      final ReservableRequestConcurrencyController concurrencyController,
                                      final RootSwayingLeafRequestTracker tracker) {
        this.responseErrorMapper = responseErrorMapper;
        this.delegate = delegate;
        this.tracker = tracker;
        this.concurrencyController = concurrencyController;
    }

    @Override
    public int score() {
        return tracker.score();
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
        return delegate.request(request)
                .liftSync(target -> new StreamingHttpResponseSubscriber(target, this));
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

    private static final class StreamingHttpResponseSubscriber implements Subscriber<StreamingHttpResponse> {
        private static final AtomicIntegerFieldUpdater<StreamingHttpResponseSubscriber> doneUpdater =
                newUpdater(StreamingHttpResponseSubscriber.class, "done");
        private final Subscriber<? super StreamingHttpResponse> target;
        private final DefaultHttpLoadBalancedConnection conn;
        private final long startTime;

        private volatile int done;

        StreamingHttpResponseSubscriber(final Subscriber<? super StreamingHttpResponse> target,
                                        final DefaultHttpLoadBalancedConnection conn) {
            this.target = target;
            this.conn = conn;
            startTime = conn.tracker.beforeStart();
        }

        @Override
        public void onSubscribe(final Cancellable cancellable) {
            target.onSubscribe(() -> {
                try {
                    if (doneUpdater.compareAndSet(this, 0, 1)) {
                        conn.tracker.onError(startTime, ErrorClass.CANCELLED);
                    }
                } finally {
                    cancellable.cancel();
                }
            });
        }

        @Override
        public void onSuccess(@Nullable final StreamingHttpResponse result) {
            try {
                if (doneUpdater.compareAndSet(this, 0, 1)) {
                    final ErrorClass eClass = conn.responseErrorMapper.apply(result);
                    if (eClass != null) {
                        conn.tracker.onError(startTime, eClass);
                    } else {
                        conn.tracker.onSuccess(startTime);
                    }
                }
            } catch (Throwable t) {
                target.onError(t);
                return;
            }
            target.onSuccess(result);
        }

        @Override
        public void onError(final Throwable t) {
            try {
                if (doneUpdater.compareAndSet(this, 0, 1)) {
                    conn.tracker.onError(startTime, LOCAL_ORIGIN_REQUEST_FAILED);
                }
            } catch (Throwable tt) {
                target.onError(tt);
                return;
            }
            target.onError(t);
        }
    }
}
