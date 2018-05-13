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

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.transport.api.ExecutionContext;

import java.util.function.Function;

/**
 * Provides a means to make a HTTP request.
 * @param <I> The type of payload of the request.
 * @param <O> The type of payload of the response.
 */
public abstract class HttpRequester<I, O> implements ListenableAsyncCloseable {
    /**
     * Send a {@code request}.
     * @param request the request to send.
     * @return The response.
     */
    public abstract Single<HttpResponse<O>> request(HttpRequest<I> request);

    /**
     * Get the {@link ExecutionContext} used during construction of this object.
     * <p>
     * Note that the {@link ExecutionContext#getIoExecutor()} will not necessarily be associated with a specific thread
     * unless that was how this object was built.
     * @return the {@link ExecutionContext} used during construction of this object.
     */
    public abstract ExecutionContext getExecutionContext();

    /**
     * Convert this {@link HttpRequester} to the {@link BlockingHttpRequester} API.
     * <p>
     * This API is provided for convenience for a more familiar sequential programming model. It is recommended that
     * filters are implemented using the {@link HttpRequester} asynchronous API for maximum portability.
     * @return a {@link BlockingHttpRequester} representation of this {@link HttpRequester}.
     */
    public final BlockingHttpRequester<I, O> asBlockingRequester() {
        return asBlockingRequesterInternal();
    }

    /**
     * Convert this {@link HttpRequester} to the {@link AggregatedHttpRequester} API.
     * <p>
     * This API is provided for convenience. It is recommended that
     * filters are implemented using the {@link HttpRequester} asynchronous API for maximum portability.
     *
     * @param requestPayloadTransformer {@link Function} to convert an {@link HttpPayloadChunk} to {@link I}.
     * This is to make sure that we can use {@code this} {@link HttpRequester} for the returned
     * {@link AggregatedHttpRequester}. Use {@link Function#identity()} if {@code this} {@link HttpClient} already
     * handles {@link HttpPayloadChunk} for request payload.
     * @param responsePayloadTransformer {@link Function} to convert an {@link O} to {@link HttpPayloadChunk}.
     * This is to make sure that we can use {@code this} {@link HttpRequester} for the returned
     * {@link AggregatedHttpRequester}. Use {@link Function#identity()} if {@code this} {@link HttpRequester} already
     * returns response with {@link HttpPayloadChunk} as payload.
     * @return a {@link AggregatedHttpRequester} representation of this {@link HttpRequester}.
     */
    public final AggregatedHttpRequester asAggregatedRequester(Function<HttpPayloadChunk, I> requestPayloadTransformer,
                                                               Function<O, HttpPayloadChunk> responsePayloadTransformer) {
        HttpRequester<HttpPayloadChunk, HttpPayloadChunk> chunkRequester = new HttpRequester<HttpPayloadChunk, HttpPayloadChunk>() {
            @Override
            public Single<HttpResponse<HttpPayloadChunk>> request(final HttpRequest<HttpPayloadChunk> request) {
                return HttpRequester.this.request(request.transformPayloadBody(pubChunk -> pubChunk.map(requestPayloadTransformer)))
                        .map(resp -> resp.transformPayloadBody(pubChunk -> pubChunk.map(responsePayloadTransformer)));
            }

            @Override
            public ExecutionContext getExecutionContext() {
                return HttpRequester.this.getExecutionContext();
            }

            @Override
            public Completable onClose() {
                return HttpRequester.this.onClose();
            }

            @Override
            public Completable closeAsync() {
                return HttpRequester.this.closeAsync();
            }
        };

        return new AggregatedHttpRequester(chunkRequester);
    }

    BlockingHttpRequester<I, O> asBlockingRequesterInternal() {
        return new HttpRequesterToBlockingHttpRequester<>(this);
    }
}
