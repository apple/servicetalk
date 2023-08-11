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
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class HttpPayloadDiscardWatchdogServiceFilter implements StreamingHttpServiceFilterFactory {

    /**
     * Instance of {@link HttpPayloadDiscardWatchdogServiceFilter}.
     */
    public static final StreamingHttpServiceFilterFactory INSTANCE = new HttpPayloadDiscardWatchdogServiceFilter();

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpPayloadDiscardWatchdogServiceFilter.class);

    static final ContextMap.Key<Publisher> payloadPublisherKey = ContextMap.Key
            .newKey("io.servicetalk.http.netty.payloadPublisher", Publisher.class);

    static final ContextMap.Key<Boolean> payloadSubscribedKey = ContextMap.Key
            .newKey("io.servicetalk.http.netty.payloadSubscribed", Boolean.class);

    private HttpPayloadDiscardWatchdogServiceFilter() {
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
                        .handle(ctx, request, responseFactory)
                        .map(response -> {
                            request.context().put(payloadPublisherKey, response.payloadBody());
                            return response.transformPayloadBody(bufferPublisher ->
                                    bufferPublisher.beforeOnSubscribe(subscription ->
                                            request.context().put(payloadSubscribedKey, true)));
                        });
            }

            @Override
            public HttpExecutionStrategy requiredOffloads() {
                return HttpExecutionStrategies.offloadNone();
            }
        };
    }
}
