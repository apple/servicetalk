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
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.http.api.HttpLifecycleObserver;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;
import io.servicetalk.transport.api.ConnectionInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import static io.servicetalk.http.netty.HttpMessageDiscardWatchdogServiceFilter.MESSAGE_PUBLISHER_KEY;
import static io.servicetalk.http.netty.HttpMessageDiscardWatchdogServiceFilter.MESSAGE_SUBSCRIBED_KEY;

final class HttpMessageDiscardCleanerServiceLifecycleObserver implements HttpLifecycleObserver {

    private static final Logger LOGGER = LoggerFactory
            .getLogger(HttpMessageDiscardCleanerServiceLifecycleObserver.class);

    /**
     * Instance of {@link HttpMessageDiscardCleanerServiceLifecycleObserver}.
     */
    static final StreamingHttpServiceFilterFactory FILTER =
            new HttpLifecycleObserverServiceFilter(new HttpMessageDiscardCleanerServiceLifecycleObserver());

    /**
     * Helps to remember if we logged an error for user-defined filters already to not spam the logs.
     * <p>
     * NOTE: this variable is intentionally not volatile since thread visibility is not a concern, but repeated
     * volatile accesses are.
     */
    private static boolean loggedError;

    private HttpMessageDiscardCleanerServiceLifecycleObserver() {
        // Singleton
    }

    @Override
    public HttpExchangeObserver onNewExchange() {

        return new HttpExchangeObserver() {

            @Nullable
            private HttpRequestMetaData requestMetaData;

            @Override
            public HttpRequestObserver onRequest(final HttpRequestMetaData requestMetaData) {
                this.requestMetaData = requestMetaData;
                return NoopHttpLifecycleObserver.NoopHttpRequestObserver.INSTANCE;
            }

            @Override
            public HttpResponseObserver onResponse(final HttpResponseMetaData responseMetaData) {
                return NoopHttpLifecycleObserver.NoopHttpResponseObserver.INSTANCE;
            }

            @Override
            public void onExchangeFinally() {
                if (requestMetaData != null) {
                    final ContextMap requestContext = requestMetaData.context();
                    if (requestContext.get(MESSAGE_SUBSCRIBED_KEY) == null) {
                        // No-one subscribed to the message (or there is none), so if there is a message
                        // proactively clean it up.
                        Publisher<?> message = requestContext.get(MESSAGE_PUBLISHER_KEY);
                        if (message != null) {
                            if (!loggedError) {
                                LOGGER.error("Proactively cleaning up HTTP response message which has been " +
                                                "dropped - this is a strong indication of a bug in a user-defined " +
                                                "filter. Request: {}",
                                        requestMetaData);
                                loggedError = true;
                            }
                            message.ignoreElements().subscribe();
                        }
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
