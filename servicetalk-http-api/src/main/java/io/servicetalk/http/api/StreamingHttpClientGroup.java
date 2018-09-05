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
import io.servicetalk.http.api.StreamingHttpClient.ReservedStreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpClient.UpgradableStreamingHttpResponse;
import io.servicetalk.transport.api.ExecutionContext;

import java.util.function.Function;

/**
 * The equivalent of {@link HttpClientGroup} but that accepts {@link StreamingHttpRequest} and returns
 * {@link StreamingHttpResponse}.
 *
 * @param <UnresolvedAddress> The address type used to create new {@link StreamingHttpClient}s.
 */
public abstract class StreamingHttpClientGroup<UnresolvedAddress> implements
                                                              StreamingHttpRequestFactory, ListenableAsyncCloseable {
    /**
     * Locate or create a client and delegate to {@link StreamingHttpClient#request(StreamingHttpRequest)}.
     *
     * @param key Identifies the {@link StreamingHttpClient} to use, or provides enough information to create
     * an {@link StreamingHttpClient} if non exist.
     * @param request The {@link StreamingHttpRequest} to send.
     * @return The received {@link StreamingHttpResponse}.
     * @see StreamingHttpClient#request(StreamingHttpRequest)
     */
    public abstract Single<StreamingHttpResponse> request(
            GroupKey<UnresolvedAddress> key, StreamingHttpRequest request);

    /**
     * Locate or create a client and delegate to {@link StreamingHttpClient#reserveConnection(StreamingHttpRequest)}.
     *
     * @param key Identifies the {@link StreamingHttpClient} to use, or provides enough information to create
     * an {@link StreamingHttpClient} if non exist.
     * @param request The {@link StreamingHttpRequest} which may provide more information about which
     * {@link StreamingHttpConnection} to reserve.
     * @return A {@link StreamingHttpClient.ReservedStreamingHttpConnection}.
     * @see StreamingHttpClient#reserveConnection(StreamingHttpRequest)
     */
    public abstract Single<? extends ReservedStreamingHttpConnection> reserveConnection(
            GroupKey<UnresolvedAddress> key, StreamingHttpRequest request);

    /**
     * Locate or create a client and delegate to {@link StreamingHttpClient#upgradeConnection(StreamingHttpRequest)}.
     *
     * @param key Identifies the {@link StreamingHttpClient} to use, or provides enough information to create
     * an {@link StreamingHttpClient} if non exist.
     * @param request The {@link StreamingHttpRequest} which may provide more information about which
     * {@link StreamingHttpConnection} to upgrade.
     * @return An object that provides the {@link StreamingHttpResponse} for the upgrade attempt and also contains the
     * {@link StreamingHttpConnection} used for the upgrade.
     * @see StreamingHttpClient#upgradeConnection(StreamingHttpRequest)
     */
    public abstract Single<? extends UpgradableStreamingHttpResponse> upgradeConnection(
            GroupKey<UnresolvedAddress> key, StreamingHttpRequest request);

    /**
     * Convert this {@link StreamingHttpClientGroup} to the {@link StreamingHttpClient} API. This can simplify the
     * request APIs and usage pattern of this {@link StreamingHttpClientGroup} assuming the address can be extracted
     * from the {@link StreamingHttpRequest}.
     * <p>
     * <b>Note:</b> close of any created {@link StreamingHttpClient} will close the associated
     * {@link StreamingHttpClientGroup} instance.
     *
     * @param requestToGroupKeyFunc A {@link Function} which returns the {@link GroupKey} given a
     * {@link StreamingHttpRequest}.
     * @param executionContext the {@link ExecutionContext} to use for
     * {@link StreamingHttpClient#getExecutionContext()}.
     * @return A {@link StreamingHttpClient}, which is backed by this {@link StreamingHttpClientGroup}.
     */
    public final StreamingHttpClient asClient(final Function<StreamingHttpRequest,
                                                    GroupKey<UnresolvedAddress>> requestToGroupKeyFunc,
                                              final ExecutionContext executionContext) {
        return new StremaingHttpClientGroupToStreamingHttpClient<>(this, requestToGroupKeyFunc, executionContext);
    }

    /**
     * Convert this {@link StreamingHttpClientGroup} to the {@link HttpClientGroup} API.
     * <p>
     * Note that the resulting {@link HttpClientGroup} may still be subject to any blocking, in memory
     * aggregation, and other behavior as this {@link StreamingHttpClientGroup}.
     * @return a {@link HttpClientGroup} representation of this {@link StreamingHttpClientGroup}.
     */
    public final HttpClientGroup<UnresolvedAddress> asClientGroup() {
        return asClientGroupInternal();
    }

    /**
     * Convert this {@link StreamingHttpClientGroup} to the {@link BlockingStreamingHttpClientGroup} API.
     * <p>
     * Note that the resulting {@link BlockingStreamingHttpClientGroup} may still be subject to any blocking, in memory
     * aggregation, and other behavior as this {@link StreamingHttpClientGroup}.
     * @return a {@link BlockingStreamingHttpClientGroup} representation of this {@link StreamingHttpClientGroup}.
     */
    public final BlockingStreamingHttpClientGroup<UnresolvedAddress> asBlockingStreamingClientGroup() {
        return asBlockingStreamingClientGroupInternal();
    }

    /**
     * Convert this {@link StreamingHttpClientGroup} to the {@link BlockingHttpClientGroup} API.
     * <p>
     * Note that the resulting {@link BlockingHttpClientGroup} may still be subject to any blocking, in
     * memory aggregation, and other behavior as this {@link StreamingHttpClientGroup}.
     * @return a {@link BlockingHttpClientGroup} representation of this {@link StreamingHttpClientGroup}.
     */
    public final BlockingHttpClientGroup<UnresolvedAddress> asBlockingClientGroup() {
        return asBlockingClientGroupInternal();
    }

    HttpClientGroup<UnresolvedAddress> asClientGroupInternal() {
        return new StreamingHttpClientGroupToHttpClientGroup<>(this);
    }

    BlockingStreamingHttpClientGroup<UnresolvedAddress> asBlockingStreamingClientGroupInternal() {
        return new StreamingHttpClientGroupToBlockingStreamingHttpClientGroup<>(this);
    }

    BlockingHttpClientGroup<UnresolvedAddress> asBlockingClientGroupInternal() {
        return new StreamingHttpClientGroupToBlockingHttpClientGroup<>(this);
    }
}
