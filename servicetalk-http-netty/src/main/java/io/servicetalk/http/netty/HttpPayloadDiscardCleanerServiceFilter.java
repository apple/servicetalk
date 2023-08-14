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

import javax.annotation.Nullable;

import static io.servicetalk.http.netty.HttpPayloadDiscardWatchdogServiceFilter.payloadPublisherKey;
import static io.servicetalk.http.netty.HttpPayloadDiscardWatchdogServiceFilter.payloadSubscribedKey;

final class HttpPayloadDiscardCleanerServiceFilter implements StreamingHttpServiceFilterFactory, HttpLifecycleObserver {

    /**
     * Instance of {@link HttpPayloadDiscardCleanerServiceFilter}.
     */
    static final StreamingHttpServiceFilterFactory INSTANCE = new HttpPayloadDiscardCleanerServiceFilter();

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpPayloadDiscardCleanerServiceFilter.class);

    private HttpPayloadDiscardCleanerServiceFilter() {
        // Singleton
    }

    @Override
    public StreamingHttpServiceFilter create(final StreamingHttpService service) {

        final WatchdogHttpLifecycleObserver observer = new WatchdogHttpLifecycleObserver(this, false);

        return new StreamingHttpServiceFilter(service) {
            @Override
            public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                        final StreamingHttpRequest request,
                                                        final StreamingHttpResponseFactory responseFactory) {
                return observer.trackLifecycle(ctx, request, r -> delegate().handle(ctx, r, responseFactory));
            }

            @Override
            public HttpExecutionStrategy requiredOffloads() {
                return HttpExecutionStrategies.offloadNone();
            }
        };
    }

    private static class WatchdogHttpLifecycleObserver extends AbstractLifecycleObserverHttpFilter {
        WatchdogHttpLifecycleObserver(final HttpLifecycleObserver observer, final boolean client) {
            super(observer, client);
        }
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
                    if (requestContext.get(payloadSubscribedKey) == null) {
                        // No-one subscribed to the payload (or there is none), so if there is a payload
                        // proactively clean it up.
                        Publisher<?> payload = requestContext.get(payloadPublisherKey);
                        if (payload != null) {
                            LOGGER.debug("Proactively cleaning up HTTP response payload which has been dropped - " +
                                            "this is a strong indication of a bug in a filter. Request: {}",
                                    requestMetaData);
                            payload.ignoreElements().subscribe();
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

            private void tryCleanPayload() {

            }
        };
    }
}
