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
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpClient.ReservedHttpConnection;
import io.servicetalk.http.api.HttpClientGroup;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;

import static java.util.Objects.requireNonNull;

/**
 * Wraps another {@link HttpClientGroup} and delegate all methods invocations to it.
 *
 * @param <UnresolvedAddress> The address type used to create new {@link HttpClient}s.
 */
public abstract class DelegatingHttpClientGroup<UnresolvedAddress> extends HttpClientGroup<UnresolvedAddress> {

    private final HttpClientGroup<UnresolvedAddress> delegate;

    /**
     * Creates a new instance and delegate all method invocations to the passed {@link HttpClientGroup}.
     *
     * @param delegate The {@link HttpClientGroup} to delegate to.
     */
    protected DelegatingHttpClientGroup(final HttpClientGroup<UnresolvedAddress> delegate) {
        this.delegate = requireNonNull(delegate);
    }

    @Override
    public Single<HttpResponse<HttpPayloadChunk>> request(final GroupKey<UnresolvedAddress> key,
                                                          final HttpRequest<HttpPayloadChunk> request) {
        return delegate.request(key, request);
    }

    @Override
    public Single<? extends ReservedHttpConnection> reserveConnection(final GroupKey<UnresolvedAddress> key,
                                                                      final HttpRequest<HttpPayloadChunk> request) {
        return delegate.reserveConnection(key, request);
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
