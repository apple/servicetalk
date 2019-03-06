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

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.internal.PlatformDependent.throwException;
import static io.servicetalk.concurrent.internal.ThrowableUtil.unknownStackTrace;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

/**
 * An implementation of {@link CompletionStage} that executes user code via the
 * {@link io.servicetalk.concurrent.Executor} associated with the original {@link Single}. If a {@link Executor} is
 * explicitly provided in one of the {@link CompletionStage} methods, then the {@link Executor} will be used to execute
 * user code for the returned {@link CompletionStage}.
 * <p>
 * It is possible to accomplish this by extending {@link CompletableFuture} but this approach was not taken for the
 * following reasons:
 * <ul>
 *   <li>Avoiding additional thread context switches - Fine grained control over completion and listener notification
 *   means we can avoid offloading if the Single's termination results in completing this {@link CompletionStage}
 *   (as opposed to {@link #cancel(boolean)}, or {@link #complete(Object)}).</li>
 *   <li>Similar code content / complexity - {@link CompletableFuture} will notify late listeners on the calling thread,
 *   we would anyways have to override every single method to ensure the {@link Executor} is applied correctly. This
 *   would require using `whenComplete` to determine when the current {@link CompletableFuture} completes and then the
 *   call back would be responsible for interpreting the results and applying it differently for the current method
 *   signature (e.g. {@link #thenAccept(Consumer)} is only called when a successful completion occurs).</li>
 *   <li>Provisional - AsyncContext optimizations - Supporting AsyncContext does not require setting of previous state,
 *   and only requires saving the state before executing and restoring after (because we need to make sure the user code
 *   modifying AsyncContext doesn't leak outside its scope). This can remove a {@link ThreadLocal#get()},
 *   {@link ThreadLocal#set(Object)}, and AsyncContext listeners notification per operation.</li>
 *   <li>Provisional - Read Only Interface - At one point we explored having a "read-only" view of
 *   {@link CompletionStage} which doesn't support {@link CompletionStage#toCompletableFuture()} because the semantics
 *   line up more closely with {@link Single}. The custom implementation allows us to avoid users casting to
 *   {@link CompletableFuture} and trying to complete the future (which goes against how data flows through async
 *   sources). This feature is currently not used but maybe in the future.</li>
 * </ul>
 * <p>
 * This implementation currently doesn't execute {@link ForkJoinTask}s, for current lack of demand.
 * @param <T> The type of data if/when this {@link CompletionStage} is completed successfully.
 */
abstract class ExecutorCompletionStage<T> implements CompletionStage<T>, Future<T> {
    private static final AtomicReferenceFieldUpdater<ExecutorCompletionStage, Listener> listenersUpdater =
            newUpdater(ExecutorCompletionStage.class, Listener.class, "listeners");
    private static final AtomicReferenceFieldUpdater<ExecutorCompletionStage, Object> resultUpdater =
            newUpdater(ExecutorCompletionStage.class, Object.class, "result");
    private static final Object NULL = new Object();
    private final io.servicetalk.concurrent.Executor executor;
    @SuppressWarnings("unused")
    @Nullable
    private volatile Listener<T> listeners;
    @SuppressWarnings("unused")
    @Nullable
    private volatile Object result;

    ExecutorCompletionStage(io.servicetalk.concurrent.Executor executor) {
        this.executor = executor;
    }

    @Override
    public final <U> CompletionStage<U> thenApply(final Function<? super T, ? extends U> fn) {
        requireNonNull(fn);
        ExecutorCompletionStage<U> stage = newDependentStage(executor);
        Object result = this.result;
        if (result != null) {
            ApplyOnSuccessListener.completeFuture(stage, result, fn, executor);
        } else {
            pushNewListener(new ApplyOnSuccessListener<>(stage, fn));
        }
        return stage;
    }

    @Override
    public final <U> CompletionStage<U> thenApplyAsync(final Function<? super T, ? extends U> fn) {
        return thenApply(fn);
    }

    @Override
    public final <U> CompletionStage<U> thenApplyAsync(final Function<? super T, ? extends U> fn,
                                                       final Executor executor) {
        requireNonNull(fn);
        requireNonNull(executor);
        ExecutorCompletionStage<U> stage = newDependentStage(executor);
        Object result = this.result;
        if (result != null) {
            JdkExecutorApplyOnSuccessListener.completeFuture(stage, result, fn, executor);
        } else {
            pushNewListener(new JdkExecutorApplyOnSuccessListener<>(executor, stage, fn));
        }
        return stage;
    }

    @Override
    public final CompletionStage<Void> thenAccept(final Consumer<? super T> action) {
        requireNonNull(action);
        ExecutorCompletionStage<Void> stage = newDependentStage(executor);
        Object result = this.result;
        if (result != null) {
            AcceptOnSuccessListener.completeFuture(stage, result, action, executor);
        } else {
            pushNewListener(new AcceptOnSuccessListener<>(stage, action));
        }
        return stage;
    }

    @Override
    public final CompletionStage<Void> thenAcceptAsync(final Consumer<? super T> action) {
        return thenAccept(action);
    }

    @Override
    public final CompletionStage<Void> thenAcceptAsync(final Consumer<? super T> action, final Executor executor) {
        requireNonNull(action);
        requireNonNull(executor);
        ExecutorCompletionStage<Void> stage = newDependentStage(executor);
        Object result = this.result;
        if (result != null) {
            JdkExecutorAcceptOnSuccessListener.completeFuture(stage, result, action, executor);
        } else {
            pushNewListener(new JdkExecutorAcceptOnSuccessListener<>(executor, stage, action));
        }
        return stage;
    }

    @Override
    public final CompletionStage<Void> thenRun(final Runnable action) {
        requireNonNull(action);
        ExecutorCompletionStage<Void> stage = newDependentStage(executor);
        Object result = this.result;
        if (result != null) {
            RunOnSuccessListener.completeFuture(stage, result, action, executor);
        } else {
            pushNewListener(new RunOnSuccessListener<>(stage, action));
        }
        return stage;
    }

    @Override
    public final CompletionStage<Void> thenRunAsync(final Runnable action) {
        return thenRun(action);
    }

    @Override
    public final CompletionStage<Void> thenRunAsync(final Runnable action, final Executor executor) {
        requireNonNull(action);
        requireNonNull(executor);
        ExecutorCompletionStage<Void> stage = newDependentStage(executor);
        Object result = this.result;
        if (result != null) {
            JdkExecutorRunOnSuccessListener.completeFuture(stage, result, action, executor);
        } else {
            pushNewListener(new JdkExecutorRunOnSuccessListener<>(executor, stage, action));
        }
        return stage;
    }

    @Override
    public final <U, V> CompletionStage<V> thenCombine(final CompletionStage<? extends U> other,
                                                       final BiFunction<? super T, ? super U, ? extends V> fn) {
        requireNonNull(other);
        requireNonNull(fn);
        ExecutorCompletionStage<V> stage = newDependentStage(executor);
        pushNewListener(CombineOnListener.newInstance(executor, stage, other, fn));
        return stage;
    }

    @Override
    public final <U, V> CompletionStage<V> thenCombineAsync(final CompletionStage<? extends U> other,
                                                            final BiFunction<? super T, ? super U, ? extends V> fn) {
        return thenCombine(other, fn);
    }

    @Override
    public final <U, V> CompletionStage<V> thenCombineAsync(final CompletionStage<? extends U> other,
                                                            final BiFunction<? super T, ? super U, ? extends V> fn,
                                                            final Executor executor) {
        requireNonNull(other);
        requireNonNull(fn);
        ExecutorCompletionStage<V> stage = newDependentStage(executor);
        pushNewListener(JdkExecutorCombineOnListener.newInstance(executor, stage, other, fn));
        return stage;
    }

    @Override
    public final <U> CompletionStage<Void> thenAcceptBoth(final CompletionStage<? extends U> other,
                                                          final BiConsumer<? super T, ? super U> action) {
        requireNonNull(other);
        requireNonNull(action);
        ExecutorCompletionStage<Void> stage = newDependentStage(executor);
        pushNewListener(AcceptBothOnListener.newInstance(executor, stage, other, action));
        return stage;
    }

    @Override
    public final <U> CompletionStage<Void> thenAcceptBothAsync(final CompletionStage<? extends U> other,
                                                               final BiConsumer<? super T, ? super U> action) {
        return thenAcceptBoth(other, action);
    }

    @Override
    public final <U> CompletionStage<Void> thenAcceptBothAsync(final CompletionStage<? extends U> other,
                                                               final BiConsumer<? super T, ? super U> action,
                                                               final Executor executor) {
        requireNonNull(other);
        requireNonNull(action);
        requireNonNull(executor);
        ExecutorCompletionStage<Void> stage = newDependentStage(executor);
        pushNewListener(JdkExecutorAcceptBothOnListener.newInstance(executor, stage, other, action));
        return stage;
    }

