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

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static io.servicetalk.concurrent.internal.ConcurrentUtils.releaseLock;
import static io.servicetalk.concurrent.internal.ConcurrentUtils.tryAcquireLock;
import static io.servicetalk.concurrent.internal.ThrowableUtils.catchUnexpected;
import static io.servicetalk.utils.internal.PlatformDependent.throwException;
import static java.util.Collections.newSetFromMap;

/**
 * A {@link Cancellable} that contains other {@link Cancellable}s.
 * <p>
 * {@link Cancellable#cancel()} is propagated to all active {@link Cancellable}s. Any new {@link Cancellable} added
 * after that will be immediately cancelled.
 */
final class SetDynamicCompositeCancellable implements DynamicCompositeCancellable {
    private static final AtomicIntegerFieldUpdater<SetDynamicCompositeCancellable> cancelledUpdater =
            AtomicIntegerFieldUpdater.newUpdater(SetDynamicCompositeCancellable.class, "cancelled");
    private static final AtomicIntegerFieldUpdater<SetDynamicCompositeCancellable> cancelAllLockUpdater =
            AtomicIntegerFieldUpdater.newUpdater(SetDynamicCompositeCancellable.class, "cancelAllLock");
    @SuppressWarnings("unused")
    private volatile int cancelled;
    @SuppressWarnings("unused")
    private volatile int cancelAllLock;

    private final Set<Cancellable> cancellables;

    /**
     * Create a new instance.
     */
    SetDynamicCompositeCancellable() {
        this(8);
    }

    /**
     * Create a new instance.
     * @param initialSize The initial size of the internal {@link Set}.
     */
    SetDynamicCompositeCancellable(int initialSize) {
        cancellables = newSetFromMap(new ConcurrentHashMap<>(initialSize));
    }

    @Override
    public void cancel() {
        if (cancelledUpdater.compareAndSet(this, 0, 1)) {
            cancelAll();
        }
    }

    @Override
    public boolean add(Cancellable toAdd) {
        if (!cancellables.add(toAdd)) {
            toAdd.cancel(); // out of memory, or user has implemented equals/hashCode so there is overlap.
            return false;
        } else if (isCancelled()) {
            cancelAll(); // avoid concurrency on toAdd if another thread invokes cancel(), just cancel all.
            return false;
        }
        return true;
    }

    @Override
    public boolean remove(Cancellable toRemove) {
        return cancellables.remove(toRemove);
    }

    @Override
    public boolean isCancelled() {
        return cancelled != 0;
    }

    private void cancelAll() {
        Throwable delayedCause = null;
        boolean tryAcquire = true;
        while (tryAcquire && tryAcquireLock(cancelAllLockUpdater, this)) {
            try {
                Iterator<Cancellable> itr = cancellables.iterator();
                while (itr.hasNext()) {
                    try {
                        Cancellable cancellable = itr.next();
                        itr.remove();
                        cancellable.cancel();
                    } catch (Throwable cause) {
                        delayedCause = catchUnexpected(delayedCause, cause);
                    }
                }
            } finally {
                tryAcquire = !releaseLock(cancelAllLockUpdater, this);
            }
        }

        if (delayedCause != null) {
            throwException(delayedCause);
        }
    }
}
