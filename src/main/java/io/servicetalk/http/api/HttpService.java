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
import java.util.function.Function;

import static io.servicetalk.http.api.DefaultFullHttpRequest.toHttpRequest;
import static io.servicetalk.http.api.DefaultFullHttpResponse.from;

/**
 * A service contract for the HTTP protocol.
 * @param <I> Type of payload of a request handled by this service.
 * @param <O> Type of payload of a response handled by this service.
 */
public abstract class HttpService<I, O> implements AsyncCloseable {
    /**
     * Handles a single HTTP request.
     *
     * @param ctx Context of the service.
     * @param request to handle.
     * @return {@link Single} of HTTP response.
     */
    public abstract Single<HttpResponse<O>> handle(ConnectionContext ctx, HttpRequest<I> request);

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
     * Convert this {@link HttpService} to the {@link BlockingHttpService} API.
     * <p>
     * This API is provided for convenience for a more familiar sequential programming model. It is recommended that
     * filters are implemented using the {@link HttpService} asynchronous API for maximum portability.
     * @return a {@link BlockingHttpService} representation of this {@link HttpService}.
     */
    public final BlockingHttpService<I, O> asBlockingService() {
        return asBlockingServiceInternal();
    }

    /**
     * Convert this {@link HttpService} to the {@link AggregatedHttpService} API.
     * <p>
     * This API is provided for convenience for a more familiar sequential programming model. It is recommended that
     * filters are implemented using the {@link HttpService} asynchronous API for maximum portability.
     * @param requestPayloadTransformer {@link Function} to convert an {@link HttpPayloadChunk} to {@link I}.
     * This is to make sure that we can use {@code this} {@link HttpService} for the returned
     * {@link AggregatedHttpService}. Use {@link Function#identity()} if {@code this} {@link HttpService} already
     * handles {@link HttpPayloadChunk} for request payload.
     * @param responsePayloadTransformer {@link Function} to convert an {@link O} to {@link HttpPayloadChunk}.
     * This is to make sure that we can use {@code this} {@link HttpService} for the returned
     * {@link AggregatedHttpService}. Use {@link Function#identity()} if {@code this} {@link HttpService} already
     * returns response with {@link HttpPayloadChunk} as payload.
     * @return a {@link AggregatedHttpService} representation of this {@link HttpService}.
     */
    public final AggregatedHttpService asAggregatedService(Function<HttpPayloadChunk, I> requestPayloadTransformer,
                                                           Function<O, HttpPayloadChunk> responsePayloadTransformer) {
        return new AggregatedHttpService() {
            @Override
            public Single<FullHttpResponse> handle(final ConnectionContext ctx, final FullHttpRequest fullHttpRequest) {
                final HttpRequest<I> req = toHttpRequest(fullHttpRequest)
                        .transformPayloadBody(chunkSrc -> chunkSrc.map(requestPayloadTransformer));
                return HttpService.this.handle(ctx, req)
                        .flatMap(r -> from(r.transformPayloadBody(chunkSrc -> chunkSrc.map(responsePayloadTransformer)),
                        ctx.getBufferAllocator()));
            }
        };
    }

    /**
     * Provides a means to override the behavior of {@link #asBlockingService()} for internal classes.
     * @return a {@link BlockingHttpService} representation of this {@link HttpService}.
     */
    BlockingHttpService<I, O> asBlockingServiceInternal() {
        return new HttpServiceToBlockingHttpService<>(this);
    }

    /**
     * Create a new {@link HttpService} from a {@link BiFunction}.
     * @param handleFunc Provides the functionality for the {@link #handle(ConnectionContext, HttpRequest)} method.
     * @param <I> Type of payload of a request handled by this service.
     * @param <O> Type of payload of a response handled by this service.
     * @return a new {@link HttpService}.
     */
    public static <I, O> HttpService<I, O> fromAsync(BiFunction<ConnectionContext,
                                                                HttpRequest<I>,
                                                                Single<HttpResponse<O>>> handleFunc) {
        return new HttpService<I, O>() {
            @Override
            public Single<HttpResponse<O>> handle(final ConnectionContext ctx, final HttpRequest<I> request) {
                return handleFunc.apply(ctx, request);
            }
        };
    }
}
