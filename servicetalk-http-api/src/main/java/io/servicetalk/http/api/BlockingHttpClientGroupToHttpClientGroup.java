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
import io.servicetalk.http.api.BlockingHttpClient.BlockingUpgradableHttpResponse;
import io.servicetalk.http.api.BlockingHttpClientToHttpClient.BlockingToReservedHttpConnection;
import io.servicetalk.http.api.BlockingHttpClientToHttpClient.BlockingToUpgradableHttpResponse;
import io.servicetalk.http.api.HttpClient.ReservedHttpConnection;
import io.servicetalk.http.api.HttpClient.UpgradableHttpResponse;

import static io.servicetalk.concurrent.api.Completable.error;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.http.api.BlockingUtils.blockingToCompletable;
import static io.servicetalk.http.api.BlockingUtils.blockingToSingle;
import static io.servicetalk.http.api.HttpResponses.fromBlockingResponse;
import static java.util.Objects.requireNonNull;

final class BlockingHttpClientGroupToHttpClientGroup<UnresolvedAddress> extends HttpClientGroup<UnresolvedAddress> {
    private final BlockingHttpClientGroup<UnresolvedAddress> blockingClientGroup;

    BlockingHttpClientGroupToHttpClientGroup(BlockingHttpClientGroup<UnresolvedAddress> blockingClientGroup) {
        this.blockingClientGroup = requireNonNull(blockingClientGroup);
    }

    @Override
    public Single<HttpResponse<HttpPayloadChunk>> request(final GroupKey<UnresolvedAddress> key,
                                                          final HttpRequest<HttpPayloadChunk> request) {
        return blockingToSingle(() -> fromBlockingResponse(blockingClientGroup.request(key,
                new DefaultBlockingHttpRequest<>(request))));
    }

    @Override
    public Single<? extends ReservedHttpConnection> reserveConnection(
            final GroupKey<UnresolvedAddress> key, final HttpRequest<HttpPayloadChunk> request) {
        return blockingToSingle(() -> new BlockingToReservedHttpConnection(blockingClientGroup.reserveConnection(
                key, new DefaultBlockingHttpRequest<>(request))));
    }

    @Override
    public Single<? extends UpgradableHttpResponse<HttpPayloadChunk>> upgradeConnection(
            final GroupKey<UnresolvedAddress> key, final HttpRequest<HttpPayloadChunk> request) {
        return blockingToSingle(() -> {
            BlockingUpgradableHttpResponse<HttpPayloadChunk> upgradeResponse =
                    blockingClientGroup.upgradeConnection(key, new DefaultBlockingHttpRequest<>(request));
            return new BlockingToUpgradableHttpResponse<>(upgradeResponse, from(upgradeResponse.getPayloadBody()));
        });
    }

    @Override
    public Completable onClose() {
        if (blockingClientGroup instanceof HttpClientGroupToBlockingHttpClientGroup) {
            return ((HttpClientGroupToBlockingHttpClientGroup<?>) blockingClientGroup).onClose();
        }

        return error(new UnsupportedOperationException("unsupported type: " + blockingClientGroup.getClass()));
    }

    @Override
    public Completable closeAsync() {
        return blockingToCompletable(blockingClientGroup::close);
    }

    @Override
    BlockingHttpClientGroup<UnresolvedAddress> asBlockingClientGroupInternal() {
        return blockingClientGroup;
    }
}
