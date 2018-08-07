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
 * A service contract for the HTTP protocol.
 */
public abstract class HttpService implements AsyncCloseable {
    /**
     * Handles a single HTTP request.
     *
     * @param ctx Context of the service.
     * @param request to handle.
     * @return {@link Single} of HTTP response.
     */
    public abstract Single<HttpResponse<HttpPayloadChunk>> handle(ConnectionContext ctx,
                                                                  HttpRequest<HttpPayloadChunk> request);

    /**
     * Closes this {@link HttpService} asynchronously.
     *
     * @return {@link Completable} that when subscribed will close this {@link HttpService}.
     */
    @Override
    public Completable closeAsync() {
        return Completable.completed();
    }

    /**
     * Convert this {@link HttpService} to the {@link AggregatedHttpService} API.
     *
     * @return a {@link AggregatedHttpService} representation of this {@link HttpService}.
     */
    public final AggregatedHttpService asAggregatedService() {
        return asAggregatedServiceInternal();
    }

    /**
     * Convert this {@link HttpService} to the {@link BlockingHttpService} API.
     * <p>
     * This API is provided for convenience for a more familiar sequential programming model. It is recommended that
     * filters are implemented using the {@link HttpService} asynchronous API for maximum portability.
     * @return a {@link BlockingHttpService} representation of this {@link HttpService}.
     */
    public final BlockingHttpService asBlockingService() {
        return asBlockingServiceInternal();
    }

    /**
     * Convert this {@link HttpService} to the {@link BlockingAggregatedHttpService} API.
     * <p>
     * This API is provided for convenience for a more familiar sequential programming model. It is recommended that
     * filters are implemented using the {@link HttpService} asynchronous API for maximum portability.
     * @return a {@link BlockingAggregatedHttpService} representation of this {@link HttpService}.
     */
    public final BlockingAggregatedHttpService asBlockingAggregatedService() {
        return asBlockingAggregatedServiceInternal();
    }

    /**
     * Create a new {@link HttpService} from a {@link BiFunction}.
     * @param handleFunc Provides the functionality for the {@link #handle(ConnectionContext, HttpRequest)} method.
     * @return a new {@link HttpService}.
     */
    public static HttpService fromAsync(BiFunction<ConnectionContext,
                                                HttpRequest<HttpPayloadChunk>,
                                                Single<HttpResponse<HttpPayloadChunk>>> handleFunc) {
        return new HttpService() {
            @Override
            public Single<HttpResponse<HttpPayloadChunk>> handle(final ConnectionContext ctx,
                                                                 final HttpRequest<HttpPayloadChunk> request) {
                return handleFunc.apply(ctx, request);
            }
        };
    }

    AggregatedHttpService asAggregatedServiceInternal() {
        return new HttpServiceToAggregatedHttpService(this);
    }

    BlockingHttpService asBlockingServiceInternal() {
        return new HttpServiceToBlockingHttpService(this);
    }

    BlockingAggregatedHttpService asBlockingAggregatedServiceInternal() {
        return new HttpServiceToBlockingAggregatedHttpService(this);
    }
}