    @Override
    public final CompletionStage<Void> runAfterBoth(final CompletionStage<?> other, final Runnable action) {
        requireNonNull(other);
        requireNonNull(action);
        ExecutorCompletionStage<Void> stage = newDependentStage(executor);
        pushNewListener(RunAfterBothOnListener.newInstance(executor, stage, other, action));
        return stage;
    }

    @Override
    public final CompletionStage<Void> runAfterBothAsync(final CompletionStage<?> other, final Runnable action) {
        return runAfterBoth(other, action);
    }

    @Override
    public final CompletionStage<Void> runAfterBothAsync(final CompletionStage<?> other, final Runnable action,
                                                         final Executor executor) {
        requireNonNull(other);
        requireNonNull(action);
        requireNonNull(executor);
        ExecutorCompletionStage<Void> stage = newDependentStage(executor);
        pushNewListener(JdkExecutorRunAfterBothOnListener.newInstance(executor, stage, other, action));
        return stage;
    }

    @Override
    public final <U> CompletionStage<U> applyToEither(final CompletionStage<? extends T> other,
                                                      final Function<? super T, U> fn) {
        requireNonNull(other);
        requireNonNull(fn);
        ExecutorCompletionStage<U> stage = newDependentStage(executor);
        pushNewListener(ApplyEitherListener.newInstance(executor, stage, other, fn));
        return stage;
    }

    @Override
    public final <U> CompletionStage<U> applyToEitherAsync(final CompletionStage<? extends T> other,
                                                           final Function<? super T, U> fn) {
        return applyToEither(other, fn);
    }

    @Override
    public final <U> CompletionStage<U> applyToEitherAsync(final CompletionStage<? extends T> other,
                                                           final Function<? super T, U> fn, final Executor executor) {
        requireNonNull(other);
        requireNonNull(fn);
        requireNonNull(executor);
        ExecutorCompletionStage<U> stage = newDependentStage(executor);
        pushNewListener(JdkExecutorApplyEitherListener.newInstance(executor, stage, other, fn));
        return stage;
    }

    @Override
    public final CompletionStage<Void> acceptEither(final CompletionStage<? extends T> other,
                                                    final Consumer<? super T> action) {
        requireNonNull(other);
        requireNonNull(action);
        ExecutorCompletionStage<Void> stage = newDependentStage(executor);
        pushNewListener(AcceptEitherListener.newInstance(executor, stage, other, action));
        return stage;
    }

    @Override
    public final CompletionStage<Void> acceptEitherAsync(final CompletionStage<? extends T> other,
                                                   final Consumer<? super T> action) {
        return acceptEither(other, action);
    }

    @Override
    public final CompletionStage<Void> acceptEitherAsync(final CompletionStage<? extends T> other,
                                                         final Consumer<? super T> action, final Executor executor) {
        requireNonNull(other);
        requireNonNull(action);
        requireNonNull(executor);
        ExecutorCompletionStage<Void> stage = newDependentStage(executor);
        pushNewListener(JdkExecutorAcceptEitherListener.newInstance(executor, stage, other, action));
        return stage;
    }

    @Override
    public final CompletionStage<Void> runAfterEither(final CompletionStage<?> other, final Runnable action) {
        requireNonNull(other);
        requireNonNull(action);
        ExecutorCompletionStage<Void> stage = newDependentStage(executor);
        pushNewListener(RunEitherListener.newInstance(executor, stage, other, action));
        return stage;
    }

    @Override
    public final CompletionStage<Void> runAfterEitherAsync(final CompletionStage<?> other, final Runnable action) {
        return runAfterEither(other, action);
    }

    @Override
    public final CompletionStage<Void> runAfterEitherAsync(final CompletionStage<?> other, final Runnable action,
                                                           final Executor executor) {
        requireNonNull(other);
        requireNonNull(action);
        requireNonNull(executor);
        ExecutorCompletionStage<Void> stage = newDependentStage(executor);
        pushNewListener(JdkExecutorRunEitherListener.newInstance(executor, stage, other, action));
        return stage;
    }

    @Override
    public final <U> CompletionStage<U> thenCompose(final Function<? super T, ? extends CompletionStage<U>> fn) {
        requireNonNull(fn);
        ExecutorCompletionStage<U> stage = newDependentStage(executor);
        Object result = this.result;
        if (result != null) {
            ComposeOnSuccessListener.completeFuture(stage, result, fn, executor);
        } else {
            pushNewListener(new ComposeOnSuccessListener<>(stage, fn));
        }
        return stage;
    }

    @Override
    public final <U> CompletionStage<U> thenComposeAsync(final Function<? super T, ? extends CompletionStage<U>> fn) {
        return thenCompose(fn);
    }

    @Override
    public final <U> CompletionStage<U> thenComposeAsync(final Function<? super T, ? extends CompletionStage<U>> fn,
                                                         final Executor executor) {
        requireNonNull(fn);
        requireNonNull(executor);
        ExecutorCompletionStage<U> stage = newDependentStage(executor);
        Object result = this.result;
        if (result != null) {
            JdkExecutorComposeOnSuccessListener.completeFuture(stage, result, fn, executor);
        } else {
            pushNewListener(new JdkExecutorComposeOnSuccessListener<>(executor, stage, fn));
        }
        return stage;
    }

    @Override
    public final CompletionStage<T> exceptionally(final Function<Throwable, ? extends T> fn) {
        requireNonNull(fn);
        ExecutorCompletionStage<T> stage = newDependentStage(executor);
        Object result = this.result;
        if (result != null) {
            ErrorOnErrorListener.completeFuture(stage, result, fn, executor);
        } else {
            pushNewListener(new ErrorOnErrorListener<>(stage, fn));
        }
        return stage;
    }

    @Override
    public final CompletionStage<T> whenComplete(final BiConsumer<? super T, ? super Throwable> action) {
        requireNonNull(action);
        ExecutorCompletionStage<T> stage = newDependentStage(executor);
        Object result = this.result;
        if (result != null) {
            WhenCompleteListener.completeFuture(stage, result, action, executor);
        } else {
            pushNewListener(new WhenCompleteListener<>(stage, action));
        }
        return stage;
    }

    @Override
    public final CompletionStage<T> whenCompleteAsync(final BiConsumer<? super T, ? super Throwable> action) {
        return whenComplete(action);
    }

    @Override
    public final CompletionStage<T> whenCompleteAsync(final BiConsumer<? super T, ? super Throwable> action,
                                                      final Executor executor) {
        requireNonNull(action);
        requireNonNull(executor);
        ExecutorCompletionStage<T> stage = newDependentStage(executor);
        Object result = this.result;
        if (result != null) {
            JdkExecutorWhenCompleteListener.completeFuture(stage, result, action, executor);
        } else {
            pushNewListener(new JdkExecutorWhenCompleteListener<>(executor, stage, action));
        }
        return stage;
    }

    @Override
    public final <U> CompletionStage<U> handle(final BiFunction<? super T, Throwable, ? extends U> fn) {
        requireNonNull(fn);
        ExecutorCompletionStage<U> stage = newDependentStage(executor);
        Object result = this.result;
        if (result != null) {
            HandleListener.completeFuture(stage, result, fn, executor);
        } else {
            pushNewListener(new HandleListener<>(stage, fn));
        }
        return stage;
    }

    @Override
    public final <U> CompletionStage<U> handleAsync(final BiFunction<? super T, Throwable, ? extends U> fn) {
        return handle(fn);
    }

    @Override
    public final <U> CompletionStage<U> handleAsync(final BiFunction<? super T, Throwable, ? extends U> fn,
                                                    final Executor executor) {
        requireNonNull(fn);
        requireNonNull(executor);
        ExecutorCompletionStage<U> stage = newDependentStage(executor);
        Object result = this.result;
        if (result != null) {
            JdkExecutorHandleListener.completeFuture(stage, result, fn, executor);
        } else {
            pushNewListener(new JdkExecutorHandleListener<>(executor, stage, fn));
        }
        return stage;
    }

    @Override
    public final CompletableFuture<T> toCompletableFuture() {
        return ExecutorCompletionStageToCompletableFuture.forStage(this);
    }

    @Override
    public final boolean cancel(final boolean mayInterruptIfRunning) {
        Object result = this.result;
        if (result != null) {
            return false;
        }

        ErrorResult errorResult = new ErrorResult(new CancellationException());
        if (!resultUpdater.compareAndSet(this, null, errorResult)) {
            return false;
        }
        try {
            doCancel();
        } finally {
            tryNotifyErrorOutsideExecutor(errorResult.cause);
        }
        return true;
    }

    abstract void doCancel();

