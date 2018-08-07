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

import io.servicetalk.http.api.AggregatedHttpClient.AggregatedReservedHttpConnection;
import io.servicetalk.http.api.AggregatedHttpClient.AggregatedUpgradableHttpResponse;
import io.servicetalk.http.api.BlockingAggregatedHttpClientToAggregatedHttpClient.BlockingAggregatedReservedToAggregatedHttpConnection;
import io.servicetalk.http.api.BlockingAggregatedHttpClientToHttpClient.BlockingAggregatedReservedHttpConnectionToReserved;
import io.servicetalk.http.api.BlockingHttpClient.BlockingReservedHttpConnection;
import io.servicetalk.http.api.HttpClient.ReservedHttpConnection;

/**
 * The equivalent of {@link AggregatedHttpClient} but with synchronous/blocking APIs instead of asynchronous APIs.
 */
public abstract class BlockingAggregatedHttpClient extends BlockingAggregatedHttpRequester {
    /**
     * Reserve a {@link BlockingAggregatedReservedHttpConnection} for handling the provided
     * {@link AggregatedHttpRequest} but <b>does not execute it</b>!
     *
     * @param request Allows the underlying layers to know what {@link BlockingAggregatedHttpConnection}s are valid to
     * reserve. For example this may provide some insight into shard or other info.
     * @return a {@link BlockingAggregatedReservedHttpConnection}.
     * @throws Exception if a exception occurs during the reservation process.
     * @see HttpClient#reserveConnection(HttpRequest)
     */
    public abstract BlockingAggregatedReservedHttpConnection reserveConnection(
            AggregatedHttpRequest<HttpPayloadChunk> request) throws Exception;

    /**
     * Attempt a <a href="https://tools.ietf.org/html/rfc7230.html#section-6.7">protocol upgrade</a>.
     * As part of the <a href="https://tools.ietf.org/html/rfc7230.html#section-6.7">protocol upgrade</a> process there
     * cannot be any pipelined requests pending or any pipeline requests issued during the upgrade process. That means
     * the {@link BlockingAggregatedHttpConnection} associated with the {@link AggregatedUpgradableHttpResponse} will be
     * reserved for exclusive use. The code responsible for determining the result of the upgrade attempt is responsible
     * for calling {@link AggregatedUpgradableHttpResponse#getHttpConnection(boolean)}.
     *
     * @param request the request which initiates the upgrade.
     * @return An {@link AggregatedUpgradableHttpResponse} for the upgrade attempt and also contains the
     * {@link BlockingAggregatedHttpConnection} used for the upgrade.
     * @throws Exception if a exception occurs during the upgrade process.
     * @see HttpClient#upgradeConnection(HttpRequest)
     */
    public abstract AggregatedUpgradableHttpResponse<HttpPayloadChunk> upgradeConnection(
            AggregatedHttpRequest<HttpPayloadChunk> request) throws Exception;

    /**
     * Convert this {@link BlockingAggregatedHttpClient} to the {@link HttpClient} API.
     *
     * @return a {@link HttpClient} representation of this {@link BlockingAggregatedHttpClient}.
     */
    public final HttpClient asClient() {
        return asClientInternal();
    }

    /**
     * Convert this {@link BlockingAggregatedHttpClient} to the {@link AggregatedHttpClient} API.
     *
     * @return a {@link AggregatedHttpClient} representation of this {@link BlockingAggregatedHttpClient}.
     */
    public final AggregatedHttpClient asAggregatedClient() {
        return asAggregatedClientInternal();
    }

    /**
     * Convert this {@link BlockingAggregatedHttpClient} to the {@link BlockingHttpClient} API.
     *
     * @return a {@link BlockingHttpClient} representation of this {@link BlockingAggregatedHttpClient}.
     */
    public final BlockingHttpClient asBlockingClient() {
        return asClient().asBlockingClient();
    }

    HttpClient asClientInternal() {
        return new BlockingAggregatedHttpClientToHttpClient(this);
    }

    AggregatedHttpClient asAggregatedClientInternal() {
        return new BlockingAggregatedHttpClientToAggregatedHttpClient(this);
    }

    /**
     * A special type of {@link BlockingAggregatedHttpConnection} for the exclusive use of the caller of
     * {@link #reserveConnection(AggregatedHttpRequest)}.
     * @see ReservedHttpConnection
     */
    public abstract static class BlockingAggregatedReservedHttpConnection extends BlockingAggregatedHttpConnection {
        /**
         * Releases this reserved {@link BlockingAggregatedHttpConnection} to be used for subsequent requests.
         * This method must be idempotent, i.e. calling multiple times must not have side-effects.
         *
         * @throws Exception if any exception occurs during releasing.
         */
        public abstract void release() throws Exception;

        /**
         * Convert this {@link BlockingAggregatedReservedHttpConnection} to the {@link ReservedHttpConnection} API.
         * <p>
         * Note that the resulting {@link ReservedHttpConnection} may still be subject to any blocking, in memory
         * aggregation, and other behavior as this {@link BlockingAggregatedReservedHttpConnection}.
         *
         * @return a {@link ReservedHttpConnection} representation of this
         * {@link BlockingAggregatedReservedHttpConnection}.
         */
        public final ReservedHttpConnection asReservedConnection() {
            return asConnectionInternal();
        }

        /**
         * Convert this {@link BlockingAggregatedReservedHttpConnection} to the {@link AggregatedReservedHttpConnection}
         * API.
         * <p>
         * Note that the resulting {@link AggregatedReservedHttpConnection} may still be subject to any blocking, in
         * memory aggregation, and other behavior as this {@link BlockingAggregatedReservedHttpConnection}.
         *
         * @return a {@link AggregatedReservedHttpConnection} representation of this
         * {@link BlockingAggregatedReservedHttpConnection}.
         */
        public final AggregatedReservedHttpConnection asAggregatedReservedConnection() {
            return asAggregatedConnectionInternal();
        }

        /**
         * Convert this {@link BlockingAggregatedReservedHttpConnection} to the {@link BlockingReservedHttpConnection}
         * API.
         * <p>
         * Note that the resulting {@link BlockingReservedHttpConnection} may still be subject to in
         * memory aggregation and other behavior as this {@link BlockingAggregatedReservedHttpConnection}.
         *
         * @return a {@link BlockingReservedHttpConnection} representation of this
         * {@link BlockingAggregatedReservedHttpConnection}.
         */
        public final BlockingReservedHttpConnection asBlockingReservedConnection() {
            return asReservedConnection().asBlockingReservedConnection();
        }

        @Override
        ReservedHttpConnection asConnectionInternal() {
            return new BlockingAggregatedReservedHttpConnectionToReserved(this);
        }

        @Override
        AggregatedReservedHttpConnection asAggregatedConnectionInternal() {
            return new BlockingAggregatedReservedToAggregatedHttpConnection(this);
        }
    }
}
