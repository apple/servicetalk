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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * A concurrency primitive for providing thread safety without using locks.
 *
 * A {@link SequentialExecutor} is queue of tasks that are executed one at a time in the order they were
 * received. This provides a way to serialize work between threads without needing to use locks which can
 * result in thread contention and thread deadlock scenarios.
 */
final class SequentialExecutor implements Executor {

    private final Logger logger;
    private final AtomicReference<Cell> tail = new AtomicReference<>();

    SequentialExecutor(Class<?> clazz) {
        this(LoggerFactory.getLogger(clazz));
    }

    SequentialExecutor(final Logger logger) {
        this.logger = requireNonNull(logger, "logger");
    }

    @Override
    public void execute(Runnable command) {
        final Cell next = new Cell(command);
        Cell t = tail.getAndSet(next);
        if (t != null) {
            // Execution already started. Link the old tail to the new tail.
            t.next = next;
        } else {
            // We are the first element in the queue so it's our responsibility to drain.
            // Note that the getAndSet establishes the happens before with relation to the previous draining
            // threads since we must successfully perform a CAS operation to terminate draining.
            drain(next);
        }
    }

    private void drain(Cell next) {
        for (;;) {
            assert next != null;
            safeRun(next.runnable);

            // Attempt to get the next element.
            Cell n = next.next;
            if (n != null) {
                next = n;
                continue;
            }
            // There doesn't seem to be another element linked. See if it was the tail and if so terminate draining.
            // Note that a successful CAS established a happens-before relationship with future draining threads.
            if (tail.compareAndSet(next, null)) {
                return;
            }
            // next isn't the tail but the link hasn't resolved: we must poll until it does.
            while ((n = next.next) == null) {
                // Still not resolved: try again.
            }
            next = n;
        }
    }

    private void safeRun(Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception ex) {
            logger.error("Exception caught in sequential execution", ex);
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