    abstract <U> ExecutorCompletionStage<U> newDependentStage(io.servicetalk.concurrent.Executor executor);

    private <U> ExecutorCompletionStage<U> newDependentStage(Executor executor) {
        return newDependentStage(new JdkExecutorWrapper(executor));
    }

    @Override
    public final boolean isCancelled() {
        Object result = this.result;
        return result instanceof ErrorResult && ((ErrorResult) result).cause instanceof CancellationException;
    }

    @Override
    public final boolean isDone() {
        return result != null;
    }

    @Nullable
    @Override
    public final T get() throws InterruptedException, ExecutionException {
        return reportGet(waitingGet(true));
    }

    @Nullable
    @Override
    public final T get(final long timeout, final TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        Object result = this.result;
        if (result != null) {
            return reportGet(result);
        }

        final GetListener<T> newListener = new GetListener<>();
        pushNewListener(newListener);
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        long durationRemainingNs = unit.toNanos(timeout);
        if (durationRemainingNs <= 0) {
            throw new TimeoutException();
        }
        return reportGet(newListener.blockingTimedWait(durationRemainingNs));
    }

    @Override
    public String toString() {
        Object r = result;
        int count;
        return r == null ?
                ((count = getListenerCount()) == 0 ?
                        "[Not completed]" :
                        "[Not completed, " + count + " listeners]") :
                ((r instanceof ErrorResult) ?
                        "[Completed exceptionally]" :
                        "[Completed normally]");
    }

    @Nullable
    final T join() {
        try {
            return reportJoin(waitingGet(false));
        } catch (InterruptedException e) {
            throw new Error(e);
        }
    }

    @Nullable
    final T getNow(@Nullable T valueIfAbsent) {
        Object result = this.result;
        return result == null ? valueIfAbsent : reportJoin(result);
    }

    final boolean complete(@Nullable T value) {
        if (resultUpdater.compareAndSet(this, null, wrap(value))) {
            if (executor == immediate()) {
                tryNotifyResult(value);
            } else {
                executor.execute(() -> tryNotifyResult(value));
            }
            return true;
        }
        return false;
    }

    final void completeNoExec(@Nullable T value) {
        if (resultUpdater.compareAndSet(this, null, wrap(value))) {
            tryNotifyResult(value);
        }
    }

    final boolean completeExceptionally(Throwable ex) {
        if (resultUpdater.compareAndSet(this, null, new ErrorResult(requireNonNull(ex)))) {
            tryNotifyErrorOutsideExecutor(ex);
            return true;
        }
        return false;
    }

    final void completeExceptionallyNoExec(Throwable ex) {
        if (resultUpdater.compareAndSet(this, null, new ErrorResult(ex))) {
            tryNotifyError(ex);
        }
    }

    final boolean isCompletedExceptionally() {
        return result instanceof ErrorResult;
    }

    final void obtrudeValue(@Nullable T value) {
        result = wrap(value);
    }

    final void obtrudeException(Throwable ex) {
        result = new ErrorResult(ex);
    }

    final int getListenerCount() {
        int count = 0;
        // Best effort iteration on non volatile next pointer, for visibility to happen
        for (Listener<T> node = listeners; node != null; node = node.next) {
            ++count;
        }
        return count;
    }

    @Nullable
    private static <T> T reportGet(Object result) throws ExecutionException {
        if (result instanceof ErrorResult) {
            ErrorResult errorResult = (ErrorResult) result;
            if (errorResult.cause instanceof CancellationException) {
                throw (CancellationException) errorResult.cause;
            } else if (errorResult.cause instanceof CompletionException) {
                throw new ExecutionException(errorResult.cause.getCause());
            }
            throw new ExecutionException(errorResult.cause);
        } else {
            return unwrap(result);
        }
    }

    @Nullable
    private static <T> T reportJoin(Object result) {
        if (result instanceof ErrorResult) {
            ErrorResult errorResult = (ErrorResult) result;
            if (errorResult.cause instanceof CancellationException) {
                throw (CancellationException) errorResult.cause;
            } else if (errorResult.cause instanceof CompletionException) {
                throw (CompletionException) errorResult.cause;
            }
            throw new CompletionException(errorResult.cause);
        } else {
            return unwrap(result);
        }
    }

    private Object waitingGet(boolean interruptible) throws InterruptedException {
        Object result = this.result;
        if (result != null) {
            return result;
        }

        final GetListener<T> newListener = new GetListener<>();
        pushNewListener(newListener);
        return newListener.blockingWait(interruptible);
    }

    private void tryNotifyRawOutsideExecutor(Object result) {
        if (executor == immediate()) {
            tryNotifyRaw(result);
        } else {
            executor.execute(() -> tryNotifyRaw(result));
        }
    }

    private void tryNotifyRaw(Object result) {
        final Throwable overallCause;
        if (result instanceof ErrorResult) {
            overallCause = drainListenerQueueError(((ErrorResult) result).cause);
        } else {
            overallCause = drainListenerQueueSuccess(unwrap(result));
        }

        if (overallCause != null) {
            throwException(overallCause);
        }
    }

    private void tryNotifyErrorOutsideExecutor(Throwable ex) {
        if (executor == immediate()) {
            tryNotifyError(ex);
        } else {
            executor.execute(() -> tryNotifyError(ex));
        }
    }

    private void tryNotifyError(Throwable cause) {
        Throwable overallCause = drainListenerQueueError(cause);
        if (overallCause != null) {
            throwException(overallCause);
        }
    }

    private void tryNotifyResult(@Nullable T result) {
        Throwable overallCause = drainListenerQueueSuccess(result);
        if (overallCause != null) {
            throwException(overallCause);
        }
    }

    @Nullable
    private Throwable drainListenerQueueError(Throwable resultCause) {
        Throwable overallCause = null;
        for (;;) {
            Listener<T> listeners = this.listeners;
            if (listeners == null) {
                break;
            } else if (listenersUpdater.compareAndSet(this, listeners, listeners.next)) {
                listeners.next = null; // prevent GC nepotism
                try {
                    listeners.onError(resultCause);
                } catch (Throwable cause) {
                    if (overallCause == null) {
                        overallCause = cause;
                    } else {
                        overallCause.addSuppressed(cause);
                    }
                }
            }
        }
        return overallCause;
    }

    @Nullable
    private Throwable drainListenerQueueSuccess(@Nullable T result) {
        Throwable overallCause = null;
        for (;;) {
            Listener<T> listeners = this.listeners;
            if (listeners == null) {
                break;
            } else if (listenersUpdater.compareAndSet(this, listeners, listeners.next)) {
                listeners.next = null; // prevent GC nepotism
                try {
                    listeners.onSuccess(result);
                } catch (Throwable cause) {
                    if (overallCause == null) {
                        overallCause = cause;
                    } else {
                        overallCause.addSuppressed(cause);
                    }
                }
            }
        }
        return overallCause;
    }

    private void pushNewListener(Listener<T> newListener) {
        for (;;) {
            newListener.next = listeners;
            if (listenersUpdater.compareAndSet(this, newListener.next, newListener)) {
                if (newListener.next == null) {
                    Object result = this.result;
                    if (result != null) {
                        tryNotifyRawOutsideExecutor(result);
                    }
                }
                break;
            }
        }
    }

    private abstract static class Listener<T> {
        // protected by volatile write/read barrier, so no need to be volatile*.
        // *other than getListenerCount which is best effort, just for informational purposes, and explicitly not
        // intended for concurrency control.
        @Nullable
        Listener<T> next;

        abstract void onError(Throwable cause);

        abstract void onSuccess(@Nullable T value);
    }

    private static final class GetListener<T> extends Listener<T> {
        @Nullable
        private Object result;

        @Override
        void onSuccess(@Nullable final T value) {
            result = wrap(value);
            synchronized (this) {
                notifyAll();
            }
        }

        @Override
        void onError(final Throwable cause) {
            result = new ErrorResult(cause);
            synchronized (this) {
                notifyAll();
            }
        }

        Object blockingTimedWait(long durationRemainingNs) throws TimeoutException, InterruptedException {
            long timeStampA = System.nanoTime();
            long timeStampB;
            // Blocking code!
            synchronized (this) {
                while (result == null) {
                    wait(NANOSECONDS.toMillis(durationRemainingNs));
                    if (result != null) {
                        break;
                    }
                    timeStampB = System.nanoTime();
                    durationRemainingNs -= timeStampB - timeStampA;
                    if (durationRemainingNs <= 0) {
                        throw new TimeoutException();
                    }
                    // Instead of calling System.nanoTime twice per loop, we call it a single time, and swap the
                    // value in preparation for the next iteration of the loop.
                    timeStampA = timeStampB;
                }
            }
            // Blocking code!
            return result;
        }

