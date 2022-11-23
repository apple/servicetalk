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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import static io.servicetalk.concurrent.api.GlobalExecutor.GLOBAL_EXECUTOR;
import static io.servicetalk.concurrent.api.ImmediateExecutor.IMMEDIATE_EXECUTOR;

/**
 * Utility methods to create various {@link Executor}s.
 */
public final class Executors {
    private static final Logger LOGGER = LoggerFactory.getLogger(Executors.class);
    static final CopyOnWriteExecutorPluginSet EXECUTOR_PLUGINS = new CopyOnWriteExecutorPluginSet();

    static {
        AsyncContext.autoEnable();
    }

    private Executors() {
        // no instances
    }

    /**
     * Returns an {@link Executor} that executes all tasks submitted via {@link Executor#execute(Runnable)} immediately
     * by calling {@link Runnable#run()} on the calling thread. {@link Executor#schedule(Runnable, long, TimeUnit)} will
     * use a global scheduler.
     *
     * @return An {@link Executor} that executes all tasks submitted via {@link Executor#execute(Runnable)}
     * immediately on the calling thread.
     */
    public static Executor immediate() {
        return IMMEDIATE_EXECUTOR;
    }

    /**
     * Returns a global {@link Executor} instance, which should be used for operations that do not involve I/O and
     * are expected to happen concurrently.
     * It creates as many threads as required but reuses threads when possible. It is therefore 'safe to block' when
     * using it.
     * @return An {@link Executor} which serves as a global mechanism for executing concurrent operations.
     */
    public static Executor global() {
        return GLOBAL_EXECUTOR;
    }

    /**
     * Creates a new {@link Executor} that has a fixed number of threads as specified by the {@code size}.
     *
     * @param size Number of threads used by the newly created {@link Executor}.
     * @return A new {@link Executor} that will use the {@code size} number of threads.
     */
    public static Executor newFixedSizeExecutor(int size) {
        return newFixedSizeExecutor(size, new DefaultThreadFactory());
    }

    /**
     * Creates a new {@link Executor} that has a fixed number of threads as specified by the {@code size}.
     *
     * @param size Number of threads used by the newly created {@link Executor}.
     * @param threadFactory {@link ThreadFactory} to use.
     * @return A new {@link Executor} that will use the {@code size} number of threads.
     */
    public static Executor newFixedSizeExecutor(int size, ThreadFactory threadFactory) {
        return EXECUTOR_PLUGINS.wrapExecutor(new DefaultExecutor(size, size, threadFactory));
    }

    /**
     * Creates a new {@link Executor} that creates as many threads as required but reuses threads when possible.
     *
     * @return A new {@link Executor}.
     */
    public static Executor newCachedThreadExecutor() {
        return newCachedThreadExecutor(new DefaultThreadFactory());
    }

    /**
     * Creates a new {@link Executor} that creates as many threads as required but reuses threads when possible.
     *
     * @param threadFactory {@link ThreadFactory} to use.
     * @return A new {@link Executor}.
     */
    public static Executor newCachedThreadExecutor(ThreadFactory threadFactory) {
        return EXECUTOR_PLUGINS.wrapExecutor(new DefaultExecutor(1, Integer.MAX_VALUE, threadFactory));
    }

    /**
     * Creates a new {@link Executor} that creates as many threads as required but reuses threads when possible.
     *
     * @param threadFactory {@link ThreadFactory} to use.
     * @return A new {@link Executor}.
     */
    public static Executor newForkJoinExecutor(ForkJoinWorkerThreadFactory threadFactory) {
        return EXECUTOR_PLUGINS.wrapExecutor(
                new DefaultExecutor(new ForkJoinPool(Runtime.getRuntime().availableProcessors() * 2,
                        threadFactory, (t, e) -> LOGGER.error("Uncaught exception from thread={}", t, e), true))
        );
    }

