/*
 * Copyright © 2018, 2020 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.DelegatingListenableAsyncCloseable;

import java.net.SocketAddress;
import java.net.SocketOption;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;

/**
 * A {@link ConnectionContext} implementation that delegates all calls to a provided {@link ConnectionContext}. Any of
 * the methods can be overridden by implementations to change the behavior.
 */
public class DelegatingConnectionContext extends DelegatingListenableAsyncCloseable<ConnectionContext>
        implements ConnectionContext {

    /**
     * New instance.
     *
     * @param delegate {@link ConnectionContext} to delegate all calls.
     */
    public DelegatingConnectionContext(final ConnectionContext delegate) {
        super(delegate);
    }

    /**
     * Get the {@link ConnectionContext} that this class delegates to.
     *
     * @return the {@link ConnectionContext} that this class delegates to.
     */
    @Override
    protected ConnectionContext delegate() {
        return super.delegate();
    }

    @Override
    public String connectionId() {
        return delegate().connectionId();
    }

    @Override
    public SocketAddress localAddress() {
        return delegate().localAddress();
    }

    @Override
    public SocketAddress remoteAddress() {
        return delegate().remoteAddress();
    }

    @Nullable
    @Override
    public SslConfig sslConfig() {
        return delegate().sslConfig();
    }

    @Nullable
    @Override
    public SSLSession sslSession() {
        return delegate().sslSession();
    }

    @Override
    public ExecutionContext<?> executionContext() {
        return delegate().executionContext();
    }

    @Override
    public <T> T socketOption(final SocketOption<T> option) {
        return delegate().socketOption(option);
    }

    @Override
    public Protocol protocol() {
        return delegate().protocol();
    }

    @Nullable
    @Override
    public ConnectionContext parent() {
        return delegate().parent();
    }

    @Override
    public String toString() {
        return delegate().toString();
    }
}
