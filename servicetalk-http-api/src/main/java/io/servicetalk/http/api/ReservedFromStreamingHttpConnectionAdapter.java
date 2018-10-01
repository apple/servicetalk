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

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.StreamingHttpClient.ReservedStreamingHttpConnection;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

/**
 * A {@link ReservedStreamingHttpConnection} that delegates all methods to a different {@link StreamingHttpConnection}.
 */
public abstract class ReservedFromStreamingHttpConnectionAdapter extends ReservedStreamingHttpConnection {
    private final StreamingHttpConnection delegate;

    /**
     * Create a new instance.
     *
     * @param delegate The {@link StreamingHttpConnection} to delegate all calls to.
     */
    protected ReservedFromStreamingHttpConnectionAdapter(final StreamingHttpConnection delegate) {
        super(delegate.reqRespFactory);
        this.delegate = delegate;
    }

    /**
     * Get the {@link ReservedStreamingHttpConnection} that this class delegates to.
     * @return the {@link ReservedStreamingHttpConnection} that this class delegates to.
     */
    protected final StreamingHttpConnection delegate() {
        return delegate;
    }

    @Override
    public ConnectionContext connectionContext() {
        return delegate.connectionContext();
    }

    @Override
    public <T> Publisher<T> settingStream(final SettingKey<T> settingKey) {
        return delegate.settingStream(settingKey);
    }

    @Override
    public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy, final StreamingHttpRequest request) {
        return delegate.request(strategy, request);
    }

    @Override
    public ExecutionContext executionContext() {
        return delegate.executionContext();
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
        return ReservedFromStreamingHttpConnectionAdapter.class.getSimpleName() + "(" + delegate + ")";
    }
}
