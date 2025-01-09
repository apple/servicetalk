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
package io.servicetalk.http.netty;

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpLifecycleObserver;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;
import io.servicetalk.transport.api.ConnectionInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

/**
 * Filter which tracks message bodies and warns if they are not discarded properly.
 */
final class HttpMessageDiscardWatchdogServiceFilter implements StreamingHttpServiceFilterFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpMessageDiscardWatchdogServiceFilter.class);

    /**
     * Instance of {@link HttpMessageDiscardWatchdogServiceFilter}.
     */
    static final StreamingHttpServiceFilterFactory INSTANCE = new HttpMessageDiscardWatchdogServiceFilter();

    /**
     * Instance of {@link HttpLifecycleObserverServiceFilter} with the cleaner implementation.
     */
    static final StreamingHttpServiceFilterFactory CLEANER =
            new HttpLifecycleObserverServiceFilter(new CleanerHttpLifecycleObserver());

    private static final ContextMap.Key<AtomicReference<Publisher<?>>> MESSAGE_PUBLISHER_KEY = ContextMap.Key
            .newKey(HttpMessageDiscardWatchdogServiceFilter.class.getName() + ".messagePublisher",
                    generifyAtomicReference());

    private HttpMessageDiscardWatchdogServiceFilter() {
        // Singleton
    }

    @Override
    public StreamingHttpServiceFilter create(final StreamingHttpService service) {

        return new StreamingHttpServiceFilter(service) {
            @Override
            public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                        final StreamingHttpRequest request,
                                                        final StreamingHttpResponseFactory responseFactory) {
                return delegate()
                        .handle(ctx, request.transformMessageBody(LeakDetection::instrument), responseFactory)
                        .map(response -> {
                            // always write the buffer publisher into the request context. When a downstream subscriber
                            // arrives, mark the message as subscribed explicitly (having a message present and no
                            // subscription is an indicator that it must be freed later on).
                            final AtomicReference<Publisher<?>> reference = request.context()
                                    .computeIfAbsent(MESSAGE_PUBLISHER_KEY, key -> new AtomicReference<>());
                            assert reference != null;
                            if (reference.getAndSet(response.messageBody()) != null) {
                                // If a previous message exists, the Single<StreamingHttpResponse> got resubscribed to
                                // (i.e. during a retry) and so previous message body needs to be cleaned up by the
                                // user.
                                LOGGER.warn("Discovered un-drained HTTP response message body which has " +
                                        "been dropped by user code - this is a strong indication of a bug " +
                                        "in a user-defined filter. Responses (or their message body) must " +
                                        "be fully consumed before retrying.");
                            }

                            return response.transformMessageBody(msgPublisher -> msgPublisher.beforeSubscriber(() -> {
                                reference.set(null);
                                return NoopSubscriber.INSTANCE;
                            }));
                        });
            }
        };
    }

    @Override
    public HttpExecutionStrategy requiredOffloads() {
        return HttpExecutionStrategies.offloadNone();
    }

    @SuppressWarnings("unchecked")
    static <T> Class<T> generifyAtomicReference() {
        return (Class<T>) AtomicReference.class;
    }

    static final class NoopSubscriber implements PublisherSource.Subscriber<Object> {

        static final NoopSubscriber INSTANCE = new NoopSubscriber();

        private NoopSubscriber() {
            // Singleton
        }

        @Override
        public void onSubscribe(final PublisherSource.Subscription subscription) {
        }

        @Override
        public void onNext(@Nullable final Object o) {
        }

        @Override
        public void onError(final Throwable t) {
        }

        @Override
        public void onComplete() {
        }
    }

    /**
     * This {@link HttpLifecycleObserver} works in combination with the
     * {@link HttpMessageDiscardWatchdogServiceFilter} to track and clean up message bodies which have been discarded
     * by user filters.
     */
    private static final class CleanerHttpLifecycleObserver implements HttpLifecycleObserver {

        private CleanerHttpLifecycleObserver() {
            // Singleton
        }

        @Override
        public HttpExchangeObserver onNewExchange() {

            return new HttpExchangeObserver() {

                @Nullable
                private ContextMap requestContext;

                @Override
                public HttpRequestObserver onRequest(final HttpRequestMetaData requestMetaData) {
                    this.requestContext = requestMetaData.context();
                    return NoopHttpLifecycleObserver.NoopHttpRequestObserver.INSTANCE;
                }

                @Override
                public HttpResponseObserver onResponse(final HttpResponseMetaData responseMetaData) {
                    return NoopHttpLifecycleObserver.NoopHttpResponseObserver.INSTANCE;
                }

                @Override
                public void onExchangeFinally() {
                    if (requestContext != null) {
                        final AtomicReference<?> maybePublisher = requestContext.get(MESSAGE_PUBLISHER_KEY);
                        if (maybePublisher != null && maybePublisher.get() != null) {
                            // No-one subscribed to the message (or there is none), so if there is a message
                            // tell the user to clean it up.
                            LOGGER.warn("Discovered un-drained HTTP response message body which has " +
                                    "been dropped by user code - this is a strong indication of a bug " +
                                    "in a user-defined filter. Responses (or their message body) must " +
                                    "be fully consumed before discarding.");
                        }
                    }
                }

                @Override
                public void onConnectionSelected(final ConnectionInfo info) {
                }

                @Override
                public void onResponseError(final Throwable cause) {
                }

                @Override
                public void onResponseCancel() {
                }
            };
        }
    }
}
