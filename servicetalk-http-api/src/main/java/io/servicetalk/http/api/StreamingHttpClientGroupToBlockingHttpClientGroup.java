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
import io.servicetalk.http.api.HttpClient.UpgradableHttpResponse;
import io.servicetalk.http.api.StreamingHttpClient.ReservedStreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpClient.UpgradableStreamingHttpResponse;

import static io.servicetalk.http.api.BlockingUtils.blockingInvocation;
import static java.util.Objects.requireNonNull;

final class StreamingHttpClientGroupToBlockingHttpClientGroup<UnresolvedAddress>
        extends BlockingHttpClientGroup<UnresolvedAddress> {
    private final StreamingHttpClientGroup<UnresolvedAddress> clientGroup;

    StreamingHttpClientGroupToBlockingHttpClientGroup(StreamingHttpClientGroup<UnresolvedAddress> clientGroup) {
        super(new StreamingHttpRequestResponseFactoryToHttpRequestResponseFactory(clientGroup.reqRespFactory));
        this.clientGroup = requireNonNull(clientGroup);
    }

    @Override
    public HttpResponse request(
            final GroupKey<UnresolvedAddress> key, final HttpRequest request) throws Exception {
        return blockingInvocation(clientGroup.request(key, request.toStreamingRequest())
                .flatMap(StreamingHttpResponse::toResponse));
    }

    @Override
    public ReservedBlockingHttpConnection reserveConnection(final GroupKey<UnresolvedAddress> key,
                                                            final HttpRequest request) throws Exception {
        return blockingInvocation(clientGroup.reserveConnection(key, request.toStreamingRequest())
                .map(ReservedStreamingHttpConnection::asReservedBlockingConnection));
    }

    @Override
    public UpgradableHttpResponse upgradeConnection(
            final GroupKey<UnresolvedAddress> key, final HttpRequest request) throws Exception {
        return blockingInvocation(clientGroup.upgradeConnection(key, request.toStreamingRequest())
                .flatMap(UpgradableStreamingHttpResponse::toResponse));
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