        Object blockingWait(boolean interruptible) throws InterruptedException {
            // Blocking code!
            if (interruptible) {
                synchronized (this) {
                    while (result == null) {
                        wait();
                    }
                }
            } else {
                boolean wasInterrupted = false;
                synchronized (this) {
                    while (result == null) {
                        try {
                            wait();
                        } catch (InterruptedException e) {
                            wasInterrupted = true;
                        }
                    }
                }
                if (wasInterrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            // Blocking code!
            return result;
        }
    }

    private static final class JdkExecutorRunAfterBothOnListener<T> extends BothFutureOnListener<T, Object, Void> {
        private final Executor executor;
        private final Runnable runnable;

        private JdkExecutorRunAfterBothOnListener(final Executor executor,
                                                  final ExecutorCompletionStage<Void> stage, final Runnable runnable) {
            super(stage);
            this.executor = executor;
            this.runnable = runnable;
        }

        static <T> JdkExecutorRunAfterBothOnListener<T> newInstance(final Executor executor,
                                                                    final ExecutorCompletionStage<Void> stage,
                                                                    final CompletionStage<?> other,
                                                                    final Runnable runnable) {
            JdkExecutorRunAfterBothOnListener<T> listener =
                    new JdkExecutorRunAfterBothOnListener<>(executor, stage, runnable);
            other.whenComplete(listener);
            return listener;
        }

        @Override
        void completeFuture(final ExecutorCompletionStage<Void> stage, @Nullable final T tValue,
                            @Nullable final Object uValue) {
            executor.execute(() -> RunOnSuccessListener.onSuccess(stage, runnable));
        }

        @Override
        void completeFuture(final ExecutorCompletionStage<Void> stage, final Throwable cause) {
            jdkCompleteExceptionally(stage, cause);
        }
    }

    private static final class RunAfterBothOnListener<T> extends BothFutureOnListener<T, Object, Void> {
        private final io.servicetalk.concurrent.Executor executor;
        private final Runnable runnable;

        private RunAfterBothOnListener(final io.servicetalk.concurrent.Executor executor,
                                       final ExecutorCompletionStage<Void> stage,
                                       final Runnable runnable) {
            super(stage);
            this.executor = executor;
            this.runnable = runnable;
        }

        static <T> RunAfterBothOnListener<T> newInstance(final io.servicetalk.concurrent.Executor executor,
                                                         final ExecutorCompletionStage<Void> stage,
                                                         final CompletionStage<?> other,
                                                         final Runnable runnable) {
            RunAfterBothOnListener<T> listener = new RunAfterBothOnListener<>(executor, stage, runnable);
            other.whenComplete(listener);
            return listener;
        }

        @Override
        void completeFuture(final ExecutorCompletionStage<Void> stage, @Nullable final T tValue,
                            @Nullable final Object uValue) {
            if (executor == immediate()) {
                RunOnSuccessListener.onSuccess(stage, runnable);
            } else {
                executor.execute(() -> RunOnSuccessListener.onSuccess(stage, runnable));
            }
        }

        @Override
        void completeFuture(final ExecutorCompletionStage<Void> stage, final Throwable cause) {
            if (executor == immediate()) {
                stage.completeExceptionallyNoExec(cause);
            } else {
                stage.completeExceptionally(cause);
            }
        }
    }

    private static final class JdkExecutorAcceptBothOnListener<T, U> extends BothFutureOnListener<T, U, Void> {
        private final Executor executor;
        private final BiConsumer<? super T, ? super U> fn;

        private JdkExecutorAcceptBothOnListener(final Executor executor,
                                                final ExecutorCompletionStage<Void> stage,
                                                final BiConsumer<? super T, ? super U> fn) {
            super(stage);
            this.executor = executor;
            this.fn = fn;
        }

        static <T, U> JdkExecutorAcceptBothOnListener<T, U> newInstance(
                final Executor executor, final ExecutorCompletionStage<Void> stage,
                final CompletionStage<? extends U> other, final BiConsumer<? super T, ? super U> fn) {
            JdkExecutorAcceptBothOnListener<T, U> listener =
                    new JdkExecutorAcceptBothOnListener<>(executor, stage, fn);
            other.whenComplete(listener);
            return listener;
        }

        @Override
        void completeFuture(final ExecutorCompletionStage<Void> stage,
                            @Nullable final T tValue, @Nullable final U uValue) {
            executor.execute(() -> AcceptBothOnListener.completeFuture(stage, tValue, uValue, fn));
        }

        @Override
        void completeFuture(final ExecutorCompletionStage<Void> stage, final Throwable cause) {
            jdkCompleteExceptionally(stage, cause);
        }
    }

    private static final class AcceptBothOnListener<T, U> extends BothFutureOnListener<T, U, Void> {
        private final io.servicetalk.concurrent.Executor executor;
        private final BiConsumer<? super T, ? super U> fn;

        private AcceptBothOnListener(final io.servicetalk.concurrent.Executor executor,
                                     final ExecutorCompletionStage<Void> stage,
                                     final BiConsumer<? super T, ? super U> fn) {
            super(stage);
            this.executor = executor;
            this.fn = fn;
        }

        static <T, U> AcceptBothOnListener<T, U> newInstance(final io.servicetalk.concurrent.Executor executor,
                                                             final ExecutorCompletionStage<Void> stage,
                                                             final CompletionStage<? extends U> other,
                                                             final BiConsumer<? super T, ? super U> fn) {
            AcceptBothOnListener<T, U> listener = new AcceptBothOnListener<>(executor, stage, fn);
            other.whenComplete(listener);
            return listener;
        }

        @Override
        void completeFuture(final ExecutorCompletionStage<Void> stage,
                            @Nullable final T tValue, @Nullable final U uValue) {
            if (executor == immediate()) {
                completeFuture(stage, tValue, uValue, fn);
            } else {
                executor.execute(() -> completeFuture(stage, tValue, uValue, fn));
            }
        }

        @Override
        void completeFuture(final ExecutorCompletionStage<Void> stage, final Throwable cause) {
            if (executor == immediate()) {
                stage.completeExceptionallyNoExec(cause);
            } else {
                stage.completeExceptionally(cause);
            }
        }

        static <T, U> void completeFuture(ExecutorCompletionStage<Void> stage, @Nullable T tValue, @Nullable U uValue,
                                          BiConsumer<? super T, ? super U> fn) {
            try {
                fn.accept(tValue, uValue);
            } catch (Throwable cause) {
                stage.completeExceptionallyNoExec(cause);
                return;
            }
            stage.completeNoExec(null);
        }
    }

    private static final class JdkExecutorCombineOnListener<T, U, V> extends BothFutureOnListener<T, U, V> {
        private final Executor executor;
        private final BiFunction<? super T, ? super U, ? extends V> fn;

        private JdkExecutorCombineOnListener(final Executor executor,
                                             final ExecutorCompletionStage<V> stage,
                                             final BiFunction<? super T, ? super U, ? extends V> fn) {
            super(stage);
            this.executor = executor;
            this.fn = fn;
        }

        static <T, U, V> JdkExecutorCombineOnListener<T, U, V> newInstance(
                final Executor executor, final ExecutorCompletionStage<V> stage,
                final CompletionStage<? extends U> other, final BiFunction<? super T, ? super U, ? extends V> fn) {
            JdkExecutorCombineOnListener<T, U, V> listener = new JdkExecutorCombineOnListener<>(executor, stage, fn);
            other.whenComplete(listener);
            return listener;
        }

        @Override
        void completeFuture(final ExecutorCompletionStage<V> stage, @Nullable final T tValue,
                            @Nullable final U uValue) {
            executor.execute(() -> CombineOnListener.completeFuture(stage, tValue, uValue, fn));
        }

        @Override
        void completeFuture(final ExecutorCompletionStage<V> stage, final Throwable cause) {
            jdkCompleteExceptionally(stage, cause);
        }
    }

    private static final class CombineOnListener<T, U, V> extends BothFutureOnListener<T, U, V> {
        private final io.servicetalk.concurrent.Executor executor;
        private final BiFunction<? super T, ? super U, ? extends V> fn;

        private CombineOnListener(io.servicetalk.concurrent.Executor executor,
                                  final ExecutorCompletionStage<V> stage,
                                  final BiFunction<? super T, ? super U, ? extends V> fn) {
            super(stage);
            this.executor = executor;
            this.fn = fn;
        }

        static <T, U, V> CombineOnListener<T, U, V> newInstance(io.servicetalk.concurrent.Executor executor,
                                                                ExecutorCompletionStage<V> stage,
                                                                CompletionStage<? extends U> other,
                                                                BiFunction<? super T, ? super U, ? extends V> fn) {
            CombineOnListener<T, U, V> listener = new CombineOnListener<>(executor, stage, fn);
            other.whenComplete(listener);
            return listener;
        }

        @Override
        void completeFuture(final ExecutorCompletionStage<V> stage,
                            @Nullable final T tValue, @Nullable final U uValue) {
            if (executor == immediate()) {
                completeFuture(stage, tValue, uValue, fn);
            } else {
                executor.execute(() -> completeFuture(stage, tValue, uValue, fn));
            }
        }

        @Override
        void completeFuture(final ExecutorCompletionStage<V> stage, final Throwable cause) {
            if (executor == immediate()) {
                stage.completeExceptionallyNoExec(cause);
            } else {
                stage.completeExceptionally(cause);
            }
        }

        static <T, U, V> void completeFuture(final ExecutorCompletionStage<V> stage, @Nullable final T tValue,
                                             @Nullable final U uValue,
                                             BiFunction<? super T, ? super U, ? extends V> fn) {
            final V vValue;
            try {
                vValue = fn.apply(tValue, uValue);
            } catch (Throwable cause) {
                stage.completeExceptionallyNoExec(cause);
                return;
            }
            stage.completeNoExec(vValue);
        }
    }

    private static final class JdkExecutorHandleListener<T, U> extends Listener<T> {
        private final Executor executor;
        private final ExecutorCompletionStage<U> stage;
        private final BiFunction<? super T, Throwable, ? extends U> fn;

        JdkExecutorHandleListener(final Executor executor, final ExecutorCompletionStage<U> stage,
                                  BiFunction<? super T, Throwable, ? extends U> fn) {
            this.executor = executor;
            this.stage = stage;
            this.fn = fn;
        }

        @Override
        void onSuccess(@Nullable final T value) {
            executor.execute(() -> HandleListener.handleSuccess(stage, value, fn));
        }

        @Override
        void onError(final Throwable cause) {
            executor.execute(() -> HandleListener.handleError(stage, cause, fn));
        }

        static <T, U> void completeFuture(ExecutorCompletionStage<U> stage, Object result,
                                          BiFunction<? super T, Throwable, ? extends U> fn, Executor executor) {
            if (result instanceof ErrorResult) {
                executor.execute(() -> HandleListener.handleError(stage, ((ErrorResult) result).cause, fn));
            } else {
                executor.execute(() -> HandleListener.handleSuccess(stage, unwrap(result), fn));
            }
        }
    }

    private static final class HandleListener<T, U> extends Listener<T> {
        private final ExecutorCompletionStage<U> stage;
        private final BiFunction<? super T, Throwable, ? extends U> fn;

        HandleListener(ExecutorCompletionStage<U> stage,
                       BiFunction<? super T, Throwable, ? extends U> fn) {
            this.stage = stage;
            this.fn = fn;
        }

        @Override
        void onSuccess(@Nullable final T value) {
            handleSuccess(stage, value, fn);
        }

        @Override
        void onError(final Throwable cause) {
            handleError(stage, cause, fn);
        }

        static <T, U> void handleSuccess(ExecutorCompletionStage<U> stage, @Nullable T value,
                                         BiFunction<? super T, Throwable, ? extends U> fn) {
            final U uValue;
            try {
                uValue = fn.apply(value, null);
            } catch (Throwable cause2) {
                stage.completeExceptionallyNoExec(cause2);
                return;
            }
            stage.completeNoExec(uValue);
        }

        static <T, U> void handleError(ExecutorCompletionStage<U> stage, Throwable cause,
                                       BiFunction<? super T, Throwable, ? extends U> fn) {
            final U uValue;
            try {
                uValue = fn.apply(null, cause);
            } catch (Throwable cause2) {
                stage.completeExceptionallyNoExec(cause2);
                return;
            }
            stage.completeNoExec(uValue);
        }

        static <T, U> void completeFuture(ExecutorCompletionStage<U> stage, Object result,
                                          BiFunction<? super T, Throwable, ? extends U> fn,
                                          io.servicetalk.concurrent.Executor executor) {
            if (executor == immediate()) {
                if (result instanceof ErrorResult) {
                    handleError(stage, ((ErrorResult) result).cause, fn);
                } else {
                    handleSuccess(stage, unwrap(result), fn);
                }
            } else {
                if (result instanceof ErrorResult) {
                    executor.execute(() -> handleError(stage, ((ErrorResult) result).cause, fn));
                } else {
                    executor.execute(() -> handleSuccess(stage, unwrap(result), fn));
                }
            }
        }
    }

    private static final class JdkExecutorWhenCompleteListener<T> extends Listener<T> {
        private final Executor executor;
        private final ExecutorCompletionStage<T> stage;
        private final BiConsumer<? super T, ? super Throwable> fn;

        JdkExecutorWhenCompleteListener(final Executor executor, final ExecutorCompletionStage<T> stage,
                                        final BiConsumer<? super T, ? super Throwable> fn) {
            this.executor = executor;
            this.stage = stage;
            this.fn = fn;
        }

        @Override
        void onSuccess(@Nullable final T value) {
            executor.execute(() -> WhenCompleteListener.handleSuccess(stage, value, fn));
        }

        @Override
        void onError(final Throwable cause) {
            executor.execute(() -> WhenCompleteListener.handleError(stage, cause, fn));
        }

        static <T> void completeFuture(ExecutorCompletionStage<T> stage, Object result,
                                       BiConsumer<? super T, ? super Throwable> fn,
                                       Executor executor) {
            if (result instanceof ErrorResult) {
                executor.execute(() -> WhenCompleteListener.handleError(stage, ((ErrorResult) result).cause, fn));
            } else {
                executor.execute(() -> WhenCompleteListener.handleSuccess(stage, unwrap(result), fn));
            }
        }
    }

    private static final class WhenCompleteListener<T> extends Listener<T> {
        private final ExecutorCompletionStage<T> stage;
        private final BiConsumer<? super T, ? super Throwable> fn;

        WhenCompleteListener(ExecutorCompletionStage<T> stage,
                             BiConsumer<? super T, ? super Throwable> fn) {
            this.stage = stage;
            this.fn = fn;
        }

        @Override
        void onSuccess(@Nullable final T value) {
            handleSuccess(stage, value, fn);
        }

        @Override
        void onError(final Throwable cause) {
            handleError(stage, cause, fn);
        }

        static <T> void handleSuccess(ExecutorCompletionStage<T> stage, @Nullable T value,
                                      BiConsumer<? super T, ? super Throwable> fn) {
            try {
                fn.accept(value, null);
            } catch (Throwable cause2) {
                stage.completeExceptionallyNoExec(cause2);
                return;
            }
            stage.completeNoExec(value);
        }

        static <T> void handleError(ExecutorCompletionStage<T> stage, Throwable cause,
                                    BiConsumer<? super T, ? super Throwable> fn) {
            try {
                fn.accept(null, cause);
            } catch (Throwable cause2) {
                stage.completeExceptionallyNoExec(cause2);
                return;
            }
            stage.completeExceptionallyNoExec(cause);
        }

        static <T> void completeFuture(ExecutorCompletionStage<T> stage, Object result,
                                       BiConsumer<? super T, ? super Throwable> fn,
                                       io.servicetalk.concurrent.Executor executor) {
            if (executor == immediate()) {
                if (result instanceof ErrorResult) {
                    handleError(stage, ((ErrorResult) result).cause, fn);
                } else {
                    handleSuccess(stage, unwrap(result), fn);
                }
            } else {
                if (result instanceof ErrorResult) {
                    executor.execute(() -> handleError(stage, ((ErrorResult) result).cause, fn));
                } else {
                    executor.execute(() -> handleSuccess(stage, unwrap(result), fn));
                }
            }
        }
    }

    private static final class ErrorOnErrorListener<T> extends Listener<T> {
        private final ExecutorCompletionStage<T> stage;
        private final Function<Throwable, ? extends T> fn;

        ErrorOnErrorListener(ExecutorCompletionStage<T> stage, Function<Throwable, ? extends T> fn) {
            this.stage = stage;
            this.fn = fn;
        }

        @Override
        void onError(final Throwable cause) {
            handleError(stage, cause, fn);
        }

        @Override
        void onSuccess(@Nullable final T value) {
            stage.completeNoExec(value);
        }

        static <T> void handleError(ExecutorCompletionStage<T> stage, Throwable cause,
                                    Function<Throwable, ? extends T> fn) {
            final T tValue;
            try {
                tValue = fn.apply(cause);
            } catch (Throwable cause2) {
                stage.completeExceptionallyNoExec(cause2);
                return;
            }
            stage.completeNoExec(tValue);
        }

        static <T> void completeFuture(ExecutorCompletionStage<T> stage, Object result,
                                       Function<Throwable, ? extends T> fn,
                                       io.servicetalk.concurrent.Executor executor) {
            if (executor == immediate()) {
                if (result instanceof ErrorResult) {
                    handleError(stage, ((ErrorResult) result).cause, fn);
                } else {
                    stage.completeNoExec(unwrap(result));
                }
            } else {
                if (result instanceof ErrorResult) {
                    executor.execute(() -> handleError(stage, ((ErrorResult) result).cause, fn));
                } else {
                    stage.complete(unwrap(result));
                }
            }
        }
    }

    private static final class JdkExecutorComposeOnSuccessListener<T, U> extends Listener<T> {
        private final Executor executor;
        private final ExecutorCompletionStage<U> stage;
        private final Function<? super T, ? extends CompletionStage<U>> fn;

        JdkExecutorComposeOnSuccessListener(final Executor executor, ExecutorCompletionStage<U> stage,
                                            Function<? super T, ? extends CompletionStage<U>> fn) {
            this.executor = executor;
            this.stage = stage;
            this.fn = fn;
        }

        @Override
        void onError(final Throwable cause) {
            jdkCompleteExceptionally(stage, cause);
        }

        @Override
        void onSuccess(@Nullable final T value) {
            handleSuccess(executor, value, stage, fn);
        }

        static <T, U> void handleSuccess(Executor executor, @Nullable T value, ExecutorCompletionStage<U> stage,
                                         Function<? super T, ? extends CompletionStage<U>> fn) {
            executor.execute(() -> ComposeOnSuccessListener.handleSuccess(stage, value, fn));
        }

        static <T, U> void completeFuture(ExecutorCompletionStage<U> stage, Object result,
                                          Function<? super T, ? extends CompletionStage<U>> fn, Executor executor) {
            if (result instanceof ErrorResult) {
                stage.completeExceptionally(((ErrorResult) result).cause);
            } else {
                handleSuccess(executor, unwrap(result), stage, fn);
            }
        }
    }

    private static final class ComposeOnSuccessListener<T, U> extends Listener<T> {
        private final ExecutorCompletionStage<U> stage;
        private final Function<? super T, ? extends CompletionStage<U>> fn;

        ComposeOnSuccessListener(ExecutorCompletionStage<U> stage,
                                 Function<? super T, ? extends CompletionStage<U>> fn) {
            this.stage = stage;
            this.fn = fn;
        }

        @Override
        void onError(final Throwable cause) {
            stage.completeExceptionallyNoExec(cause);
        }

        @Override
        void onSuccess(@Nullable final T value) {
            handleSuccess(stage, value, fn);
        }

        static <T, U> void handleSuccess(ExecutorCompletionStage<U> stage, @Nullable T value,
                                         Function<? super T, ? extends CompletionStage<U>> fn) {
            try {
                fn.apply(value).whenComplete((uValue, cause) -> {
                    if (cause != null) {
                        stage.completeExceptionally(cause);
                    } else {
                        stage.complete(uValue);
                    }
                });
            } catch (Throwable cause) {
                stage.completeExceptionallyNoExec(cause);
            }
        }

        static <T, U> void completeFuture(ExecutorCompletionStage<U> stage, Object result,
                                          Function<? super T, ? extends CompletionStage<U>> fn,
                                          io.servicetalk.concurrent.Executor executor) {
            if (executor == immediate()) {
                if (result instanceof ErrorResult) {
                    stage.completeExceptionallyNoExec(((ErrorResult) result).cause);
                } else {
                    handleSuccess(stage, unwrap(result), fn);
                }
            } else {
                if (result instanceof ErrorResult) {
                    stage.completeExceptionally(((ErrorResult) result).cause);
                } else {
                    executor.execute(() -> handleSuccess(stage, unwrap(result), fn));
                }
            }
        }
    }

    private static final class JdkExecutorRunEitherListener<T> extends EitherFutureOnListener<T, Object, Void> {
        private final Executor executor;
        private final Runnable action;

        private JdkExecutorRunEitherListener(final Executor executor,
                                             final ExecutorCompletionStage<Void> stage,
                                             final Runnable action) {
            super(stage);
            this.executor = executor;
            this.action = action;
        }

        static <T> JdkExecutorRunEitherListener<T> newInstance(final Executor executor,
                                                               final ExecutorCompletionStage<Void> stage,
                                                               final CompletionStage<?> other,
                                                               final Runnable fn) {
            JdkExecutorRunEitherListener<T> listener = new JdkExecutorRunEitherListener<>(executor, stage, fn);
            other.whenComplete(listener);
            return listener;
        }

        @Override
        void completeFutureT(final ExecutorCompletionStage<Void> stage, @Nullable final T tValue) {
            completeFutureU(stage, tValue);
        }

        @Override
        void completeFutureU(ExecutorCompletionStage<Void> stage, @Nullable final Object tValue) {
            executor.execute(() -> RunEitherListener.completeFuture(stage, action));
        }

        @Override
        void completeFuture(final ExecutorCompletionStage<Void> stage, final Throwable cause) {
            jdkCompleteExceptionally(stage, cause);
        }
    }

    private static final class RunEitherListener<T> extends EitherFutureOnListener<T, Object, Void> {
        private final io.servicetalk.concurrent.Executor executor;
        private final Runnable action;

        private RunEitherListener(io.servicetalk.concurrent.Executor executor,
                                  final ExecutorCompletionStage<Void> stage, final Runnable action) {
            super(stage);
            this.executor = executor;
            this.action = action;
        }

        static <T> RunEitherListener<T> newInstance(io.servicetalk.concurrent.Executor executor,
                                                    final ExecutorCompletionStage<Void> stage,
                                                    final CompletionStage<?> other,
                                                    final Runnable fn) {
            RunEitherListener<T> listener = new RunEitherListener<>(executor, stage, fn);
            other.whenComplete(listener);
            return listener;
        }

        @Override
        void completeFutureT(final ExecutorCompletionStage<Void> stage, @Nullable final T tValue) {
            completeFutureU(stage, tValue);
        }

        @Override
        void completeFutureU(ExecutorCompletionStage<Void> stage, @Nullable final Object tValue) {
            if (executor == immediate()) {
                completeFuture(stage, action);
            } else {
                executor.execute(() -> completeFuture(stage, action));
            }
        }

        @Override
        void completeFuture(final ExecutorCompletionStage<Void> stage, final Throwable cause) {
            if (executor == immediate()) {
                stage.completeExceptionallyNoExec(cause);
            } else {
                stage.completeExceptionally(cause);
            }
        }

        static void completeFuture(final ExecutorCompletionStage<Void> stage, Runnable action) {
            try {
                action.run();
            } catch (Throwable cause) {
                stage.completeExceptionallyNoExec(cause);
                return;
            }
            stage.completeNoExec(null);
        }
    }

    private static final class JdkExecutorAcceptEitherListener<T> extends EitherFutureOnListener<T, T, Void> {
        private final Executor executor;
        private final Consumer<? super T> action;

        private JdkExecutorAcceptEitherListener(final Executor executor, final ExecutorCompletionStage<Void> stage,
                                                final Consumer<? super T> action) {
            super(stage);
            this.executor = executor;
            this.action = action;
        }

        static <T> JdkExecutorAcceptEitherListener<T> newInstance(final Executor executor,
                                                                  final ExecutorCompletionStage<Void> stage,
                                                                  final CompletionStage<? extends T> other,
                                                                  final Consumer<? super T> fn) {
            JdkExecutorAcceptEitherListener<T> listener = new JdkExecutorAcceptEitherListener<>(executor, stage, fn);
            other.whenComplete(listener);
            return listener;
        }

        @Override
        void completeFutureT(final ExecutorCompletionStage<Void> stage, @Nullable final T tValue) {
            executor.execute(() -> AcceptEitherListener.completeFuture(stage, tValue, action));
        }

        @Override
        void completeFutureU(ExecutorCompletionStage<Void> stage, @Nullable final T tValue) {
            completeFutureT(stage, tValue);
        }

        @Override
        void completeFuture(final ExecutorCompletionStage<Void> stage, final Throwable cause) {
            jdkCompleteExceptionally(stage, cause);
        }
    }

    private static final class AcceptEitherListener<T> extends EitherFutureOnListener<T, T, Void> {
        private final io.servicetalk.concurrent.Executor executor;
        private final Consumer<? super T> action;

        private AcceptEitherListener(io.servicetalk.concurrent.Executor executor,
                                     final ExecutorCompletionStage<Void> stage, final Consumer<? super T> action) {
            super(stage);
            this.executor = executor;
            this.action = action;
        }

        static <T> AcceptEitherListener<T> newInstance(io.servicetalk.concurrent.Executor executor,
                                                       final ExecutorCompletionStage<Void> stage,
                                                       final CompletionStage<? extends T> other,
                                                       final Consumer<? super T> fn) {
            AcceptEitherListener<T> listener = new AcceptEitherListener<>(executor, stage, fn);
            other.whenComplete(listener);
            return listener;
        }

        @Override
        void completeFutureT(final ExecutorCompletionStage<Void> stage, @Nullable final T tValue) {
            if (executor == immediate()) {
                completeFuture(stage, tValue, action);
            } else {
                executor.execute(() -> completeFuture(stage, tValue, action));
            }
        }

        @Override
        void completeFutureU(ExecutorCompletionStage<Void> stage, @Nullable final T tValue) {
            completeFutureT(stage, tValue);
        }

        @Override
        void completeFuture(final ExecutorCompletionStage<Void> stage, final Throwable cause) {
            if (executor == immediate()) {
                stage.completeExceptionallyNoExec(cause);
            } else {
                stage.completeExceptionally(cause);
            }
        }

        static <T> void completeFuture(final ExecutorCompletionStage<Void> stage, @Nullable final T tValue,
                                       final Consumer<? super T> action) {
            try {
                action.accept(tValue);
            } catch (Throwable cause) {
                stage.completeExceptionallyNoExec(cause);
                return;
            }
            stage.completeNoExec(null);
        }
    }

    private static final class JdkExecutorApplyEitherListener<T, U> extends EitherFutureOnListener<T, T, U> {
        private final Executor executor;
        private final Function<? super T, U> fn;

        private JdkExecutorApplyEitherListener(final Executor executor, final ExecutorCompletionStage<U> stage,
                                               final Function<? super T, U> fn) {
            super(stage);
            this.executor = executor;
            this.fn = fn;
        }

        static <T, U> JdkExecutorApplyEitherListener<T, U> newInstance(final Executor executor,
                                                                       final ExecutorCompletionStage<U> stage,
                                                                       final CompletionStage<? extends T> other,
                                                                       final Function<? super T, U> fn) {
            JdkExecutorApplyEitherListener<T, U> listener = new JdkExecutorApplyEitherListener<>(executor, stage, fn);
            other.whenComplete(listener);
            return listener;
        }

        @Override
        void completeFutureT(final ExecutorCompletionStage<U> stage, @Nullable final T tValue) {
            executor.execute(() -> ApplyEitherListener.completeFuture(stage, tValue, fn));
        }

        @Override
        void completeFutureU(ExecutorCompletionStage<U> stage, @Nullable final T tValue) {
            completeFutureT(stage, tValue);
        }

        @Override
        void completeFuture(final ExecutorCompletionStage<U> stage, final Throwable cause) {
            jdkCompleteExceptionally(stage, cause);
        }
    }

    private static final class ApplyEitherListener<T, U> extends EitherFutureOnListener<T, T, U> {
        private final io.servicetalk.concurrent.Executor executor;
        private final Function<? super T, U> fn;

        private ApplyEitherListener(io.servicetalk.concurrent.Executor executor,
                                    final ExecutorCompletionStage<U> stage, final Function<? super T, U> fn) {
            super(stage);
            this.executor = executor;
            this.fn = fn;
        }

        static <T, U> ApplyEitherListener<T, U> newInstance(io.servicetalk.concurrent.Executor executor,
                                                            final ExecutorCompletionStage<U> stage,
                                                            final CompletionStage<? extends T> other,
                                                            final Function<? super T, U> fn) {
            ApplyEitherListener<T, U> listener = new ApplyEitherListener<>(executor, stage, fn);
            other.whenComplete(listener);
            return listener;
        }

        @Override
        void completeFutureT(ExecutorCompletionStage<U> stage, @Nullable final T tValue) {
            if (executor == immediate()) {
                completeFuture(stage, tValue, fn);
            } else {
                executor.execute(() -> completeFuture(stage, tValue, fn));
            }
        }

        @Override
        void completeFutureU(ExecutorCompletionStage<U> stage, @Nullable final T tValue) {
            completeFutureT(stage, tValue);
        }

        @Override
        void completeFuture(final ExecutorCompletionStage<U> stage, final Throwable cause) {
            if (executor == immediate()) {
                stage.completeExceptionallyNoExec(cause);
            } else {
                stage.completeExceptionally(cause);
            }
        }

        static <T, U> void completeFuture(ExecutorCompletionStage<U> stage, @Nullable final T tValue,
                                          Function<? super T, U> fn) {
            final U value;
            try {
                value = fn.apply(tValue);
            } catch (Throwable cause) {
                stage.completeExceptionallyNoExec(cause);
                return;
            }
            stage.completeNoExec(value);
        }
    }

    private abstract static class EitherFutureOnListener<T, U, V> extends Listener<T>
            implements BiConsumer<U, Throwable> {
        private static final AtomicIntegerFieldUpdater<EitherFutureOnListener> doneUpdater =
                AtomicIntegerFieldUpdater.newUpdater(EitherFutureOnListener.class, "done");
        @SuppressWarnings("unused")
        @Nullable
        private volatile int done;
        private final ExecutorCompletionStage<V> stage;

        EitherFutureOnListener(final ExecutorCompletionStage<V> stage) {
            this.stage = stage;
        }

        @Override
        final void onSuccess(@Nullable final T value) {
            if (doneUpdater.compareAndSet(this, 0, 1)) {
                completeFutureT(stage, value);
            }
        }

        @Override
        final void onError(final Throwable cause) {
            if (doneUpdater.compareAndSet(this, 0, 1)) {
                completeFuture(stage, cause);
            }
        }

        @Override
        public final void accept(final U value, final Throwable throwable) {
            if (doneUpdater.compareAndSet(this, 0, 1)) {
                if (throwable != null) {
                    completeFuture(stage, throwable);
                } else {
                    completeFutureU(stage, value);
                }
            }
        }

        abstract void completeFutureT(ExecutorCompletionStage<V> stage, @Nullable T tValue);

        abstract void completeFutureU(ExecutorCompletionStage<V> stage, @Nullable U uValue);

        abstract void completeFuture(ExecutorCompletionStage<V> stage, Throwable cause);
    }

    private abstract static class BothFutureOnListener<T, U, V> extends Listener<T>
            implements BiConsumer<U, Throwable> {
        private static final AtomicReferenceFieldUpdater<BothFutureOnListener, Object> firstResultUpdater =
                newUpdater(BothFutureOnListener.class, Object.class, "firstResult");
        @Nullable
        private volatile Object firstResult;
        private final ExecutorCompletionStage<V> stage;

        BothFutureOnListener(final ExecutorCompletionStage<V> stage) {
            this.stage = stage;
        }

        @Override
        final void onSuccess(@Nullable final T value) {
            for (;;) {
                Object firstValue = this.firstResult;
                if (firstValue == null) {
                    if (firstResultUpdater.compareAndSet(this, null, wrap(value))) {
                        break;
                    }
                } else if (firstValue instanceof ErrorResult) {
                    break;
                } else {
                    completeFuture(stage, value, unwrap(firstValue));
                    firstResult = ErrorResult.DUMMY; // prevent GC nepotism
                    break;
                }
            }
        }

        @Override
        final void onError(final Throwable cause) {
            if (firstResultUpdater.compareAndSet(this, null, ErrorResult.DUMMY)) {
                completeFuture(stage, cause);
            }
        }

        @Override
        public final void accept(final U value, final Throwable throwable) {
            for (;;) {
                Object firstValue = this.firstResult;
                if (firstValue == null) {
                    if (firstResultUpdater.compareAndSet(this, null, wrap(value))) {
                        break;
                    }
                } else if (firstValue instanceof ErrorResult) {
                    break;
                } else {
                    completeFuture(stage, unwrap(firstValue), value);
                    firstResult = ErrorResult.DUMMY; // prevent GC nepotism
                    break;
                }
            }
        }

        abstract void completeFuture(ExecutorCompletionStage<V> stage, @Nullable T tValue, @Nullable U uValue);

        abstract void completeFuture(ExecutorCompletionStage<V> stage, Throwable cause);
    }

    private static final class JdkExecutorRunOnSuccessListener<T> extends Listener<T> {
        private final Executor executor;
        private final ExecutorCompletionStage<Void> stage;
        private final Runnable runnable;

        JdkExecutorRunOnSuccessListener(final Executor executor, final ExecutorCompletionStage<Void> stage,
                                        final Runnable runnable) {
            this.executor = executor;
            this.stage = stage;
            this.runnable = runnable;
        }

        @Override
        void onError(final Throwable cause) {
            jdkCompleteExceptionally(stage, cause);
        }

        @Override
        void onSuccess(@Nullable final T value) {
            executor.execute(() -> RunOnSuccessListener.onSuccess(stage, runnable));
        }

        static void completeFuture(ExecutorCompletionStage<Void> stage,
                                   Object result,
                                   Runnable runnable,
                                   Executor executor) {
            if (result instanceof ErrorResult) {
                executor.execute(() -> stage.completeExceptionallyNoExec(((ErrorResult) result).cause));
            } else {
                executor.execute(() -> RunOnSuccessListener.onSuccess(stage, runnable));
            }
        }
    }

    private static final class RunOnSuccessListener<T> extends Listener<T> {
        private final ExecutorCompletionStage<Void> stage;
        private final Runnable runnable;

        RunOnSuccessListener(ExecutorCompletionStage<Void> stage, Runnable runnable) {
            this.stage = stage;
            this.runnable = runnable;
        }

        @Override
        void onError(final Throwable cause) {
            stage.completeExceptionallyNoExec(cause);
        }

        @Override
        void onSuccess(@Nullable final T value) {
            onSuccess(stage, runnable);
        }

        static void onSuccess(ExecutorCompletionStage<Void> stage, Runnable runnable) {
            try {
                runnable.run();
            } catch (Throwable cause) {
                stage.completeExceptionallyNoExec(cause);
                return;
            }
            stage.completeNoExec(null);
        }

        static void completeFuture(ExecutorCompletionStage<Void> stage,
                                   Object result,
                                   Runnable runnable,
                                   io.servicetalk.concurrent.Executor executor) {
            if (executor == immediate()) {
                if (result instanceof ErrorResult) {
                    stage.completeExceptionallyNoExec(((ErrorResult) result).cause);
                } else {
                    onSuccess(stage, runnable);
                }
            } else {
                if (result instanceof ErrorResult) {
                    stage.completeExceptionally(((ErrorResult) result).cause);
                } else {
                    executor.execute(() -> onSuccess(stage, runnable));
                }
            }
        }
    }

    private static final class JdkExecutorAcceptOnSuccessListener<T> extends Listener<T> {
        private final Executor executor;
        private final ExecutorCompletionStage<Void> stage;
        private final Consumer<? super T> consumer;

        JdkExecutorAcceptOnSuccessListener(final Executor executor, final ExecutorCompletionStage<Void> stage,
                                           final Consumer<? super T> consumer) {
            this.executor = executor;
            this.stage = stage;
            this.consumer = consumer;
        }

        @Override
        void onError(final Throwable cause) {
            jdkCompleteExceptionally(stage, cause);
        }

        @Override
        void onSuccess(@Nullable final T value) {
            executor.execute(() -> AcceptOnSuccessListener.onSuccess(stage, value, consumer));
        }

        static <T> void completeFuture(ExecutorCompletionStage<Void> stage,
                                       Object result,
                                       final Consumer<? super T> action,
                                       Executor executor) {
            if (result instanceof ErrorResult) {
                executor.execute(() -> stage.completeExceptionally(((ErrorResult) result).cause));
            } else {
                executor.execute(() -> AcceptOnSuccessListener.onSuccess(stage, unwrap(result), action));
            }
        }
    }

    private static final class AcceptOnSuccessListener<T> extends Listener<T> {
        private final ExecutorCompletionStage<Void> stage;
        private final Consumer<? super T> consumer;

        AcceptOnSuccessListener(ExecutorCompletionStage<Void> stage,
                                Consumer<? super T> consumer) {
            this.stage = stage;
            this.consumer = consumer;
        }

        @Override
        void onError(final Throwable cause) {
            stage.completeExceptionallyNoExec(cause);
        }

        @Override
        void onSuccess(@Nullable final T value) {
            onSuccess(stage, value, consumer);
        }

        static <T> void onSuccess(ExecutorCompletionStage<Void> stage, @Nullable final T value,
                                  Consumer<? super T> consumer) {
            try {
                consumer.accept(value);
            } catch (Throwable cause) {
                stage.completeExceptionallyNoExec(cause);
                return;
            }
            stage.completeNoExec(null);
        }

        static <T> void completeFuture(ExecutorCompletionStage<Void> stage,
                                       Object result,
                                       final Consumer<? super T> action,
                                       io.servicetalk.concurrent.Executor executor) {
            if (executor == immediate()) {
                if (result instanceof ErrorResult) {
                    stage.completeExceptionallyNoExec(((ErrorResult) result).cause);
                } else {
                    onSuccess(stage, unwrap(result), action);
                }
            } else {
                if (result instanceof ErrorResult) {
                    stage.completeExceptionally(((ErrorResult) result).cause);
                } else {
                    executor.execute(() -> onSuccess(stage, unwrap(result), action));
                }
            }
        }
    }

    private static final class JdkExecutorApplyOnSuccessListener<T, U> extends Listener<T> {
        private final Executor executor;
        private final ExecutorCompletionStage<U> stage;
        private final Function<? super T, ? extends U> fn;

        JdkExecutorApplyOnSuccessListener(final Executor executor, final ExecutorCompletionStage<U> stage,
                                          final Function<? super T, ? extends U> fn) {
            this.executor = executor;
            this.stage = stage;
            this.fn = fn;
        }

        @Override
        void onError(final Throwable cause) {
            jdkCompleteExceptionally(stage, cause);
        }

        @Override
        void onSuccess(@Nullable final T value) {
            executor.execute(() -> ApplyOnSuccessListener.onSuccess(stage, value, fn));
        }

        static <T, U> void completeFuture(ExecutorCompletionStage<U> stage, Object result,
                                          final Function<? super T, ? extends U> fn,
                                          Executor executor) {
            if (result instanceof ErrorResult) {
                executor.execute(() -> stage.completeExceptionallyNoExec(((ErrorResult) result).cause));
            } else {
                executor.execute(() -> ApplyOnSuccessListener.onSuccess(stage, unwrap(result), fn));
            }
        }
    }

    private static final class ApplyOnSuccessListener<T, U> extends Listener<T> {
        private final ExecutorCompletionStage<U> stage;
        private final Function<? super T, ? extends U> fn;

        ApplyOnSuccessListener(ExecutorCompletionStage<U> stage,
                               Function<? super T, ? extends U> fn) {
            this.stage = stage;
            this.fn = fn;
        }

        @Override
        void onError(final Throwable cause) {
            stage.completeExceptionallyNoExec(cause);
        }

        @Override
        void onSuccess(@Nullable final T value) {
            onSuccess(stage, value, fn);
        }

        static <T, U> void onSuccess(ExecutorCompletionStage<U> stage, @Nullable final T value,
                                     Function<? super T, ? extends U> fn) {
            final U result;
            try {
                result = fn.apply(value);
            } catch (Throwable cause) {
                stage.completeExceptionallyNoExec(cause);
                return;
            }
            stage.completeNoExec(result);
        }

        static <U, T> void completeFuture(ExecutorCompletionStage<U> stage,
                                          Object result,
                                          Function<? super T, ? extends U> fn,
                                          io.servicetalk.concurrent.Executor executor) {
            if (executor == immediate()) {
                if (result instanceof ErrorResult) {
                    stage.completeExceptionallyNoExec(((ErrorResult) result).cause);
                } else {
                    onSuccess(stage, unwrap(result), fn);
                }
            } else {
                if (result instanceof ErrorResult) {
                    stage.completeExceptionally(((ErrorResult) result).cause);
                } else {
                    executor.execute(() -> onSuccess(stage, unwrap(result), fn));
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static <T> T unwrap(Object result) {
        return result == NULL ? null : (T) result;
    }

    private static <T> Object wrap(@Nullable T result) {
        return result == null ? NULL : result;
    }

    /**
     * Complete a {@link ExecutorCompletionStage} that is associated with a JDK {@link Executor}.
     * This method can be used when no user code is invoked, and instead the failure is just being propagated to the
     * next stage. In this case we may be able to avoid an offload if the next stage is already completed or
     * not associated with a JDK {@link Executor}.
     * @param stage The {@link ExecutorCompletionStage} to complete.
     * @param cause The failure cause.
     * @param <U> The type of data for {@code stage}.
     */
    private static <U> void jdkCompleteExceptionally(ExecutorCompletionStage<U> stage, Throwable cause) {
        stage.completeExceptionallyNoExec(cause);
    }

    private static final class ErrorResult {
        static final ErrorResult DUMMY = new ErrorResult(unknownStackTrace(new RuntimeException(), ErrorResult.class,
                "<init>"));
        final Throwable cause;
        ErrorResult(Throwable cause) {
            this.cause = cause;
        }
    }

    private static final class JdkExecutorWrapper implements io.servicetalk.concurrent.Executor {
        private final Executor executor;

        JdkExecutorWrapper(Executor executor) {
            this.executor = executor;
        }

        @Override
        public Cancellable execute(final Runnable task) {
            executor.execute(task);
            return IGNORE_CANCEL;
        }

        @Override
        public Cancellable schedule(final Runnable task, final long delay, final TimeUnit unit) {
            throw new UnsupportedOperationException();
        }
    }
}
