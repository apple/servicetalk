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

import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.http.api.AggregatedHttpClient.AggregatedReservedHttpConnection;
import io.servicetalk.http.api.BlockingAggregatedHttpClient.BlockingAggregatedReservedHttpConnection;
import io.servicetalk.http.api.BlockingHttpClientToHttpClient.BlockingToReservedHttpConnection;
import io.servicetalk.http.api.HttpClient.ReservedHttpConnection;
import io.servicetalk.http.api.HttpClient.UpgradableHttpResponse;

import java.util.function.Function;

/**
 * The equivalent of {@link HttpClient} but with synchronous/blocking APIs instead of asynchronous APIs.
 */
public abstract class BlockingHttpClient extends BlockingHttpRequester {
    /**
     * Reserve a {@link BlockingHttpConnection} for handling the provided {@link BlockingHttpRequest}
     * but <b>does not execute it</b>!
     *
     * @param request Allows the underlying layers to know what {@link BlockingHttpConnection}s are valid to reserve.
     * For example this may provide some insight into shard or other info.
     * @return a {@link ReservedHttpConnection}.
     * @throws Exception if a exception occurs during the reservation process.
     * @see HttpClient#reserveConnection(HttpRequest)
     */
    public abstract BlockingReservedHttpConnection reserveConnection(BlockingHttpRequest<HttpPayloadChunk> request)
            throws Exception;

    /**
     * Attempt a <a href="https://tools.ietf.org/html/rfc7230.html#section-6.7">protocol upgrade</a>.
     * As part of the <a href="https://tools.ietf.org/html/rfc7230.html#section-6.7">protocol upgrade</a> process there
     * cannot be any pipelined requests pending or any pipeline requests issued during the upgrade process. That means
     * the {@link BlockingHttpConnection} associated with the {@link BlockingUpgradableHttpResponse} will be reserved
     * for exclusive use. The code responsible for determining the result of the upgrade attempt is responsible for
     * calling {@link BlockingUpgradableHttpResponse#getHttpConnection(boolean)}.
     *
     * @param request the request which initiates the upgrade.
     * @return An object that provides the {@link HttpResponse} for the upgrade attempt and also contains the
     * {@link BlockingHttpConnection} used for the upgrade.
     * @throws Exception if a exception occurs during the upgrade process.
     * @see HttpClient#upgradeConnection(HttpRequest)
     */
    public abstract BlockingUpgradableHttpResponse<HttpPayloadChunk> upgradeConnection(
            BlockingHttpRequest<HttpPayloadChunk> request) throws Exception;

    /**
     * Convert this {@link BlockingHttpClient} to the {@link HttpClient} API.
     * <p>
     * Note that the resulting {@link HttpClient} may still be subject to any blocking, in memory aggregation, and
     * other behavior as this {@link BlockingHttpClient}.
     *
     * @return a {@link HttpClient} representation of this {@link BlockingHttpClient}.
     */
    public final HttpClient asClient() {
        return asClientInternal();
    }

    /**
     * Convert this {@link BlockingHttpClient} to the {@link AggregatedHttpClient} API.
     * <p>
     * Note that the resulting {@link AggregatedHttpClient} may still be subject to any blocking, in memory aggregation, and
     * other behavior as this {@link BlockingHttpClient}.
     *
     * @return a {@link AggregatedHttpClient} representation of this {@link BlockingHttpClient}.
     */
    public final AggregatedHttpClient asAggregatedClient() {
        return asClient().asAggregatedClient();
    }

    /**
     * Convert this {@link BlockingHttpClient} to the {@link BlockingAggregatedHttpClient} API.
     * <p>
     * Note that the resulting {@link BlockingAggregatedHttpClient} may still be subject to in memory
     * aggregation and other behavior as this {@link BlockingHttpClient}.
     *
     * @return a {@link BlockingAggregatedHttpClient} representation of this {@link BlockingHttpClient}.
     */
    public final BlockingAggregatedHttpClient asBlockingAggregatedClient() {
        return asClient().asBlockingAggregatedClient();
    }

