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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.internal.SignalOffloader;
import io.servicetalk.concurrent.internal.SignalOffloaderFactory;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static io.servicetalk.concurrent.internal.SignalOffloaders.newTaskBasedOffloader;
import static io.servicetalk.concurrent.internal.SignalOffloaders.newThreadBasedOffloader;
import static java.util.Objects.requireNonNull;

/**
 * An {@link Executor} which is also a {@link SignalOffloaderFactory}.
 */
public final class OffloaderAwareExecutor implements Executor, SignalOffloaderFactory {

    private final Executor delegate;
    private final boolean threadBased;

    /**
     * New instance.
     *
     * @param delegate {@link Executor} to use.
     * @param threadBased If {@link SignalOffloader} created by this {@link Executor} will have thread affinity.
     */
    public OffloaderAwareExecutor(final Executor delegate, boolean threadBased) {
        this.delegate = requireNonNull(delegate);
        this.threadBased = threadBased;
    }

    @Override
    public Cancellable execute(final Runnable task) throws RejectedExecutionException {
        return delegate.execute(task);
    }

    @Override
    public Cancellable schedule(final Runnable task, final long delay, final TimeUnit unit)
            throws RejectedExecutionException {
        return delegate.schedule(task, delay, unit);
    }

    @Override
    public Completable closeAsync() {
        return delegate.closeAsync();
    }

    @Override
    public SignalOffloader newSignalOffloader(final io.servicetalk.concurrent.Executor executor) {
        return threadBased ? newThreadBasedOffloader(delegate) : newTaskBasedOffloader(delegate);
    }

    @Override
    public boolean threadAffinity() {
        return threadBased;
    }

    @Override
    public Completable onClose() {
        return delegate.onClose();
    }
}
