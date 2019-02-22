/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.api.internal;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.internal.SignalOffloader;
import io.servicetalk.concurrent.internal.SignalOffloaderFactory;
import io.servicetalk.concurrent.internal.SignalOffloaders;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static io.servicetalk.concurrent.internal.SignalOffloaders.newThreadBasedOffloader;
import static java.util.Objects.requireNonNull;

/**
 * An {@link Executor} which is also a {@link SignalOffloaderFactory} and hence can influence a specific
 * {@link SignalOffloader} used by this {@link Executor}.
 */
public final class OffloaderAwareExecutor implements Executor, SignalOffloaderFactory {

    private final Executor delegate;
    private final SignalOffloaderFactory offloaderFactory;

    /**
     * New instance.
     *
     * @param delegate Actual {@link Executor} to use.
     * @param offloaderFactory {@link SignalOffloaderFactory} to use.
     */
    public OffloaderAwareExecutor(final Executor delegate, final SignalOffloaderFactory offloaderFactory) {
        this.delegate = requireNonNull(delegate);
        this.offloaderFactory = requireNonNull(offloaderFactory);
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
    public Completable onClose() {
        return delegate.onClose();
    }

    @Override
    public Completable closeAsync() {
        return delegate.closeAsync();
    }

    @Override
    public SignalOffloader newSignalOffloader(final io.servicetalk.concurrent.Executor executor) {
        return offloaderFactory.newSignalOffloader(executor);
    }

    @Override
    public boolean hasThreadAffinity() {
        return offloaderFactory.hasThreadAffinity();
    }

    /**
     * If the passed {@link Executor} does not honor thread affinity then return a new {@link Executor} that does honor
     * thread affinity.
     *
     * @param executor {@link Executor} to inspect and wrap if required.
     * @return An {@link Executor} that honors thread affinity.
     */
    public static Executor ensureThreadAffinity(final Executor executor) {
        if (SignalOffloaders.hasThreadAffinity(executor)) {
            return executor;
        }
        return new OffloaderAwareExecutor(executor, new SignalOffloaderFactory() {
            @Override
            public SignalOffloader newSignalOffloader(final io.servicetalk.concurrent.Executor executor) {
                return newThreadBasedOffloader(executor);
            }

            @Override
            public boolean hasThreadAffinity() {
                return true;
            }
        });
    }
}
