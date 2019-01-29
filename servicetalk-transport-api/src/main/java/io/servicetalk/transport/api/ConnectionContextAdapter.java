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
package io.servicetalk.transport.api;

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;

import java.net.SocketAddress;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;

import static java.util.Objects.requireNonNull;

/**
 * A {@link ConnectionContext} implementation that delegates all calls to a provided {@link ConnectionContext}. Any of
 * the methods can be overridden by implementations to change the behavior.
 */
public class ConnectionContextAdapter implements ConnectionContext {

    private final ConnectionContext delegate;

    /**
     * New instance.
     *
     * @param delegate {@link ConnectionContext} to delegate all calls.
     */
    public ConnectionContextAdapter(final ConnectionContext delegate) {
        this.delegate = requireNonNull(delegate);
    }

    /**
     * Get the {@link ConnectionContext} that this class delegates to.
     *
     * @return the {@link ConnectionContext} that this class delegates to.
     */
    protected final ConnectionContext delegate() {
        return delegate;
    }

    @Override
    public SocketAddress localAddress() {
        return delegate.localAddress();
    }

    @Override
    public SocketAddress remoteAddress() {
        return delegate.remoteAddress();
    }

    @Nullable
    @Override
    public SSLSession sslSession() {
        return delegate.sslSession();
    }

    @Override
    public ExecutionContext executionContext() {
        return delegate.executionContext();
    }

    @Override
    public Single<Throwable> transportError() {
        return delegate.transportError();
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
