/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.examples.grpc.context;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.context.api.ContextMap.Key;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
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

import static io.servicetalk.buffer.api.CharSequences.newAsciiString;
import static io.servicetalk.context.api.ContextMap.Key.newKey;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadNone;

/**
 * HTTP filters that translate information between request/response context and request/response HTTP headers.
 */
public final class HttpContextFilters {

    public static final Key<CharSequence> USER_ID_KEY = newKey("user-id-key", CharSequence.class);
    private static final CharSequence USER_ID_HEADER = newAsciiString("user-id-header");

    private HttpContextFilters() {
        // No instances
    }

    public static StreamingHttpClientFilterFactory clientFilter() {
        return ClientFilter.INSTANCE;
    }

    public static StreamingHttpServiceFilterFactory serviceFilter() {
        return ServiceFilter.INSTANCE;
    }

    private static final class ClientFilter implements StreamingHttpClientFilterFactory {

        static final StreamingHttpClientFilterFactory INSTANCE = new ClientFilter();

        private ClientFilter() {
            // Singleton
        }

        @Override
        public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
            return new StreamingHttpClientFilter(client) {
                @Override
                protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                                final StreamingHttpRequest request) {
                    // Single.defer(...) helps to make sure the header is set on every retry
                    return Single.defer(() -> {
                        // gRPC request context can be accessed via HTTP request context:
                        CharSequence userId = request.context().get(USER_ID_KEY);
                        if (userId != null) {
                            request.setHeader(USER_ID_HEADER, userId);
                        }
                        return delegate.request(request).whenOnSuccess(response -> {
                            CharSequence responseUserId = response.headers().get(USER_ID_HEADER);
                            if (responseUserId != null) {
                                // HTTP response context will be translated to gRPC response context.
                                // If necessary, the entire HttpHeaders object can be put into the context.
                                response.context().put(USER_ID_KEY, responseUserId);
                            }
                        }).shareContextOnSubscribe();
                    });
                }
            };
        }

        @Override
        public HttpExecutionStrategy requiredOffloads() {
            return offloadNone();   // This filter doesn't block
        }
    }

    private static final class ServiceFilter implements StreamingHttpServiceFilterFactory {

        static final StreamingHttpServiceFilterFactory INSTANCE = new ServiceFilter();

        private ServiceFilter() {
            // Singleton
        }

        @Override
        public StreamingHttpServiceFilter create(final StreamingHttpService service) {
            return new StreamingHttpServiceFilter(service) {
                @Override
                public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                            final StreamingHttpRequest request,
                                                            final StreamingHttpResponseFactory responseFactory) {
                    CharSequence userId = request.headers().get(USER_ID_HEADER);
                    if (userId != null) {
                        // HTTP request context will be translated to gRPC request context.
                        // If necessary, the entire HttpHeaders object can be put into the context.
                        request.context().put(USER_ID_KEY, userId);
                    }
                    return delegate().handle(ctx, request, responseFactory).whenOnSuccess(response -> {
                        // gRPC response context can be accessed via HTTP response context:
                        CharSequence responseUserId = response.context().get(USER_ID_KEY);
                        if (responseUserId != null) {
                            response.setHeader(USER_ID_HEADER, responseUserId);
                        }
                    });
                }
            };
        }

        @Override
        public HttpExecutionStrategy requiredOffloads() {
            return offloadNone();   // This filter doesn't block
        }
    }
}
