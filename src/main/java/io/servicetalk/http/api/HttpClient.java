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
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.BlockingHttpClient.BlockingReservedHttpConnection;
import io.servicetalk.http.api.HttpClientToBlockingHttpClient.ReservedHttpConnectionToBlocking;
import io.servicetalk.transport.api.ExecutionContext;

import java.util.function.Function;

/**
 * Provides a means to issue requests against HTTP service. The implementation is free to maintain a collection of
 * {@link HttpConnection} instances and distribute calls to {@link #request(HttpRequest)} amongst this collection.
 * @param <I> The type of payload of the request.
 * @param <O> The type of payload of the response.
 */
public abstract class HttpClient<I, O> extends HttpRequester<I, O> {
    /**
     * Reserve a {@link HttpConnection} for handling the provided {@link HttpRequest}
     * but <b>does not execute it</b>!
     * @param request Allows the underlying layers to know what {@link HttpConnection}s are valid to reserve.
     * For example this may provide some insight into shard or other info.
     * @return a {@link ReservedHttpConnection}.
     */
    public abstract Single<? extends ReservedHttpConnection<I, O>> reserveConnection(HttpRequest<I> request);

    /**
     * Attempt a <a href="https://tools.ietf.org/html/rfc7230.html#section-6.7">protocol upgrade</a>.
     * As part of the <a href="https://tools.ietf.org/html/rfc7230.html#section-6.7">protocol upgrade</a> process there
     * cannot be any pipelined requests pending or any pipeline requests issued during the upgrade process. That means
     * the {@link HttpConnection} associated with the {@link UpgradableHttpResponse} will be reserved for exclusive use.
     * The code responsible for determining the result of the upgrade attempt is responsible for calling
     * {@link UpgradableHttpResponse#getHttpConnection(boolean)}.
     * @param request the request which initiates the upgrade.
     * @return An object that provides the {@link HttpResponse} for the upgrade attempt and also contains the
     * {@link HttpConnection} used for the upgrade.
     */
    public abstract Single<? extends UpgradableHttpResponse<I, O>> upgradeConnection(HttpRequest<I> request);

    /**
     * Convert this {@link HttpClient} to the {@link BlockingHttpClient} API.
     * <p>
     * This API is provided for convenience for a more familiar sequential programming model. It is recommended that
     * filters are implemented using the {@link HttpClient} asynchronous API for maximum portability.
     * @return a {@link BlockingHttpClient} representation of this {@link HttpClient}.
     */
    public final BlockingHttpClient<I, O> asBlockingClient() {
        return asBlockingClientInternal();
    }

    /**
     * Convert this {@link HttpClient} to the {@link AggregatedHttpClient} API.
     * <p>
     * This API is provided for convenience. It is recommended that
     * filters are implemented using the {@link HttpClient} asynchronous API for maximum portability.
     *
     * @param requestPayloadTransformer {@link Function} to convert an {@link HttpPayloadChunk} to {@link I}.
     * This is to make sure that we can use {@code this} {@link HttpClient} for the returned
     * {@link AggregatedHttpClient}. Use {@link Function#identity()} if {@code this} {@link HttpClient} already
     * handles {@link HttpPayloadChunk} for request payload.
     * @param responsePayloadTransformer {@link Function} to convert an {@link O} to {@link HttpPayloadChunk}.
     * This is to make sure that we can use {@code this} {@link HttpClient} for the returned
     * {@link AggregatedHttpClient}. Use {@link Function#identity()} if {@code this} {@link HttpClient} already
     * returns response with {@link HttpPayloadChunk} as payload.
     * @return a {@link AggregatedHttpClient} representation of this {@link HttpRequester}.
     */
    public final AggregatedHttpClient asAggregatedClient(Function<HttpPayloadChunk, I> requestPayloadTransformer,
                                                         Function<O, HttpPayloadChunk> responsePayloadTransformer) {
        HttpClient<HttpPayloadChunk, HttpPayloadChunk> chunkClient = new HttpClient<HttpPayloadChunk, HttpPayloadChunk>() {
            @Override
            public Single<? extends ReservedHttpConnection<HttpPayloadChunk, HttpPayloadChunk>> reserveConnection(final HttpRequest<HttpPayloadChunk> request) {
                //TODO: Support upgrade and reserve for aggregated client.
                return Single.error(new UnsupportedOperationException("Reserve not supported for aggregated client."));
            }

            @Override
            public Single<? extends UpgradableHttpResponse<HttpPayloadChunk, HttpPayloadChunk>> upgradeConnection(final HttpRequest<HttpPayloadChunk> request) {
                //TODO: Support upgrade and reserve for aggregated client.
                return Single.error(new UnsupportedOperationException("Upgrade not supported for aggregated client."));
            }

            @Override
            public Single<HttpResponse<HttpPayloadChunk>> request(final HttpRequest<HttpPayloadChunk> request) {
                return HttpClient.this.request(request.transformPayloadBody(pubChunk -> pubChunk.map(requestPayloadTransformer)))
                        .map(resp -> resp.transformPayloadBody(pubChunk -> pubChunk.map(responsePayloadTransformer)));
            }

            @Override
            public ExecutionContext getExecutionContext() {
                return HttpClient.this.getExecutionContext();
            }

            @Override
            public Completable onClose() {
                return HttpClient.this.onClose();
            }

            @Override
            public Completable closeAsync() {
                return HttpClient.this.closeAsync();
            }
        };

        return new AggregatedHttpClient(chunkClient);
    }

