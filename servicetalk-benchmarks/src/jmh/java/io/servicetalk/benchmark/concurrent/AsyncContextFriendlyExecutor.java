/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.benchmark.concurrent;

import io.servicetalk.concurrent.api.DefaultThreadFactory;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.lang.Thread.NORM_PRIORITY;

/**
 * This class is instantiated by JMH and is configured via system JMH's system property interface.
 * <pre>
 * -Djmh.executor=CUSTOM
 * -Djmh.executor.class=io.servicetalk.benchmark.concurrent.AsyncContextMapBenchmark\$AsyncContextFriendlyExecutor
 * </pre>
 */
public final class AsyncContextFriendlyExecutor implements ExecutorService {
    private final ExecutorService executor;

    public AsyncContextFriendlyExecutor(int maxThread, String prefix) {
        executor = Executors.newCachedThreadPool(new DefaultThreadFactory(prefix, false, NORM_PRIORITY));
    }

    @Override
    public void shutdown() {
        executor.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return executor.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return executor.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return executor.isTerminated();
    }

    @Override
    public boolean awaitTermination(final long timeout, final TimeUnit unit) throws InterruptedException {
        return executor.awaitTermination(timeout, unit);
    }

    @Override
    public <T> Future<T> submit(final Callable<T> task) {
        return executor.submit(task);
    }

    @Override
    public <T> Future<T> submit(final Runnable task, final T result) {
        return executor.submit(task, result);
    }

    @Override
    public Future<?> submit(final Runnable task) {
        return executor.submit(task);
    }

    @Override
    public <T> List<Future<T>> invokeAll(final Collection<? extends Callable<T>> tasks) throws InterruptedException {
        return executor.invokeAll(tasks);
    }

    @Override
    public <T> List<Future<T>> invokeAll(final Collection<? extends Callable<T>> tasks, final long timeout,
                                         final TimeUnit unit) throws InterruptedException {
        return executor.invokeAll(tasks, timeout, unit);
    }

    @Override
    public <T> T invokeAny(final Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException {
        return executor.invokeAny(tasks);
    }

    @Override
    public <T> T invokeAny(final Collection<? extends Callable<T>> tasks, final long timeout, final TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return executor.invokeAny(tasks, timeout, unit);
    }

    @Override
    public void execute(final Runnable command) {
        executor.execute(command);
    }
}
