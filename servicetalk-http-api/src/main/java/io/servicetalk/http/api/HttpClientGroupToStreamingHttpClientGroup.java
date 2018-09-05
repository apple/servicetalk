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
import io.servicetalk.http.api.HttpClientToStreamingHttpClient.ReservedHttpConnectionToReservedStreamingHttpConnection;
import io.servicetalk.http.api.HttpClientToStreamingHttpClient.UpgradableHttpResponseToUpgradableStreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpClient.ReservedStreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpClient.UpgradableStreamingHttpResponse;

import static io.servicetalk.http.api.BufferHttpRequest.from;
import static java.util.Objects.requireNonNull;

final class HttpClientGroupToStreamingHttpClientGroup<UnresolvedAddress> extends
                                                                       StreamingHttpClientGroup<UnresolvedAddress> {
    private final HttpClientGroup<UnresolvedAddress> group;

    HttpClientGroupToStreamingHttpClientGroup(HttpClientGroup<UnresolvedAddress> group) {
        this.group = requireNonNull(group);
    }

    @Override
    public Single<StreamingHttpResponse<HttpPayloadChunk>> request(
            final GroupKey<UnresolvedAddress> key, final StreamingHttpRequest<HttpPayloadChunk> request) {
        return from(request, key.getExecutionContext().getBufferAllocator()).flatMap(aggregatedRequest ->
                        group.request(key, aggregatedRequest))
                .map(BufferHttpResponse::toHttpResponse);
    }

    @Override
    public Single<? extends ReservedStreamingHttpConnection> reserveConnection(
            final GroupKey<UnresolvedAddress> key, final StreamingHttpRequest<HttpPayloadChunk> request) {
        return from(request, key.getExecutionContext().getBufferAllocator()).flatMap(aggregatedRequest ->
                group.reserveConnection(key, aggregatedRequest))
                .map(ReservedHttpConnectionToReservedStreamingHttpConnection::new);
    }

    @Override
    public Single<? extends UpgradableStreamingHttpResponse<HttpPayloadChunk>> upgradeConnection(
            final GroupKey<UnresolvedAddress> key, final StreamingHttpRequest<HttpPayloadChunk> request) {
        return from(request, key.getExecutionContext().getBufferAllocator())
                .flatMap(aggregatedRequest -> group.upgradeConnection(key, aggregatedRequest))
                .map(UpgradableHttpResponseToUpgradableStreamingHttpResponse::newUpgradeResponse);
    }

    @Override
    public Completable onClose() {
        return group.onClose();
    }

    @Override
    public Completable closeAsync() {
        return group.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return group.closeAsyncGracefully();
    }

    @Override
    HttpClientGroup<UnresolvedAddress> asClientGroupInternal() {
        return group;
    }
}
