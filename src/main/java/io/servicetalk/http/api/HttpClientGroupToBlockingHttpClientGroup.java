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
import io.servicetalk.http.api.BlockingHttpClient.BlockingReservedHttpConnection;

import static io.servicetalk.concurrent.internal.Await.awaitIndefinitelyNonNull;
import static io.servicetalk.http.api.HttpRequests.fromBlockingRequest;
import static java.util.Objects.requireNonNull;

final class HttpClientGroupToBlockingHttpClientGroup<UnresolvedAddress> extends
                                                                  BlockingHttpClientGroup<UnresolvedAddress> {
    private final HttpClientGroup<UnresolvedAddress> clientGroup;

    HttpClientGroupToBlockingHttpClientGroup(HttpClientGroup<UnresolvedAddress> clientGroup) {
        this.clientGroup = requireNonNull(clientGroup);
    }

    @Override
    public BlockingHttpResponse<HttpPayloadChunk> request(final GroupKey<UnresolvedAddress> key,
                                                          final BlockingHttpRequest<HttpPayloadChunk> request)
            throws Exception {
        // It is assumed that users will always apply timeouts at the HttpService layer (e.g. via filter). So we don't
        // apply any explicit timeout here and just wait forever.
        return new DefaultBlockingHttpResponse<>(
                awaitIndefinitelyNonNull(clientGroup.request(key, fromBlockingRequest(request))));
    }

    @Override
    public BlockingReservedHttpConnection reserveConnection(final GroupKey<UnresolvedAddress> key,
                                                            final BlockingHttpRequest<HttpPayloadChunk> request)
            throws Exception {
        // It is assumed that users will always apply timeouts at the HttpService layer (e.g. via filter). So we don't
        // apply any explicit timeout here and just wait forever.
        return awaitIndefinitelyNonNull(clientGroup.reserveConnection(key, fromBlockingRequest(request)))
                .asBlockingReservedConnection();
    }

    @Override
    public void close() throws Exception {
        BlockingUtils.close(clientGroup);
    }

    Completable onClose() {
        return clientGroup.onClose();
    }

    @Override
    HttpClientGroup<UnresolvedAddress> asClientGroupInternal() {
        return clientGroup;
    }
}
