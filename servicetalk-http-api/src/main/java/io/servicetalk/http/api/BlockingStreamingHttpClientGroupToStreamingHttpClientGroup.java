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
import io.servicetalk.http.api.BlockingStreamingHttpClientToStreamingHttpClient.BlockingToReservedStreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpClient.ReservedStreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpClient.UpgradableStreamingHttpResponse;

import static io.servicetalk.concurrent.api.Completable.error;
import static io.servicetalk.http.api.BlockingUtils.blockingToCompletable;
import static io.servicetalk.http.api.BlockingUtils.blockingToSingle;
import static java.util.Objects.requireNonNull;

final class BlockingStreamingHttpClientGroupToStreamingHttpClientGroup<UnresolvedAddress> extends
                                                                      StreamingHttpClientGroup<UnresolvedAddress> {
    private final BlockingStreamingHttpClientGroup<UnresolvedAddress> blockingClientGroup;

    BlockingStreamingHttpClientGroupToStreamingHttpClientGroup(
            BlockingStreamingHttpClientGroup<UnresolvedAddress> blockingClientGroup) {
        super(new BlockingStreamingHttpRequestFactoryToStreamingHttpRequestFactory(blockingClientGroup.requestFactory),
                new BlockingStreamingHttpResponseFactoryToStreamingHttpResponseFactory(
                        blockingClientGroup.getHttpResponseFactory()));
        this.blockingClientGroup = requireNonNull(blockingClientGroup);
    }

    @Override
    public Single<StreamingHttpResponse> request(final GroupKey<UnresolvedAddress> key,
                                                 final StreamingHttpRequest request) {
        return blockingToSingle(() -> blockingClientGroup.request(key,
                request.toBlockingStreamingRequest()).toStreamingResponse());
    }

    @Override
    public Single<? extends ReservedStreamingHttpConnection> reserveConnection(
            final GroupKey<UnresolvedAddress> key, final StreamingHttpRequest request) {
        return blockingToSingle(() -> new BlockingToReservedStreamingHttpConnection(
                blockingClientGroup.reserveConnection(key, request.toBlockingStreamingRequest())));
    }

    @Override
    public Single<? extends UpgradableStreamingHttpResponse> upgradeConnection(
            final GroupKey<UnresolvedAddress> key, final StreamingHttpRequest request) {
        return blockingToSingle(() ->
                blockingClientGroup.upgradeConnection(key, request.toBlockingStreamingRequest()).toStreamingResponse());
    }

    @Override
    public Completable onClose() {
        if (blockingClientGroup instanceof StreamingHttpClientGroupToBlockingStreamingHttpClientGroup) {
            return ((StreamingHttpClientGroupToBlockingStreamingHttpClientGroup<?>) blockingClientGroup).onClose();
        }

        return error(new UnsupportedOperationException("unsupported type: " + blockingClientGroup.getClass()));
    }

    @Override
    public Completable closeAsync() {
        return blockingToCompletable(blockingClientGroup::close);
    }

    @Override
    BlockingStreamingHttpClientGroup<UnresolvedAddress> asBlockingStreamingClientGroupInternal() {
        return blockingClientGroup;
    }
}
