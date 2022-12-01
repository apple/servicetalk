/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.Cancellable;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

final class ContextPreservingStExecutor implements Executor {
    private final Executor delegate;

    private ContextPreservingStExecutor(Executor delegate) {
        this.delegate = requireNonNull(delegate);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{delegate=" + delegate + '}';
    }

    @Override
    public Cancellable execute(final Runnable task) {
        return delegate.execute(new ContextPreservingRunnable(task));
    }

    @Override
    public Cancellable schedule(final Runnable task, final long delay, final TimeUnit unit) {
        return delegate.schedule(new ContextPreservingRunnable(task), delay, unit);
    }

    @Override
    public Cancellable schedule(final Runnable task, final Duration delay) {
        return delegate.schedule(new ContextPreservingRunnable(task), delay);
    }

    @Override
    public long currentTime(TimeUnit unit) {
        return delegate.currentTime(unit);
    }

    @Override
    public Completable onClose() {
        return delegate.onClose();
    }

    @Override
    public Completable onClosing() {
        return delegate.onClosing();
    }

    @Override
    public Completable closeAsync() {
        return delegate.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return delegate.closeAsyncGracefully();
    }

    static Executor of(Executor delegate) {
        return delegate instanceof ContextPreservingStExecutor ? delegate :
                new ContextPreservingStExecutor(delegate);
    }

    // Don't override methods that return an async source. Context will be captured at subscribe by the async source.
}
