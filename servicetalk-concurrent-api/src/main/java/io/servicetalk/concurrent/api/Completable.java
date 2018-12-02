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
import io.servicetalk.concurrent.internal.SignalOffloader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.CompletableDoOnUtils.doOnCompleteSupplier;
import static io.servicetalk.concurrent.api.CompletableDoOnUtils.doOnErrorSupplier;
import static io.servicetalk.concurrent.api.CompletableDoOnUtils.doOnSubscribeSupplier;
import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.internal.SignalOffloaders.newOffloaderFor;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;

/**
 * An asynchronous computation that does not emit any data. It just completes or emits an error.
 */
public abstract class Completable implements io.servicetalk.concurrent.Completable {
    private static final Logger LOGGER = LoggerFactory.getLogger(Completable.class);

    private final Executor executor;

    static {
        AsyncContext.autoEnable();
    }

    /**
     * New instance.
     */
    protected Completable() {
        this(immediate());
    }

    /**
     * New instance.
     *
     * @param executor {@link Executor} to use for this {@link Completable}.
     */
    Completable(final Executor executor) {
        this.executor = requireNonNull(executor);
    }

    //
    // Operators Begin
    //

    /**
     * Ignores any error returned by this {@link Completable} and resume to a new {@link Completable}.
     * <p>
     * This method provides similar capabilities to a try/catch block in sequential programming:
     * <pre>{@code
     *     try {
     *         resultOfThisCompletable();
     *     } catch (Throwable cause) {
     *         // Note that nextFactory returning a error Completable is like re-throwing (nextFactory shouldn't throw).
     *         nextFactory.apply(cause);
     *     }
     * }</pre>
     *
     * @param nextFactory Returns the next {@link Completable}, if this {@link Completable} emits an error.
     * @return The new {@link Completable}.
     */
    public final Completable onErrorResume(Function<? super Throwable, Completable> nextFactory) {
        return new ResumeCompletable(this, nextFactory, executor);
    }

    /**
     * Invokes the {@code onComplete} {@link Runnable} argument when {@link Subscriber#onComplete()} is called for
     * {@link Subscriber}s of the returned {@link Completable}.
     * <p>
     * The order in which {@code onComplete} will be invoked relative to {@link Subscriber#onComplete()} is
     * undefined. If you need strict ordering see {@link #doBeforeComplete(Runnable)} and
     * {@link #doAfterComplete(Runnable)}.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *  // NOTE: The order of operations here is not guaranteed by this method!
     *  nextOperation(result);
     *  onComplete.run();
     * }</pre>
     *
     * @param onComplete Invoked when {@link Subscriber#onComplete()} is called for {@link Subscriber}s of the returned
     * {@link Completable}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Completable}.
     * @see #doBeforeComplete(Runnable)
     * @see #doAfterComplete(Runnable)
     */
    public final Completable doOnComplete(Runnable onComplete) {
        return doAfterComplete(onComplete);
    }

    /**
     * Invokes the {@code onError} {@link Consumer} argument when {@link Subscriber#onError(Throwable)} is called for
     * {@link Subscriber}s of the returned {@link Completable}.
     * <p>
     * The order in which {@code onError} will be invoked relative to {@link Subscriber#onError(Throwable)} is
     * undefined. If you need strict ordering see {@link #doBeforeError(Consumer)} and
     * {@link #doAfterError(Consumer)}.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *  try {
     *    resultOfThisCompletable();
     *  } catch (Throwable cause) {
     *      // NOTE: The order of operations here is not guaranteed by this method!
     *      nextOperation(cause);
     *      onError.accept(cause);
     *  }
     * }</pre>
     *
     * @param onError Invoked when {@link Subscriber#onError(Throwable)} is called for {@link Subscriber}s of the
     * returned {@link Completable}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Completable}.
     * @see #doBeforeError(Consumer)
     * @see #doAfterError(Consumer)
     */
    public final Completable doOnError(Consumer<Throwable> onError) {
        return doAfterError(onError);
    }

    /**
     * Invokes the {@code doFinally} {@link Runnable} argument exactly once, when any of the following terminal methods
     * are called:
     * <ul>
     *     <li>{@link Subscriber#onComplete()}</li>
     *     <li>{@link Subscriber#onError(Throwable)}</li>
     *     <li>{@link Cancellable#cancel()}</li>
     * </ul>
     * for Subscriptions/{@link Subscriber}s of the returned {@link Completable}.
     * <p>
     * The order in which {@code doFinally} will be invoked relative to the above methods is undefined. If you need
     * strict ordering see {@link #doBeforeFinally(Runnable)} and {@link #doAfterFinally(Runnable)}.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *  try {
     *      resultOfThisCompletable();
     *  } finally {
     *      // NOTE: The order of operations here is not guaranteed by this method!
     *      nextOperation(); // Maybe notifying of cancellation, or termination
     *      doFinally.run();
     *  }
     * }</pre>
     *
     * @param doFinally Invoked exactly once, when any of the following terminal methods are called:
     * <ul>
     *     <li>{@link Subscriber#onComplete()}</li>
     *     <li>{@link Subscriber#onError(Throwable)}</li>
     *     <li>{@link Cancellable#cancel()}</li>
     * </ul>
     * for Subscriptions/{@link Subscriber}s of the returned {@link Completable} <strong>MUST NOT</strong> throw.
     * @return The new {@link Completable}.
     * @see #doAfterFinally(Runnable)
     * @see #doBeforeFinally(Runnable)
     */
    public final Completable doFinally(Runnable doFinally) {
        return doAfterFinally(doFinally);
    }

    /**
     * Invokes the {@code onCancel} {@link Runnable} argument when {@link Cancellable#cancel()} is called for
     * Subscriptions of the returned {@link Completable}.
     * <p>
     * The order in which {@code doFinally} will be invoked relative to {@link Cancellable#cancel()} is undefined. If
     * you need strict ordering see {@link #doBeforeCancel(Runnable)} and {@link #doAfterCancel(Runnable)}.

     * @param onCancel Invoked when {@link Cancellable#cancel()} is called for Subscriptions of the
     * returned {@link Completable}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Completable}.
     * @see #doBeforeCancel(Runnable)
     * @see #doAfterCancel(Runnable)
     */
    public final Completable doOnCancel(Runnable onCancel) {
        return doAfterCancel(onCancel);
    }

    /**
     * Creates a new {@link Completable} that will mimic the signals of this {@link Completable} but will terminate
     * with a {@link TimeoutException} if time {@code duration} elapses between {@link #subscribe(Subscriber)} and
     * termination. The timer starts when the returned {@link Completable} is {@link #subscribe(Subscriber) subscribed}
     * to.
     * <p>
     * In the event of timeout any {@link Cancellable} from {@link Subscriber#onSubscribe(Cancellable)} will be
     * {@link Cancellable#cancel() cancelled} and the associated {@link Subscriber} will be
     * {@link Subscriber#onError(Throwable) terminated}.
     * @param duration The time duration which is allowed to elapse before {@link Subscriber#onComplete()}.
     * @param unit The units for {@code duration}.
     * @return a new {@link Completable} that will mimic the signals of this {@link Completable} but will terminate with
     * a {@link TimeoutException} if time {@code duration} elapses before {@link Subscriber#onComplete()}.
     * @see <a href="http://reactivex.io/documentation/operators/timeout.html">ReactiveX timeout operator.</a>
     */
    public final Completable timeout(long duration, TimeUnit unit) {
        return timeout(duration, unit, executor);
    }

    /**
     * Creates a new {@link Completable} that will mimic the signals of this {@link Completable} but will terminate
     * with a {@link TimeoutException} if time {@code duration} elapses between {@link #subscribe(Subscriber)} and
     * termination. The timer starts when the returned {@link Completable} is {@link #subscribe(Subscriber) subscribed}
     * to.
     * <p>
     * In the event of timeout any {@link Cancellable} from {@link Subscriber#onSubscribe(Cancellable)} will be
     * {@link Cancellable#cancel() cancelled} and the associated {@link Subscriber} will be
     * {@link Subscriber#onError(Throwable) terminated}.
     * @param duration The time duration which is allowed to elapse before {@link Subscriber#onComplete()}.
     * @param unit The units for {@code duration}.
     * @param timeoutExecutor The {@link Executor} to use for managing the timer notifications.
     * @return a new {@link Completable} that will mimic the signals of this {@link Completable} but will terminate with
     * a {@link TimeoutException} if time {@code duration} elapses before {@link Subscriber#onComplete()}.
     * @see <a href="http://reactivex.io/documentation/operators/timeout.html">ReactiveX timeout operator.</a>
     */
    public final Completable timeout(long duration, TimeUnit unit, Executor timeoutExecutor) {
        return new TimeoutCompletable(this, duration, unit, timeoutExecutor);
    }

