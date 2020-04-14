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

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

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
     * held can be detected by {@link #releaseLock(AtomicIntegerFieldUpdater, Object)}.
     * @param lockUpdater The {@link AtomicIntegerFieldUpdater} used to control the lock state.
     * @param owner The owner of the lock object.
     * @param <T> The type of object that owns the lock.
     * @return {@code true} if the lock was acquired, {@code false} otherwise.
     */
    public static <T> boolean tryAcquireLock(AtomicIntegerFieldUpdater<T> lockUpdater, T owner) {
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
     * Release a lock that was previously acquired via {@link #tryAcquireLock(AtomicIntegerFieldUpdater, Object)}.
     * @param lockUpdater The {@link AtomicIntegerFieldUpdater} used to control the lock state.
     * @param owner The owner of the lock object.
     * @param <T> The type of object that owns the lock.
     * @return {@code true} if the lock was released, and no other attempts were made to acquire the lock while it
     * was held. {@code false} if the lock was released but another attempt was made to acquire the lock before it was
     * released.
     */
    public static <T> boolean releaseLock(AtomicIntegerFieldUpdater<T> lockUpdater, T owner) {
        return lockUpdater.getAndSet(owner, CONCURRENT_IDLE) == CONCURRENT_EMITTING;
    }
}
