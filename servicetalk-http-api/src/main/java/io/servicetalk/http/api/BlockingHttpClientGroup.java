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
import io.servicetalk.http.api.BlockingHttpClient.ReservedBlockingHttpConnection;
import io.servicetalk.http.api.HttpClient.UpgradableHttpResponse;

import static java.util.Objects.requireNonNull;

/**
 * The equivalent of {@link HttpClientGroup} but with synchronous/blocking APIs instead of asynchronous APIs.
 *
 * @param <UnresolvedAddress> The address type used to create new {@link BlockingHttpClient}s.
 */
public abstract class BlockingHttpClientGroup<UnresolvedAddress> implements HttpRequestFactory, AutoCloseable {
    final HttpRequestResponseFactory reqRespFactory;

    /**
     * Create a new instance.
     * @param reqRespFactory The {@link HttpRequestResponseFactory} used to
     * {@link #newRequest(HttpRequestMethod, String) create new requests} and {@link #getHttpResponseFactory()}.
     */
    protected BlockingHttpClientGroup(final HttpRequestResponseFactory reqRespFactory) {
        this.reqRespFactory = requireNonNull(reqRespFactory);
    }

    /**
     * Locate or create a client and delegate to {@link BlockingHttpClient#request(HttpRequest)}.
     *
     * @param key Identifies the {@link BlockingHttpClient} to use, or provides enough information to create
     * a {@link BlockingHttpClient} if non exist.
     * @param request The {@link HttpRequest} to send.
     * @return The received {@link HttpResponse}.
     * @throws Exception if an exception occurs during the request processing.
     * @see BlockingHttpClient#request(HttpRequest)
     */
    public abstract HttpResponse request(GroupKey<UnresolvedAddress> key, HttpRequest request) throws Exception;

    /**
     * Locate or create a client and delegate to
     * {@link BlockingHttpClient#reserveConnection(HttpRequest)}.
     *
     * @param key Identifies the {@link BlockingHttpClient} to use, or provides enough information to create
     * a {@link BlockingHttpClient} if non exist.
     * @param request The {@link HttpRequest} which may provide more information about which
     * {@link BlockingHttpConnection} to
     * reserve.
     * @return A {@link ReservedBlockingHttpConnection}.
     * @throws Exception if a exception occurs during the reservation process.
     * @see BlockingHttpClient#reserveConnection(HttpRequest)
     */
    public abstract ReservedBlockingHttpConnection reserveConnection(
            GroupKey<UnresolvedAddress> key, HttpRequest request) throws Exception;

    /**
     * Locate or create a client and delegate to
     * {@link BlockingHttpClient#upgradeConnection(HttpRequest)}.
     *
     * @param key Identifies the {@link BlockingHttpClient} to use, or provides enough information to create
     * a {@link BlockingHttpClient} if non exist.
     * @param request The {@link HttpRequest} which may provide more information about which
     * {@link BlockingHttpConnection} to upgrade.
     * @return An object that provides the {@link StreamingHttpResponse} for the upgrade attempt and also contains the
     * {@link StreamingHttpConnection} used for the upgrade.
     * @throws Exception if a exception occurs during the reservation process.
     * @see BlockingHttpClient#upgradeConnection(HttpRequest)
     */
    public abstract UpgradableHttpResponse upgradeConnection(
            GroupKey<UnresolvedAddress> key, HttpRequest request) throws Exception;

    @Override
    public final HttpRequest newRequest(HttpRequestMethod method, String requestTarget) {
        return reqRespFactory.newRequest(method, requestTarget);
    }

    /**
     * Get a {@link HttpResponseFactory}.
     * @return a {@link HttpResponseFactory}.
     */
    public final HttpResponseFactory getHttpResponseFactory() {
        return reqRespFactory;
    }

    /**
     * Convert this {@link BlockingHttpClientGroup} to the {@link StreamingHttpClientGroup} API.
     * <p>
     * Note that the resulting {@link StreamingHttpClientGroup} may still be subject to any blocking, in memory
     * aggregation, and other behavior as this {@link BlockingHttpClientGroup}.
     * @return a {@link StreamingHttpClientGroup} representation of this {@link BlockingHttpClientGroup}.
     */
    public final StreamingHttpClientGroup<UnresolvedAddress> asStreamingClientGroup() {
        return asStreamingClientGroupInternal();
    }

    /**
     * Convert this {@link BlockingHttpClientGroup} to the {@link HttpClientGroup} API.
     * <p>
     * Note that the resulting {@link HttpClientGroup} may still be subject to any blocking, in memory
     * aggregation, and other behavior as this {@link BlockingHttpClientGroup}.
     * @return a {@link HttpClientGroup} representation of this {@link BlockingHttpClientGroup}.
     */
    public final HttpClientGroup<UnresolvedAddress> asClientGroup() {
        return asClientGroupInternal();
    }

    /**
     * Convert this {@link BlockingHttpClientGroup} to the {@link BlockingStreamingHttpClientGroup} API.
     * <p>
     * Note that the resulting {@link BlockingStreamingHttpClientGroup} may still be subject to in memory
     * aggregation and other behavior as this {@link BlockingHttpClientGroup}.
     * @return a {@link BlockingStreamingHttpClientGroup} representation of this {@link BlockingHttpClientGroup}.
     */
    public final BlockingStreamingHttpClientGroup<UnresolvedAddress> asBlockingStreamingClientGroup() {
        return asStreamingClientGroup().asBlockingStreamingClientGroup();
    }

    StreamingHttpClientGroup<UnresolvedAddress> asStreamingClientGroupInternal() {
        return new BlockingHttpClientGroupToStreamingHttpClientGroup<>(this);
    }

    HttpClientGroup<UnresolvedAddress> asClientGroupInternal() {
        return new BlockingHttpClientGroupToHttpClientGroup<>(this);
    }
}
