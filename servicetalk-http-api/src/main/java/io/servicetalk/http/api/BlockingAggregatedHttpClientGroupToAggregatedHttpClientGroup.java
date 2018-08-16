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
import io.servicetalk.http.api.AggregatedHttpClient.AggregatedUpgradableHttpResponse;

import static io.servicetalk.concurrent.api.Completable.error;
import static io.servicetalk.http.api.BlockingUtils.blockingToCompletable;
import static io.servicetalk.http.api.BlockingUtils.blockingToSingle;
import static java.util.Objects.requireNonNull;

final class BlockingAggregatedHttpClientGroupToAggregatedHttpClientGroup<UnresolvedAddress>
        extends AggregatedHttpClientGroup<UnresolvedAddress> {
    private final BlockingAggregatedHttpClientGroup<UnresolvedAddress> clientGroup;

    BlockingAggregatedHttpClientGroupToAggregatedHttpClientGroup(
            BlockingAggregatedHttpClientGroup<UnresolvedAddress> clientGroup) {
        this.clientGroup = requireNonNull(clientGroup);
    }

    @Override
    public Single<AggregatedHttpResponse<HttpPayloadChunk>> request(final GroupKey<UnresolvedAddress> key,
                                                                final AggregatedHttpRequest<HttpPayloadChunk> request) {
        return blockingToSingle(() -> clientGroup.request(key, request));
    }

    @Override
    public Single<? extends AggregatedReservedHttpConnection> reserveConnection(final GroupKey<UnresolvedAddress> key,
                                                                final AggregatedHttpRequest<HttpPayloadChunk> request) {
        return blockingToSingle(() -> clientGroup.reserveConnection(key, request).asAggregatedReservedConnection());
    }

    @Override
    public Single<? extends AggregatedUpgradableHttpResponse<HttpPayloadChunk>> upgradeConnection(
            final GroupKey<UnresolvedAddress> key, final AggregatedHttpRequest<HttpPayloadChunk> request) {
        return blockingToSingle(() -> clientGroup.upgradeConnection(key, request));
    }

    @Override
    public Completable onClose() {
        if (clientGroup instanceof AggregatedHttpClientGroupToBlockingAggregatedHttpClientGroup) {
            return ((AggregatedHttpClientGroupToBlockingAggregatedHttpClientGroup) clientGroup).onClose();
        }

        return error(new UnsupportedOperationException("unsupported type: " + clientGroup.getClass()));
    }

    @Override
    public Completable closeAsync() {
        return blockingToCompletable(clientGroup::close);
    }

    @Override
    BlockingAggregatedHttpClientGroup<UnresolvedAddress> asBlockingAggregatedClientGroupInternal() {
        return clientGroup;
    }
}
