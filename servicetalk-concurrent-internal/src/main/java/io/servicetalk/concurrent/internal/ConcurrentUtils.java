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

import java.util.Collection;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.Consumer;

/**
 * Utilities which can be used for concurrency.
 */
public final class ConcurrentUtils {
    private static final int NOT_EXECUTING_EXCLUSIVE = 0;
    private static final int EXECUTING_EXCLUSIVE = 1;
    private static final int CONCURRENT_EXECUTE_EXCLUSIVE = 2;

    public static final int CONCURRENT_IDLE = 0;
    public static final int CONCURRENT_EMITTING = 1;

    private ConcurrentUtils() {
        // No instances.
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

    /**
     * Drains the passed single-consumer {@link Queue} and ensures that it is empty before returning.
     * This accounts for any additions to the {@link Queue} while drain is in progress.
     * Multiple threads can call this method concurrently but only one thread will actively drain the {@link Queue}.
     * Any {@link Throwable} thrown by {@code forEach} {@link Consumer} does not terminate draining but throws all
     * thrown {@link Throwable}s after drain.
     *
     * @param queue {@link Queue} to drain.
     * @param forEach {@link Consumer} for each item that is drained.
     * @param drainActiveUpdater An {@link AtomicIntegerFieldUpdater} for an {@code int} that is used to guard against
     * concurrent drains.
     * @param flagOwner Holding instance for {@code drainActiveUpdater}.
     * @param <T> Type of items stored in the {@link Queue}.
     * @param <R> Type of the object holding the {@link int} referred by {@link AtomicIntegerFieldUpdater}.
     * @return Number of items drained from the queue.
     * @throws RuntimeException All {@link Throwable} thrown by {@code forEach} {@link Consumer} are added as suppressed
     * causes.
     */
    public static <T, R> long drainSingleConsumerQueueDelayThrow(final Queue<T> queue, final Consumer<T> forEach,
                                                                 final AtomicIntegerFieldUpdater<R> drainActiveUpdater,
                                                                 final R flagOwner) {
        RuntimeException cause = null;
        long drainedCount = 0;
        do {
            if (!drainActiveUpdater.compareAndSet(flagOwner, CONCURRENT_IDLE, CONCURRENT_EMITTING)) {
                break;
            }
            try {
                T t;
                while ((t = queue.poll()) != null) {
                    ++drainedCount;
                    try {
                        forEach.accept(t);
                    } catch (Throwable th) {
                        if (cause == null) {
                            cause = new RuntimeException("Unexpected exception consuming: " + t, th);
                        } else {
                            cause.addSuppressed(th);
                        }
                    }
                }
            } finally {
                drainActiveUpdater.set(flagOwner, CONCURRENT_IDLE);
            }
            // We need to loop around again and check if we can acquire the "drain lock" in case there was elements
            // added after we finished draining the queue but before we released the "drain lock".
        } while (!queue.isEmpty());

        if (cause != null) {
            throw cause;
        }

        return drainedCount;
    }

    /**
     * Drains the passed single-consumer {@link Collection} and ensures that it is empty before returning.
     * This accounts for any additions to the {@link Collection} while drain is in progress.
     * Multiple threads can call this method concurrently but only one thread will actively drain the
     * {@link Collection}. Any {@link Throwable} thrown by {@code forEach} {@link Consumer} does not terminate draining
     * but throws all thrown {@link Throwable}s after drain.
     *
     * @param source {@link Collection} to drain.
     * @param forEach {@link Consumer} for each item that is drained.
     * @param drainActiveUpdater An {@link AtomicIntegerFieldUpdater} for an {@code int} that is used to guard against
     * concurrent drains.
     * @param flagOwner Holding instance for {@code drainActiveUpdater}.
     * @param <T> Type of items stored in the {@link Collection}.
     * @param <R> Type of the object holding the {@link int} referred by {@link AtomicIntegerFieldUpdater}.
     * @return Number of items drained from the collection.
     * @throws RuntimeException All {@link Throwable} thrown by {@code forEach} {@link Consumer} are added as suppressed
     * causes.
     */
    public static <T, R> long drainSingleConsumerCollectionDelayThrow(
            final Collection<T> source, final Consumer<T> forEach,
            final AtomicIntegerFieldUpdater<R> drainActiveUpdater, final R flagOwner) {
        RuntimeException cause = null;
        long drainedCount = 0;
        do {
            if (!drainActiveUpdater.compareAndSet(flagOwner, CONCURRENT_IDLE, CONCURRENT_EMITTING)) {
                break;
            }
            try {
                for (Iterator<T> iterator = source.iterator(); iterator.hasNext();) {
                    T item = iterator.next();
                    iterator.remove();
                    ++drainedCount;
                    try {
                        forEach.accept(item);
                    } catch (Throwable th) {
                        if (cause == null) {
                            cause = new RuntimeException("Unexpected exception consuming: " + item, th);
                        } else {
                            cause.addSuppressed(th);
                        }
                    }
                }
            } finally {
                drainActiveUpdater.set(flagOwner, CONCURRENT_IDLE);
            }
            // We need to loop around again and check if we can acquire the "drain lock" in case there was elements
            // added after we finished draining the source but before we released the "drain lock".
        } while (!source.isEmpty());

        if (cause != null) {
            throw cause;
        }
        return drainedCount;
    }

    /**
     * Executes the passed {@link Runnable} assuring that concurrent invocations of this method does not concurrently
     * execute the {@link Runnable}. However, any invocation, while another execution is in progress, will trigger
     * execution of the {@link Runnable} again after the current execution completes. This process will repeat till no
     * invocation was received during the execution of the {@link Runnable} or any execution threw an exception.
     *
     * @param task {@link Runnable} to execute. Any unchecked exception thrown by this task is thrown from this method.
     * @param exclusionFlag {@link AtomicIntegerFieldUpdater} to update a flag that guarantees exclusive execution of
     * the task. This flag is assumed to be {@code 0} when this method is invoked and must not be modified elsewhere.
     * @param flagOwner Owning instance of the
     * @param <T> Type of the owner of the flags.
     */
    public static <T> void executeExclusive(final Runnable task, final AtomicIntegerFieldUpdater<T> exclusionFlag,
                                            final T flagOwner) {
        int currentState;
        do {
            for (;;) {
                currentState = exclusionFlag.get(flagOwner);
                if (currentState == NOT_EXECUTING_EXCLUSIVE && exclusionFlag.compareAndSet(flagOwner,
                        NOT_EXECUTING_EXCLUSIVE, EXECUTING_EXCLUSIVE)) {
                    break;
                } else if (currentState == CONCURRENT_EXECUTE_EXCLUSIVE
                        || (currentState == EXECUTING_EXCLUSIVE && exclusionFlag.compareAndSet(flagOwner,
                        EXECUTING_EXCLUSIVE, CONCURRENT_EXECUTE_EXCLUSIVE))) {
                    return;
                }
            }

            try {
                task.run();
            } finally {
                for (;;) {
                    currentState = exclusionFlag.get(flagOwner);
                    if (currentState == CONCURRENT_EXECUTE_EXCLUSIVE) {
                        // Once set to CONCURRENT_EXECUTE_EXCLUSIVE, only the executing thread changes it.
                        exclusionFlag.set(flagOwner, NOT_EXECUTING_EXCLUSIVE);
                        // Run loop again since we got a concurrent request.
                        break;
                    } else if (currentState == EXECUTING_EXCLUSIVE && exclusionFlag.compareAndSet(flagOwner,
                            EXECUTING_EXCLUSIVE, NOT_EXECUTING_EXCLUSIVE)) {
                        // break the loop since no concurrent request was received.
                        currentState = NOT_EXECUTING_EXCLUSIVE;
                        break;
                    }
                }
            }
        } while (currentState != NOT_EXECUTING_EXCLUSIVE);
    }
}
