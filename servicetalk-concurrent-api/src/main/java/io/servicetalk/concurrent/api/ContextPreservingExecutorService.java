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

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.util.Objects.requireNonNull;

class ContextPreservingExecutorService<X extends ExecutorService> implements ExecutorService {
    protected final X delegate;

    ContextPreservingExecutorService(X delegate) {
        this.delegate = requireNonNull(delegate);
    }

    @Override
    public final void execute(Runnable command) {
        delegate.execute(new ContextPreservingRunnable(command));
    }

    @Override
    public final void shutdown() {
        delegate.shutdown();
    }

    @Override
    public final List<Runnable> shutdownNow() {
        return delegate.shutdownNow();
    }

    @Override
    public final boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public final boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public final boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }

    @Override
    public final <T> Future<T> submit(Callable<T> task) {
        return delegate.submit(new ContextPreservingCallable<>(task));
    }

    @Override
    public final <T> Future<T> submit(Runnable task, T result) {
        return delegate.submit(new ContextPreservingRunnable(task), result);
    }

    @Override
    public final Future<?> submit(Runnable task) {
        return delegate.submit(new ContextPreservingRunnable(task));
    }

    @Override
    public final <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        return delegate.invokeAll(ContextAwareExecutorUtils.wrap(tasks));
    }

    @Override
    public final <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException {
        return delegate.invokeAll(ContextAwareExecutorUtils.wrap(tasks), timeout, unit);
    }

    @Override
    public final <T> T invokeAny(Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException {
        return delegate.invokeAny(ContextAwareExecutorUtils.wrap(tasks));
    }

    @Override
    public final <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return delegate.invokeAny(ContextAwareExecutorUtils.wrap(tasks), timeout, unit);
    }

    static ExecutorService of(ExecutorService executor) {
        return executor instanceof ContextPreservingExecutorService ? executor :
                new ContextPreservingExecutorService<>(executor);
    }
}
