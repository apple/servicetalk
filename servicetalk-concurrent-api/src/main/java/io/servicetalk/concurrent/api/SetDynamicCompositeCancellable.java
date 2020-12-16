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
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import javax.annotation.Nullable;

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
    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<SetDynamicCompositeCancellable, Set> setUpdater =
            AtomicReferenceFieldUpdater.newUpdater(SetDynamicCompositeCancellable.class, Set.class, "set");
    @Nullable
    private volatile Set<Cancellable> set;

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
        set = newSetFromMap(new ConcurrentHashMap<>(initialSize));
    }

    @Override
    public void cancel() {
        @SuppressWarnings("unchecked")
        final Set<Cancellable> currentSet = (Set<Cancellable>) setUpdater.getAndSet(this, null);
        if (currentSet != null) {
            Throwable delayedCause = null;
            for (Cancellable c : currentSet) {
                try {
                    // Removal while iterating typically results in ConcurrentModificationException, but not for
                    // ConcurrentHashMap. We use this approach to avoid concurrent invocation of cancel() between
                    // this method and add (if they race).
                    if (currentSet.remove(c)) {
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
        final Set<Cancellable> currentSet = set;
        if (currentSet == null) {
            toAdd.cancel();
            return false;
        } else if (!currentSet.add(toAdd)) {
            return false; // user has implemented equals/hashCode so there is overlap?
        } else if (!setUpdater.compareAndSet(this, currentSet, currentSet)) {
            if (currentSet.remove(toAdd)) {
                toAdd.cancel();
            }
            return false;
        }
        return true;
    }

    @Override
    public boolean remove(Cancellable toRemove) {
        final Set<Cancellable> currentSet = set;
        return currentSet != null && currentSet.remove(toRemove);
    }

    @Override
    public boolean isCancelled() {
        return set == null;
    }
}
