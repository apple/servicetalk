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
import io.servicetalk.http.api.StreamingHttpClient.ReservedStreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpClient.UpgradableStreamingHttpResponse;

/**
 * A {@link StreamingHttpClientGroup} that delegates all methods to a different {@link StreamingHttpClientGroup}.
 */
public abstract class StreamingHttpClientGroupAdapter<UnresolvedAddress> extends
                                                                         StreamingHttpClientGroup<UnresolvedAddress> {
    private final StreamingHttpClientGroup<UnresolvedAddress> delegate;

    /**
     * Create a new instance.
     *
     * @param delegate The {@link StreamingHttpClientGroup} to delegate all calls to.
     */
    protected StreamingHttpClientGroupAdapter(final StreamingHttpClientGroup<UnresolvedAddress> delegate) {
        super(delegate.reqRespFactory);
        this.delegate = delegate;
    }

    /**
     * Get the {@link StreamingHttpClientGroup} that this class delegates to.
     * @return the {@link StreamingHttpClientGroup} that this class delegates to.
     */
    protected final StreamingHttpClientGroup<UnresolvedAddress> getDelegate() {
        return delegate;
    }

    @Override
    public Single<StreamingHttpResponse> request(final GroupKey<UnresolvedAddress> key,
                                                 final StreamingHttpRequest request) {
        return delegate.request(key, request);
    }

    @Override
    public Single<? extends ReservedStreamingHttpConnection> reserveConnection(final GroupKey<UnresolvedAddress> key,
                                                                               final StreamingHttpRequest request) {
        return delegate.reserveConnection(key, request);
    }

    @Override
    public Single<? extends UpgradableStreamingHttpResponse> upgradeConnection(
            final GroupKey<UnresolvedAddress> key, final StreamingHttpRequest request) {
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
        return StreamingHttpClientGroupAdapter.class.getSimpleName() + "(" + delegate + ")";
    }
}
