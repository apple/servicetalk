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

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

import static io.servicetalk.concurrent.internal.ThrowableUtils.catchUnexpected;
import static io.servicetalk.utils.internal.PlatformDependent.throwException;
import static java.util.Collections.newSetFromMap;
import static java.util.concurrent.atomic.AtomicLongFieldUpdater.newUpdater;

final class CancellableSet implements Cancellable {
    private static final AtomicLongFieldUpdater<CancellableSet> counterUpdater =
            newUpdater(CancellableSet.class, "counter");
    private final Set<Cancellable> set;
    private volatile long counter;

    /**
     * Create a new instance.
     */
    CancellableSet() {
        this(8);
    }

    /**
     * Create a new instance.
     * @param initialSize The initial size of the internal {@link Set}.
     */
    CancellableSet(int initialSize) {
        set = newSetFromMap(new ConcurrentHashMap<>(initialSize));
    }

    /**
     * {@inheritDoc}
     * <p>
     * Cancel all {@link Cancellable} that have been previously added via {@link #add(Cancellable)} which have not yet
     * been cancelled, and all future {@link Cancellable}s added via {@link #add(Cancellable)} will also be cancelled.
     */
    @Override
    public void cancel() {
        if (counterUpdater.getAndSet(this, Long.MIN_VALUE) >= 0) {
            Throwable delayedCause = null;
            for (Cancellable c : set) {
                try {
                    // Removal while iterating typically results in ConcurrentModificationException, but not for
                    // ConcurrentHashMap. We use this approach to avoid concurrent invocation of cancel() between
                    // this method and add (if they race).
                    if (set.remove(c)) {
                        c.cancel();
                    }
                } catch (Throwable cause) {
                    delayedCause = catchUnexpected(delayedCause, cause);
                }
            }

            if (delayedCause != null) {
                throwException(delayedCause);
            }
        }
    }

    /**
     * Add a {@link Cancellable} that will be cancelled when this object's {@link #cancel()} method is called,
     * or be cancelled immediately if this object's {@link #cancel()} method has already been called.
     * @param toAdd The {@link Cancellable} to add.
     * @return {@code true} if the {@code toAdd} was added, and {@code false} if {@code toAdd} was not added because
     * it already exists. If {@code false} then {@link Cancellable#cancel()} will be called unless the reason is
     * {@code toAdd} has already been added.
     */
    boolean add(Cancellable toAdd) {
        if (counter < 0) { // make best effort to avoid the set if cancelled already
            toAdd.cancel();
            return false;
        } else if (!set.add(toAdd)) {
            return false; // user has implemented equals/hashCode so there is overlap?
        } else if (counterUpdater.incrementAndGet(this) <= 0) {
            // We must check the state after we insert into the set as the terminal event could have raced with this
            // method and we need to ensure the subscriber is terminated.
            // We don't check for overflow as we assume we will never exceed Long number of elements.
            if (set.remove(toAdd)) {
                toAdd.cancel();
            }
            return false;
        }
        return true;
    }

    /**
     * Remove a {@link Cancellable} such that it will no longer be cancelled when this object's {@link #cancel()} method
     * is called.
     * @param toRemove The {@link Cancellable} to remove.
     * @return {@code true} if {@code toRemove} was found and removed.
     */
    boolean remove(Cancellable toRemove) {
        return set.remove(toRemove);
    }

    /**
     * Determine if {@link #cancel()} has been called.
     * @return {@code true} if {@link #cancel()} has been called.
     */
    boolean isCancelled() {
        return counter < 0;
    }
}