    /**
     * Creates a new {@link Executor} from the provided {@code jdkExecutor}. <p>
     * Delayed task execution will be delegated to a global scheduler, unless passed
     * {@link java.util.concurrent.Executor} is an instance of {@link ScheduledExecutorService}.<p>
     * Task execution will not honor cancellations unless passed {@link java.util.concurrent.Executor}
     * is an instance of {@link ExecutorService}.
     * <p><strong>Long running tasks</strong></p>
     * {@link java.util.concurrent.Executor} implementations are expected to run long running (blocking) tasks which may
     * depend on other tasks submitted to the same {@link java.util.concurrent.Executor} instance.
     * In order to avoid deadlocks, it is generally a good idea to not allow task queuing in the
     * {@link java.util.concurrent.Executor}.
     *
     * @param jdkExecutor {@link java.util.concurrent.Executor} to use for executing tasks.
     * The lifetime of this object is transferred to the return value. In other words {@link Executor#closeAsync()} will
     * call {@link ExecutorService#shutdown()} (if possible).
     * @return {@link Executor} that wraps the passed {@code jdkExecutor}.
     */
    public static Executor from(java.util.concurrent.Executor jdkExecutor) {
        return EXECUTOR_PLUGINS.wrapExecutor(new DefaultExecutor(jdkExecutor));
    }

    /**
     * Creates a new {@link Executor} from the provided {@link ExecutorService}. <p>
     * Delayed task execution will be delegated to a global scheduler, unless passed {@link ExecutorService}
     * is an instance of {@link ScheduledExecutorService}.<p>
     * When a running task is cancelled, the thread running it will be interrupted.
     * For overriding this behavior use {@link #from(ExecutorService, boolean)}.
     * <p><strong>Long running tasks</strong></p>
     * {@link java.util.concurrent.Executor} implementations are expected to run long running (blocking) tasks which may
     * depend on other tasks submitted to the same {@link java.util.concurrent.Executor} instance.
     * In order to avoid deadlocks, it is generally a good idea to not allow task queuing in the
     * {@link java.util.concurrent.Executor}.
     *
     * @param executorService {@link ExecutorService} to use for executing tasks.
     * The lifetime of this object is transferred to the return value. In other words {@link Executor#closeAsync()} will
     * call {@link ExecutorService#shutdown()}.
     * @return {@link Executor} that wraps the passed {@code executorService}.
     */
    public static Executor from(ExecutorService executorService) {
        return EXECUTOR_PLUGINS.wrapExecutor(new DefaultExecutor(executorService));
    }

    /**
     * Creates a new {@link Executor} from the provided {@link ExecutorService}.
     * Delayed task execution will be delegated to a global scheduler, unless passed {@link ExecutorService}
     * is an instance of {@link ScheduledExecutorService}.
     * <p><strong>Long running tasks</strong></p>
     * {@link java.util.concurrent.Executor} implementations are expected to run long running (blocking) tasks which may
     * depend on other tasks submitted to the same {@link java.util.concurrent.Executor} instance.
     * In order to avoid deadlocks, it is generally a good idea to not allow task queuing in the
     * {@link java.util.concurrent.Executor}.
     *
     * @param executorService {@link ExecutorService} to use for executing tasks.
     * The lifetime of this object is transferred to the return value. In other words {@link Executor#closeAsync()} will
     * call {@link ExecutorService#shutdown()}.
     * @param mayInterruptOnCancel If set to {@code true}, when a task is cancelled, thread running the task will be
     * interrupted.
     * @return {@link Executor} that wraps the passed {@code executorService}.
     */
    public static Executor from(ExecutorService executorService, boolean mayInterruptOnCancel) {
        return EXECUTOR_PLUGINS.wrapExecutor(new DefaultExecutor(executorService, mayInterruptOnCancel));
    }

    /**
     * Creates a new {@link Executor} from the provided {@link ScheduledExecutorService}.
     * When a running task is cancelled, the thread running it will be interrupted.
     * For overriding this behavior use {@link #from(ScheduledExecutorService, boolean)}.
     * <p><strong>Long running tasks</strong></p>
     * {@link java.util.concurrent.Executor} implementations are expected to run long running (blocking) tasks which may
     * depend on other tasks submitted to the same {@link java.util.concurrent.Executor} instance.
     * In order to avoid deadlocks, it is generally a good idea to not allow task queuing in the
     * {@link java.util.concurrent.Executor}.
     *
     * @param scheduledExecutorService {@link ScheduledExecutorService} to use for executing tasks.
     * The lifetime of this object is transferred to the return value. In other words {@link Executor#closeAsync()} will
     * call {@link ScheduledExecutorService#shutdown()}.
     * @return {@link Executor} that wraps the passed {@code scheduledExecutorService}.
     */
    public static Executor from(ScheduledExecutorService scheduledExecutorService) {
        return EXECUTOR_PLUGINS.wrapExecutor(new DefaultExecutor(scheduledExecutorService, scheduledExecutorService));
    }

