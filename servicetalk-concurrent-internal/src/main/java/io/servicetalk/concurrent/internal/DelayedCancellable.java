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
 * A {@link Cancellable} which serves as a placeholder until the "real" {@link Cancellable} is available.
 */
public class DelayedCancellable implements Cancellable {
    private static final AtomicReferenceFieldUpdater<DelayedCancellable, Cancellable> currentUpdater =
            newUpdater(DelayedCancellable.class, Cancellable.class, "current");
    @SuppressWarnings("unused")
    @Nullable
    private volatile Cancellable current;

    /**
     * Set the delayed {@link Cancellable}. This method can only be called a single time and
     * subsequent calls will result in {@link #cancel()} being call on {@code delayedCancellable}.
     * @param delayedCancellable The delayed {@link Cancellable}.
     */
    public final void setDelayedCancellable(Cancellable delayedCancellable) {
        if (!currentUpdater.compareAndSet(this, null, requireNonNull(delayedCancellable))) {
            delayedCancellable.cancel();
        }
    }

    @Override
    public void cancel() {
        Cancellable oldCancellable = currentUpdater.getAndSet(this, IGNORE_CANCEL);
        if (oldCancellable != null) {
            oldCancellable.cancel();
        }
    }
}
