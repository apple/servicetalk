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
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpClient.ReservedHttpConnection;
import io.servicetalk.http.api.HttpClient.UpgradableHttpResponse;
import io.servicetalk.transport.api.ExecutionContext;

import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * Logically this interface provides a Map&lt;{@link GroupKey}, {@link HttpClient}&gt;, and also the ability to
 * create new {@link HttpClient} objects if none yet exist.
 *
 * @param <UnresolvedAddress> The address type used to create new {@link HttpClient}s.
 */
public abstract class HttpClientGroup<UnresolvedAddress> implements HttpRequestFactory, ListenableAsyncCloseable {
    final HttpRequestResponseFactory reqRespFactory;

    /**
     * Create a new instance.
     * @param reqRespFactory The {@link HttpRequestResponseFactory} used to
     * {@link #newRequest(HttpRequestMethod, String) create new requests} and {@link #httpResponseFactory()}.
     */
    protected HttpClientGroup(final HttpRequestResponseFactory reqRespFactory) {
        this.reqRespFactory = requireNonNull(reqRespFactory);
    }

    /**
     * Locate or create a client and delegate to {@link HttpClient#request(HttpRequest)}.
     *
     * @param key Identifies the {@link HttpClient} to use, or provides enough information to create
     * an {@link HttpClient} if non exist.
     * @param request The {@link HttpRequest} to send.
     * @return The received {@link HttpResponse}.
     * @see HttpClient#request(HttpRequest)
     */
    public abstract Single<? extends HttpResponse> request(GroupKey<UnresolvedAddress> key, HttpRequest request);

    /**
     * Locate or create a client and delegate to {@link HttpClient#reserveConnection(HttpRequest)}.
     *
     * @param key Identifies the {@link HttpClient} to use, or provides enough information to create
     * an {@link HttpClient} if non exist.
     * @param request The {@link HttpRequest} which may provide more information about which
     * {@link HttpConnection} to reserve.
     * @return A {@link ReservedHttpConnection}.
     * @see HttpClient#reserveConnection(HttpRequest)
     */
    public abstract Single<? extends ReservedHttpConnection> reserveConnection(
            GroupKey<UnresolvedAddress> key, HttpRequest request);

    /**
     * Locate or create a client and delegate to {@link HttpClient#upgradeConnection(HttpRequest)}.
     *
     * @param key Identifies the {@link HttpClient} to use, or provides enough information to create
     * an {@link HttpClient} if non exist.
     * @param request The {@link HttpRequest} which may provide more information about which
     * {@link HttpConnection} to upgrade.
     * @return An object that provides the {@link StreamingHttpResponse} for the upgrade attempt and also contains the
     * {@link StreamingHttpConnection} used for the upgrade.
     * @see HttpClient#upgradeConnection(HttpRequest)
     */
    public abstract Single<? extends UpgradableHttpResponse> upgradeConnection(
            GroupKey<UnresolvedAddress> key, HttpRequest request);

    @Override
    public final HttpRequest newRequest(HttpRequestMethod method, String requestTarget) {
        return reqRespFactory.newRequest(method, requestTarget);
    }

    /**
     * Get a {@link HttpResponseFactory}.
     * @return a {@link HttpResponseFactory}.
     */
    public final HttpResponseFactory httpResponseFactory() {
        return reqRespFactory;
    }

    /**
     * Convert this {@link HttpClientGroup} to the {@link HttpRequester} API. This can simplify the
     * request APIs and usage pattern of this {@link HttpClientGroup} assuming the address can be extracted
     * from the {@link HttpRequest}.
     * <p>
     * <b>Note:</b> close of any created {@link HttpRequester} will close existing
     * {@link HttpClientGroup} instance.
     *
     * @param requestToGroupKeyFunc A {@link Function} which returns the {@link GroupKey} given a
     * {@link HttpRequest}.
     * @param executionContext the {@link ExecutionContext} to use for
     * {@link HttpRequester#executionContext()}.
     * @return A {@link HttpRequester}, which is backed by this {@link HttpClientGroup}.
     */
    public final HttpRequester asRequester(final Function<HttpRequest,
                                                    GroupKey<UnresolvedAddress>> requestToGroupKeyFunc,
                                           final ExecutionContext executionContext) {
        return new HttpClientGroupToHttpRequester<>(this, requestToGroupKeyFunc, executionContext);
    }

    /**
     * Convert this {@link HttpClientGroup} to the {@link StreamingHttpClientGroup} API.
     * <p>
     * Note that the resulting {@link StreamingHttpClientGroup} may still be subject to any blocking, in memory
     * aggregation, and other behavior as this {@link HttpClientGroup}.
     * @return a {@link StreamingHttpClientGroup} representation of this {@link HttpClientGroup}.
     */
    public final StreamingHttpClientGroup<UnresolvedAddress> asStreamingClientGroup() {
        return asStreamingClientGroupInternal();
    }

    /**
     * Convert this {@link HttpClientGroup} to the {@link BlockingStreamingHttpClientGroup} API.
     * <p>
     * Note that the resulting {@link BlockingStreamingHttpClientGroup} may still be subject to any blocking, in memory
     * aggregation, and other behavior as this {@link HttpClientGroup}.
     * @return a {@link BlockingStreamingHttpClientGroup} representation of this {@link HttpClientGroup}.
     */
    public final BlockingStreamingHttpClientGroup<UnresolvedAddress> asBlockingStreamingClientGroup() {
        return asStreamingClientGroup().asBlockingStreamingClientGroup();
    }

    /**
     * Convert this {@link HttpClientGroup} to the {@link BlockingHttpClientGroup} API.
     * <p>
     * Note that the resulting {@link BlockingHttpClientGroup} may still be subject to any blocking, in
     * memory aggregation, and other behavior as this {@link HttpClientGroup}.
     * @return a {@link BlockingHttpClientGroup} representation of this {@link HttpClientGroup}.
     */
    public final BlockingHttpClientGroup<UnresolvedAddress> asBlockingClientGroup() {
        return asBlockingClientGroupInternal();
    }

    StreamingHttpClientGroup<UnresolvedAddress> asStreamingClientGroupInternal() {
        return new HttpClientGroupToStreamingHttpClientGroup<>(this);
    }

    BlockingHttpClientGroup<UnresolvedAddress> asBlockingClientGroupInternal() {
        return new HttpClientGroupToBlockingHttpClientGroup<>(this);
    }
}
