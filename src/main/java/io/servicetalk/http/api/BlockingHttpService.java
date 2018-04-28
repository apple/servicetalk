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

import io.servicetalk.transport.api.ConnectionContext;

import java.util.function.BiFunction;

/**
 * The equivalent of {@link HttpService} but with synchronous/blocking APIs instead of asynchronous APIs.
 * @param <I> Type of payload of a request handled by this service.
 * @param <O> Type of payload of a response handled by this service.
 */
public abstract class BlockingHttpService<I, O> implements AutoCloseable {
    /**
     * Handles a single HTTP request.
     * @param ctx Context of the service.
     * @param request to handle.
     * @return a {@link BlockingHttpResponse} which represents the HTTP response.
     * @throws Exception If an exception occurs during request processing.
     */
    public abstract BlockingHttpResponse<O> handle(ConnectionContext ctx, BlockingHttpRequest<I> request)
            throws Exception;

    @Override
    public void close() throws Exception {
        // noop
    }

    /**
     * Convert this {@link BlockingHttpService} to the {@link HttpService} asynchronous API.
     * <p>
     * Note that the resulting {@link HttpService} will still be subject to any blocking, in memory aggregation, and
     * other behavior as this {@link BlockingHttpService}.
     * @return a {@link HttpService} representation of this {@link BlockingHttpService}.
     */
    public final HttpService<I, O> asAsynchronousService() {
        return asAsynchronousServiceInternal();
    }

    /**
     * Provides a means to override the behavior of {@link #asAsynchronousService()} for internal classes.
     * @return a {@link HttpService} representation of this {@link BlockingHttpService}.
     */
    HttpService<I, O> asAsynchronousServiceInternal() {
        return new BlockingHttpServiceToHttpService<>(this);
    }

    /**
     * Create a new {@link BlockingHttpService} from a {@link BiFunction}.
     * @param handleFunc Provides the functionality for the {@link #handle(ConnectionContext, BlockingHttpRequest)}
     * method.
     * @param <I> Type of payload of a request handled by this service.
     * @param <O> Type of payload of a response handled by this service.
     * @return a new {@link BlockingHttpService}.
     */
    public static <I, O> BlockingHttpService<I, O> fromBlocking(BiFunction<ConnectionContext,
                                                                           BlockingHttpRequest<I>,
                                                                           BlockingHttpResponse<O>> handleFunc) {
        return new BlockingHttpService<I, O>() {
            @Override
            public BlockingHttpResponse<O> handle(final ConnectionContext ctx, final BlockingHttpRequest<I> request) {
                return handleFunc.apply(ctx, request);
            }
        };
    }
}
