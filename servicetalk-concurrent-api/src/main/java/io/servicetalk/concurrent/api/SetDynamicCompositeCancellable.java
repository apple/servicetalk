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
import io.servicetalk.concurrent.internal.FlowControlUtils;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

import static io.servicetalk.concurrent.internal.FlowControlUtils.tryIncrementIfNotNegative;
import static io.servicetalk.concurrent.internal.ThrowableUtils.catchUnexpected;
import static io.servicetalk.utils.internal.PlatformDependent.throwException;
import static java.util.Collections.newSetFromMap;
import static java.util.concurrent.atomic.AtomicLongFieldUpdater.newUpdater;

/**
 * A {@link Cancellable} that contains other {@link Cancellable}s.
 * <p>
 * {@link Cancellable#cancel()} is propagated to all active {@link Cancellable}s. Any new {@link Cancellable} added
 * after that will be immediately cancelled.
 */
final class SetDynamicCompositeCancellable implements DynamicCompositeCancellable {
    private static final AtomicLongFieldUpdater<SetDynamicCompositeCancellable> sizeUpdater =
            newUpdater(SetDynamicCompositeCancellable.class, "size");
    @SuppressWarnings("unused")
    private volatile long size;
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
        if (sizeUpdater.getAndSet(this, -1) >= 0) {
            Throwable delayedCause = null;
            for (Cancellable c : cancellables) {
                try {
                    // Removal while iterating typically results in ConcurrentModificationException, but not for
                    // ConcurrentHashMap. We use this approach to avoid concurrent invocation of cancel() between
                    // this method and add (if they race).
                    if (cancellables.remove(c)) {
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

    @Override
    public boolean add(Cancellable toAdd) {
        if (!cancellables.add(toAdd)) {
            toAdd.cancel(); // out of memory, user has implemented equals/hashCode so there is overlap.
            return false;
        } else if (!tryIncrementIfNotNegative(sizeUpdater, this)) {
            if (cancellables.remove(toAdd)) {
                toAdd.cancel();
            }
            return false;
        }
        return true;
    }

    @Override
    public boolean remove(Cancellable toRemove) {
        if (cancellables.remove(toRemove)) {
            sizeUpdater.accumulateAndGet(this, -1, FlowControlUtils::addWithOverflowProtectionIfNotNegative);
            return true;
        }
        return false;
    }

    @Override
    public boolean isCancelled() {
        return size < 0;
    }
}
