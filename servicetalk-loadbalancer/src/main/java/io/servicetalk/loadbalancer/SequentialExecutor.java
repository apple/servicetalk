/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.loadbalancer;

import io.servicetalk.concurrent.api.AsyncContext;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * A concurrency primitive for providing thread safety without using locks.
 * <p>
 * A {@link SequentialExecutor} is queue of tasks that are executed one at a time in the order they were
 * received. This provides a way to serialize work between threads without needing to use locks which can
 * result in thread contention and thread deadlock scenarios.
 */
final class SequentialExecutor implements Executor {

    /**
     * Handler of exceptions thrown by submitted {@link Runnable}s.
     */
    @FunctionalInterface
    interface ExceptionHandler {

        /**
         * Handle the exception thrown from a submitted {@link Runnable}.
         * <p>
         * Note that if this method throws the behavior is undefined.
         *
         * @param ex the {@link Throwable} thrown by the {@link Runnable}.
         */
        void onException(Throwable ex);
    }

    private final ExceptionHandler exceptionHandler;
    private final AtomicReference<Cell> tail = new AtomicReference<>();
    @Nullable
    private Thread currentDrainingThread;

    SequentialExecutor(final ExceptionHandler exceptionHandler) {
        this.exceptionHandler = requireNonNull(exceptionHandler, "exceptionHandler");
    }

    public boolean isCurrentThreadDraining() {
        // Even though `currentDrainingThread` is not a volatile field this is thread safe:
        // the only way that `currentDrainingThread` will ever equal this thread, even if
        // we get a stale value, is if _this_ thread set it.
        // The null check is just an optimization: it's really the second check that matters.
        return currentDrainingThread != null && currentDrainingThread == Thread.currentThread();
    }

    @Override
    public void execute(Runnable command) {
        // Make sure we propagate async contexts.
        command = AsyncContext.wrapRunnable(requireNonNull(command, "command"));
        final Cell next = new Cell(command);
        Cell prev = tail.getAndSet(next);
        if (prev != null) {
            // Execution already started. Link the old tail to the new tail.
            prev.next = next;
        } else {
            // We are the first element in the queue so it's our responsibility to drain.
            // Note that the getAndSet establishes the happens before with relation to the previous draining
            // threads since we must successfully perform a CAS operation to terminate draining.
            drain(next);
        }
    }

    private void drain(Cell next) {
        final Thread thisThread = Thread.currentThread();
        currentDrainingThread = thisThread;
        for (;;) {
            assert next != null;
            try {
                next.runnable.run();
            } catch (Throwable ex) {
                exceptionHandler.onException(ex);
            }

            // Attempt to get the next element.
            Cell n = next.next;
            if (n == null) {
                // There doesn't seem to be another element linked. See if it was the tail and if so terminate draining.
                // Note that a successful CAS established a happens-before relationship with future draining threads.
                // Note that we also have to clear the draining thread before the CAS to prevent races.
                currentDrainingThread = null;
                if (tail.compareAndSet(next, null)) {
                    break;
                }
                // Next isn't the tail but the link hasn't resolved: we must re-set the draining thread and poll until
                // it does resolve then we can keep on trucking.
                currentDrainingThread = thisThread;
                while ((n = next.next) == null) {
                    // Still not resolved: yield and then try again.
                    Thread.yield();
                }
            }
            next = n;
        }
    }

    private static final class Cell {

        final Runnable runnable;
        @Nullable
        volatile Cell next;

        Cell(Runnable runnable) {
            this.runnable = runnable;
        }
    }
}