    HttpClient asClientInternal() {
        return new BlockingHttpClientToHttpClient(this);
    }

    /**
     * A special type of {@link BlockingHttpConnection} for the exclusive use of the caller of
     * {@link #reserveConnection(BlockingHttpRequest)}.
     * @see ReservedHttpConnection
     */
    public abstract static class BlockingReservedHttpConnection extends BlockingHttpConnection {
        /**
         * Releases this reserved {@link BlockingHttpConnection} to be used for subsequent requests.
         * This method must be idempotent, i.e. calling multiple times must not have side-effects.
         *
         * @throws Exception if any exception occurs during releasing.
         */
        public abstract void release() throws Exception;

        /**
         * Convert this {@link BlockingReservedHttpConnection} to the {@link ReservedHttpConnection} API.
         * <p>
         * Note that the resulting {@link ReservedHttpConnection} may still be subject to any blocking, in memory
         * aggregation, and other behavior as this {@link BlockingReservedHttpConnection}.
         *
         * @return a {@link ReservedHttpConnection} representation of this {@link BlockingReservedHttpConnection}.
         */
        public final ReservedHttpConnection asReservedConnection() {
            return asConnectionInternal();
        }

        /**
         * Convert this {@link BlockingReservedHttpConnection} to the {@link AggregatedReservedHttpConnection} API.
         * <p>
         * Note that the resulting {@link AggregatedReservedHttpConnection} may still be subject to any blocking, in
         * memory aggregation, and other behavior as this {@link BlockingReservedHttpConnection}.
         *
         * @return a {@link AggregatedReservedHttpConnection} representation of this
         * {@link BlockingReservedHttpConnection}.
         */
        public final AggregatedReservedHttpConnection asAggregatedReservedConnection() {
            return asReservedConnection().asAggregatedReservedConnection();
        }

        /**
         * Convert this {@link BlockingReservedHttpConnection} to the {@link BlockingAggregatedReservedHttpConnection}
         * API.
         * <p>
         * Note that the resulting {@link BlockingAggregatedReservedHttpConnection} may still be subject to in memory
         * aggregation and other behavior as this {@link BlockingReservedHttpConnection}.
         *
         * @return a {@link BlockingAggregatedReservedHttpConnection} representation of this
         * {@link BlockingReservedHttpConnection}.
         */
        public final BlockingAggregatedReservedHttpConnection asBlockingAggregatedReservedConnection() {
            return asReservedConnection().asBlockingAggregatedReservedConnection();
        }

        @Override
        ReservedHttpConnection asConnectionInternal() {
            return new BlockingToReservedHttpConnection(this);
        }
    }

    /**
     * A special type of response returned by upgrade requests {@link #upgradeConnection(BlockingHttpRequest)}. This
     * object allows the upgrade code to inform the HTTP implementation if the {@link AggregatedHttpConnection} can
     * continue using the HTTP protocol or not.
     * @param <T> The type of data in the {@link BlockingHttpResponse}.
     * @see UpgradableHttpResponse
     */
    public interface BlockingUpgradableHttpResponse<T> extends BlockingHttpResponse<T> {
        /**
         * Called by the code responsible for processing the upgrade response.
         * <p>
         * The caller of this method is responsible for calling {@link BlockingReservedHttpConnection#release()} on the
         * return value!
         *
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
        BlockingReservedHttpConnection getHttpConnection(boolean releaseReturnsToClient);

        @Override
        <R> BlockingUpgradableHttpResponse<R> transformPayloadBody(Function<BlockingIterable<T>,
                                                                            BlockingIterable<R>> transformer);

        @Override
        BlockingUpgradableHttpResponse<T> setVersion(HttpProtocolVersion version);

        @Override
        BlockingUpgradableHttpResponse<T> setStatus(HttpResponseStatus status);
    }
}
