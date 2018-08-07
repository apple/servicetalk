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

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static io.servicetalk.concurrent.Completable.Subscriber;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

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
     * Creates a new {@link Completable} that will complete after the time duration expires.
     *
     * @param delay The time duration which is allowed to elapse between
     * {@link Completable#subscribe(Completable.Subscriber)} and {@link Subscriber#onComplete()}.
     * @param unit The units for {@code duration}.
     * @return a new {@link Completable} that will complete after the time duration expires.
     * @see <a href="http://reactivex.io/documentation/operators/timer.html">ReactiveX Timer.</a>
     */
    default Completable timer(long delay, TimeUnit unit) {
        return new TimerCompletable(delay, unit, this);
    }

    /**
     * Creates a new {@link Completable} that will complete after the time duration expires.
     * @param delay The time duration which is allowed to elapse between
     * {@link Completable#subscribe(Completable.Subscriber)} and {@link Subscriber#onComplete()}.
     *
     * @return a new {@link Completable} that will complete after the time duration expires.
     * @see <a href="http://reactivex.io/documentation/operators/timer.html">ReactiveX Timer.</a>
     */
    default Completable timer(Duration delay) {
        return new TimerCompletable(delay, this);
    }

    /**
     * Create a new {@link Completable} that executes the passed {@link Runnable} on each
     * {@link Completable#subscribe(Completable.Subscriber)}.
     *
     * @param runnable The {@link Runnable} to execute on each {@link Completable#subscribe(Completable.Subscriber)}.
     * @return a new {@link Completable} that executes a {@link Runnable} on each
     * {@link Completable#subscribe(Completable.Subscriber)}.
     */
    default Completable submit(Runnable runnable) {
        return new SubmitCompletable(runnable, this);
    }

    /**
     * Creates a new {@link Completable} that creates and executes a {@link Runnable} when subscribed to.
     *
     * @param runnableSupplier {@link Supplier} to create a new {@link Runnable} for every call to
     * {@link Completable#subscribe(Subscriber)} to the returned {@link Completable}.
     * @return A new {@link Completable} that creates and executes a new {@link Runnable} using
     * {@code runnableSupplier} for every call to {@link Completable#subscribe(Subscriber)}.
     */
    default Completable submitRunnable(Supplier<Runnable> runnableSupplier) {
        return new SubmitSupplierCompletable(runnableSupplier, this);
    }

    /**
     * Creates a new {@link Single} that creates and executes the passed {@link Callable} when subscribed to.
     *
     * @param callable The {@link Callable} to execute on each {@link Single#subscribe(Single.Subscriber)}.
     * @param <T> Type of the {@link Single}.
     * @return a new {@link Single} that obtains a {@link Callable} from {@code callableSupplier} and executes it
     * on each {@link Single#subscribe(Single.Subscriber)}.
     */
    default <T> Single<T> submit(Callable<? extends T> callable) {
        return new SubmitSingle<>(callable, this);
    }

    /**
     * Create a new {@link Single} that obtains a {@link Callable} from {@code callableSupplier} and executes on each
     * {@link Single#subscribe(Single.Subscriber)}.
     *
     * @param callableSupplier {@link Supplier} to create a new {@link Callable} for every call to
     * {@link Single#subscribe(Single.Subscriber)} to the returned {@link Single}.
     * @param <T> Type of the {@link Single}.
     * @return A new {@link Single} that creates and executes a new {@link Callable} using
     * {@code callableSupplier} for every call to {@link Single#subscribe(Single.Subscriber)}.
     */
    default <T> Single<T> submitCallable(Supplier<? extends Callable<? extends T>> callableSupplier) {
        return new SubmitSupplierSingle<>(callableSupplier, this);
    }
}
