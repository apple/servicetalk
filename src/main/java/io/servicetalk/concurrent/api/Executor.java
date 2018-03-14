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

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * A general abstraction to execute immediate and delayed tasks.
 *
 * <h2>Long running tasks</h2>
 * {@link Executor} implementations are expected to run long running (blocking) tasks which may depend on other tasks
 * submitted to the same {@link Executor} instance.
 * In order to avoid deadlocks, it is generally a good idea to not allow task queuing in the {@link Executor}.
 */
public interface Executor extends ListenableAsyncCloseable {

    /**
     * Executes the passed {@code task} as soon as possible.
     *
     * @param task to execute.
     * @return {@link Cancellable} to cancel the task if not yet executed.
     * @throws RejectedExecutionException If the task is rejected.
     */
    Cancellable execute(Runnable task) throws RejectedExecutionException;

    /**
     * Schedules a tick for the passed {@code duration} on this {@link Executor} to terminate the returned {@link Completable} when done.
     *
     * @param duration of the tick.
     * @param durationUnit {@link TimeUnit} for the duration.
     * @return A {@link Completable} that terminates successfully when the passed {@code duration} has passed.
     * It will terminate with failure if the tick can not be scheduled or completed.
     */
    Completable schedule(long duration, TimeUnit durationUnit);
}