    /**
     * Creates a new {@link Completable} that will mimic the signals of this {@link Completable} but will terminate
     * with a {@link TimeoutException} if time {@code duration} elapses between {@link #subscribe(Subscriber)} and
     * termination. The timer starts when the returned {@link Completable} is {@link #subscribe(Subscriber) subscribed}
     * to.
     * <p>
     * In the event of timeout any {@link Cancellable} from {@link Subscriber#onSubscribe(Cancellable)} will be
     * {@link Cancellable#cancel() cancelled} and the associated {@link Subscriber} will be
     * {@link Subscriber#onError(Throwable) terminated}.
     * @param duration The time duration which is allowed to elapse before {@link Subscriber#onComplete()}.
     * @return a new {@link Completable} that will mimic the signals of this {@link Completable} but will terminate with
     * a {@link TimeoutException} if time {@code duration} elapses before {@link Subscriber#onComplete()}.
     * @see <a href="http://reactivex.io/documentation/operators/timeout.html">ReactiveX timeout operator.</a>
     */
    public final Completable timeout(Duration duration) {
        return timeout(duration, executor);
    }

    /**
     * Creates a new {@link Completable} that will mimic the signals of this {@link Completable} but will terminate
     * with a {@link TimeoutException} if time {@code duration} elapses between {@link #subscribe(Subscriber)} and
     * termination. The timer starts when the returned {@link Completable} is {@link #subscribe(Subscriber) subscribed}
     * to.
     * <p>
     * In the event of timeout any {@link Cancellable} from {@link Subscriber#onSubscribe(Cancellable)} will be
     * {@link Cancellable#cancel() cancelled} and the associated {@link Subscriber} will be
     * {@link Subscriber#onError(Throwable) terminated}.
     * @param duration The time duration which is allowed to elapse before {@link Subscriber#onComplete()}.
     * @param timeoutExecutor The {@link Executor} to use for managing the timer notifications.
     * @return a new {@link Completable} that will mimic the signals of this {@link Completable} but will terminate with
     * a {@link TimeoutException} if time {@code duration} elapses before {@link Subscriber#onComplete()}.
     * @see <a href="http://reactivex.io/documentation/operators/timeout.html">ReactiveX timeout operator.</a>
     */
    public final Completable timeout(Duration duration, Executor timeoutExecutor) {
        return new TimeoutCompletable(this, duration, timeoutExecutor);
    }

    /**
     * Once this {@link Completable} is terminated successfully, subscribe to {@code next} {@link Completable}
     * and propagate its terminal signal to the returned {@link Completable}. Any error from this {@link Completable}
     * or {@code next} {@link Completable} are propagated to the returned {@link Completable}.
     * <p>
     * This method provides a means to sequence the execution of two asynchronous sources and in sequential programming
     * is similar to:
     * <pre>{@code
     *     resultOfThisCompletable();
     *     nextCompletable();
     * }</pre>
     *
     * @param next {@link Completable} to subscribe after this {@link Completable} terminates successfully.
     * @return A {@link Completable} that emits the terminal signal of {@code next} {@link Completable}, after this
     * {@link Completable} has terminated successfully.
     */
    public final Completable concatWith(Completable next) {
        return new CompletableConcatWithCompletable(this, next, executor);
    }

    /**
     * Once this {@link Completable} is terminated successfully, subscribe to {@code next} {@link Single}
     * and propagate the result to the returned {@link Single}. Any error from this {@link Completable} or {@code next}
     * {@link Single} are propagated to the returned {@link Single}.
     * <p>
     * This method provides a means to sequence the execution of two asynchronous sources and in sequential programming
     * is similar to:
     * <pre>{@code
     *     resultOfThisCompletable();
     *     T result = resultOfNextSingle();
     *     return result;
     * }</pre>
     *
     * @param next {@link Single} to subscribe after this {@link Completable} terminates successfully.
     * @param <T> Type of result of the returned {@link Single}.
     * @return A {@link Single} that emits the result of {@code next} {@link Single}, after this {@link Completable}
     * has terminated successfully.
     */
    public final <T> Single<T> concatWith(Single<? extends T> next) {
        return new CompletableConcatWithSingle<>(this, next, executor);
    }

    /**
     * Once this {@link Completable} is terminated successfully, subscribe to {@code next} {@link Publisher}
     * and propagate all emissions to the returned {@link Publisher}. Any error from this {@link Completable} or
     * {@code next} {@link Publisher} are propagated to the returned {@link Publisher}.
     * <p>
     * This method provides a means to sequence the execution of two asynchronous sources and in sequential programming
     * is similar to:
     * <pre>{@code
     *     List<T> results = new ...;
     *     resultOfThisCompletable();
     *     results.addAll(nextStream());
     *     return results;
     * }</pre>
     *
     * @param next {@link Publisher} to subscribe after this {@link Completable} terminates successfully.
     * @param <T> Type of objects emitted from the returned {@link Publisher}.
     * @return A {@link Publisher} that emits all items emitted from {@code next} {@link Publisher}, after this
     * {@link Completable} has terminated successfully.
     */
    public final <T> Publisher<T> concatWith(Publisher<? extends T> next) {
        return toSingle().flatMapPublisher(aVoid -> next);
    }

    /**
     * Merges this {@link Completable} with the {@code other} {@link Completable}s so that the resulting
     * {@link Completable} terminates successfully when all of these complete or terminates with an error when any one
     * terminates with an error.
     * <p>
     * This method provides a means to merge multiple asynchronous sources, fails-fast in the presence of any errors,
     * and in sequential programming is similar to:
     * <pre>{@code
     *     ExecutorService e = ...;
     *     List<Future<Void>> futures = ...;
     *     futures.add(e.submit(() -> resultOfThisCompletable()));
     *     for (Completable c : other) {
     *         futures.add(e.submit(() -> resultOfCompletable(c));
     *     }
     *     // This is an approximation, this operator does not provide any ordering guarantees for the results.
     *     for (Future<Void> future : futures) {
     *         future.get(); // Throws if the processing for this item failed.
     *     }
     * }</pre>
     *
     * @param other {@link Completable}s to merge.
     * @return {@link Completable} that terminates successfully when this and all {@code other} {@link Completable}s
     * complete or terminates with an error when any one terminates with an error.
     */
    public final Completable merge(Completable... other) {
        return new MergeCompletable(false, this, executor, other);
    }

    /**
     * Merges this {@link Completable} with the {@code other} {@link Completable}s so that the resulting
     * {@link Completable} terminates successfully when all of these complete or terminates with an error when any one
     * terminates with an error.
     * <p>
     * This method provides a means to merge multiple asynchronous sources, fails-fast in the presence of any errors,
     * and in sequential programming is similar to:
     * <pre>{@code
     *     ExecutorService e = ...;
     *     List<Future<Void>> futures = ...;
     *     futures.add(e.submit(() -> resultOfThisCompletable()));
     *     for (Completable c : other) {
     *         futures.add(e.submit(() -> resultOfCompletable(c));
     *     }
     *     // This is an approximation, this operator does not provide any ordering guarantees for the results.
     *     for (Future<Void> future : futures) {
     *         future.get(); // Throws if the processing for this item failed.
     *     }
     * }</pre>
     *
     * @param other {@link Completable}s to merge.
     * @return {@link Completable} that terminates successfully when this and all {@code other} {@link Completable}s
     * complete or terminates with an error when any one terminates with an error.
     */
    public final Completable merge(Iterable<? extends Completable> other) {
        return new IterableMergeCompletable(false, this, other, executor);
    }

