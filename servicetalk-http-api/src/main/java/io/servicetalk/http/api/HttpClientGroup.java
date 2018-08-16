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

/**
 * Logically this interface provides a <pre>{@code Map<GroupKey, HttpClient>}</pre>, and also the ability to create new
 * {@link HttpClient} objects if none yet exist.
 *
 * @param <UnresolvedAddress> The address type used to create new {@link HttpClient}s.
 */
public abstract class HttpClientGroup<UnresolvedAddress> implements ListenableAsyncCloseable {
    /**
     * Locate or create a client and delegate to {@link HttpClient#request(HttpRequest)}.
     *
     * @param key Identifies the {@link HttpClient} to use, or provides enough information to create
     * an {@link HttpClient} if non exist.
     * @param request The {@link HttpRequest} to send.
     * @return The received {@link HttpResponse}.
     * @see HttpClient#request(HttpRequest)
     */
    public abstract Single<HttpResponse<HttpPayloadChunk>> request(GroupKey<UnresolvedAddress> key,
                                                                   HttpRequest<HttpPayloadChunk> request);

    /**
     * Locate or create a client and delegate to {@link HttpClient#reserveConnection(HttpRequest)}.
     *
     * @param key Identifies the {@link HttpClient} to use, or provides enough information to create
     * an {@link HttpClient} if non exist.
     * @param request The {@link HttpRequest} which may provide more information about which {@link HttpConnection} to
     * reserve.
     * @return A {@link ReservedHttpConnection}.
     * @see HttpClient#reserveConnection(HttpRequest)
     */
    public abstract Single<? extends ReservedHttpConnection> reserveConnection(GroupKey<UnresolvedAddress> key,
                                                                               HttpRequest<HttpPayloadChunk> request);

    /**
     * Locate or create a client and delegate to {@link HttpClient#upgradeConnection(HttpRequest)}.
     *
     * @param key Identifies the {@link HttpClient} to use, or provides enough information to create
     * an {@link HttpClient} if non exist.
     * @param request The {@link HttpRequest} which may provide more information about which {@link HttpConnection} to
     * upgrade.
     * @return An object that provides the {@link HttpResponse} for the upgrade attempt and also contains the
     * {@link HttpConnection} used for the upgrade.
     * @see HttpClient#upgradeConnection(HttpRequest)
     */
    public abstract Single<? extends UpgradableHttpResponse<HttpPayloadChunk>> upgradeConnection(
            GroupKey<UnresolvedAddress> key, HttpRequest<HttpPayloadChunk> request);

    /**
     * Convert this {@link HttpClientGroup} to the {@link HttpClient} API. This can simplify the request APIs and
     * usage pattern of this {@link HttpClientGroup} assuming the address can be extracted from the {@link HttpRequest}.
     * <p>
     * <b>Note:</b> close of any created {@link HttpClient} will close the associated {@link HttpClientGroup} instance.
     *
     * @param requestToGroupKeyFunc A {@link Function} which returns the {@link GroupKey} given a {@link HttpRequest}.
     * @param executionContext the {@link ExecutionContext} to use for {@link HttpClient#getExecutionContext()}.
     * @return A {@link HttpClient}, which is backed by this {@link HttpClientGroup}.
     */
    public final HttpClient asClient(final Function<HttpRequest<HttpPayloadChunk>,
                                                    GroupKey<UnresolvedAddress>> requestToGroupKeyFunc,
                                     final ExecutionContext executionContext) {
        return new HttpClientGroupToHttpClient<>(this, requestToGroupKeyFunc, executionContext);
    }

    /**
     * Convert this {@link HttpClientGroup} to the {@link AggregatedHttpClientGroup} API.
     * <p>
     * Note that the resulting {@link AggregatedHttpClientGroup} may still be subject to any blocking, in memory
     * aggregation, and other behavior as this {@link HttpClientGroup}.
     * @return a {@link AggregatedHttpClientGroup} representation of this {@link HttpClientGroup}.
     */
    public final AggregatedHttpClientGroup<UnresolvedAddress> asAggregatedClientGroup() {
        return asAggregatedClientGroupInternal();
    }

    /**
     * Convert this {@link HttpClientGroup} to the {@link BlockingHttpClientGroup} API.
     * <p>
     * Note that the resulting {@link BlockingHttpClientGroup} may still be subject to any blocking, in memory
     * aggregation, and other behavior as this {@link HttpClientGroup}.
     * @return a {@link BlockingHttpClientGroup} representation of this {@link HttpClientGroup}.
     */
    public final BlockingHttpClientGroup<UnresolvedAddress> asBlockingClientGroup() {
        return asBlockingClientGroupInternal();
    }

    /**
     * Convert this {@link HttpClientGroup} to the {@link BlockingAggregatedHttpClientGroup} API.
     * <p>
     * Note that the resulting {@link BlockingAggregatedHttpClientGroup} may still be subject to any blocking, in
     * memory aggregation, and other behavior as this {@link HttpClientGroup}.
     * @return a {@link BlockingAggregatedHttpClientGroup} representation of this {@link HttpClientGroup}.
     */
    public final BlockingAggregatedHttpClientGroup<UnresolvedAddress> asBlockingAggregatedClientGroup() {
        return asBlockingAggregatedClientGroupInternal();
    }

    AggregatedHttpClientGroup<UnresolvedAddress> asAggregatedClientGroupInternal() {
        return new HttpClientGroupToAggregatedHttpClientGroup<>(this);
    }

    BlockingHttpClientGroup<UnresolvedAddress> asBlockingClientGroupInternal() {
        return new HttpClientGroupToBlockingHttpClientGroup<>(this);
    }

    BlockingAggregatedHttpClientGroup<UnresolvedAddress> asBlockingAggregatedClientGroupInternal() {
        return new HttpClientGroupToBlockingAggregatedHttpClientGroup<>(this);
    }
}