    /**
     * Creates a new {@link Executor} from the provided {@link ScheduledExecutorService}.
     * <p><strong>Long running tasks</strong></p>
     * {@link java.util.concurrent.Executor} implementations are expected to run long running (blocking) tasks which may
     * depend on other tasks submitted to the same {@link java.util.concurrent.Executor} instance.
     * In order to avoid deadlocks, it is generally a good idea to not allow task queuing in the
     * {@link java.util.concurrent.Executor}.
     *
     * @param scheduledExecutorService {@link ScheduledExecutorService} to use for executing tasks.
     * The lifetime of this object is transferred to the return value. In other words {@link Executor#closeAsync()} will
     * call {@link ScheduledExecutorService#shutdown()}.
     * @param mayInterruptOnCancel If set to {@code true}, when a task is cancelled, thread running the task will be
     * interrupted.
     * @return {@link Executor} that wraps the passed {@code scheduledExecutorService}.
     */
    public static Executor from(ScheduledExecutorService scheduledExecutorService, boolean mayInterruptOnCancel) {
        return EXECUTOR_PLUGINS.wrapExecutor(
                new DefaultExecutor(scheduledExecutorService, scheduledExecutorService, mayInterruptOnCancel));
    }

    /**
     * Creates a new {@link Executor} using {@code executor} to execute immediate tasks and {@code scheduler} to
     * schedule delayed tasks.
     * When a running task is cancelled, the thread running it will be interrupted.
     * For overriding this behavior use {@link #from(java.util.concurrent.Executor, ScheduledExecutorService, boolean)}.
     * Task execution will not honor cancellations unless passed {@link java.util.concurrent.Executor}
     * is an instance of {@link ExecutorService}.
     * <p><strong>Long running tasks</strong></p>
     * {@link java.util.concurrent.Executor} implementations are expected to run long running (blocking) tasks which may
     * depend on other tasks submitted to the same {@link java.util.concurrent.Executor} instance.
     * In order to avoid deadlocks, it is generally a good idea to not allow task queuing in the
     * {@link java.util.concurrent.Executor}.
     *
     * @param jdkExecutor {@link java.util.concurrent.Executor} to use for executing tasks.
     * The lifetime of this object is transferred to the return value. In other words {@link Executor#closeAsync()} will
     * call {@link ExecutorService#shutdown()} (if possible).
     * @param scheduledExecutorService {@link ScheduledExecutorService} to use for executing tasks.
     * The lifetime of this object is transferred to the return value. In other words {@link Executor#closeAsync()} will
     * call {@link ScheduledExecutorService#shutdown()}.
     * @return A new {@link Executor}.
     */
    public static Executor from(java.util.concurrent.Executor jdkExecutor,
                                ScheduledExecutorService scheduledExecutorService) {
        return from(jdkExecutor, scheduledExecutorService, true);
    }

    /**
     * Creates a new {@link Executor} using {@code executor} to execute immediate tasks and {@code scheduler} to
     * schedule delayed tasks.
     * Task execution will not honor cancellations unless passed {@link java.util.concurrent.Executor}
     * is an instance of {@link ExecutorService}.
     * <p><strong>Long running tasks</strong></p>
     * {@link java.util.concurrent.Executor} implementations are expected to run long running (blocking) tasks which may
     * depend on other tasks submitted to the same {@link java.util.concurrent.Executor} instance.
     * In order to avoid deadlocks, it is generally a good idea to not allow task queuing in the
     * {@link java.util.concurrent.Executor}.
     *
     * @param jdkExecutor {@link java.util.concurrent.Executor} to use for executing tasks.
     * The lifetime of this object is transferred to the return value. In other words {@link Executor#closeAsync()} will
     * call {@link ExecutorService#shutdown()} (if possible).
     * @param scheduledExecutorService {@link ScheduledExecutorService} to use for executing tasks.
     * The lifetime of this object is transferred to the return value. In other words {@link Executor#closeAsync()} will
     * call {@link ScheduledExecutorService#shutdown()}.
     * @param mayInterruptOnCancel If set to {@code true}, when a task is cancelled, thread running the task will be
     * interrupted.
     * @return A new {@link Executor}.
     */
    public static Executor from(java.util.concurrent.Executor jdkExecutor,
                                ScheduledExecutorService scheduledExecutorService, boolean mayInterruptOnCancel) {
        return EXECUTOR_PLUGINS.wrapExecutor(
                new DefaultExecutor(jdkExecutor, scheduledExecutorService, mayInterruptOnCancel));
    }
}
