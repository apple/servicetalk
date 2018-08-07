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
import io.servicetalk.transport.api.ConnectionContext;

import java.util.function.BiFunction;

/**
 * The equivalent of {@link AggregatedHttpService} but with synchronous/blocking APIs instead of asynchronous APIs.
 */
public abstract class BlockingAggregatedHttpService implements AutoCloseable {
    /**
     * Handles a single HTTP request.
     *
     * @param ctx Context of the service.
     * @param request to handle.
     * @return {@link Single} of HTTP response.
     * @throws Exception If an exception occurs during request processing.
     */
    public abstract AggregatedHttpResponse<HttpPayloadChunk> handle(ConnectionContext ctx,
                                                                    AggregatedHttpRequest<HttpPayloadChunk> request)
        throws Exception;

    @Override
    public void close() throws Exception {
        // noop
    }

    /**
     * Convert this {@link BlockingAggregatedHttpService} to the {@link HttpService} API.
     * <p>
     * This API is provided for convenience for a more familiar sequential programming model. It is recommended that
     * filters are implemented using the {@link HttpService} asynchronous API for maximum portability.
     *
     * @return a {@link HttpService} representation of this {@link BlockingAggregatedHttpService}.
     */
    public final HttpService asService() {
        return asServiceInternal();
    }

    /**
     * Convert this {@link BlockingAggregatedHttpService} to the {@link AggregatedHttpService} API.
     * <p>
     * This API is provided for convenience for a more familiar sequential programming model. It is recommended that
     * filters are implemented using the {@link HttpService} asynchronous API for maximum portability.
     *
     * @return a {@link AggregatedHttpService} representation of this {@link BlockingAggregatedHttpService}.
     */
    public final AggregatedHttpService asAggregatedService() {
        return asAggregatedServiceInternal();
    }

    /**
     * Convert this {@link BlockingAggregatedHttpService} to the {@link BlockingHttpService} API.
     * <p>
     * This API is provided for convenience for a more familiar sequential programming model. It is recommended that
     * filters are implemented using the {@link HttpService} asynchronous API for maximum portability.
     *
     * @return a {@link BlockingHttpService} representation of this {@link BlockingAggregatedHttpService}.
     */
    public final BlockingHttpService asBlockingService() {
        return asService().asBlockingService();
    }

    /**
     * Create a new {@link HttpService} from a {@link BiFunction}.
     * @param handleFunc Provides the functionality for the {@link #handle(ConnectionContext, AggregatedHttpRequest)}
     * method.
     * @return a new {@link BlockingAggregatedHttpService}.
     */
    public static BlockingAggregatedHttpService fromBlockingAggregated(BiFunction<ConnectionContext,
            AggregatedHttpRequest<HttpPayloadChunk>,
            AggregatedHttpResponse<HttpPayloadChunk>> handleFunc) {
        return new BlockingAggregatedHttpService() {
            @Override
            public AggregatedHttpResponse<HttpPayloadChunk> handle(final ConnectionContext ctx,
                                                             final AggregatedHttpRequest<HttpPayloadChunk> request) {
                return handleFunc.apply(ctx, request);
            }
        };
    }

    HttpService asServiceInternal() {
        return new BlockingAggregatedHttpServiceToHttpService(this);
    }

    AggregatedHttpService asAggregatedServiceInternal() {
        return new BlockingAggregatedHttpServiceToAggregatedHttpService(this);
    }
}
