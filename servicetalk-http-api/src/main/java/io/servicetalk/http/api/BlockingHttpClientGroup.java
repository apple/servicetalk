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

import io.servicetalk.client.api.GroupKey;
import io.servicetalk.http.api.BlockingHttpClient.BlockingReservedHttpConnection;

/**
 * The equivalent of {@link HttpClientGroup} but with synchronous/blocking APIs instead of asynchronous APIs.
 *
 * @param <UnresolvedAddress> The address type used to create new {@link BlockingHttpClient}s.
 */
public abstract class BlockingHttpClientGroup<UnresolvedAddress> implements AutoCloseable {
    /**
     * Locate or create a client and delegate to {@link BlockingHttpClient#request(BlockingHttpRequest)}.
     *
     * @param key Identifies the {@link BlockingHttpClient} to use, or provides enough information to create
     * a {@link BlockingHttpClient} if non exist.
     * @param request The {@link BlockingHttpRequest} to send.
     * @return The received {@link BlockingHttpResponse}.
     * @throws Exception if an exception occurs during the request processing.
     * @see BlockingHttpClient#request(BlockingHttpRequest)
     */
    public abstract BlockingHttpResponse<HttpPayloadChunk> request(GroupKey<UnresolvedAddress> key,
                                                                   BlockingHttpRequest<HttpPayloadChunk> request)
            throws Exception;

    /**
     * Locate or create a client and delegate to {@link BlockingHttpClient#reserveConnection(BlockingHttpRequest)}.
     *
     * @param key Identifies the {@link BlockingHttpClient} to use, or provides enough information to create
     * a {@link BlockingHttpClient} if non exist.
     * @param request The {@link HttpRequest} which may provide more information about which
     * {@link BlockingHttpConnection} to reserve.
     * @return A {@link BlockingReservedHttpConnection}.
     * @throws Exception if a exception occurs during the reservation process.
     * @see BlockingHttpClient#reserveConnection(BlockingHttpRequest)
     */
    public abstract BlockingReservedHttpConnection reserveConnection(GroupKey<UnresolvedAddress> key,
                                                                     BlockingHttpRequest<HttpPayloadChunk> request)
            throws Exception;

    /**
     * Convert this {@link BlockingHttpClientGroup} to the {@link HttpClientGroup} API.
     * <p>
     * Note that the resulting {@link HttpClientGroup} may still be subject to any blocking, in memory aggregation, and
     * other behavior as this {@link BlockingHttpClientGroup}.
     *
     * @return a {@link HttpClientGroup} representation of this {@link BlockingHttpClientGroup}.
     */
    public final HttpClientGroup<UnresolvedAddress> asClientGroup() {
        return asClientGroupInternal();
    }

    /**
     * Convert this {@link BlockingHttpClientGroup} to the {@link AggregatedHttpClientGroup} API.
     * <p>
     * Note that the resulting {@link AggregatedHttpClientGroup} may still be subject to any blocking, in memory
     * aggregation, and other behavior as this {@link BlockingHttpClientGroup}.
     *
     * @return a {@link AggregatedHttpClientGroup} representation of this {@link BlockingHttpClientGroup}.
     */
    public final AggregatedHttpClientGroup<UnresolvedAddress> asAggregatedClientGroup() {
        return asClientGroup().asAggregatedClientGroup();
    }

    /**
     * Convert this {@link BlockingHttpClientGroup} to the {@link BlockingAggregatedHttpClientGroup} API.
     * <p>
     * Note that the resulting {@link BlockingAggregatedHttpClientGroup} may still be subject to in
     * memory aggregation and other behavior as this {@link BlockingHttpClientGroup}.
     *
     * @return a {@link BlockingAggregatedHttpClientGroup} representation of this {@link BlockingHttpClientGroup}.
     */
    public final BlockingAggregatedHttpClientGroup<UnresolvedAddress> asBlockingAggregatedClientGroup() {
        return asClientGroup().asBlockingAggregatedClientGroup();
    }

    HttpClientGroup<UnresolvedAddress> asClientGroupInternal() {
        return new BlockingHttpClientGroupToHttpClientGroup<>(this);
    }
}
