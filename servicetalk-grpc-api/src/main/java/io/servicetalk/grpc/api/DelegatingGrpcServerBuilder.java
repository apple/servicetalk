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

import io.servicetalk.concurrent.api.Single;

import java.time.Duration;

import static java.util.Objects.requireNonNull;

/**
 * A {@link GrpcServerBuilder} that delegates all methods to another {@link GrpcServerBuilder}.
 */
public class DelegatingGrpcServerBuilder implements GrpcServerBuilder {

    private GrpcServerBuilder delegate;

    public DelegatingGrpcServerBuilder(final GrpcServerBuilder delegate) {
        this.delegate = requireNonNull(delegate);
    }

    /**
     * Returns the {@link GrpcServerBuilder} delegate.
     *
     * @return Delegate {@link GrpcServerBuilder}.
     */
    protected final GrpcServerBuilder delegate() {
        return delegate;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{delegate=" + delegate() + '}';
    }

    @Override
    public GrpcServerBuilder initializeHttp(final HttpInitializer initializer) {
        delegate = delegate.initializeHttp(initializer);
        return this;
    }

    @Override
    public GrpcServerBuilder defaultTimeout(final Duration defaultTimeout) {
        delegate = delegate.defaultTimeout(defaultTimeout);
        return this;
    }

    @Override
    public GrpcServerBuilder appendTimeoutFilter(final boolean append) {
        delegate = delegate.appendTimeoutFilter(false);
        return this;
    }

    @Override
    public GrpcServerBuilder lifecycleObserver(final GrpcLifecycleObserver lifecycleObserver) {
        delegate = delegate.lifecycleObserver(lifecycleObserver);
        return this;
    }

    @Override
    public Single<GrpcServerContext> listen(final GrpcBindableService<?>... services) {
        return delegate.listen(services);
    }

    @Override
    public Single<GrpcServerContext> listen(final GrpcServiceFactory<?>... serviceFactories) {
        return delegate.listen(serviceFactories);
    }

    @Override
    public GrpcServerContext listenAndAwait(final GrpcServiceFactory<?>... serviceFactories) throws Exception {
        return delegate.listenAndAwait(serviceFactories);
    }

    @Override
    public GrpcServerContext listenAndAwait(final GrpcBindableService<?>... services) throws Exception {
        return delegate.listenAndAwait(services);
    }
}
