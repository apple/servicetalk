/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.grpc.api;

import java.time.Duration;

import static java.util.Objects.requireNonNull;

/**
 * A {@link GrpcClientBuilder} that delegates all methods to another {@link GrpcClientBuilder}.
 *
 * @param <U> the type of address before resolution (unresolved address)
 * @param <R> the type of address after resolution (resolved address)
 */
public class DelegatingGrpcClientBuilder<U, R> implements GrpcClientBuilder<U, R> {

    private GrpcClientBuilder<U, R> delegate;

    public DelegatingGrpcClientBuilder(final GrpcClientBuilder<U, R> delegate) {
        this.delegate = requireNonNull(delegate);
    }

    /**
     * Returns the {@link GrpcClientBuilder} delegate.
     *
     * @return Delegate {@link GrpcClientBuilder}.
     */
    protected final GrpcClientBuilder<U, R> delegate() {
        return delegate;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{delegate=" + delegate() + '}';
    }

    @Override
    public GrpcClientBuilder<U, R> initializeHttp(final HttpInitializer<U, R> initializer) {
        delegate = delegate.initializeHttp(initializer);
        return this;
    }

    @Override
    public GrpcClientBuilder<U, R> defaultTimeout(final Duration defaultTimeout) {
        delegate = delegate.defaultTimeout(defaultTimeout);
        return this;
    }

    @Override
    public <Client extends GrpcClient<?>> Client build(final GrpcClientFactory<Client, ?> clientFactory) {
        return delegate.build(clientFactory);
    }

    @Override
    public <BlockingClient extends BlockingGrpcClient<?>> BlockingClient buildBlocking(
            final GrpcClientFactory<?, BlockingClient> clientFactory) {
        return delegate.buildBlocking(clientFactory);
    }

    @Override
    public MultiClientBuilder buildMulti() {
        return delegate.buildMulti();
    }
}