    /**
     * Merges the passed {@link Publisher} with this {@link Completable}.
     * <p>
     * The resulting {@link Publisher} emits all items emitted by the passed {@link Publisher} and terminates
     * successfully when both this {@link Completable} and the passed {@link Publisher} terminates successfully.
     * It terminates with an error when any one of this {@link Completable} or passed {@link Publisher} terminates with
     * an error.
     * <pre>{@code
     *     ExecutorService e = ...;
     *     Future<?> future1 = e.submit(() -> resultOfThisCompletable()));
     *     Future<?> future2 = e.submit(() -> resultOfMergeWithStream());
     *     // This is an approximation, this operator does not provide any ordering guarantees for the results.
     *     future1.get(); // Throws if this Completable failed.
     *     future2.get(); // Throws if mergeWith Publisher failed.
     * }</pre>
     *
     * @param mergeWith the {@link Publisher} to merge in
     * @param <T> The value type of the resulting {@link Publisher}.
     * @return {@link Publisher} that emits all items emitted by the passed {@link Publisher} and terminates
     * successfully when both this {@link Completable} and the passed {@link Publisher} terminates successfully.
     * It terminates with an error when any one of this {@link Completable} or passed {@link Publisher} terminates with
     * an error.
     *
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX merge operator.</a>
     */
    public final <T> Publisher<T> merge(Publisher<T> mergeWith) {
        return new CompletableMergeWithPublisher<>(this, mergeWith, executor);
    }

    /**
     * Merges this {@link Completable} with the {@code other} {@link Completable}s, and delays error notification until
     * all involved {@link Completable}s terminate.
     * <p>
     * Use {@link #merge(Completable...)} if any error should immediately terminate the returned {@link Completable}.
     * <p>
     * This method provides a means to merge multiple asynchronous sources, delays throwing in the presence of any
     * errors, and in sequential programming is similar to:
     * <pre>{@code
     *     ExecutorService e = ...;
     *     List<Future<Void>> futures = ...;
     *     futures.add(e.submit(() -> resultOfThisCompletable()));
     *     for (Completable c : other) {
     *         futures.add(e.submit(() -> resultOfCompletable(c));
     *     }
     *     Throwable overallCause = null;
     *     // This is an approximation, this operator does not provide any ordering guarantees for the results.
     *     for (Future<Void> future : futures) {
     *         try {
     *             f.get();
     *         } catch (Throwable cause) {
     *             if (overallCause != null) {
     *                 overallCause = cause;
     *             }
     *         }
     *     }
     *     if (overallCause != null) {
     *         throw overallCause;
     *     }
     * }</pre>
     *
     * @param other {@link Completable}s to merge.
     * @return {@link Completable} that terminates after {@code this} {@link Completable} and all {@code other}
     * {@link Completable}s. If all involved {@link Completable}s terminate successfully then the return value will
     * terminate successfully. If any {@link Completable} terminates in an error, then the return value will also
     * terminate in an error.
     */
    public final Completable mergeDelayError(Completable... other) {
        return new MergeCompletable(true, this, executor, other);
    }

    /**
     * Merges this {@link Completable} with the {@code other} {@link Completable}s, and delays error notification until
     * all involved {@link Completable}s terminate.
     * <p>
     * Use {@link #merge(Iterable)} if any error should immediately terminate the returned {@link Completable}.
     * <p>
     * This method provides a means to merge multiple asynchronous sources, delays throwing in the presence of any
     * errors, and in sequential programming is similar to:
     * <pre>{@code
     *     ExecutorService e = ...;
     *     List<Future<Void>> futures = ...;
     *     futures.add(e.submit(() -> resultOfThisCompletable()));
     *     for (Completable c : other) {
     *         futures.add(e.submit(() -> resultOfCompletable(c));
     *     }
     *     Throwable overallCause = null;
     *     // This is an approximation, this operator does not provide any ordering guarantees for the results.
     *     for (Future<Void> future : futures) {
     *         try {
     *             f.get();
     *         } catch (Throwable cause) {
     *             if (overallCause != null) {
     *                 overallCause = cause;
     *             }
     *         }
     *     }
     *     if (overallCause != null) {
     *         throw overallCause;
     *     }
     * }</pre>
     *
     * @param other {@link Completable}s to merge.
     * @return {@link Completable} that terminates after {@code this} {@link Completable} and all {@code other}
     * {@link Completable}s. If all involved {@link Completable}s terminate successfully then the return value will
     * terminate successfully. If any {@link Completable} terminates in an error, then the return value will also
     * terminate in an error.
     */
    public final Completable mergeDelayError(Iterable<? extends Completable> other) {
        return new IterableMergeCompletable(true, this, other, executor);
    }

    /**
     * Re-subscribes to this {@link Completable} if an error is emitted and the passed {@link BiIntPredicate} returns
     * {@code true}.
     * <p>
     * This method provides a means to retry an operation under certain failure conditions and in sequential programming
     * is similar to:
     * <pre>{@code
     *     public T execute() {
     *         return execute(0);
     *     }
     *
     *     private T execute(int attempts) {
     *         try {
     *             resultOfThisCompletable();
     *         } catch (Throwable cause) {
     *             if (shouldRetry.apply(attempts + 1, cause)) {
     *                 return execute(attempts + 1);
     *             } else {
     *                 throw cause;
     *             }
     *         }
     *     }
     * }</pre>
     *
     * @param shouldRetry {@link BiIntPredicate} that given the retry count and the most recent {@link Throwable}
     * emitted from this
     * {@link Completable} determines if the operation should be retried.
     * @return A {@link Completable} that completes with this {@link Completable} or re-subscribes if an error is
     * emitted and if the passed {@link BiPredicate} returned {@code true}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/retry.html">ReactiveX retry operator.</a>
     */
    public final Completable retry(BiIntPredicate<Throwable> shouldRetry) {
        return toSingle().retry(shouldRetry).ignoreResult();
    }

    /**
     * Re-subscribes to this {@link Completable} if an error is emitted and the {@link Completable} returned by the
     * supplied {@link BiIntFunction} completes successfully. If the returned {@link Completable} emits an error, the
     * returned {@link Completable} terminates with that error.
     * <p>
     * This method provides a means to retry an operation under certain failure conditions in an asynchronous fashion
     * and in sequential programming is similar to:
     * <pre>{@code
     *     public T execute() {
     *         return execute(0);
     *     }
     *
     *     private T execute(int attempts) {
     *         try {
     *             resultOfThisCompletable();
     *         } catch (Throwable cause) {
     *             try {
     *                 shouldRetry.apply(attempts + 1, cause); // Either throws or completes normally
     *                 execute(attempts + 1);
     *             } catch (Throwable ignored) {
     *                 throw cause;
     *             }
     *         }
     *     }
     * }</pre>
     *
     * @param retryWhen {@link BiIntFunction} that given the retry count and the most recent {@link Throwable} emitted
     * from this {@link Completable} returns a {@link Completable}. If this {@link Completable} emits an error, that
     * error is emitted from the returned {@link Completable}, otherwise, original {@link Completable} is re-subscribed
     * when this {@link Completable} completes.
     *
     * @return A {@link Completable} that completes with this {@link Completable} or re-subscribes if an error is
     * emitted and {@link Completable} returned by {@link BiFunction} completes successfully.
     *
     * @see <a href="http://reactivex.io/documentation/operators/retry.html">ReactiveX retry operator.</a>
     */
    public final Completable retryWhen(BiIntFunction<Throwable, Completable> retryWhen) {
        return toSingle().retryWhen(retryWhen).ignoreResult();
    }

    /**
     * Re-subscribes to this {@link Completable} when it completes and the passed {@link IntPredicate} returns
     * {@code true}.
     * <p>
     * This method provides a means to repeat an operation multiple times and in sequential programming is similar to:
     * <pre>{@code
     *     int i = 0;
     *     do {
     *         resultOfThisCompletable();
     *     } while (shouldRepeat.test(++i));
     * }</pre>
     *
     * @param shouldRepeat {@link IntPredicate} that given the repeat count determines if the operation should be
     * repeated.
     * @return A {@link Completable} that completes after all re-subscriptions completes.
     *
     * @see <a href="http://reactivex.io/documentation/operators/repeat.html">ReactiveX repeat operator.</a>
     */
    public final Completable repeat(IntPredicate shouldRepeat) {
        return toPublisher().repeat(shouldRepeat).ignoreElements();
    }

