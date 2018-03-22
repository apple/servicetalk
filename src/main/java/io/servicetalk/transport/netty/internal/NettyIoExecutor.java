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
package io.servicetalk.transport.netty.internal;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.transport.api.IoExecutor;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * {@link IoExecutor} for netty.
 *
 * <h2>Caution</h2>
 * Implementations of this interface assumes that they would not be used to run blocking code.
 * If this assumption is violated, it will impact eventloop responsiveness and hence should be avoided.
 */
public interface NettyIoExecutor extends IoExecutor {

    /**
     * Executes the passed {@code task} on the eventloop as soon as possible.
     *
     * <h2>Caution</h2>
     * Implementation of this method assumes there would be no blocking code inside {@link Runnable}.
     * If this assumption is violated, it will impact eventloop responsiveness and hence should be avoided.
     *
     * @param task to execute.
     * @return {@link Cancellable} to cancel the task if not yet executed.
     * @throws RejectedExecutionException If the task is rejected.
     */
    Cancellable executeOnEventloop(Runnable task);

    /**
     * Schedules a tick for the passed {@code duration} on the eventloop to terminate the returned {@link Completable}
     * when done.
     *
     * <h2>Caution</h2>
     * Implementation of the returned {@link Completable} assumes there would be no blocking code run when interacting
     * with it. If this assumption is violated, it will impact eventloop responsiveness and hence should be avoided.
     *
     * @param duration of the tick.
     * @param durationUnit {@link TimeUnit} for the duration.
     * @return A {@link Completable} that terminates successfully when the passed {@code duration} has passed.
     * It will terminate with failure if the tick can not be scheduled or completed.
     */
    Completable scheduleOnEventloop(long duration, TimeUnit durationUnit);
}
