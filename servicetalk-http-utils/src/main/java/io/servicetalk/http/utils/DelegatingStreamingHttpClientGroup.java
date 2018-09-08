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
package io.servicetalk.http.utils;

import io.servicetalk.client.api.GroupKey;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpClientGroup;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;

import static java.util.Objects.requireNonNull;

/**
 * Wraps another {@link StreamingHttpClientGroup} and delegate all methods invocations to it.
 *
 * @param <UnresolvedAddress> The address type used to create new {@link StreamingHttpClient}s.
 */
public abstract class DelegatingStreamingHttpClientGroup<UnresolvedAddress> extends StreamingHttpClientGroup<UnresolvedAddress> {

    private final StreamingHttpClientGroup<UnresolvedAddress> delegate;

    /**
     * Creates a new instance and delegate all method invocations to the passed {@link StreamingHttpClientGroup}.
     *
     * @param delegate The {@link StreamingHttpClientGroup} to delegate to.
     */
    protected DelegatingStreamingHttpClientGroup(final StreamingHttpClientGroup<UnresolvedAddress> delegate) {
        this.delegate = requireNonNull(delegate);
    }

    @Override
    public Single<StreamingHttpResponse<HttpPayloadChunk>> request(final GroupKey<UnresolvedAddress> key,
                                                                   final StreamingHttpRequest<HttpPayloadChunk> request) {
        return delegate.request(key, request);
    }

    @Override
    public Single<? extends StreamingHttpClient.ReservedStreamingHttpConnection> reserveConnection(final GroupKey<UnresolvedAddress> key,
                                                                                                   final StreamingHttpRequest<HttpPayloadChunk> request) {
        return delegate.reserveConnection(key, request);
    }

    @Override
    public Single<? extends StreamingHttpClient.UpgradableStreamingHttpResponse<HttpPayloadChunk>> upgradeConnection(
            final GroupKey<UnresolvedAddress> key, final StreamingHttpRequest<HttpPayloadChunk> request) {
        return delegate.upgradeConnection(key, request);
    }

    @Override
    public Completable onClose() {
        return delegate.onClose();
    }

    @Override
    public Completable closeAsync() {
        return delegate.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return delegate.closeAsyncGracefully();
    }
}
