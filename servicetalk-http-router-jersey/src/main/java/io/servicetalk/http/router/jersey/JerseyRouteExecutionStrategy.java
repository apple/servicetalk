/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.router.jersey;

import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpExecutionStrategy;

import static java.util.Objects.requireNonNull;

class JerseyRouteExecutionStrategy implements HttpExecutionStrategy {

    private final HttpExecutionStrategy delegate;
    private final Executor executor;

    JerseyRouteExecutionStrategy(final HttpExecutionStrategy delegate, Executor executor) {
        this.delegate = requireNonNull(delegate);
        this.executor = executor;
    }

    @Override
    public int hashCode() {
        return 31 * delegate.hashCode() + executor.hashCode();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final JerseyRouteExecutionStrategy that = (JerseyRouteExecutionStrategy) o;
        return delegate.equals(that.delegate) && executor.equals(that.executor);
    }

    @Override
    public boolean hasOffloads() {
        return delegate.hasOffloads();
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    @Override
    public boolean isMetadataReceiveOffloaded() {
        return delegate.isMetadataReceiveOffloaded();
    }

    @Override
    public boolean isDataReceiveOffloaded() {
        return delegate.isDataReceiveOffloaded();
    }

    @Override
    public boolean isSendOffloaded() {
        return delegate.isSendOffloaded();
    }

    @Override
    public HttpExecutionStrategy merge(final HttpExecutionStrategy other) {
        return delegate.merge(other);
    }

    @Override
    public <T> Single<T> offloadSend(final Executor fallback, final Single<T> original) {
        return delegate.offloadSend(executor(fallback), original);
    }

    @Override
    public <T> Single<T> offloadReceive(final Executor fallback, final Single<T> original) {
        return delegate.offloadReceive(executor(fallback), original);
    }

    @Override
    public <T> Publisher<T> offloadSend(final Executor fallback, final Publisher<T> original) {
        return delegate.offloadSend(executor(fallback), original);
    }

    @Override
    public <T> Publisher<T> offloadReceive(final Executor fallback, final Publisher<T> original) {
        return delegate.offloadReceive(executor(fallback), original);
    }

    public Executor executor() {
        return executor;
    }

    private Executor executor(Executor fallback) {
        return null != executor ? executor : fallback;
    }
}
