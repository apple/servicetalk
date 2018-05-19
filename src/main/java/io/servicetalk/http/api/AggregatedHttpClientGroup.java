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
import io.servicetalk.http.api.AggregatedHttpClient.AggregatedReservedHttpConnection;
import io.servicetalk.transport.api.ExecutionContext;

import java.util.function.Function;

/**
 * The equivalent of {@link HttpClientGroup} but that accepts {@link AggregatedHttpRequest} and returns
 * {@link AggregatedHttpResponse}.
 *
 * @param <UnresolvedAddress> The address type used to create new {@link AggregatedHttpClient}s.
 */
public abstract class AggregatedHttpClientGroup<UnresolvedAddress> implements ListenableAsyncCloseable {
    /**
     * Locate or create a client and delegate to {@link AggregatedHttpClient#request(AggregatedHttpRequest)}.
     *
     * @param key Identifies the {@link HttpClient} to use, or provides enough information to create
     * an {@link AggregatedHttpClient} if non exist.
     * @param request The {@link AggregatedHttpRequest} to send.
     * @return The received {@link AggregatedHttpResponse}.
     * @see AggregatedHttpClient#request(AggregatedHttpRequest)
     */
    public abstract Single<AggregatedHttpResponse<HttpPayloadChunk>> request(GroupKey<UnresolvedAddress> key,
                                                                             AggregatedHttpRequest<HttpPayloadChunk> request);

    /**
     * Locate or create a client and delegate to {@link HttpClient#reserveConnection(HttpRequest)}.
     *
     * @param key Identifies the {@link HttpClient} to use, or provides enough information to create
     * an {@link HttpClient} if non exist.
     * @param request The {@link HttpRequest} which may provide more information about which {@link HttpConnection} to
     * reserve.
     * @return A {@link AggregatedReservedHttpConnection}.
     * @see AggregatedHttpClient#reserveConnection(AggregatedHttpRequest)
     */
    public abstract Single<? extends AggregatedReservedHttpConnection> reserveConnection(
                                                                             GroupKey<UnresolvedAddress> key,
                                                                             AggregatedHttpRequest<HttpPayloadChunk> request);

    /**
     * Convert this {@link AggregatedHttpClientGroup} to the {@link AggregatedHttpRequester} API. This can simplify the
     * request APIs and usage pattern of this {@link AggregatedHttpClientGroup} assuming the address can be extracted
     * from the {@link AggregatedHttpRequest}.
     * <p>
     * <b>Note:</b> close of any created {@link AggregatedHttpRequester} will close existing
     * {@link AggregatedHttpClientGroup} instance.
     *
     * @param requestToGroupKeyFunc A {@link Function} which returns the {@link GroupKey} given a
     * {@link AggregatedHttpRequest}.
     * @param executionContext the {@link ExecutionContext} to use for
     * {@link AggregatedHttpRequester#getExecutionContext()}.
     * @return A {@link AggregatedHttpRequester}, which is backed by this {@link AggregatedHttpClientGroup}.
     */
    public final AggregatedHttpRequester asAggregatedRequester(final Function<AggregatedHttpRequest<HttpPayloadChunk>,
                                                                    GroupKey<UnresolvedAddress>> requestToGroupKeyFunc,
                                                               final ExecutionContext executionContext) {
        return new AggregatedHttpClientGroupToAggregatedHttpRequester<>(this, requestToGroupKeyFunc, executionContext);
    }

    /**
     * Convert this {@link AggregatedHttpClientGroup} to the {@link HttpClientGroup} API.
     * <p>
     * Note that the resulting {@link AggregatedHttpClientGroup} will still be subject to any blocking, in memory
     * aggregation, and other behavior as this {@link AggregatedHttpClientGroup}.
     * @return a {@link HttpClientGroup} representation of this {@link AggregatedHttpClientGroup}.
     */
    public final HttpClientGroup<UnresolvedAddress> asClientGroup() {
        return asClientGroupInternal();
    }

    HttpClientGroup<UnresolvedAddress> asClientGroupInternal() {
        return new AggregatedHttpClientGroupToHttpClientGroup<>(this);
    }
}