    BlockingHttpClient<I, O> asBlockingClientInternal() {
        return new HttpClientToBlockingHttpClient<>(this);
    }

    /**
     * A special type of {@link HttpConnection} for the exclusive use of the caller of
     * {@link #reserveConnection(HttpRequest)}.
     * @param <I> The type of payload of the request.
     * @param <O> The type of payload of the response.
     */
    public abstract static class ReservedHttpConnection<I, O> extends HttpConnection<I, O> {
        /**
         * Releases this reserved {@link HttpConnection} to be used for subsequent requests.
         * This method must be idempotent, i.e. calling multiple times must not have side-effects.
         *
         * @return the {@code Completable} that is notified on releaseAsync.
         */
        public abstract Completable releaseAsync();

        /**
         * Convert this {@link HttpClient} to the {@link BlockingHttpClient} API.
         * <p>
         * This API is provided for convenience for a more familiar sequential programming model. It is recommended that
         * filters are implemented using the {@link HttpClient} asynchronous API for maximum portability.
         * @return a {@link BlockingHttpClient} representation of this {@link HttpClient}.
         */
        public final BlockingReservedHttpConnection<I, O> asBlockingReservedConnection() {
            return asBlockingReservedConnectionInternal();
        }

        BlockingReservedHttpConnection<I, O> asBlockingReservedConnectionInternal() {
            return new ReservedHttpConnectionToBlocking<>(this);
        }
    }

    /**
     * A special type of response returned by upgrade requests {@link #upgradeConnection(HttpRequest)}. This objects
     * allows the upgrade code to inform the HTTP implementation if the {@link HttpConnection} can continue using the
     * HTTP protocol or not.
     * @param <I> The type of payload of the request.
     * @param <O> The type of payload of the response.
     */
    public interface UpgradableHttpResponse<I, O> extends HttpResponse<O> {
        /**
         * Called by the code responsible for processing the upgrade response.
         * <p>
         * The caller of this method is responsible for calling {@link ReservedHttpConnection#releaseAsync()} on the
         * return value!
         * @param releaseReturnsToClient
         * <ul>
         *     <li>{@code true} means the {@link HttpConnection} associated with the return value can be used by this
         *     {@link HttpClient} when {@link ReservedHttpConnection#releaseAsync()} is called. This typically means the
         *     upgrade attempt was unsuccessful, but you can continue talking HTTP. However this may also be used if the
         *     upgrade was successful, but the upgrade protocol shares semantics that are similar enough to HTTP that
         *     the same {@link HttpClient} API can still be used (e.g. HTTP/2).</li>
         *     <li>{@code false} means the {@link HttpConnection} associated with the return value can
         *     <strong>not</strong> be used by this {@link HttpClient} when
         *     {@link ReservedHttpConnection#releaseAsync()} is called. This typically means the upgrade attempt was
         *     successful and the semantics of the upgrade protocol are sufficiently different that the
         *     {@link HttpClient} API no longer makes sense.</li>
         * </ul>
         * @return A {@link ReservedHttpConnection} which contains the {@link HttpConnection} used for the upgrade
         * attempt, and controls the lifetime of the {@link HttpConnection} relative to this {@link HttpClient}.
         */
        ReservedHttpConnection<I, O> getHttpConnection(boolean releaseReturnsToClient);

        @Override
        <R> UpgradableHttpResponse<I, R> transformPayloadBody(Function<Publisher<O>, Publisher<R>> transformer);
    }
}
