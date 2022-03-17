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

import io.servicetalk.concurrent.PublisherSource.Subscription;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

import static java.lang.Math.min;

/**
 * Utilities which can be used for concurrency.
 */
public final class ConcurrentUtils {
    /**
     * {@link Thread#getId()} is defined be a positive number. The lock value can be in one of the following states:
     * <ul>
     *     <li>{@code 0} - unlocked</li>
     *     <li>{@code >0} - locked by the {@link Thread} whose {@link Thread#getId()} matches this value</li>
     *     <li>{@code <0} - locked by the {@link Thread} whose {@link Thread#getId()} matches the negative of this
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
     * Acquire a lock that is exclusively held with no re-entry, but attempts to acquire the lock while it is
     * held can be detected by {@link #releaseLock(AtomicInteger)}.
     * @param lock The {@link AtomicInteger} used to control the lock state.
     * @return {@code true} if the lock was acquired, {@code false} otherwise.
     */
    public static boolean tryAcquireLock(AtomicInteger lock) {
        for (;;) {
            final int prevEmitting = lock.get();
            if (prevEmitting == CONCURRENT_IDLE) {
                if (lock.compareAndSet(CONCURRENT_IDLE, CONCURRENT_EMITTING)) {
                    return true;
                }
            } else if (lock.compareAndSet(prevEmitting, CONCURRENT_PENDING)) {
                return false;
            }
        }
    }

    /**
     * Release a lock that was previously acquired via {@link #tryAcquireLock(AtomicInteger)}.
     * @param lock The {@link AtomicInteger} used to control the lock state.
     * @return {@code true} if the lock was released, and no other attempts were made to acquire the lock while it
     * was held. {@code false} if the lock was released but another attempt was made to acquire the lock before it was
     * released.
     */
    public static boolean releaseLock(AtomicInteger lock) {
        return lock.getAndSet(CONCURRENT_IDLE) == CONCURRENT_EMITTING;
    }

    /**
     * Acquire a lock that allows reentry and attempts to acquire the lock while it is
     * held can be detected by {@link #releaseReentrantLock(AtomicLongFieldUpdater, long, Object)}.
     * <p>
     * This lock <strong>must</strong> eventually be released by the same thread that acquired the lock. If the thread
     * that acquires this lock is terminated before releasing the lock state is undefined.
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
     * @return {@code true} if the lock was released, or releases a prior re-entrant acquire, and no other attempts were
     * made to acquire the lock while it was held. {@code false} if the lock was released but another attempt was made
     * to acquire the lock before it was released.
     */
    public static <T> boolean releaseReentrantLock(final AtomicLongFieldUpdater<T> lockUpdater,
                                                   final long acquireId, final T owner) {
        assert acquireId != REENTRANT_LOCK_ZERO_THREAD_ID;
        return acquireId < REENTRANT_LOCK_ZERO_THREAD_ID ||
                lockUpdater.getAndSet(owner, REENTRANT_LOCK_ZERO_THREAD_ID) == acquireId;
    }

    /**
     * Attempts to increment {@code sourceRequestedUpdater} in order to make it the same as {@code requestNUpdater}
     * while not exceeding the {@code limit}. Note that the return value maybe larger than {@code limit} if
     * there is "stolen" demand (e.g. {@code emitting > sourceRequested}). In this case the assumed peer sources have
     * requested signals, and instead the signals were delivered to the local source.
     * @param requestNUpdater The total number which has been requested (typically from
     * {@link Subscription#request(long)}).
     * @param sourceRequestedUpdater The total number which has actually been passed to
     * {@link Subscription#request(long)}. This outstanding count
     * {@code sourceRequestedUpdater - emittedUpdater} should not exceed {@code limit} unless there are peer sources
     * which may result in "unsolicited" emissions.
     * @param emittedUpdater The amount of data that has been emitted/delivered by the source.
     * @param limit The maximum outstanding demand from the source at any given time.
     * @param owner The object which all atomic updater parameters are associated with.
     * @param <T> The type of object which owns the atomic updater parameters.
     * @return The amount which {@code sourceRequestedUpdater} was increased plus any "stolen" demand. This value is
     * typically used for upstream {@link Subscription#request(long)} calls.
     */
    public static <T> long calculateSourceRequested(final AtomicLongFieldUpdater<T> requestNUpdater,
                                                    final AtomicLongFieldUpdater<T> sourceRequestedUpdater,
                                                    final AtomicLongFieldUpdater<T> emittedUpdater,
                                                    final int limit, final T owner) {
        for (;;) {
            final long sourceRequested = sourceRequestedUpdater.get(owner);
            final long requested = requestNUpdater.get(owner);
            assert sourceRequested <= requested;
            if (requested == sourceRequested) {
                return 0;
            }
            final long emitted = emittedUpdater.get(owner);
            // Connected sources (like each Publisher in a group-by) may buffer data before requesting as the peer
            // source could have requested the data. In such cases, the source would drain and then call this method
            // leading to emitted > sourceRequested
            if (emitted > requested) {
                // In a single threaded world this should never happen, but in multi-threaded scenarios it is possible
                // the Subscriber thread invokes this method and hasn't yet observed an update to requested on the
                // Subscription thread. In this case the Subscription thread MUST invoke this method as well and will
                // fall into one of the respective cases below.
                return 0;
            } else if (emitted >= sourceRequested) {
                // sourceRequested ... emitted ...[delta]... requested
                final long delta = requested - emitted;
                final int toRequest = (int) min(limit, delta);
                if (sourceRequestedUpdater.compareAndSet(owner, sourceRequested, emitted + toRequest)) {
                    // (emitted - sourceRequested) is the "stolen" amount (emissions with no corresponding demand).
                    // We need to give the demand back to upstream or else peer sources may be starved of demand.
                    return toRequest + (emitted - sourceRequested);
                }
            } else {
                // emitted ...[outstanding]... sourceRequested ...[delta]... requested
                final long outstanding = sourceRequested - emitted;
                final long delta = requested - sourceRequested;
                final int toRequest = (int) min(limit - outstanding, delta);
                if (sourceRequestedUpdater.compareAndSet(owner, sourceRequested, sourceRequested + toRequest)) {
                    return toRequest;
                }
            }
        }
    }
}
