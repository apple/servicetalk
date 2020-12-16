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
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.ThrowableUtils.catchUnexpected;
import static io.servicetalk.utils.internal.PlatformDependent.throwException;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

final class QueueDynamicCompositeCancellable implements DynamicCompositeCancellable {
    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<QueueDynamicCompositeCancellable, Queue> queueUpdater =
            newUpdater(QueueDynamicCompositeCancellable.class, Queue.class, "queue");
    @Nullable
    private volatile Queue<Cancellable> queue = new ConcurrentLinkedQueue<>();

    @Override
    public void cancel() {
        @SuppressWarnings("unchecked")
        final Queue<Cancellable> currentQueue = (Queue<Cancellable>) queueUpdater.getAndSet(this, null);
        if (currentQueue != null) {
            Throwable delayedCause = null;
            Cancellable cancellable;
            while ((cancellable = currentQueue.poll()) != null) {
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
        final Queue<Cancellable> currentQueue = queue;
        if (currentQueue == null || !currentQueue.offer(toAdd)) {
            toAdd.cancel();
            return false;
        } else if (!queueUpdater.compareAndSet(this, currentQueue, currentQueue)) {
            if (currentQueue.remove(toAdd)) {
                toAdd.cancel();
            }
            return false;
        }
        return true;
    }

    @Override
    public boolean remove(Cancellable toRemove) {
        final Queue<Cancellable> currentQueue = queue;
        return currentQueue != null && currentQueue.remove(toRemove);
    }

    @Override
    public boolean isCancelled() {
        return queue == null;
    }
}