    /**
     * Re-subscribes to this {@link Completable} when it completes and the {@link Completable} returned by the supplied
     * {@link IntFunction} completes successfully. If the returned {@link Completable} emits an error, the returned
     * {@link Completable} emits an error.
     * <p>
     * This method provides a means to repeat an operation multiple times when in an asynchronous fashion and in
     * sequential programming is similar to:
     * <pre>{@code
     *     int i = 0;
     *     while (true) {
     *         resultOfThisCompletable();
     *         try {
     *             repeatWhen.apply(++i); // Either throws or completes normally
     *         } catch (Throwable cause) {
     *             break;
     *         }
     *     }
     * }</pre>
     *
     * @param repeatWhen {@link IntFunction} that given the repeat count returns a {@link Completable}.
     * If this {@link Completable} emits an error repeat is terminated, otherwise, original {@link Completable} is
     * re-subscribed when this {@link Completable} completes.
     *
     * @return A {@link Completable} that completes after all re-subscriptions completes.
     *
     * @see <a href="http://reactivex.io/documentation/operators/retry.html">ReactiveX retry operator.</a>
     */
    public final Completable repeatWhen(IntFunction<Completable> repeatWhen) {
        return toPublisher().repeatWhen(repeatWhen).ignoreElements();
    }

    /**
     * Invokes the {@code onSubscribe} {@link Consumer} argument <strong>before</strong>
     * {@link Subscriber#onSubscribe(Cancellable)} is called for {@link Subscriber}s of the returned
     * {@link Completable}.
     *
     * @param onSubscribe Invoked <strong>before</strong> {@link Subscriber#onSubscribe(Cancellable)} is called for
     * {@link Subscriber}s of the returned {@link Completable}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Completable}.
     */
    public final Completable doBeforeSubscribe(Consumer<Cancellable> onSubscribe) {
        return doBeforeSubscriber(doOnSubscribeSupplier(onSubscribe));
    }

    /**
     * Invokes the {@code onComplete} {@link Runnable} argument <strong>before</strong> {@link Subscriber#onComplete()}
     * is called for {@link Subscriber}s of the returned {@link Completable}.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *  resultOfThisCompletable();
     *  onComplete.run();
     *  nextOperation();
     * }</pre>
     *
     * @param onComplete Invoked <strong>before</strong> {@link Subscriber#onComplete()} is called for
     * {@link Subscriber}s of the returned {@link Completable}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Completable}.
     */
    public final Completable doBeforeComplete(Runnable onComplete) {
        return doBeforeSubscriber(doOnCompleteSupplier(onComplete));
    }

    /**
     * Invokes the {@code onError} {@link Consumer} argument <strong>before</strong>
     * {@link Subscriber#onError(Throwable)} is called for {@link Subscriber}s of the returned {@link Completable}.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *  try {
     *    resultOfThisCompletable();
     *  } catch (Throwable cause) {
     *      onError.accept(cause);
     *      nextOperation(cause);
     *  }
     * }</pre>
     *
     * @param onError Invoked <strong>before</strong> {@link Subscriber#onError(Throwable)} is called for
     * {@link Subscriber}s of the returned {@link Completable}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Completable}.
     */
    public final Completable doBeforeError(Consumer<Throwable> onError) {
        return doBeforeSubscriber(doOnErrorSupplier(onError));
    }

    /**
     * Invokes the {@code onCancel} {@link Runnable} argument <strong>before</strong> {@link Cancellable#cancel()} is
     * called for Subscriptions of the returned {@link Completable}.
     *
     * @param onCancel Invoked <strong>before</strong> {@link Cancellable#cancel()} is called for Subscriptions of the
     * returned {@link Completable}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Completable}.
     */
    public final Completable doBeforeCancel(Runnable onCancel) {
        return new DoCancellableCompletable(this, onCancel::run, true, executor);
    }

    /**
     * Invokes the {@code doFinally} {@link Runnable} argument <strong>before</strong> any of the following terminal
     * methods are called:
     * <ul>
     *     <li>{@link Subscriber#onComplete()}</li>
     *     <li>{@link Subscriber#onError(Throwable)}</li>
     *     <li>{@link Cancellable#cancel()}</li>
     * </ul>
     * for Subscriptions/{@link Subscriber}s of the returned {@link Completable}.
     * <pre>{@code
     *  try {
     *      resultOfThisCompletable();
     *  } finally {
     *      doFinally.run();
     *      nextOperation(); // Maybe notifying of cancellation, or termination
     *  }
     * }</pre>
     *
     * @param doFinally Invoked <strong>before</strong> any of the following terminal methods are called:
     * <ul>
     *     <li>{@link Subscriber#onComplete()}</li>
     *     <li>{@link Subscriber#onError(Throwable)}</li>
     *     <li>{@link Cancellable#cancel()}</li>
     * </ul>
     * for Subscriptions/{@link Subscriber}s of the returned {@link Completable}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Completable}.
     */
    public final Completable doBeforeFinally(Runnable doFinally) {
        return new DoBeforeFinallyCompletable(this, doFinally, executor);
    }

    /**
     * Creates a new {@link Subscriber} (via the {@code subscriberSupplier} argument) on each call to
     * {@link #subscribe(Subscriber)} and invokes all the {@link Subscriber} methods <strong>before</strong> the
     * {@link Subscriber}s of the returned {@link Completable}.
     *
     * @param subscriberSupplier Creates a new {@link Subscriber} on each call to {@link #subscribe(Subscriber)} and
     * invokes all the {@link Subscriber} methods <strong>before</strong> the {@link Subscriber}s of the returned
     * {@link Completable}. {@link Subscriber} methods <strong>MUST NOT</strong> throw.
     * @return The new {@link Completable}.
     */
    public final Completable doBeforeSubscriber(Supplier<Subscriber> subscriberSupplier) {
        return new DoBeforeSubscriberCompletable(this, subscriberSupplier, executor);
    }

    /**
     * Invokes the {@code onSubscribe} {@link Consumer} argument <strong>after</strong>
     * {@link Subscriber#onSubscribe(Cancellable)} is called for {@link Subscriber}s of the returned
     * {@link Completable}.
     *
     * @param onSubscribe Invoked <strong>after</strong> {@link Subscriber#onSubscribe(Cancellable)} is called for
     * {@link Subscriber}s of the returned {@link Completable}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Completable}.
     */
    public final Completable doAfterSubscribe(Consumer<Cancellable> onSubscribe) {
        return doAfterSubscriber(doOnSubscribeSupplier(onSubscribe));
    }

    /**
     * Invokes the {@code onComplete} {@link Runnable} argument <strong>after</strong> {@link Subscriber#onComplete()}
     * is called for {@link Subscriber}s of the returned {@link Completable}.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *  resultOfThisCompletable();
     *  nextOperation();
     *  onComplete.run();
     * }</pre>
     *
     * @param onComplete Invoked <strong>after</strong> {@link Subscriber#onComplete()} is called for
     * {@link Subscriber}s of the returned {@link Completable}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Completable}.
     */
    public final Completable doAfterComplete(Runnable onComplete) {
        return doAfterSubscriber(doOnCompleteSupplier(onComplete));
    }

    /**
     * Invokes the {@code onError} {@link Consumer} argument <strong>after</strong>
     * {@link Subscriber#onError(Throwable)} is called for {@link Subscriber}s of the returned {@link Completable}.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *  try {
     *    resultOfThisCompletable();
     *  } catch (Throwable cause) {
     *      nextOperation(cause);
     *      onError.accept(cause);
     *  }
     * }</pre>
     *
     * @param onError Invoked <strong>after</strong> {@link Subscriber#onError(Throwable)} is called for
     * {@link Subscriber}s of the returned {@link Completable}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Completable}.
     */
    public final Completable doAfterError(Consumer<Throwable> onError) {
        return doAfterSubscriber(doOnErrorSupplier(onError));
    }

    /**
     * Invokes the {@code onCancel} {@link Runnable} argument <strong>after</strong> {@link Cancellable#cancel()} is
     * called for Subscriptions of the returned {@link Completable}.
     *
     * @param onCancel Invoked <strong>after</strong> {@link Cancellable#cancel()} is called for Subscriptions of the
     * returned {@link Completable}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Completable}.
     */
    public final Completable doAfterCancel(Runnable onCancel) {
        return new DoCancellableCompletable(this, onCancel::run, false, executor);
    }

