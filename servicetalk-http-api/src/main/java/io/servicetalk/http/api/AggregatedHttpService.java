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

import io.servicetalk.concurrent.api.AsyncCloseable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.transport.api.ConnectionContext;

import java.util.function.BiFunction;

/**
 * Same as {@link HttpService} but that accepts {@link AggregatedHttpRequest} and returns {@link AggregatedHttpResponse}.
 */
public abstract class AggregatedHttpService implements AsyncCloseable {
    /**
     * Handles a single HTTP request.
     *
     * @param ctx Context of the service.
     * @param request to handle.
     * @return {@link Single} of HTTP response.
     */
    public abstract Single<AggregatedHttpResponse<HttpPayloadChunk>> handle(ConnectionContext ctx,
                                                                    AggregatedHttpRequest<HttpPayloadChunk> request);

    /**
     * Closes this {@link AggregatedHttpService} asynchronously.
     *
     * @return {@link Completable} that when subscribed will close this {@link AggregatedHttpService}.
     */
    @Override
    public Completable closeAsync() {
        return Completable.completed();
    }

    /**
     * Convert this {@link AggregatedHttpService} to the {@link HttpService} API.
     * <p>
     * This API is provided for convenience for a more familiar sequential programming model. It is recommended that
     * filters are implemented using the {@link HttpService} asynchronous API for maximum portability.
     *
     * @return a {@link HttpService} representation of this {@link AggregatedHttpService}.
     */
    public final HttpService asService() {
        return asServiceInternal();
    }

    /**
     * Convert this {@link AggregatedHttpService} to the {@link BlockingHttpService} API.
     * <p>
     * This API is provided for convenience for a more familiar sequential programming model. It is recommended that
     * filters are implemented using the {@link HttpService} asynchronous API for maximum portability.
     *
     * @return a {@link BlockingHttpService} representation of this {@link AggregatedHttpService}.
     */
    public final BlockingHttpService asBlockingService() {
        return asService().asBlockingService();
    }

    /**
     * Convert this {@link AggregatedHttpService} to the {@link BlockingAggregatedHttpService} API.
     * <p>
     * This API is provided for convenience for a more familiar sequential programming model. It is recommended that
     * filters are implemented using the {@link HttpService} asynchronous API for maximum portability.
     *
     * @return a {@link BlockingAggregatedHttpService} representation of this {@link AggregatedHttpService}.
     */
    public final BlockingAggregatedHttpService asBlockingAggregatedService() {
        return asBlockingAggregatedServiceInternal();
    }

    /**
     * Create a new {@link AggregatedHttpService} from a {@link BiFunction}.
     *
     * @param handleFunc Provides the functionality for the {@link #handle(ConnectionContext, AggregatedHttpRequest)} method.
     * @return a new {@link AggregatedHttpService}.
     */
    public static AggregatedHttpService fromAggregated(BiFunction<ConnectionContext,
            AggregatedHttpRequest<HttpPayloadChunk>,
                            Single<AggregatedHttpResponse<HttpPayloadChunk>>> handleFunc) {
        return new AggregatedHttpService() {
            @Override
            public Single<AggregatedHttpResponse<HttpPayloadChunk>> handle(final ConnectionContext ctx,
                                                                           final AggregatedHttpRequest<HttpPayloadChunk> request) {
                return handleFunc.apply(ctx, request);
            }
        };
    }

    HttpService asServiceInternal() {
        return new AggregatedHttpServiceToHttpService(this);
    }

    BlockingAggregatedHttpService asBlockingAggregatedServiceInternal() {
        return new AggregatedHttpServiceToBlockingAggregatedHttpService(this);
    }
}
