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

import java.util.Queue;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.Consumer;

/**
 * Utilities which can be used for concurrency.
 */
public final class ConcurrentUtils {
    private static final int CONCURRENT_IDLE = 0;
    private static final int CONCURRENT_EMITTING = 1;
    private static final int CONCURRENT_PENDING = 2;

    private ConcurrentUtils() {
        // No instances.
    }

    /**
     * Acquire a lock that is exclusively held with no re-entry, but attempts to acquire the lock while it is
     * held can be detected by {@link #releasePendingLock(AtomicIntegerFieldUpdater, Object)}.
     * @param lockUpdater The {@link AtomicIntegerFieldUpdater} used to control the lock state.
     * @param owner The owner of the lock object.
     * @param <T> The type of object that owns the lock.
     * @return {@code true} if the lock was acquired, {@code false} otherwise.
     */
    public static <T> boolean acquirePendingLock(AtomicIntegerFieldUpdater<T> lockUpdater, T owner) {
        for (;;) {
            final int prevEmitting = lockUpdater.get(owner);
            if (prevEmitting == CONCURRENT_IDLE) {
                if (lockUpdater.compareAndSet(owner, CONCURRENT_IDLE, CONCURRENT_EMITTING)) {
                    return true;
                }
            } else if (lockUpdater.compareAndSet(owner, prevEmitting, CONCURRENT_PENDING)) {
                return false;
            }
        }
    }

    /**
     * Release a lock that was previously acquired via {@link #acquirePendingLock(AtomicIntegerFieldUpdater, Object)}.
     * @param lockUpdater The {@link AtomicIntegerFieldUpdater} used to control the lock state.
     * @param owner The owner of the lock object.
     * @param <T> The type of object that owns the lock.
     * @return {@code true} if the lock was released, and no other attempts were made to acquire the lock while it
     * was held. {@code false} if the lock was released but another attempt was made to acquire the lock before it was
     * released.
     */
    public static <T> boolean releasePendingLock(AtomicIntegerFieldUpdater<T> lockUpdater, T owner) {
        return lockUpdater.getAndSet(owner, CONCURRENT_IDLE) == CONCURRENT_EMITTING;
    }

    /**
     * Drains the passed single-consumer {@link Queue} and ensures that it is empty before returning.
     * This accounts for any additions to the {@link Queue} while drain is in progress.
     * Multiple threads can call this method concurrently but only one thread will actively drain the {@link Queue}.
     *
     * @param queue {@link Queue} to drain.
     * @param forEach {@link Consumer} for each item that is drained.
     * @param drainActiveUpdater An {@link AtomicIntegerFieldUpdater} for an {@code int} that is used to guard against
     * concurrent drains.
     * @param flagOwner Holding instance for {@code drainActiveUpdater}.
     * @param <T> Type of items stored in the {@link Queue}.
     * @param <R> Type of the object holding the {@link int} referred by {@link AtomicIntegerFieldUpdater}.
     * @return Number of items drained from the queue.
     */
    public static <T, R> long drainSingleConsumerQueue(final Queue<T> queue, final Consumer<T> forEach,
                                                       final AtomicIntegerFieldUpdater<R> drainActiveUpdater,
                                                       final R flagOwner) {
        long drainedCount = 0;
        do {
            if (!drainActiveUpdater.compareAndSet(flagOwner, CONCURRENT_IDLE, CONCURRENT_EMITTING)) {
                break;
            }
            try {
                T t;
                while ((t = queue.poll()) != null) {
                    ++drainedCount;
                    forEach.accept(t);
                }
            } finally {
                drainActiveUpdater.set(flagOwner, CONCURRENT_IDLE);
            }
            // We need to loop around again and check if we can acquire the "drain lock" in case there was elements
            // added after we finished draining the queue but before we released the "drain lock".
        } while (!queue.isEmpty());

        return drainedCount;
    }
}
