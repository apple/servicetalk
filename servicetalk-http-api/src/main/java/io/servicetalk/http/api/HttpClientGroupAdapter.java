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

/**
 * A {@link HttpClientGroup} that delegates all methods to a different {@link HttpClientGroup}.
 * @param <UnresolvedAddress> The address type used to create new {@link HttpClient}s.
 */
public abstract class HttpClientGroupAdapter<UnresolvedAddress> extends HttpClientGroup<UnresolvedAddress> {
    private final HttpClientGroup<UnresolvedAddress> delegate;

    /**
     * Create a new instance.
     *
     * @param delegate The {@link HttpClientGroup} to delegate all calls to.
     */
    protected HttpClientGroupAdapter(final HttpClientGroup<UnresolvedAddress> delegate) {
        super(delegate.reqRespFactory);
        this.delegate = delegate;
    }

    /**
     * Get the {@link HttpClientGroup} that this class delegates to.
     * @return the {@link HttpClientGroup} that this class delegates to.
     */
    protected final HttpClientGroup<UnresolvedAddress> getDelegate() {
        return delegate;
    }

    @Override
    public Single<? extends HttpResponse> request(final GroupKey<UnresolvedAddress> key, final HttpRequest request) {
        return delegate.request(key, request);
    }

    @Override
    public Single<? extends ReservedHttpConnection> reserveConnection(final GroupKey<UnresolvedAddress> key,
                                                                      final HttpRequest request) {
        return delegate.reserveConnection(key, request);
    }

    @Override
    public Single<? extends UpgradableHttpResponse> upgradeConnection(final GroupKey<UnresolvedAddress> key,
                                                                      final HttpRequest request) {
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

    @Override
    public String toString() {
        return HttpClientGroupAdapter.class.getSimpleName() + "(" + delegate + ")";
    }
}