    /**
     * Invokes the {@code doFinally} {@link Runnable} argument <strong>after</strong> any of the following terminal
     * methods are called:
     * <ul>
     *     <li>{@link Subscriber#onComplete()}</li>
     *     <li>{@link Subscriber#onError(Throwable)}</li>
     *     <li>{@link Cancellable#cancel()}</li>
     * </ul>
     * for Subscriptions/{@link Subscriber}s of the returned {@link Completable}.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *  try {
     *      resultOfThisCompletable();
     *  } finally {
     *      nextOperation(); // Maybe notifying of cancellation, or termination
     *      doFinally.run();
     *  }
     * }</pre>
     *
     * @param doFinally Invoked <strong>after</strong> any of the following terminal methods are called:
     * <ul>
     *     <li>{@link Subscriber#onComplete()}</li>
     *     <li>{@link Subscriber#onError(Throwable)}</li>
     *     <li>{@link Cancellable#cancel()}</li>
     * </ul>
     * for Subscriptions/{@link Subscriber}s of the returned {@link Completable}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Completable}.
     */
    public final Completable doAfterFinally(Runnable doFinally) {
        return new DoAfterFinallyCompletable(this, doFinally, executor);
    }

    /**
     * Creates a new {@link Subscriber} (via the {@code subscriberSupplier} argument) on each call to
     * {@link #subscribe(Subscriber)} and invokes all the {@link Subscriber} methods <strong>after</strong> the
     * {@link Subscriber}s of the returned {@link Completable}.
     *
     * @param subscriberSupplier Creates a new {@link Subscriber} on each call to {@link #subscribe(Subscriber)} and
     * invokes all the {@link Subscriber} methods <strong>after</strong> the {@link Subscriber}s of the returned
     * {@link Completable}. {@link Subscriber} methods <strong>MUST NOT</strong> throw.
     * @return The new {@link Completable}.
     */
    public final Completable doAfterSubscriber(Supplier<Subscriber> subscriberSupplier) {
        return new DoAfterSubscriberCompletable(this, subscriberSupplier, executor);
    }

    /**
     * <strong>This method requires advanced knowledge of building operators. Before using this method please attempt
     * to compose existing operator(s) to satisfy your use case.</strong>
     * <p>
     * Returns a {@link Completable} that when {@link Completable#subscribe(Subscriber)} is called the {@code operator}
     * argument will be used to wrap the {@link Subscriber} before subscribing to this {@link Completable}.
     * <pre>{@code
     *     Completable<X> pub = ...;
     *     pub.map(..) // A
     *        .liftSynchronous(original -> modified)
     *        .doAfterFinally(..) // B
     * }</pre>
     * The {@code original -> modified} "operator" <strong>MUST</strong> be "synchronous" in that it does not interact
     * with the original {@link Subscriber} from outside the modified {@link Subscriber} or {@link Cancellable}
     * threads. That is to say this operator will not impact the {@link Executor} constraints already in place between
     * <i>A</i> and <i>B</i> above. If you need asynchronous behavior, or are unsure, see
     * {@link #liftAsynchronous(CompletableOperator)}.
     *
     * @param operator The custom operator logic. The input is the "original" {@link Subscriber} to this
     * {@link Completable} and the return is the "modified" {@link Subscriber} that provides custom operator business
     * logic.
     * @return a {@link Completable} that when {@link Completable#subscribe(Subscriber)} is called the {@code operator}
     * argument will be used to wrap the {@link Subscriber} before subscribing to this {@link Completable}.
     * @see #liftAsynchronous(CompletableOperator)
     */
    public final Completable liftSynchronous(CompletableOperator operator) {
        return new LiftSynchronousCompletableOperator(this, operator, executor);
    }

    /**
     * <strong>This method requires advanced knowledge of building operators. Before using this method please attempt
     * to compose existing operator(s) to satisfy your use case.</strong>
     * <p>
     * Returns a {@link Completable} that when {@link Completable#subscribe(Subscriber)} is called the {@code operator}
     * argument will be used to wrap the {@link Subscriber} before subscribing to this {@link Completable}.
     * <pre>{@code
     *     Publisher<X> pub = ...;
     *     pub.map(..) // A
     *        .liftAsynchronous(original -> modified)
     *        .doAfterFinally(..) // B
     * }</pre>
     *
     * The {@code original -> modified} "operator" MAY be "asynchronous" in that it may interact with the original
     * {@link Subscriber} from outside the modified {@link Subscriber} or {@link Cancellable} threads. More
     * specifically:
     * <ul>
     *  <li>all of the {@link Subscriber} invocations going "downstream" (i.e. from <i>A</i> to <i>B</i> above) MAY be
     *  offloaded via an {@link Executor}</li>
     *  <li>all of the {@link Cancellable} invocations going "upstream" (i.e. from <i>B</i> to <i>A</i> above) MAY be
     *  offloaded via an {@link Executor}</li>
     * </ul>
     * This behavior exists to prevent blocking code negatively impacting the thread that powers the upstream source of
     * data (e.g. an EventLoop).
     * @param operator The custom operator logic. The input is the "original" {@link Subscriber} to this
     * {@link Completable} and the return is the "modified" {@link Subscriber} that provides custom operator business
     * logic.
     * @return a {@link Completable} that when {@link Completable#subscribe(Subscriber)} is called the {@code operator}
     * argument will be used to wrap the {@link Subscriber} before subscribing to this {@link Completable}.
     * @see #liftSynchronous(CompletableOperator)
     */
    public final Completable liftAsynchronous(CompletableOperator operator) {
        return new LiftAsynchronousCompletableOperator(this, operator, executor);
    }

    /**
     * Creates a new {@link Completable} that will use the passed {@link Executor} to invoke all {@link Subscriber}
     * methods.
     * This method does <strong>not</strong> override preceding {@link Executor}s, if any, specified for {@code this}
     * {@link Completable}. Only subsequent operations, if any, added in this execution chain will use this
     * {@link Executor}. If such an override is required, {@link #publishOnOverride(Executor)} can be used.
     *
     * @param executor {@link Executor} to use.
     * @return A new {@link Completable} that will use the passed {@link Executor} to invoke all methods on the
     * {@link Subscriber}.
     */
    public final Completable publishOn(Executor executor) {
        return PublishAndSubscribeOnCompletables.publishOn(this, executor);
    }

    /**
     * Creates a new {@link Completable} that will use the passed {@link Executor} to invoke all {@link Subscriber}
     * methods.
     * This method overrides preceding {@link Executor}s, if any, specified for {@code this} {@link Completable}.
     * That is to say preceding and subsequent operations for this execution chain will use this {@link Executor}.
     * If such an override is not required, {@link #publishOn(Executor)} can be used.
     *
     * @param executor {@link Executor} to use.
     * @return A new {@link Completable} that will use the passed {@link Executor} to invoke all methods of
     * {@link Subscriber}, {@link Cancellable} and {@link #handleSubscribe(Completable.Subscriber)} both for the
     * returned {@link Completable} as well as {@code this} {@link Completable}.
     */
    public final Completable publishOnOverride(Executor executor) {
        return PublishAndSubscribeOnCompletables.publishOnOverride(this, executor);
    }

    /**
     * Creates a new {@link Completable} that will use the passed {@link Executor} to invoke the following methods:
     * <ul>
     *     <li>All {@link Cancellable} methods.</li>
     *     <li>The {@link #handleSubscribe(Completable.Subscriber)} method.</li>
     * </ul>
     * This method does <strong>not</strong> override preceding {@link Executor}s, if any, specified for {@code this}
     * {@link Completable}. Only subsequent operations, if any, added in this execution chain will use this
     * {@link Executor}. If such an override is required, {@link #subscribeOnOverride(Executor)} can be used.
     *
     * @param executor {@link Executor} to use.
     * @return A new {@link Completable} that will use the passed {@link Executor} to invoke all methods of
     * {@link Cancellable} and {@link #handleSubscribe(Completable.Subscriber)}.
     */
    public final Completable subscribeOn(Executor executor) {
        return PublishAndSubscribeOnCompletables.subscribeOn(this, executor);
    }

