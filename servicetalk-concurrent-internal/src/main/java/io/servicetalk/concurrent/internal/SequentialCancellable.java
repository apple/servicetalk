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
package io.servicetalk.concurrent.internal;

import io.servicetalk.concurrent.Cancellable;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

/**
 * A {@link Cancellable} that can hold at most one {@link Cancellable} that will be cancelled when this is cancelled.
 */
public class SequentialCancellable implements Cancellable {

    private static final Cancellable CANCELLED = () -> { };

    private static final AtomicReferenceFieldUpdater<SequentialCancellable, Cancellable> currentUpdater =
            newUpdater(SequentialCancellable.class, Cancellable.class, "current");
    @SuppressWarnings("unused")
    @Nullable
    private volatile Cancellable current;

    /**
     * Create a new instance with no current {@link Cancellable}.
     */
    public SequentialCancellable() {
    }

    /**
     * Create a new instance with the current {@link Cancellable} set to {@code cancellable}.
     * @param cancellable the initial {@link Cancellable}.
     */
    public SequentialCancellable(Cancellable cancellable) {
        this.current = requireNonNull(cancellable);
    }

    /**
     * Sets the current {@link Cancellable}.
     *
     * @param next to set.
     */
    public final void nextCancellable(Cancellable next) {
        Cancellable oldVal = currentUpdater.getAndSet(this, requireNonNull(next));
        if (oldVal == CANCELLED) {
            next.cancel();
        }
    }

    @Override
    public final void cancel() {
        Cancellable oldVal = currentUpdater.getAndSet(this, CANCELLED);
        if (oldVal != null) {
            oldVal.cancel();
        }
    }

    /**
     * Returns {@code true} if this {@link Cancellable} is cancelled.
     *
     * @return {@code true} if this {@link Cancellable} is cancelled.
     */
    public boolean isCancelled() {
        return current == CANCELLED;
    }
}
