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
import io.servicetalk.http.api.StreamingHttpClient.ReservedStreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpClient.UpgradableStreamingHttpResponse;

import static io.servicetalk.concurrent.api.Completable.error;
import static io.servicetalk.http.api.BlockingUtils.blockingToCompletable;
import static io.servicetalk.http.api.BlockingUtils.blockingToSingle;
import static java.util.Objects.requireNonNull;

final class BlockingHttpClientGroupToStreamingHttpClientGroup<UnresolvedAddress>
        extends StreamingHttpClientGroup<UnresolvedAddress> {
    private final BlockingHttpClientGroup<UnresolvedAddress> clientGroup;

    BlockingHttpClientGroupToStreamingHttpClientGroup(
            BlockingHttpClientGroup<UnresolvedAddress> clientGroup) {
        super(new HttpRequestResponseFactoryToStreamingHttpRequestResponseFactory(clientGroup.reqRespFactory));
        this.clientGroup = requireNonNull(clientGroup);
    }

    @Override
    public Single<StreamingHttpResponse> request(
            final GroupKey<UnresolvedAddress> key, final StreamingHttpRequest request) {
        return request.toRequest().flatMap(req -> blockingToSingle(() -> clientGroup.request(key, req))
                .map(HttpResponse::toStreamingResponse));
    }

    @Override
    public Single<? extends ReservedStreamingHttpConnection> reserveConnection(final GroupKey<UnresolvedAddress> key,
                                                                               final StreamingHttpRequest request) {
        return request.toRequest().flatMap(req -> blockingToSingle(() -> clientGroup.reserveConnection(key, req)
                    .asReservedStreamingConnection()));
    }

    @Override
    public Single<? extends UpgradableStreamingHttpResponse> upgradeConnection(
            final GroupKey<UnresolvedAddress> key, final StreamingHttpRequest request) {
        return request.toRequest().flatMap(req -> blockingToSingle(() -> clientGroup.upgradeConnection(key, req)
                .toStreamingResponse()));
    }

    @Override
    public Completable onClose() {
        if (clientGroup instanceof StreamingHttpClientGroupToBlockingHttpClientGroup) {
            return ((StreamingHttpClientGroupToBlockingHttpClientGroup<?>) clientGroup).onClose();
        }

        return error(new UnsupportedOperationException("unsupported type: " + clientGroup.getClass()));
    }

    @Override
    public Completable closeAsync() {
        return blockingToCompletable(clientGroup::close);
    }

    @Override
    BlockingHttpClientGroup<UnresolvedAddress> asBlockingClientGroupInternal() {
        return clientGroup;
    }
}
