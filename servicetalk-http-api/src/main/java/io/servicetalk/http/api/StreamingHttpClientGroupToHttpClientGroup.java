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
import io.servicetalk.http.api.HttpClient.ReservedHttpConnection;
import io.servicetalk.http.api.HttpClient.UpgradableHttpResponse;
import io.servicetalk.http.api.StreamingHttpClient.UpgradableStreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpClientToHttpClient.ReservedStreamingHttpConnectionToReservedHttpConnection;

import static java.util.Objects.requireNonNull;

final class StreamingHttpClientGroupToHttpClientGroup<UnresolvedAddress> extends
                                                                         HttpClientGroup<UnresolvedAddress> {
    private final StreamingHttpClientGroup<UnresolvedAddress> clientGroup;

    StreamingHttpClientGroupToHttpClientGroup(StreamingHttpClientGroup<UnresolvedAddress> clientGroup) {
        super(new StreamingHttpRequestFactoryToHttpRequestFactory(clientGroup));
        this.clientGroup = requireNonNull(clientGroup);
    }

    @Override
    public Single<? extends HttpResponse> request(final GroupKey<UnresolvedAddress> key, final HttpRequest request) {
        return clientGroup.request(key, request.toStreamingRequest()).flatMap(StreamingHttpResponse::toResponse);
    }

    @Override
    public Single<? extends ReservedHttpConnection> reserveConnection(
            final GroupKey<UnresolvedAddress> key, final HttpRequest request) {
        return clientGroup.reserveConnection(key, request.toStreamingRequest())
                .map(ReservedStreamingHttpConnectionToReservedHttpConnection::new);
    }

    @Override
    public Single<? extends UpgradableHttpResponse> upgradeConnection(
            final GroupKey<UnresolvedAddress> key, final HttpRequest request) {
        return clientGroup.upgradeConnection(key, request.toStreamingRequest())
                .flatMap(UpgradableStreamingHttpResponse::toResponse);
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
    StreamingHttpClientGroup<UnresolvedAddress> asStreamingClientGroupInternal() {
        return clientGroup;
    }
}
