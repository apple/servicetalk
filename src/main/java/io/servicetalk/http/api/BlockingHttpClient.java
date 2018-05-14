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

import io.servicetalk.concurrent.api.BlockingIterable;
import io.servicetalk.http.api.BlockingHttpClientToHttpClient.BlockingToReservedHttpConnection;
import io.servicetalk.http.api.HttpClient.ReservedHttpConnection;

import java.util.function.Function;

/**
 * The equivalent of {@link HttpClient} but with synchronous/blocking APIs instead of asynchronous APIs.
 * @param <I> Type of payload of a request handled by this {@link BlockingHttpClient}.
 * @param <O> Type of payload of a response handled by this {@link BlockingHttpClient}.
 */
public abstract class BlockingHttpClient<I, O> extends BlockingHttpRequester<I, O> {
    /**
     * Reserve a {@link BlockingHttpConnection} for handling the provided {@link BlockingHttpRequest}
     * but <b>does not execute it</b>!
     * @param request Allows the underlying layers to know what {@link BlockingHttpConnection}s are valid to reserve.
     * For example this may provide some insight into shard or other info.
     * @return a {@link ReservedHttpConnection}.
     * @throws Exception if a exception occurs during the reservation process.
     */
    public abstract BlockingReservedHttpConnection<I, O> reserveConnection(BlockingHttpRequest<I> request)
            throws Exception;

    /**
     * Attempt a <a href="https://tools.ietf.org/html/rfc7230.html#section-6.7">protocol upgrade</a>.
     * As part of the <a href="https://tools.ietf.org/html/rfc7230.html#section-6.7">protocol upgrade</a> process there
     * cannot be any pipelined requests pending or any pipeline requests issued during the upgrade process. That means
     * the {@link BlockingHttpConnection} associated with the {@link BlockingUpgradableHttpResponse} will be reserved
     * for exclusive use. The code responsible for determining the result of the upgrade attempt is responsible for
     * calling {@link BlockingUpgradableHttpResponse#getHttpConnection(boolean)}.
     * @param request the request which initiates the upgrade.
     * @return An object that provides the {@link HttpResponse} for the upgrade attempt and also contains the
     * {@link BlockingHttpConnection} used for the upgrade.
     * @throws Exception if a exception occurs during the upgrade process.
     */
    public abstract BlockingUpgradableHttpResponse<I, O> upgradeConnection(BlockingHttpRequest<I> request)
            throws Exception;

    /**
     * Convert this {@link BlockingHttpClient} to the {@link HttpClient} asynchronous API.
     * <p>
     * Note that the resulting {@link HttpClient} will still be subject to any blocking, in memory aggregation, and
     * other behavior as this {@link BlockingHttpClient}.
     * @return a {@link HttpClient} representation of this {@link BlockingHttpClient}.
     */
    public final HttpClient<I, O> asAsynchronousClient() {
        return asAsynchronousClientInternal();
    }

    HttpClient<I, O> asAsynchronousClientInternal() {
        return new BlockingHttpClientToHttpClient<>(this);
    }

    /**
     * A special type of {@link BlockingHttpConnection} for the exclusive use of the caller of
     * {@link #reserveConnection(BlockingHttpRequest)}.
     * @param <I> The type of payload of the request.
     * @param <O> The type of payload of the response.
     */
    public abstract static class BlockingReservedHttpConnection<I, O> extends BlockingHttpConnection<I, O> {
        /**
         * Releases this reserved {@link BlockingHttpConnection} to be used for subsequent requests.
         * This method must be idempotent, i.e. calling multiple times must not have side-effects.
         * @throws Exception if any exception occurs during releasing.
         */
        public abstract void release() throws Exception;

        /**
         * Convert this {@link BlockingReservedHttpConnection} to the {@link ReservedHttpConnection} asynchronous API.
         * <p>
         * Note that the resulting {@link ReservedHttpConnection} will still be subject to any blocking, in memory
         * aggregation, and other behavior as this {@link BlockingReservedHttpConnection}.
         * @return a {@link ReservedHttpConnection} representation of this {@link BlockingReservedHttpConnection}.
         */
        public final ReservedHttpConnection<I, O> asAsynchronousReservedConnection() {
            return asAsynchronousReservedConnectionInternal();
        }

        ReservedHttpConnection<I, O> asAsynchronousReservedConnectionInternal() {
            return new BlockingToReservedHttpConnection<>(this);
        }
    }

    public interface BlockingUpgradableHttpResponse<I, O> extends BlockingHttpResponse<O> {
        /**
         * Called by the code responsible for processing the upgrade response.
         * <p>
         * The caller of this method is responsible for calling {@link BlockingReservedHttpConnection#release()} on the
         * return value!
         * @param releaseReturnsToClient
         * <ul>
         *     <li>{@code true} means the {@link BlockingHttpConnection} associated with the return value can be used by
         *     this {@link BlockingHttpClient} when {@link BlockingReservedHttpConnection#release()} is called. This
         *     typically means the upgrade attempt was unsuccessful, but you can continue talking HTTP. However this may
         *     also be used if the upgrade was successful, but the upgrade protocol shares semantics that are similar
         *     enough to HTTP that the same {@link BlockingHttpClient} API can still be used (e.g. HTTP/2).</li>
         *     <li>{@code false} means the {@link BlockingHttpConnection} associated with the return value can
         *     <strong>not</strong> be used by this {@link BlockingHttpClient} when
         *     {@link BlockingReservedHttpConnection#release()} is called. This typically means the upgrade attempt was
         *     successful and the semantics of the upgrade protocol are sufficiently different that the
         *     {@link BlockingHttpClient} API no longer makes sense.</li>
         * </ul>
         * @return A {@link BlockingReservedHttpConnection} which contains the {@link BlockingHttpConnection} used for
         * the upgrade attempt, and controls the lifetime of the {@link BlockingHttpConnection} relative to this
         * {@link BlockingHttpClient}.
         */
        BlockingReservedHttpConnection<I, O> getHttpConnection(boolean releaseReturnsToClient);

        @Override
        <R> BlockingUpgradableHttpResponse<I, R> transformPayloadBody(Function<BlockingIterable<O>,
                                                                               BlockingIterable<R>> transformer);

        @Override
        BlockingUpgradableHttpResponse<I, O> setVersion(HttpProtocolVersion version);

        @Override
        BlockingUpgradableHttpResponse<I, O> setStatus(HttpResponseStatus status);
    }
}
