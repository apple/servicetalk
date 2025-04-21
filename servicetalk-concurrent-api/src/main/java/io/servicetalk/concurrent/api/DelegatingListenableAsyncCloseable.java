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

/**
 * {@link ListenableAsyncCloseable} that delegates all calls to another {@link ListenableAsyncCloseable}.
 */
public class DelegatingListenableAsyncCloseable extends DelegatingAsyncCloseable implements ListenableAsyncCloseable {

    private final ListenableAsyncCloseable delegate;

    /**
     * New instance.
     *
     * @param delegate {@link ListenableAsyncCloseable} to delegate all calls to.
     */
    public DelegatingListenableAsyncCloseable(final ListenableAsyncCloseable delegate) {
        super(delegate);
        this.delegate = delegate;
    }

    /**
     * Get the {@link ListenableAsyncCloseable} that this class delegates to.
     *
     * @return the {@link ListenableAsyncCloseable} that this class delegates to.
     */
    @Override
    protected ListenableAsyncCloseable delegate() {
        return delegate;
    }

    @Override
    public Completable onClose() {
        return delegate.onClose();
    }

    @Override
    public Completable onClosing() {
        return delegate.onClosing();
    }
}
