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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.AggregatedHttpClientToHttpClient.AggregatedToReservedHttpConnection;
import io.servicetalk.http.api.HttpClient.ReservedHttpConnection;

/**
 * The equivalent of {@link HttpClient} but that accepts {@link FullHttpRequest} and returns {@link FullHttpResponse}.
 */
public abstract class AggregatedHttpClient extends AggregatedHttpRequester {
    /**
     * Reserve a {@link AggregatedHttpConnection} for handling the provided {@link FullHttpRequest}
     * but <b>does not execute it</b>!
     *
     * @param request Allows the underlying layers to know what {@link AggregatedHttpConnection}s are valid to reserve.
     * For example this may provide some insight into shard or other info.
     * @return a {@link ReservedHttpConnection}.
     */
    public abstract Single<? extends AggregatedReservedHttpConnection> reserveConnection(FullHttpRequest request);

    /**
     * Attempt a <a href="https://tools.ietf.org/html/rfc7230.html#section-6.7">protocol upgrade</a>.
     * As part of the <a href="https://tools.ietf.org/html/rfc7230.html#section-6.7">protocol upgrade</a> process there
     * cannot be any pipelined requests pending or any pipeline requests issued during the upgrade process. That means
     * the {@link AggregatedHttpConnection} associated with the {@link AggregatedUpgradableHttpResponse} will be
     * reserved for exclusive use. The code responsible for determining the result of the upgrade attempt is responsible
     * for calling {@link AggregatedUpgradableHttpResponse#getHttpConnection(boolean)}.
     *
     * @param request the request which initiates the upgrade.
     * @return An object that provides the {@link HttpResponse} for the upgrade attempt and also contains the
     * {@link AggregatedHttpConnection} used for the upgrade.
     */
    public abstract Single<? extends AggregatedUpgradableHttpResponse> upgradeConnection(FullHttpRequest request);

    /**
     * Convert this {@link AggregatedHttpClient} to the {@link HttpClient} asynchronous API.
     *
     * @return a {@link HttpClient} representation of this {@link AggregatedHttpClient}.
     */
    public final HttpClient<HttpPayloadChunk, HttpPayloadChunk> asClient() {
        return asClientInternal();
    }

    HttpClient<HttpPayloadChunk, HttpPayloadChunk> asClientInternal() {
        return new AggregatedHttpClientToHttpClient(this);
    }

    /**
     * A special type of {@link AggregatedHttpConnection} for the exclusive use of the caller of
     * {@link #reserveConnection(FullHttpRequest)}.
     */
    public abstract static class AggregatedReservedHttpConnection extends AggregatedHttpConnection {
        /**
         * Releases this reserved {@link AggregatedReservedHttpConnection} to be used for subsequent requests.
         * This method must be idempotent, i.e. calling multiple times must not have side-effects.
         *
         * @return the {@code Completable} that is notified on releaseAsync.
         */
        public abstract Completable releaseAsync();

        /**
         * Convert this {@link AggregatedReservedHttpConnection} to the {@link ReservedHttpConnection} asynchronous API.
         *
         * @return a {@link ReservedHttpConnection} representation of this {@link AggregatedReservedHttpConnection}.
         */
        public final ReservedHttpConnection<HttpPayloadChunk, HttpPayloadChunk> asReservedConnection() {
            return asReservedConnectionInternal();
        }

        ReservedHttpConnection<HttpPayloadChunk, HttpPayloadChunk> asReservedConnectionInternal() {
            return new AggregatedToReservedHttpConnection(this);
        }
    }

    public interface AggregatedUpgradableHttpResponse extends FullHttpResponse {
        /**
         * Called by the code responsible for processing the upgrade response.
         * <p>
         * The caller of this method is responsible for calling {@link AggregatedReservedHttpConnection#releaseAsync()}
         * on the return value!
         *
         * @param releaseReturnsToClient
         * <ul>
         *     <li>{@code true} means the {@link HttpConnection} associated with the return value can be used by
         *     this {@link AggregatedHttpClient} when {@link AggregatedReservedHttpConnection#releaseAsync()} is called.
         *     This typically means the upgrade attempt was unsuccessful, but you can continue talking HTTP. However
         *     this may also be used if the upgrade was successful, but the upgrade protocol shares semantics that are
         *     similar enough to HTTP that the same {@link AggregatedHttpClient} API can still be used
         *     (e.g. HTTP/2).</li>
         *     <li>{@code false} means the {@link HttpConnection} associated with the return value can
         *     <strong>not</strong> be used by this {@link AggregatedHttpClient} when
         *     {@link AggregatedReservedHttpConnection#releaseAsync()} is called. This typically means the upgrade
         *     attempt was successful and the semantics of the upgrade protocol are sufficiently different that the
         *     {@link AggregatedHttpClient} API no longer makes sense.</li>
         * </ul>
         * @return A {@link AggregatedReservedHttpConnection} which contains the {@link AggregatedHttpConnection} used
         * for the upgrade attempt, and controls the lifetime of the {@link HttpConnection} relative to this
         * {@link AggregatedHttpClient}.
         */
        AggregatedReservedHttpConnection getHttpConnection(boolean releaseReturnsToClient);

        @Override
        AggregatedUpgradableHttpResponse setVersion(HttpProtocolVersion version);

        @Override
        AggregatedUpgradableHttpResponse setStatus(HttpResponseStatus status);

        @Override
        AggregatedUpgradableHttpResponse duplicate();

        @Override
        AggregatedUpgradableHttpResponse replace(Buffer content);
    }
}
