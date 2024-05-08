/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.apple.traffic.resilience.http;

import io.servicetalk.apple.capacity.limiter.api.CapacityLimiter.Ticket;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TerminalSignalConsumer;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;
import io.servicetalk.http.utils.BeforeFinallyHttpOperator;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static io.servicetalk.concurrent.api.Single.defer;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

/**
 * A filter that tracks number of pending requests.
 * We would like to check the hypothesis if we somehow loose the {@link Ticket} inside
 * {@link AbstractTrafficManagementHttpFilter} or if the counter is misaligned for a different reason, like incorrect
 * handling of terminal events by {@link BeforeFinallyHttpOperator}.
 */
final class TrackPendingRequestsHttpFilter implements StreamingHttpClientFilterFactory,
                                                      StreamingHttpServiceFilterFactory {

    private enum Position {
        BEFORE, AFTER
    }

    static final TrackPendingRequestsHttpFilter BEFORE = new TrackPendingRequestsHttpFilter(Position.BEFORE);
    static final TrackPendingRequestsHttpFilter AFTER = new TrackPendingRequestsHttpFilter(Position.AFTER);

    private final Position position;

    private TrackPendingRequestsHttpFilter(final Position position) {
        this.position = position;
    }

    @Override
    public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
        return new TrackPendingRequestsHttpClientFilter(client, position);
    }

    @Override
    public StreamingHttpServiceFilter create(final StreamingHttpService service) {
        return new TrackPendingRequestsHttpServiceFilter(service, position);
    }

    @Override
    public HttpExecutionStrategy requiredOffloads() {
        return HttpExecutionStrategies.offloadNone();
    }

    private static final class TrackPendingRequestsHttpClientFilter extends StreamingHttpClientFilter {

        private static final AtomicIntegerFieldUpdater<TrackPendingRequestsHttpClientFilter> pendingUpdater =
                newUpdater(TrackPendingRequestsHttpClientFilter.class, "pending");

        private volatile int pending;
        private final Position position;

        TrackPendingRequestsHttpClientFilter(final FilterableStreamingHttpClient client, final Position position) {
            super(client);
            this.position = position;
        }

        @Override
        protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                        final StreamingHttpRequest request) {
            return defer(() -> {
                pendingUpdater.incrementAndGet(this);
                return delegate.request(request)
                        .liftSync(new BeforeFinallyHttpOperator(new TerminalSignalConsumer() {
                            @Override
                            public void onComplete() {
                                decrement();
                            }

                            @Override
                            public void onError(final Throwable throwable) {
                                decrement();
                            }

                            @Override
                            public void cancel() {
                                decrement();
                            }

                            private void decrement() {
                                pendingUpdater.decrementAndGet(TrackPendingRequestsHttpClientFilter.this);
                            }
                        }, true))
                        .shareContextOnSubscribe();
            });
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() +
                    "{pending=" + pending +
                    ", position=" + position +
                    '}';
        }
    }

    private static final class TrackPendingRequestsHttpServiceFilter extends StreamingHttpServiceFilter {

        private static final AtomicIntegerFieldUpdater<TrackPendingRequestsHttpServiceFilter> pendingUpdater =
                newUpdater(TrackPendingRequestsHttpServiceFilter.class, "pending");

        private volatile int pending;
        private final Position position;

        TrackPendingRequestsHttpServiceFilter(final StreamingHttpService service, final Position position) {
            super(service);
            this.position = position;
        }

        @Override
        public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                    final StreamingHttpRequest request,
                                                    final StreamingHttpResponseFactory responseFactory) {
            return defer(() -> {
                pendingUpdater.incrementAndGet(this);
                return delegate().handle(ctx, request, responseFactory)
                        .liftSync(new BeforeFinallyHttpOperator(new TerminalSignalConsumer() {
                            @Override
                            public void onComplete() {
                                decrement();
                            }

                            @Override
                            public void onError(final Throwable throwable) {
                                decrement();
                            }

                            @Override
                            public void cancel() {
                                decrement();
                            }

                            private void decrement() {
                                pendingUpdater.decrementAndGet(TrackPendingRequestsHttpServiceFilter.this);
                            }
                        }, true))
                        .shareContextOnSubscribe();
            });
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() +
                    "{pending=" + pending +
                    ", position=" + position +
                    '}';
        }
    }
}
