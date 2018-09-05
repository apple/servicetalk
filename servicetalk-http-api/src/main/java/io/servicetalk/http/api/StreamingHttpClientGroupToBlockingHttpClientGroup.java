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
import io.servicetalk.http.api.BlockingHttpClient.ReservedBlockingHttpConnection;

import static io.servicetalk.http.api.BlockingUtils.blockingInvocation;
import static io.servicetalk.http.api.BufferHttpRequest.toHttpRequest;
import static io.servicetalk.http.api.BufferHttpResponse.from;
import static java.util.Objects.requireNonNull;

final class StreamingHttpClientGroupToBlockingHttpClientGroup<UnresolvedAddress>
        extends BlockingHttpClientGroup<UnresolvedAddress> {
    private final StreamingHttpClientGroup<UnresolvedAddress> clientGroup;

    StreamingHttpClientGroupToBlockingHttpClientGroup(StreamingHttpClientGroup<UnresolvedAddress> clientGroup) {
        this.clientGroup = requireNonNull(clientGroup);
    }

    @Override
    public HttpResponse<HttpPayloadChunk> request(final GroupKey<UnresolvedAddress> key,
                                                  final HttpRequest<HttpPayloadChunk> request)
            throws Exception {
        return blockingInvocation(clientGroup.request(key, toHttpRequest(request)).flatMap(response ->
                            from(response, key.getExecutionContext().getBufferAllocator())));
    }

    @Override
    public ReservedBlockingHttpConnection reserveConnection(final GroupKey<UnresolvedAddress> key,
                                                            final HttpRequest<HttpPayloadChunk> request)
            throws Exception {
        return blockingInvocation(clientGroup.reserveConnection(key, toHttpRequest(request))
                .map(StreamingHttpClient.ReservedStreamingHttpConnection::asReservedBlockingConnection));
    }

    @Override
    public HttpClient.UpgradableHttpResponse<HttpPayloadChunk> upgradeConnection(final GroupKey<UnresolvedAddress> key,
                                                                                 final HttpRequest<HttpPayloadChunk> request) throws Exception {
        return blockingInvocation(clientGroup.upgradeConnection(key, toHttpRequest(request))
                .flatMap(response -> from(response, key.getExecutionContext().getBufferAllocator())
                        .map(fullResponse -> new StreamingHttpClientToHttpClient.UpgradableStreamingHttpResponseToUpgradableHttpResponse<>(response,
                                fullResponse.getPayloadBody(), fullResponse.getTrailers()))));
    }

    @Override
    public void close() throws Exception {
        blockingInvocation(clientGroup.closeAsync());
    }

    @Override
    StreamingHttpClientGroup<UnresolvedAddress> asStreamingClientGroupInternal() {
        return clientGroup;
    }

    Completable onClose() {
        return clientGroup.onClose();
    }
}