    /**
     * Creates a new {@link Completable} that will use the passed {@link Executor} to invoke the following methods:
     * <ul>
     *     <li>All {@link Cancellable} methods.</li>
     *     <li>The {@link #handleSubscribe(Completable.Subscriber)} method.</li>
     * </ul>
     * This method overrides preceding {@link Executor}s, if any, specified for {@code this} {@link Completable}.
     * That is to say preceding and subsequent operations for this execution chain will use this {@link Executor}.
     * If such an override is not required, {@link #subscribeOn(Executor)} can be used.
     *
     * @param executor {@link Executor} to use.
     * @return A new {@link Completable} that will use the passed {@link Executor} to invoke all methods of
     * {@link Cancellable} and {@link #handleSubscribe(Completable.Subscriber)} both for the returned
     * {@link Completable} as well as {@code this} {@link Completable}.
     */
    public final Completable subscribeOnOverride(Executor executor) {
        return PublishAndSubscribeOnCompletables.subscribeOnOverride(this, executor);
    }

    /**
     * Creates a new {@link Completable} that will use the passed {@link Executor} to invoke the following methods:
     * <ul>
     *     <li>All {@link Subscriber} methods.</li>
     *     <li>All {@link Cancellable} methods.</li>
     *     <li>The {@link #handleSubscribe(Completable.Subscriber)} method.</li>
     * </ul>
     * This method does <strong>not</strong> override preceding {@link Executor}s, if any, specified for {@code this}
     * {@link Completable}. Only subsequent operations, if any, added in this execution chain will use this
     * {@link Executor}. If such an override is required, {@link #publishAndSubscribeOnOverride(Executor)} can be used.
     *
     * @param executor {@link Executor} to use.
     * @return A new {@link Completable} that will use the passed {@link Executor} to invoke all methods
     * {@link Subscriber}, {@link Cancellable} and {@link #handleSubscribe(Completable.Subscriber)}.
     */
    public final Completable publishAndSubscribeOn(Executor executor) {
        return PublishAndSubscribeOnCompletables.publishAndSubscribeOn(this, executor);
    }

    /**
     * Creates a new {@link Completable} that will use the passed {@link Executor} to invoke the following methods:
     * <ul>
     *     <li>All {@link Subscriber} methods.</li>
     *     <li>All {@link Cancellable} methods.</li>
     *     <li>The {@link #handleSubscribe(Completable.Subscriber)} method.</li>
     * </ul>
     * This method overrides preceding {@link Executor}s, if any, specified for {@code this} {@link Completable}.
     * That is to say preceding and subsequent operations for this execution chain will use this {@link Executor}.
     * If such an override is not required, {@link #publishAndSubscribeOn(Executor)} can be used.
     *
     * @param executor {@link Executor} to use.
     * @return A new {@link Completable} that will use the passed {@link Executor} to invoke all methods of
     * {@link Subscriber}, {@link Cancellable} and {@link #handleSubscribe(Completable.Subscriber)} both for the
     * returned {@link Completable} as well as {@code this} {@link Completable}.
     */
    public final Completable publishAndSubscribeOnOverride(Executor executor) {
        return PublishAndSubscribeOnCompletables.publishAndSubscribeOnOverride(this, executor);
    }

    //
    // Operators End
    //

    //
    // Conversion Operators Begin
    //

    /**
     * Converts this {@code Completable} to a {@link Publisher}.
     * <p>
     * No {@link org.reactivestreams.Subscriber#onNext(Object)} signals will be delivered to the returned
     * {@link Publisher}. Only terminal signals will be delivered. If you need more control you should consider using
     * {@link #concatWith(Publisher)}.
     * @param <T> The value type of the resulting {@link Publisher}.
     * @return A {@link Publisher} that mirrors the terminal signal from this {@link Completable}.
     */
    public final <T> Publisher<T> toPublisher() {
        return new CompletableToPublisher<>(this, executor);
    }

    /**
     * Converts this {@code Completable} to a {@link Single}.
     * <p>
     * The return value's {@link Single.Subscriber#onSuccess(Object)} value is undefined. If you need a specific value
     * you can also use {@link #concatWith(Single)} with a {@link Single#success(Object)}.
     * @param <T> The value type of the resulting {@link Single}.
     * @return A {@link Single} that mirrors the terminal signal from this {@link Completable}.
     */
    public final <T> Single<T> toSingle() {
        return new CompletableToSingle<>(this, executor);
    }

    /**
     * Converts this {@code Completable} to a {@link CompletionStage}.
     * <p>
     * The {@link CompletionStage}'s value is undefined.
     * @param <T> The value type of the resulting {@link Single}.
     * @return A {@link CompletionStage} that mirrors the terminal signal from this {@link Completable}.
     */
    public final <T> CompletionStage<T> toCompletionStage() {
        return this.<T>toSingle().toCompletionStage();
    }

    /**
     * Converts this {@code Completable} to a {@link Future}.
     * <p>
     * The {@link Future}'s value is undefined.
     * @param <T> The value type of the resulting {@link Single}.
     * @return A {@link Future} that mirrors the terminal signal from this {@link Completable}.
     */
    public final <T> Future<T> toFuture() {
        return this.<T>toSingle().toFuture();
    }

    //
    // Conversion Operators End
    //

    @Override
    public final void subscribe(Subscriber subscriber) {
        // Each Subscriber chain should have an isolated AsyncContext due to a new asynchronous scope starting. This is
        // why we copy the AsyncContext upon external user facing subscribe operations.
        AsyncContextProvider provider = AsyncContext.provider();
        subscribeWithContext(subscriber, provider.contextMap().copy(), provider);
    }

    /**
     * Subscribe to this {@link Completable} and log any {@link Subscriber#onError(Throwable)}.
     *
     * @return {@link Cancellable} used to invoke {@link Cancellable#cancel()} on the parameter of
     * {@link Subscriber#onSubscribe(Cancellable)} for this {@link Completable}.
     */
    public final Cancellable subscribe() {
        SimpleCompletableSubscriber subscriber = new SimpleCompletableSubscriber();
        subscribe(subscriber);
        return subscriber;
    }

    /**
     * Handles a subscriber to this {@code Completable}.
     * <p>
     * This method is invoked internally by {@link Completable} for every call to the
     * {@link Completable#subscribe(Subscriber)} method.
     *
     * @param subscriber the subscriber.
     */
    protected abstract void handleSubscribe(Subscriber subscriber);

    //
    // Static Utility Methods Begin
    //

    /**
     * Creates a realized completed {@code Completable}.
     *
     * @return A new {@code Completable}.
     */
    public static Completable completed() {
        return CompletedCompletable.INSTANCE;
    }

    /**
     * Creates a realized failed {@code Completable}.
     *
     * @param cause error that the returned {@code Completable} completes with.
     * @return A new {@code Completable}.
     */
    public static Completable error(Throwable cause) {
        return new FailedCompletable(requireNonNull(cause));
    }

    /**
     * Creates a {@link Completable} that never terminates.
     *
     * @return A new {@code Completable}.
     */
    public static Completable never() {
        return NeverCompletable.INSTANCE;
    }

    /**
     * Defer creation of a {@link Completable} till it is subscribed to.
     *
     * @param completableSupplier {@link Supplier} to create a new {@link Completable} for every call to
     * {@link #subscribe(Subscriber)} to the returned {@link Completable}.
     * @return A new {@link Completable} that creates a new {@link Completable} using {@code completableFactory}
     * for every call to {@link #subscribe(Subscriber)} and forwards
     * the termination signal from the newly created {@link Completable} to its {@link Subscriber}.
     */
    public static Completable defer(Supplier<Completable> completableSupplier) {
        return new CompletableDefer(completableSupplier, false);
    }

    /**
     * Defer creation of a {@link Completable} till it is subscribed to. The {@link Completable}s returned from
     * {@code singleSupplier} will share the same {@link AsyncContextMap} as the {@link Completable} return from this
     * method. A typical use case for this is if the {@link Completable}s returned from {@code singleSupplier} only
     * modify existing behavior with additional state, but not if the {@link Completable}s may come from an unrelated
     * independent source.
     * @param completableSupplier {@link Supplier} to create a new {@link Completable} for every call to
     * {@link #subscribe(Subscriber)} to the returned {@link Completable}.
     * @return A new {@link Completable} that creates a new {@link Completable} using {@code completableFactory}
     * for every call to {@link #subscribe(Subscriber)} and forwards
     * the termination signal from the newly created {@link Completable} to its {@link Subscriber}.
     */
    public static Completable deferShareContext(Supplier<Completable> completableSupplier) {
        return new CompletableDefer(completableSupplier, true);
    }

