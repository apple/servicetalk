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
package io.servicetalk.concurrent;

import java.time.Duration;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * A general abstraction to execute immediate and delayed tasks.
 *
 * <h2>Long running tasks</h2>
 * {@link Executor} implementations are expected to run long running (blocking) tasks which may depend on other tasks
 * submitted to the same {@link Executor} instance.
 * In order to avoid deadlocks, it is generally a good idea to not allow task queuing in the {@link Executor}.
 */
public interface Executor {
    /**
     * Executes the passed {@code task} as soon as possible.
     *
     * @param task to execute.
     * @return {@link Cancellable} to cancel the task if not yet executed.
     * @throws RejectedExecutionException If the task is rejected.
     */
    Cancellable execute(Runnable task) throws RejectedExecutionException;

    /**
     * Executes the passed {@code task} after {@code delay} amount of {@code unit}s time has passed.
     * <p>
     * Note this method is not guaranteed to provide real time execution. For example implementations are free to
     * consolidate tasks into time buckets to reduce the overhead of timer management at the cost of reduced timer
     * fidelity.
     *
     * @param task to execute.
     * @param delay The time duration that is allowed to elapse before {@code task} is executed.
     * @param unit The units for {@code delay}.
     * @return {@link Cancellable} to cancel the task if not yet executed.
     * @throws RejectedExecutionException If the task is rejected.
     */
    Cancellable schedule(Runnable task, long delay, TimeUnit unit) throws RejectedExecutionException;

    /**
     * Executes the passed {@code task} after {@code delay} amount time has passed.
     * <p>
     * Note this method is not guaranteed to provide real time execution. For example implementations are free to
     * consolidate tasks into time buckets to reduce the overhead of timer management at the cost of reduced timer
     * fidelity.
     *
     * @param task to execute.
     * @param delay The time duration that is allowed to elapse before {@code task} is executed.
     * @return {@link Cancellable} to cancel the task if not yet executed.
     * @throws RejectedExecutionException If the task is rejected.
     */
    default Cancellable schedule(Runnable task, Duration delay) throws RejectedExecutionException {
        return schedule(task, delay.toNanos(), NANOSECONDS);
    }

    /**
     * Returns the current time in nanoseconds of the monotonic clock associated with this executor.
     *
     * @return the current time as monotonic nanoseconds
     */
    default long currentNanos() {
        return System.nanoTime();
    }
}
