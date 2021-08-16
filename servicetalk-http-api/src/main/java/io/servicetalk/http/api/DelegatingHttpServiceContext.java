/*
 * Copyright © 2018, 2020-2021 Apple Inc. and the ServiceTalk project authors
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

import java.net.SocketAddress;
import java.net.SocketOption;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;

/**
 * An implementation of {@link HttpServiceContext} that delegates all calls to a provided {@link HttpServiceContext}.
 * Any method can be overridden to change this default behavior.
 */
public class DelegatingHttpServiceContext extends HttpServiceContext {

    private final HttpServiceContext delegate;

    /**
     * New instance.
     *
     * @param other {@link HttpServiceContext} to delegate all calls.
     */
    public DelegatingHttpServiceContext(final HttpServiceContext other) {
        super(other);
        this.delegate = other;
    }

    /**
     * Returns the delegate {@link HttpServiceContext}.
     *
     * @return the delegate {@link HttpServiceContext}.
     */
    public HttpServiceContext delegate() {
        return delegate;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{delegate=" + delegate + "}";
    }

    @Override
    public SocketAddress localAddress() {
        return delegate.localAddress();
    }

    @Override
    public SocketAddress remoteAddress() {
        return delegate.remoteAddress();
    }

    @Override
    @Nullable
    public SSLSession sslSession() {
        return delegate.sslSession();
    }

    @Override
    public HttpExecutionContext executionContext() {
        return delegate.executionContext();
    }

    @Nullable
    @Override
    public <T> T socketOption(final SocketOption<T> option) {
        return delegate.socketOption(option);
    }

    @Override
    public HttpProtocolVersion protocol() {
        return delegate.protocol();
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
