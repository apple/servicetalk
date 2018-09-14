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
import io.servicetalk.http.api.BlockingStreamingHttpClient.ReservedBlockingStreamingHttpConnection;
import io.servicetalk.http.api.BlockingStreamingHttpClient.UpgradableBlockingStreamingHttpResponse;

import static java.util.Objects.requireNonNull;

/**
 * The equivalent of {@link HttpClientGroup} but with synchronous/blocking APIs instead of asynchronous APIs.
 *
 * @param <UnresolvedAddress> The address type used to create new {@link BlockingStreamingHttpClient}s.
 */
public abstract class BlockingStreamingHttpClientGroup<UnresolvedAddress> implements
                                                          BlockingStreamingHttpRequestFactory, AutoCloseable {
    private final BlockingStreamingHttpRequestFactory requestFactory;

    /**
     * Create a new instance.
     * @param requestFactory The {@link HttpRequestFactory} used to
     * {@link #newRequest(HttpRequestMethod, String) create new requests}.
     */
    protected BlockingStreamingHttpClientGroup(BlockingStreamingHttpRequestFactory requestFactory) {
        this.requestFactory = requireNonNull(requestFactory);
    }

    /**
     * Locate or create a client and delegate to
     * {@link BlockingStreamingHttpClient#request(BlockingStreamingHttpRequest)}.
     *
     * @param key Identifies the {@link BlockingStreamingHttpClient} to use, or provides enough information to create
     * a {@link BlockingStreamingHttpClient} if non exist.
     * @param request The {@link BlockingStreamingHttpRequest} to send.
     * @return The received {@link BlockingStreamingHttpResponse}.
     * @throws Exception if an exception occurs during the request processing.
     * @see BlockingStreamingHttpClient#request(BlockingStreamingHttpRequest)
     */
    public abstract BlockingStreamingHttpResponse request(
            GroupKey<UnresolvedAddress> key, BlockingStreamingHttpRequest request) throws Exception;

    /**
     * Locate or create a client and delegate to
     * {@link BlockingStreamingHttpClient#reserveConnection(BlockingStreamingHttpRequest)}.
     *
     * @param key Identifies the {@link BlockingStreamingHttpClient} to use, or provides enough information to create
     * a {@link BlockingStreamingHttpClient} if non exist.
     * @param request The {@link StreamingHttpRequest} which may provide more information about which
     * {@link BlockingStreamingHttpConnection} to reserve.
     * @return A {@link ReservedBlockingStreamingHttpConnection}.
     * @throws Exception if a exception occurs during the reservation process.
     * @see BlockingStreamingHttpClient#reserveConnection(BlockingStreamingHttpRequest)
     */
    public abstract ReservedBlockingStreamingHttpConnection reserveConnection(
            GroupKey<UnresolvedAddress> key, BlockingStreamingHttpRequest request) throws Exception;

    /**
     * Locate or create a client and delegate to
     * {@link BlockingStreamingHttpClient#upgradeConnection(BlockingStreamingHttpRequest)}.
     *
     * @param key Identifies the {@link BlockingStreamingHttpClient} to use, or provides enough information to create
     * a {@link BlockingStreamingHttpClient} if non exist.
     * @param request The {@link StreamingHttpRequest} which may provide more information about which
     * {@link BlockingStreamingHttpConnection} to upgrade.
     * @return An object that provides the {@link StreamingHttpResponse} for the upgrade attempt and also contains the
     * {@link StreamingHttpConnection} used for the upgrade.
     * @throws Exception if a exception occurs during the reservation process.
     * @see BlockingStreamingHttpClient#upgradeConnection(BlockingStreamingHttpRequest)
     */
    public abstract UpgradableBlockingStreamingHttpResponse upgradeConnection(
            GroupKey<UnresolvedAddress> key, BlockingStreamingHttpRequest request) throws Exception;

    @Override
    public final BlockingStreamingHttpRequest newRequest(HttpRequestMethod method, String requestTarget) {
        return requestFactory.newRequest(method, requestTarget);
    }

    @Override
    public final BlockingStreamingHttpResponseFactory getHttpResponseFactory() {
        return requestFactory.getHttpResponseFactory();
    }

    /**
     * Convert this {@link BlockingStreamingHttpClientGroup} to the {@link StreamingHttpClientGroup} API.
     * <p>
     * Note that the resulting {@link StreamingHttpClientGroup} may still be subject to any blocking, in memory
     * aggregation, and other behavior as this {@link BlockingStreamingHttpClientGroup}.
     *
     * @return a {@link StreamingHttpClientGroup} representation of this {@link BlockingStreamingHttpClientGroup}.
     */
    public final StreamingHttpClientGroup<UnresolvedAddress> asStreamingClientGroup() {
        return asStreamingClientGroupInternal();
    }

    /**
     * Convert this {@link BlockingStreamingHttpClientGroup} to the {@link HttpClientGroup} API.
     * <p>
     * Note that the resulting {@link HttpClientGroup} may still be subject to any blocking, in memory
     * aggregation, and other behavior as this {@link BlockingStreamingHttpClientGroup}.
     *
     * @return a {@link HttpClientGroup} representation of this {@link BlockingStreamingHttpClientGroup}.
     */
    public final HttpClientGroup<UnresolvedAddress> asClientGroup() {
        return asStreamingClientGroup().asClientGroup();
    }

    /**
     * Convert this {@link BlockingStreamingHttpClientGroup} to the {@link BlockingHttpClientGroup} API.
     * <p>
     * Note that the resulting {@link BlockingHttpClientGroup} may still be subject to in
     * memory aggregation and other behavior as this {@link BlockingStreamingHttpClientGroup}.
     *
     * @return a {@link BlockingHttpClientGroup} representation of this
     * {@link BlockingStreamingHttpClientGroup}.
     */
    public final BlockingHttpClientGroup<UnresolvedAddress> asBlockingClientGroup() {
        return asStreamingClientGroup().asBlockingClientGroup();
    }

    StreamingHttpClientGroup<UnresolvedAddress> asStreamingClientGroupInternal() {
        return new BlockingStreamingHttpClientGroupToStreamingHttpClientGroup<>(this);
    }
}
