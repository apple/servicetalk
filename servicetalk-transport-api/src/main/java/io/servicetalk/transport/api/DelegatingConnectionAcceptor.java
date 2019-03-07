/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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

import static java.util.Objects.requireNonNull;

/**
 * An implementation of {@link ConnectionAcceptor} that delegates all methods to another {@link ConnectionAcceptor}.
 */
public class DelegatingConnectionAcceptor implements ConnectionAcceptor {

    private final ConnectionAcceptor delegate;

    /**
     * New instance.
     *
     * @param delegate {@link ConnectionAcceptor} to delegate all calls to.
     */
    public DelegatingConnectionAcceptor(final ConnectionAcceptor delegate) {
        this.delegate = requireNonNull(delegate);
    }

    @Override
    public Completable accept(final ConnectionContext context) {
        return delegate.accept(context);
    }

    @Override
    public Completable closeAsync() {
        return delegate.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return delegate.closeAsyncGracefully();
    }

    /**
     * The {@link ConnectionAcceptor} to which all calls are delegated to.
     *
     * @return {@link ConnectionAcceptor} to which all calls are delegated.
     */
    protected final ConnectionAcceptor delegate() {
        return delegate;
    }
}
