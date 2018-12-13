/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.api;

import io.servicetalk.concurrent.api.Single;

import static java.util.Objects.requireNonNull;

/**
 * A factory for {@link StreamingHttpRequestHandler}.
 */
@FunctionalInterface
public interface HttpRequestHandlerFilterFactory {

    /**
     * Create another {@link StreamingHttpRequestHandler} using the provided {@link StreamingHttpRequestHandler}.
     *
     * @param handler {@link StreamingHttpRequestHandler} to filter
     * @return {@link StreamingHttpRequestHandler} using the provided {@link StreamingHttpRequestHandler}.
     */
    StreamingHttpRequestHandler create(StreamingHttpRequestHandler handler);

    /**
     * Returns a composed function that first applies the {@code before} function to its input, and then applies
     * this function to the result.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     builder.append(filter1).append(filter2).append(filter3)
     * </pre>
     * accepting a request by a service wrapped by this filter chain, the order of invocation of these filters will be:
     * <pre>
     *     filter1 =&gt; filter2 =&gt; filter3 =&gt; service
     * </pre>
     * @param before the function to apply before this function is applied
     * @return a composed function that first applies the {@code before}
     * function and then applies this function
     */
    default HttpRequestHandlerFilterFactory append(HttpRequestHandlerFilterFactory before) {
        requireNonNull(before);
        return service -> create(before.create(service));
    }

    /**
     * Converts this {@link HttpRequestHandlerFilterFactory} to an {@link HttpServiceFilterFactory}.
     *
     * @return This {@link HttpRequestHandlerFilterFactory} as a {@link HttpServiceFilterFactory}
     */
    default HttpServiceFilterFactory asServiceFilterFactory() {
        return service -> {
            StreamingHttpRequestHandler handler = create(service);
            // Make sure we delegate
            return new StreamingHttpServiceFilter(service) {
                @Override
                public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                            final StreamingHttpRequest request,
                                                            final StreamingHttpResponseFactory responseFactory) {
                    return handler.handle(ctx, request, responseFactory);
                }
            };
        };
    }

    /**
     * Returns a function that always returns its input {@link StreamingHttpRequestHandler}.
     *
     * @return a function that always returns its input {@link StreamingHttpRequestHandler}.
     */
    static HttpRequestHandlerFilterFactory identity() {
        return handler -> handler;
    }
}