    /**
     * Convert from a {@link Future} to a {@link Completable} via {@link Future#get()}.
     * <p>
     * Note that because {@link Future} only presents blocking APIs to extract the result, so the process of getting the
     * results will block. The caller of {@link #subscribe(Subscriber)} is responsible for offloading if necessary, and
     * also offloading if {@link Cancellable#cancel()} will be called if this operation may block.
     * <p>
     * To apply a timeout see {@link #timeout(long, TimeUnit)} and related methods.
     * @param future The {@link Future} to convert.
     * @return A {@link Completable} that derives results from {@link Future}.
     * @see #timeout(long, TimeUnit)
     */
    public static Completable fromFuture(Future<?> future) {
        return Single.fromFuture(future).toCompletable();
    }

    /**
     * Convert from a {@link CompletionStage} to a {@link Completable}.
     * <p>
     * A best effort is made to propagate {@link Cancellable#cancel()} to the {@link CompletionStage}. Cancellation for
     * {@link CompletionStage} implementations will result in exceptional completion and invoke user
     * callbacks. If there is any blocking code involved in the cancellation process (including invoking user callbacks)
     * you should investigate if using an {@link Executor} is appropriate.
     * @param stage The {@link CompletionStage} to convert.
     * @return A {@link Completable} that derives results from {@link CompletionStage}.
     */
    public static Completable fromStage(CompletionStage<?> stage) {
        return Single.fromStage(stage).toCompletable();
    }

    /**
     * Returns a {@link Completable} that terminates when all the passed {@link Completable} terminate.
     * <p>
     * This will actively subscribe to a default number of {@link Completable}s concurrently, in order to alter the
     * defaults, {@link #collect(Iterable, int)}.
     * <p>
     * If any of the {@link Completable}s terminate with an error, returned {@link Completable} will immediately
     * terminate with that error. In such a case, any in-progress {@link Completable}s will be cancelled. In order to
     * delay error termination use {@link #collectDelayError(Iterable)}.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *      for (Future<Void> ft: futures) { // Provided Futures (analogous to the Completables here)
     *          // This is an approximation, this operator does not provide any ordering guarantees for the results.
     *          ft.get();
     *      }
     * }</pre>
     *
     * @param completables {@link Iterable} of {@link Completable}s, results of which are to be collected.
     * @return A new {@link Completable} that terminates successfully if all the provided {@link Completable}s have
     * terminated successfully or any one of them has terminated with a failure.
     */
    public static Completable collect(Iterable<Completable> completables) {
        return Publisher.from(completables).flatMapCompletable(identity());
    }

    /**
     * Returns a {@link Completable} that terminates when all the passed {@link Completable} terminate.
     * <p>
     * This will actively subscribe to a default number of {@link Completable}s concurrently, in order to alter the
     * defaults, {@link #collect(int, Completable...)} should be used.
     * <p>
     * If any of the {@link Completable}s terminate with an error, returned {@link Completable} will immediately
     * terminate with that error. In such a case, any in-progress {@link Completable}s will be cancelled.
     *  In order to delay error termination use {@link #collectDelayError(Completable...)}.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *      for (Future<Void> ft: futures) { // Provided Futures (analogous to the Completables here)
     *          // This is an approximation, this operator does not provide any ordering guarantees for the results.
     *          ft.get();
     *      }
     * }</pre>
     *
     * @param completables {@link Completable}s, results of which are to be collected.
     * @return A new {@link Completable} that terminates successfully if all the provided {@link Completable}s have
     * terminated successfully or any one of them has terminated with a failure.
     */
    public static Completable collect(Completable... completables) {
        return Publisher.from(completables).flatMapCompletable(identity());
    }

    /**
     * Returns a {@link Completable} that terminates when all the passed {@link Completable} terminate.
     * <p>
     * If any of the {@link Completable}s terminate with an error, returned {@link Completable} will immediately
     * terminate with that error. In such a case, any in-progress {@link Completable}s will be cancelled. In order to
     * delay error termination use {@link #collectDelayError(Iterable, int)}.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *      for (Future<Void> ft: futures) { // Provided Futures (analogous to the Completables here)
     *          // This is an approximation, this operator does not provide any ordering guarantees for the results.
     *          ft.get();
     *      }
     * }</pre>
     *
     * @param completables {@link Iterable} of {@link Completable}s, results of which are to be collected.
     * @param maxConcurrency Maximum number of {@link Completable}s that will be active at any point in time.
     * @return A new {@link Completable} that terminates successfully if all the provided {@link Completable}s have
     * terminated successfully or any one of them has terminated with a failure.
     */
    public static Completable collect(Iterable<? extends Completable> completables, int maxConcurrency) {
        return Publisher.from(completables).flatMapCompletable(identity(), maxConcurrency);
    }

    /**
     * Returns a {@link Completable} that terminates when all the passed {@link Completable} terminate.
     * <p>
     * If any of the {@link Completable}s terminate with an error, returned {@link Completable} will immediately
     * terminate with that error. In such a case, any in-progress {@link Completable}s will be cancelled.
     *  In order to delay error termination use {@link #collectDelayError(int, Completable...)}.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *      for (Future<Void> ft: futures) { // Provided Futures (analogous to the Completables here)
     *          // This is an approximation, this operator does not provide any ordering guarantees for the results.
     *          ft.get();
     *      }
     * }</pre>
     *
     * @param maxConcurrency Maximum number of {@link Completable}s that will be active at any point in time.
     * @param completables {@link Completable}s, results of which are to be collected.
     * @return A new {@link Completable} that terminates successfully if all the provided {@link Completable}s have
     * terminated successfully or any one of them has terminated with a failure.
     */
    public static Completable collect(int maxConcurrency, Completable... completables) {
        return Publisher.from(completables).flatMapCompletable(identity(), maxConcurrency);
    }

    /**
     * Returns a {@link Completable} that terminates when all the passed {@link Completable} terminate.
     * <p>
     * This will actively subscribe to a default number of {@link Completable}s concurrently, in order to alter the
     * defaults, {@link #collectDelayError(Iterable, int)} should be used.
     * <p>
     * If any of the {@link Completable}s terminate with an error, returned {@link Completable} will wait for
     * termination till all the other {@link Completable}s have been subscribed and terminated. If it is expected for
     * the returned {@link Completable} to terminate on the first failing {@link Completable},
     * {@link #collect(Iterable)} should be used.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *      List<Throwable> errors = ...;  // assume this is thread safe
     *      for (Future<Void> ft: futures) { // Provided Futures (analogous to the Completables here)
     *          // This is an approximation, this operator does not provide any ordering guarantees for the results.
     *             try {
     *                 ft.get();
     *             } catch(Throwable t) {
     *                errors.add(t);
     *             }
     *      }
     *     if (errors.isEmpty()) {
     *         return;
     *     }
     *     createAndThrowACompositeException(errors);
     * }</pre>
     *
     * @param completables {@link Iterable} of {@link Completable}s, results of which are to be collected.
     * @return A new {@link Completable} that terminates successfully if all the provided {@link Completable}s have
     * terminated successfully or any one of them has terminated with a failure.
     */
    public static Completable collectDelayError(Iterable<? extends Completable> completables) {
        return Publisher.from(completables).flatMapCompletableDelayError(identity());
    }

    /**
     * Returns a {@link Completable} that terminates when all the passed {@link Completable} terminate.
     * <p>
     * This will actively subscribe to a limited number of {@link Single}s concurrently, in order to alter the defaults,
     * {@link #collect(int, Completable...)} should be used.
     * <p>
     * If any of the {@link Single}s terminate with an error, returned {@link Single} will wait for termination till all
     * the other {@link Single}s have been subscribed and terminated. If it is expected for the returned {@link Single}
     * to terminate on the first failing {@link Single}, {@link #collect(Completable...)} should be used.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *      List<Throwable> errors = ...;  // assume this is thread safe
     *      for (Future<Void> ft: futures) { // Provided Futures (analogous to the Completables here)
     *          // This is an approximation, this operator does not provide any ordering guarantees for the results.
     *             try {
     *                 ft.get();
     *             } catch(Throwable t) {
     *                errors.add(t);
     *             }
     *      }
     *     if (errors.isEmpty()) {
     *         return;
     *     }
     *     createAndThrowACompositeException(errors);
     * }</pre>
     *
     * @param completables {@link Completable}s, results of which are to be collected.
     * @return A new {@link Completable} that terminates successfully if all the provided {@link Completable}s have
     * terminated successfully or any one of them has terminated with a failure.
     */
    public static Completable collectDelayError(Completable... completables) {
        return Publisher.from(completables).flatMapCompletableDelayError(identity());
    }

