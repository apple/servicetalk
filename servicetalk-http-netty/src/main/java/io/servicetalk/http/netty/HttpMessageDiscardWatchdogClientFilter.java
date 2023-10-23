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

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

import static io.servicetalk.http.netty.HttpMessageDiscardWatchdogServiceFilter.generifyAtomicReference;

/**
 * Filter which tracks HTTP responses and makes sure that if an exception is raised during filter pipeline
 * processing message payload bodies are cleaned up.
 */
final class HttpMessageDiscardWatchdogClientFilter implements StreamingHttpConnectionFilterFactory {

    private static final ContextMap.Key<AtomicReference<Publisher<?>>> MESSAGE_PUBLISHER_KEY = ContextMap.Key
            .newKey(HttpMessageDiscardWatchdogClientFilter.class.getName() + ".messagePublisher",
                    generifyAtomicReference());

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpMessageDiscardWatchdogClientFilter.class);

    /**
     * Instance of {@link HttpMessageDiscardWatchdogClientFilter}.
     */
    static final HttpMessageDiscardWatchdogClientFilter INSTANCE = new HttpMessageDiscardWatchdogClientFilter();

    /**
     * Instance of {@link StreamingHttpClientFilterFactory} with the cleaner implementation.
     */
    static final StreamingHttpClientFilterFactory CLIENT_CLEANER = new CleanerStreamingHttpClientFilterFactory();

    private HttpMessageDiscardWatchdogClientFilter() {
        // Singleton
    }

    @Override
    public StreamingHttpConnectionFilter create(final FilterableStreamingHttpConnection connection) {
        return new StreamingHttpConnectionFilter(connection) {
            @Override
            public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
                return delegate().request(request).map(response -> {
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
                                "in a user-defined filter. Response payload (message) body must " +
                                "be fully consumed before retrying.");
                    }

                    return response.transformMessageBody(msgPublisher -> msgPublisher.beforeSubscriber(() -> {
                        reference.set(null);
                        return HttpMessageDiscardWatchdogServiceFilter.NoopSubscriber.INSTANCE;
                    }));
                });
            }
        };
    }

    @Override
    public HttpExecutionStrategy requiredOffloads() {
        return HttpExecutionStrategies.offloadNone();
    }

    private static final class CleanerStreamingHttpClientFilterFactory implements StreamingHttpClientFilterFactory {
        @Override
        public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
            return new StreamingHttpClientFilter(client) {
                @Override
                protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                                final StreamingHttpRequest request) {
                    return delegate
                            .request(request)
                            .onErrorResume(cause -> {
                                final AtomicReference<?> maybePublisher = request.context().get(MESSAGE_PUBLISHER_KEY);
                                if (maybePublisher != null && maybePublisher.getAndSet(null) != null) {
                                    // No-one subscribed to the message (or there is none), so if there is a message
                                    // tell the user to clean it up.
                                    LOGGER.warn("Discovered un-drained HTTP response message body which has " +
                                            "been dropped by user code - this is a strong indication of a bug " +
                                            "in a user-defined filter. Response payload (message) body must " +
                                            "be fully consumed before discarding.");
                                }
                                return Single.<StreamingHttpResponse>failed(cause).shareContextOnSubscribe();
                            });
                }
            };
        }

        @Override
        public HttpExecutionStrategy requiredOffloads() {
            return HttpExecutionStrategies.offloadNone();
        }
    }
}
