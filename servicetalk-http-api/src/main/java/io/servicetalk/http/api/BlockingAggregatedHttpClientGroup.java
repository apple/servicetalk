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
import io.servicetalk.http.api.AggregatedHttpClient.AggregatedUpgradableHttpResponse;
import io.servicetalk.http.api.BlockingAggregatedHttpClient.BlockingAggregatedReservedHttpConnection;

/**
 * The equivalent of {@link AggregatedHttpClientGroup} but with synchronous/blocking APIs instead of asynchronous APIs.
 *
 * @param <UnresolvedAddress> The address type used to create new {@link BlockingAggregatedHttpClient}s.
 */
public abstract class BlockingAggregatedHttpClientGroup<UnresolvedAddress> implements AutoCloseable {
    /**
     * Locate or create a client and delegate to {@link BlockingAggregatedHttpClient#request(AggregatedHttpRequest)}.
     *
     * @param key Identifies the {@link BlockingAggregatedHttpClient} to use, or provides enough information to create
     * a {@link BlockingAggregatedHttpClient} if non exist.
     * @param request The {@link AggregatedHttpRequest} to send.
     * @return The received {@link AggregatedHttpResponse}.
     * @throws Exception if an exception occurs during the request processing.
     * @see BlockingAggregatedHttpClient#request(AggregatedHttpRequest)
     */
    public abstract AggregatedHttpResponse<HttpPayloadChunk> request(GroupKey<UnresolvedAddress> key,
                                                                     AggregatedHttpRequest<HttpPayloadChunk> request)
        throws Exception;

    /**
     * Locate or create a client and delegate to
     * {@link BlockingAggregatedHttpClient#reserveConnection(AggregatedHttpRequest)}.
     *
     * @param key Identifies the {@link BlockingAggregatedHttpClient} to use, or provides enough information to create
     * a {@link BlockingAggregatedHttpClient} if non exist.
     * @param request The {@link AggregatedHttpRequest} which may provide more information about which
     * {@link BlockingAggregatedHttpConnection} to
     * reserve.
     * @return A {@link BlockingAggregatedReservedHttpConnection}.
     * @throws Exception if a exception occurs during the reservation process.
     * @see BlockingAggregatedHttpClient#reserveConnection(AggregatedHttpRequest)
     */
    public abstract BlockingAggregatedReservedHttpConnection reserveConnection(GroupKey<UnresolvedAddress> key,
                                                                       AggregatedHttpRequest<HttpPayloadChunk> request)
        throws Exception;

    /**
     * Locate or create a client and delegate to
     * {@link BlockingAggregatedHttpClient#upgradeConnection(AggregatedHttpRequest)}.
     *
     * @param key Identifies the {@link BlockingAggregatedHttpClient} to use, or provides enough information to create
     * a {@link BlockingAggregatedHttpClient} if non exist.
     * @param request The {@link AggregatedHttpRequest} which may provide more information about which
     * {@link BlockingAggregatedHttpConnection} to upgrade.
     * @return An object that provides the {@link HttpResponse} for the upgrade attempt and also contains the
     * {@link HttpConnection} used for the upgrade.
     * @throws Exception if a exception occurs during the reservation process.
     * @see BlockingAggregatedHttpClient#upgradeConnection(AggregatedHttpRequest)
     */
    public abstract AggregatedUpgradableHttpResponse<HttpPayloadChunk> upgradeConnection(
            GroupKey<UnresolvedAddress> key,
            AggregatedHttpRequest<HttpPayloadChunk> request) throws Exception;

    /**
     * Convert this {@link BlockingAggregatedHttpClientGroup} to the {@link HttpClientGroup} API.
     * <p>
     * Note that the resulting {@link HttpClientGroup} may still be subject to any blocking, in memory
     * aggregation, and other behavior as this {@link BlockingAggregatedHttpClientGroup}.
     * @return a {@link HttpClientGroup} representation of this {@link BlockingAggregatedHttpClientGroup}.
     */
    public final HttpClientGroup<UnresolvedAddress> asClientGroup() {
        return asClientGroupInternal();
    }

    /**
     * Convert this {@link BlockingAggregatedHttpClientGroup} to the {@link AggregatedHttpClientGroup} API.
     * <p>
     * Note that the resulting {@link AggregatedHttpClientGroup} may still be subject to any blocking, in memory
     * aggregation, and other behavior as this {@link BlockingAggregatedHttpClientGroup}.
     * @return a {@link AggregatedHttpClientGroup} representation of this {@link BlockingAggregatedHttpClientGroup}.
     */
    public final AggregatedHttpClientGroup<UnresolvedAddress> asAggregatedClientGroup() {
        return asAggregatedClientGroupInternal();
    }

    /**
     * Convert this {@link BlockingAggregatedHttpClientGroup} to the {@link BlockingHttpClientGroup} API.
     * <p>
     * Note that the resulting {@link BlockingHttpClientGroup} may still be subject to in memory
     * aggregation and other behavior as this {@link BlockingAggregatedHttpClientGroup}.
     * @return a {@link BlockingHttpClientGroup} representation of this {@link BlockingAggregatedHttpClientGroup}.
     */
    public final BlockingHttpClientGroup<UnresolvedAddress> asBlockingClientGroup() {
        return asClientGroup().asBlockingClientGroup();
    }

    HttpClientGroup<UnresolvedAddress> asClientGroupInternal() {
        return new BlockingAggregatedHttpClientGroupToHttpClientGroup<>(this);
    }

    AggregatedHttpClientGroup<UnresolvedAddress> asAggregatedClientGroupInternal() {
        return new BlockingAggregatedHttpClientGroupToAggregatedHttpClientGroup<>(this);
    }
}