    /**
     * Returns a {@link Completable} that terminates when all the passed {@link Completable} terminate.
     * <p>
     * If any of the {@link Single}s terminate with an error, returned {@link Single} will wait for termination till all
     * the other {@link Single}s have been subscribed and terminated. If it is expected for the returned {@link Single}
     * to terminate on the first failing {@link Single}, {@link #collect(Iterable, int)} should be used.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *      List<Throwable> errors = ...;  // assume this is thread safe
     *      for (Future<Void> ft: futures) { // Provided Futures (analogous to the Completables here)
     *          // This is an approximation, this operator does not provide any ordering guarantees for the results.
     *             try {
     *                 ft.get();
     *             } catch(Throwable t) {
     *                errors.add(t);
     *             }
     *      }
     *     if (errors.isEmpty()) {
     *         return;
     *     }
     *     createAndThrowACompositeException(errors);
     * }</pre>
     *
     * @param completables {@link Iterable} of {@link Completable}s, results of which are to be collected.
     * @param maxConcurrency Maximum number of {@link Completable}s that will be active at any point in time.
     * @return A new {@link Completable} that terminates successfully if all the provided {@link Completable}s have
     * terminated successfully or any one of them has terminated with a failure.
     */
    public static Completable collectDelayError(Iterable<? extends Completable> completables, int maxConcurrency) {
        return Publisher.from(completables).flatMapCompletableDelayError(identity(), maxConcurrency);
    }

    /**
     * Returns a {@link Completable} that terminates when all the passed {@link Completable} terminate.
     * <p>
     * If any of the {@link Single}s terminate with an error, returned {@link Single} will wait for termination till all
     * the other {@link Single}s have been subscribed and terminated. If it is expected for the returned {@link Single}
     * to terminate on the first failing {@link Single}, {@link #collect(Iterable, int)} should be used.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *      List<Throwable> errors = ...;  // assume this is thread safe
     *      for (Future<Void> ft: futures) { // Provided Futures (analogous to the Completables here)
     *          // This is an approximation, this operator does not provide any ordering guarantees for the results.
     *             try {
     *                 ft.get();
     *             } catch(Throwable t) {
     *                errors.add(t);
     *             }
     *      }
     *     if (errors.isEmpty()) {
     *         return;
     *     }
     *     createAndThrowACompositeException(errors);
     * }</pre>
     *
     * @param maxConcurrency Maximum number of {@link Completable}s that will be active at any point in time.
     * @param completables {@link Completable}s, results of which are to be collected.
     * @return A new {@link Completable} that terminates successfully if all the provided {@link Completable}s have
     * terminated successfully or any one of them has terminated with a failure.
     */
    public static Completable collectDelayError(int maxConcurrency, Completable... completables) {
        return Publisher.from(completables).flatMapCompletableDelayError(identity(), maxConcurrency);
    }

    //
    // Static Utility Methods End
    //

    //
    // Internal Methods Begin
    //

    /**
     * Replicating a call to {@link #subscribe(Subscriber)} but with a materialized {@link AsyncContextMap}.
     * @param subscriber the subscriber.
     * @param contextMap the {@link AsyncContextMap} to use for this {@link Subscriber}.
     * @param contextProvider the {@link AsyncContextProvider} used to wrap any objects to preserve
     * {@link AsyncContextMap}.
     */
    final void subscribeWithContext(Subscriber subscriber, AsyncContextMap contextMap,
                                    AsyncContextProvider contextProvider) {
        requireNonNull(subscriber);
        final SignalOffloader signalOffloader;
        final Subscriber offloadedSubscriber;
        try {
            // This is a user-driven subscribe i.e. there is no SignalOffloader override, so create a new SignalOffloader
            // to use.
            signalOffloader = newOffloaderFor(executor);
            // Since this is a user-driven subscribe (end of the execution chain), offload Cancellable
            offloadedSubscriber = signalOffloader.offloadCancellable(
                    contextProvider.wrapCancellable(subscriber, contextMap));
        } catch (Throwable t) {
            subscriber.onSubscribe(IGNORE_CANCEL);
            subscriber.onError(t);
            return;
        }
        subscribeWithOffloaderAndContext(offloadedSubscriber, signalOffloader, contextMap, contextProvider);
    }

    /**
     * Replicating a call to {@link #subscribe(Subscriber)} but with a materialized {@link SignalOffloader} and
     * {@link AsyncContextMap}.
     * @param subscriber the subscriber.
     * @param signalOffloader {@link SignalOffloader} to use for this {@link Subscriber}.
     * @param contextMap the {@link AsyncContextMap} to use for this {@link Subscriber}.
     * @param contextProvider the {@link AsyncContextProvider} used to wrap any objects to preserve
     * {@link AsyncContextMap}.
     */
    final void subscribeWithOffloaderAndContext(Subscriber subscriber, SignalOffloader signalOffloader,
                                                AsyncContextMap contextMap, AsyncContextProvider contextProvider) {
        // this method doesn't currently do anything other than providing a way to differentiate between internal
        // subscribe calls and handleSubscribe propagation.
        handleSubscribe(subscriber, signalOffloader, contextMap, contextProvider);
    }

    /**
     * Override for {@link #handleSubscribe(Subscriber)} to offload the {@link #handleSubscribe(Subscriber)} call to the
     * passed {@link SignalOffloader}.
     * <p>
     * This method wraps the passed {@link Subscriber} using {@link SignalOffloader#offloadSubscriber(Subscriber)} and
     * then calls {@link #handleSubscribe(Subscriber)} using
     * {@link SignalOffloader#offloadSubscribe(Subscriber, Consumer)}.
     * Operators that do not wish to wrap the passed {@link Subscriber} can override this method and omit the wrapping.
     *
     * @param subscriber the subscriber.
     * @param signalOffloader {@link SignalOffloader} to use for this {@link Subscriber}.
     * @param contextMap the {@link AsyncContextMap} to use for this {@link org.reactivestreams.Subscriber}.
     * @param contextProvider the {@link AsyncContextProvider} used to wrap any objects to preserve
     */
    void handleSubscribe(Subscriber subscriber, SignalOffloader signalOffloader, AsyncContextMap contextMap,
                         AsyncContextProvider contextProvider) {
        Subscriber offloaded = signalOffloader.offloadSubscriber(
                contextProvider.wrap(subscriber, contextMap));
        signalOffloader.offloadSubscribe(offloaded, contextProvider.wrap(this::safeHandleSubscribe, contextMap));
    }

    private void safeHandleSubscribe(Subscriber subscriber) {
        try {
            handleSubscribe(subscriber);
        } catch (Throwable t) {
            LOGGER.warn("Unexpected exception from subscribe(), assuming no interaction with the Subscriber.", t);
            // At this point we are unsure if any signal was sent to the Subscriber and if it is safe to invoke the
            // Subscriber without violating specifications. However, not propagating the error to the Subscriber will
            // result in hard to debug scenarios where no further signals may be sent to the Subscriber and hence it
            // will be hard to distinguish between a "hung" source and a wrongly implemented source that violates the
            // specifications and throw from subscribe() (Rule 1.9).
            //
            // By doing the following we may violate the rules:
            // 1) Rule 2.12: onSubscribe() MUST be called at most once.
            // 2) Rule 1.7: Once a terminal state has been signaled (onError, onComplete) it is REQUIRED that no
            // further signals occur.
            subscriber.onSubscribe(IGNORE_CANCEL);
            subscriber.onError(t);
        }
    }

    /**
     * Returns the {@link Executor} used for this {@link Completable}.
     *
     * @return {@link Executor} used for this {@link Completable} via {@link #Completable(Executor)}.
     */
    final Executor getExecutor() {
        return executor;
    }

    //
    // Internal Methods End
    //
}
