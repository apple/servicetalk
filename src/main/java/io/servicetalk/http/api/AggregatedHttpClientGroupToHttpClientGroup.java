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
import io.servicetalk.http.api.AggregatedHttpClientToHttpClient.AggregatedToReservedHttpConnection;
import io.servicetalk.http.api.HttpClient.ReservedHttpConnection;

import static io.servicetalk.http.api.DefaultAggregatedHttpRequest.from;
import static java.util.Objects.requireNonNull;

final class AggregatedHttpClientGroupToHttpClientGroup<UnresolvedAddress> extends HttpClientGroup<UnresolvedAddress> {
    private final AggregatedHttpClientGroup<UnresolvedAddress> aggregatedGroup;

    AggregatedHttpClientGroupToHttpClientGroup(AggregatedHttpClientGroup<UnresolvedAddress> aggregatedGroup) {
        this.aggregatedGroup = requireNonNull(aggregatedGroup);
    }

    @Override
    public Single<HttpResponse<HttpPayloadChunk>> request(final GroupKey<UnresolvedAddress> key,
                                                          final HttpRequest<HttpPayloadChunk> request) {
        return from(request, key.getExecutionContext().getBufferAllocator()).flatMap(aggregatedRequest ->
                        aggregatedGroup.request(key, aggregatedRequest))
                .map(DefaultAggregatedHttpResponse::toHttpResponse);
    }

    @Override
    public Single<? extends ReservedHttpConnection> reserveConnection(final GroupKey<UnresolvedAddress> key,
                                                                      final HttpRequest<HttpPayloadChunk> request) {
        return from(request, key.getExecutionContext().getBufferAllocator()).flatMap(aggregatedRequest ->
                aggregatedGroup.reserveConnection(key, aggregatedRequest)).map(AggregatedToReservedHttpConnection::new);
    }

    @Override
    public Completable onClose() {
        return aggregatedGroup.onClose();
    }

    @Override
    public Completable closeAsync() {
        return aggregatedGroup.closeAsync();
    }

    @Override
    AggregatedHttpClientGroup<UnresolvedAddress> asAggregatedClientGroupInternal() {
        return aggregatedGroup;
    }
}
