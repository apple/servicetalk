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

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static io.servicetalk.concurrent.internal.ThrowableUtils.catchUnexpected;
import static io.servicetalk.utils.internal.PlatformDependent.throwException;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

final class QueueDynamicCompositeCancellable implements DynamicCompositeCancellable {
    private static final AtomicIntegerFieldUpdater<QueueDynamicCompositeCancellable> sizeUpdater =
            newUpdater(QueueDynamicCompositeCancellable.class, "size");
    @SuppressWarnings("unused")
    private volatile int size;
    private final Queue<Cancellable> cancellables = new ConcurrentLinkedQueue<>();

    @Override
    public void cancel() {
        if (sizeUpdater.getAndSet(this, -1) >= 0) {
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

    @Override
    public boolean add(Cancellable toAdd) {
        if (!cancellables.offer(toAdd) || (sizeUpdater.accumulateAndGet(this, 1,
                FlowControlUtils::addWithOverflowProtectionIfNotNegative) < 0 && cancellables.remove(toAdd))) {
            toAdd.cancel(); // out of memory, user has implemented equals/hashCode so there is overlap, or cancelled.
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
