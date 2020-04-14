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
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

/**
 * Utilities which can be used for concurrency.
 */
public final class ConcurrentUtils {
    /**
     * {@link Thread#getId()} is defined be a positive number. The lock value can be in one of the following states:
     * <ul>
     *     <li>{@code 0} - unlocked</li>
     *     <li>{@code >0} - locked by the {@link Thread} whose {@link Thread#getId()} matches this value</li>
     *     <li>{@code <0} - locked by the {@link Thread} whose {@link Thread#getId()} matches this negative of this
     *     value. Only externally visible to the thread that owns the lock on reentrant acquires.</li>
     * </ul>
     */
    private static final long REENTRANT_LOCK_ZERO_THREAD_ID = 0;
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

    /**
     * Acquire a lock that allows reentry and attempts to acquire the lock while it is
     * held can be detected by {@link #releaseReentrantLock(AtomicLongFieldUpdater, long, Object)}.
     * @param lockUpdater The {@link AtomicLongFieldUpdater} used to control the lock state.
     * @param owner The owner of the lock object.
     * @param <T> The type of object that owns the lock.
     * @return {@code 0} if the acquire was unsuccessful, otherwise an identifier that must be passed to a subsequent
     * call of {@link #releaseReentrantLock(AtomicLongFieldUpdater, long, Object)}.
     */
    public static <T> long tryAcquireReentrantLock(final AtomicLongFieldUpdater<T> lockUpdater, final T owner) {
        final long threadId = Thread.currentThread().getId();
        for (;;) {
            final long prevThreadId = lockUpdater.get(owner);
            if (prevThreadId == REENTRANT_LOCK_ZERO_THREAD_ID) {
                if (lockUpdater.compareAndSet(owner, REENTRANT_LOCK_ZERO_THREAD_ID, threadId)) {
                    return threadId;
                }
            } else if (prevThreadId == threadId || prevThreadId == -threadId) {
                return -threadId;
            } else if (lockUpdater.compareAndSet(owner, prevThreadId,
                    prevThreadId > REENTRANT_LOCK_ZERO_THREAD_ID ? -prevThreadId : prevThreadId)) {
                return REENTRANT_LOCK_ZERO_THREAD_ID;
            }
        }
    }

    /**
     * Release a lock that was previously acquired via {@link #tryAcquireReentrantLock(AtomicLongFieldUpdater, Object)}.
     * @param lockUpdater The {@link AtomicLongFieldUpdater} used to control the lock state.
     * @param acquireId The value returned from the previous call to
     * {@link #tryAcquireReentrantLock(AtomicLongFieldUpdater, Object)}.
     * @param owner The owner of the lock object.
     * @param <T> The type of object that owns the lock.
     * @return {@code true} if the lock was released, or this method call corresponds to a prior re-entrant call
     * to {@link #tryAcquireReentrantLock(AtomicLongFieldUpdater, Object)}.
     */
    public static <T> boolean releaseReentrantLock(final AtomicLongFieldUpdater<T> lockUpdater,
                                                   final long acquireId, final T owner) {
        assert acquireId != REENTRANT_LOCK_ZERO_THREAD_ID;
        return acquireId < REENTRANT_LOCK_ZERO_THREAD_ID ||
                lockUpdater.getAndSet(owner, REENTRANT_LOCK_ZERO_THREAD_ID) == acquireId;
    }
}
