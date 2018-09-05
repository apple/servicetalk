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

import java.util.function.BiFunction;

/**
 * The equivalent of {@link HttpService} but with synchronous/blocking APIs instead of asynchronous APIs.
 */
public abstract class BlockingHttpService implements AutoCloseable {
    /**
     * Handles a single HTTP request.
     *
     * @param ctx Context of the service.
     * @param request to handle.
     * @return {@link Single} of HTTP response.
     * @throws Exception If an exception occurs during request processing.
     */
    public abstract HttpResponse handle(HttpServiceContext ctx, HttpRequest request) throws Exception;

    @Override
    public void close() throws Exception {
        // noop
    }

    /**
     * Convert this {@link BlockingHttpService} to the {@link StreamingHttpService} API.
     * <p>
     * This API is provided for convenience for a more familiar sequential programming model. It is recommended that
     * filters are implemented using the {@link StreamingHttpService} asynchronous API for maximum portability.
     *
     * @return a {@link StreamingHttpService} representation of this {@link BlockingHttpService}.
     */
    public final StreamingHttpService asStreamingService() {
        return asStreamingServiceInternal();
    }

    /**
     * Convert this {@link BlockingHttpService} to the {@link HttpService} API.
     * <p>
     * This API is provided for convenience for a more familiar sequential programming model. It is recommended that
     * filters are implemented using the {@link StreamingHttpService} asynchronous API for maximum portability.
     *
     * @return a {@link HttpService} representation of this {@link BlockingHttpService}.
     */
    public final HttpService asService() {
        return asServiceInternal();
    }

    /**
     * Convert this {@link BlockingHttpService} to the {@link BlockingStreamingHttpService} API.
     * <p>
     * This API is provided for convenience for a more familiar sequential programming model. It is recommended that
     * filters are implemented using the {@link StreamingHttpService} asynchronous API for maximum portability.
     *
     * @return a {@link BlockingStreamingHttpService} representation of this {@link BlockingHttpService}.
     */
    public final BlockingStreamingHttpService asBlockingStreamingService() {
        return asStreamingService().asBlockingStreamingService();
    }

    /**
     * Create a new {@link StreamingHttpService} from a {@link BiFunction}.
     * @param handleFunc Provides the functionality for the {@link #handle(HttpServiceContext, HttpRequest)}
     * method.
     * @return a new {@link BlockingHttpService}.
     */
    public static BlockingHttpService from(BiFunction<HttpServiceContext, HttpRequest, HttpResponse> handleFunc) {
        return new BlockingHttpService() {
            @Override
            public HttpResponse handle(final HttpServiceContext ctx, final HttpRequest request) {
                return handleFunc.apply(ctx, request);
            }
        };
    }

    StreamingHttpService asStreamingServiceInternal() {
        return new BlockingHttpServiceToStreamingHttpService(this);
    }

    HttpService asServiceInternal() {
        return new BlockingHttpServiceToHttpService(this);
    }
}
