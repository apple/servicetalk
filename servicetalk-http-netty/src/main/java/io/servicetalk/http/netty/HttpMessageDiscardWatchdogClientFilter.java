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

import static io.servicetalk.http.netty.HttpMessageDiscardWatchdogFilter.USE_PHANTOM_REFERENCE;
import static io.servicetalk.http.netty.HttpMessageDiscardWatchdogServiceFilter.generifyAtomicReference;

/**
 * Filter which tracks message bodies and warns if they are not discarded properly.
 */
final class HttpMessageDiscardWatchdogClientFilter {

    private static final ContextMap.Key<AtomicReference<Publisher<?>>> MESSAGE_PUBLISHER_KEY = ContextMap.Key
            .newKey(HttpMessageDiscardWatchdogClientFilter.class.getName() + ".messagePublisher",
                    generifyAtomicReference());

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpMessageDiscardWatchdogClientFilter.class);

    /**
     * Instance of {@link HttpMessageDiscardWatchdogClientFilter}.
     */
    static final StreamingHttpConnectionFilterFactory INSTANCE = USE_PHANTOM_REFERENCE ?
            new PhantomReferenceConnectionFilterFactory() : new AtomicRefFilter();

    /**
     * Instance of {@link StreamingHttpClientFilterFactory} with the cleaner implementation.
     */
    static final StreamingHttpClientFilterFactory CLIENT_CLEANER = USE_PHANTOM_REFERENCE ?
            new NoopCleanerFilterFactory() : new CleanerStreamingHttpClientFilterFactory();

    private HttpMessageDiscardWatchdogClientFilter() {
        // no instances
    }

    // This implementation uses an atomic reference stored on the context to track when requests have been leaked.
    // It is efficient and doesn't depend on GC, but may be prone to false positives since its possible that the
    // message body was going to be drained later in the program.
    private static final class AtomicRefFilter implements StreamingHttpConnectionFilterFactory {
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
                            LOGGER.warn(HttpMessageDiscardWatchdogFilter.WARN_MESSAGE);
                        }

                        return response.transformMessageBody(msgPublisher -> msgPublisher.beforeSubscriber(() -> {
                            reference.set(null);
                            return HttpMessageDiscardWatchdogServiceFilter.NoopSubscriber.INSTANCE;
                        }));
                    });
                }
            };
        }
    }

    // A PhantomReference based version of the watchdog filter. This implementation doesn't suffer from the false
    // positives that can happen with the AtomicReference based system which allows it to drain the response, plugging
    // the leak. However, it does rely on GC and thus comes with all the caveats associated with that.
    private static final class PhantomReferenceConnectionFilterFactory implements StreamingHttpConnectionFilterFactory {

        private PhantomReferenceConnectionFilterFactory() {
            // singleton.
        }

        @Override
        public StreamingHttpConnectionFilter create(FilterableStreamingHttpConnection connection) {
            return new StreamingHttpConnectionFilter(connection) {
                @Override
                public Single<StreamingHttpResponse> request(StreamingHttpRequest request) {
                    return delegate().request(request).map(response ->
                            HttpMessageDiscardWatchdogFilterCleaner.instrument(LOGGER, response));
                }
            };
        }

        @Override
        public HttpExecutionStrategy requiredOffloads() {
            return HttpExecutionStrategies.offloadNone();
        }
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
                                    LOGGER.warn(HttpMessageDiscardWatchdogFilter.WARN_MESSAGE);
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

    private static final class NoopCleanerFilterFactory implements StreamingHttpClientFilterFactory {
        @Override
        public StreamingHttpClientFilter create(FilterableStreamingHttpClient client) {
            return new StreamingHttpClientFilter(client) { };
        }

        @Override
        public HttpExecutionStrategy requiredOffloads() {
            return HttpExecutionStrategies.offloadNone();
        }
    }
}
