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

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static io.servicetalk.concurrent.internal.ThrowableUtils.catchUnexpected;
import static io.servicetalk.utils.internal.PlatformDependent.throwException;

final class QueueDynamicCompositeCancellable implements DynamicCompositeCancellable {
    private static final AtomicIntegerFieldUpdater<QueueDynamicCompositeCancellable> cancelledUpdater =
            AtomicIntegerFieldUpdater.newUpdater(QueueDynamicCompositeCancellable.class, "cancelled");
    @SuppressWarnings("unused")
    private volatile int cancelled;

    private final Queue<Cancellable> cancellables = new ConcurrentLinkedQueue<>();

    @Override
    public void cancel() {
        if (cancelledUpdater.compareAndSet(this, 0, 1)) {
            cancelAll();
        }
    }

    @Override
    public boolean add(Cancellable toAdd) {
        if (!cancellables.offer(toAdd)) {
            toAdd.cancel();
            return false;
        } else if (isCancelled()) {
            cancelAll();
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
        Cancellable cancellable;
        while ((cancellable = cancellables.poll()) != null) {
            try {
                cancellable.cancel();
            } catch (Throwable cause) {
                delayedCause = catchUnexpected(delayedCause, cause);
            }
        }
        if (delayedCause != null) {
            throwException(delayedCause);
        }
    }
}
