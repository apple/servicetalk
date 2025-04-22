/*
 * Copyright © 2025 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.api;

import static java.util.Objects.requireNonNull;

/**
 * {@link AsyncCloseable} that delegates all calls to another {@link AsyncCloseable}.
 *
 * @param <T> The type of {@link AsyncCloseable} to delegate to.
 */
public class DelegatingAsyncCloseable<T extends AsyncCloseable> implements AsyncCloseable {

    private final T delegate;

    /**
     * New instance.
     *
     * @param delegate {@link T} subtype of {@link AsyncCloseable} to delegate all calls to.
     */
    public DelegatingAsyncCloseable(final T delegate) {
        this.delegate = requireNonNull(delegate);
    }

    /**
     * Get the {@link T} subtype of {@link AsyncCloseable} that this class delegates to.
     *
     * @return the {@link T} subtype of {@link AsyncCloseable} that this class delegates to.
     */
    protected T delegate() {
        return delegate;
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
        return this.getClass().getSimpleName() + "{delegate=" + delegate() + "}";
    }
}
