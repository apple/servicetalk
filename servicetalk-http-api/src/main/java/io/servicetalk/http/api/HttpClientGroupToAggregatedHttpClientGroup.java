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
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.AggregatedHttpClient.AggregatedReservedHttpConnection;
import io.servicetalk.http.api.HttpClientToAggregatedHttpClient.ReservedHttpConnectionToAggregated;

import static io.servicetalk.http.api.DefaultAggregatedHttpRequest.toHttpRequest;
import static io.servicetalk.http.api.DefaultAggregatedHttpResponse.from;
import static java.util.Objects.requireNonNull;

final class HttpClientGroupToAggregatedHttpClientGroup<UnresolvedAddress> extends
                                                                          AggregatedHttpClientGroup<UnresolvedAddress> {
    private final HttpClientGroup<UnresolvedAddress> clientGroup;

    HttpClientGroupToAggregatedHttpClientGroup(HttpClientGroup<UnresolvedAddress> clientGroup) {
        this.clientGroup = requireNonNull(clientGroup);
    }

    @Override
    public Single<AggregatedHttpResponse<HttpPayloadChunk>> request(final GroupKey<UnresolvedAddress> key,
                                                                    final AggregatedHttpRequest<HttpPayloadChunk> request) {
        return clientGroup.request(key, toHttpRequest(request)).flatMap(response ->
                from(response, key.getExecutionContext().getBufferAllocator()));
    }

    @Override
    public Single<? extends AggregatedReservedHttpConnection> reserveConnection(
            final GroupKey<UnresolvedAddress> key, final AggregatedHttpRequest<HttpPayloadChunk> request) {
        return clientGroup.reserveConnection(key, toHttpRequest(request)).map(ReservedHttpConnectionToAggregated::new);
    }

    @Override
    public Completable onClose() {
        return clientGroup.onClose();
    }

    @Override
    public Completable closeAsync() {
        return clientGroup.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return clientGroup.closeAsyncGracefully();
    }

    @Override
    HttpClientGroup<UnresolvedAddress> asClientGroupInternal() {
        return clientGroup;
    }
}
