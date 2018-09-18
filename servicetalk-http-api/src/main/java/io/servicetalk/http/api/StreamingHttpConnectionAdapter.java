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
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

/**
 * A {@link StreamingHttpConnection} that delegates all methods to a different {@link StreamingHttpConnection}.
 */
public abstract class StreamingHttpConnectionAdapter extends StreamingHttpConnection {
    private final StreamingHttpConnection delegate;

    /**
     * Create a new instance.
     *
     * @param delegate The {@link StreamingHttpConnection} to delegate all calls to.
     */
    protected StreamingHttpConnectionAdapter(final StreamingHttpConnection delegate) {
        super(delegate.reqRespFactory);
        this.delegate = delegate;
    }

    /**
     * Get the {@link StreamingHttpConnection} that this class delegates to.
     * @return the {@link StreamingHttpConnection} that this class delegates to.
     */
    protected final StreamingHttpConnection getDelegate() {
        return delegate;
    }

    @Override
    public ConnectionContext getConnectionContext() {
        return delegate.getConnectionContext();
    }

    @Override
    public <T> Publisher<T> getSettingStream(final SettingKey<T> settingKey) {
        return delegate.getSettingStream(settingKey);
    }

    @Override
    public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
        return delegate.request(request);
    }

    @Override
    public ExecutionContext getExecutionContext() {
        return delegate.getExecutionContext();
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
        return StreamingHttpConnectionAdapter.class.getSimpleName() + "(" + delegate + ")";
    }
}
