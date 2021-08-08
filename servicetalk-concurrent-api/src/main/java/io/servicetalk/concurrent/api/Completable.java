/*
 * Copyright © 2018-2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.CompletableSource.Subscriber;
import io.servicetalk.concurrent.api.SourceToFuture.CompletableToFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static io.servicetalk.concurrent.api.CompletableDoOnUtils.doOnCompleteSupplier;
import static io.servicetalk.concurrent.api.CompletableDoOnUtils.doOnErrorSupplier;
import static io.servicetalk.concurrent.api.CompletableDoOnUtils.doOnSubscribeSupplier;
import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Publisher.fromIterable;
import static io.servicetalk.concurrent.internal.SubscriberUtils.deliverErrorFromSource;
import static java.util.Arrays.spliterator;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

/**
 * An asynchronous computation that does not emit any data. It just completes or emits an error.
 *
 * <h2>How to subscribe?</h2>
 *
 * This class does not provide a way to subscribe using a {@link CompletableSource.Subscriber} as such calls are
 * ambiguous about the intent whether the subscribe is part of the same source (a.k.a an operator) or it is a terminal
 * subscribe. If it is required to subscribe to a source, then a {@link SourceAdapters source adapter} can be used to
 * convert to a {@link CompletableSource}.
 */
public abstract class Completable {
    private static final Logger LOGGER = LoggerFactory.getLogger(Completable.class);

    static {
        AsyncContext.autoEnable();
    }

    /**
     * New instance.
     */
    protected Completable() { }

    //
    // Operators Begin
    //

    /**
     * Transform errors emitted on this {@link Completable} into a {@link Subscriber#onComplete()} signal
     * (e.g. swallows the error).
     * <p>
     * This method provides a data transformation in sequential programming similar to:
     * <pre>{@code
     *     try {
     *         resultOfThisCompletable();
     *     } catch (Throwable cause) {
     *         // ignored
     *     }
     * }</pre>
     * @return A {@link Completable} which transform errors emitted on this {@link Completable} into a
     * {@link Subscriber#onComplete()} signal (e.g. swallows the error).
     * @see <a href="http://reactivex.io/documentation/operators/catch.html">ReactiveX catch operator.</a>
     */
    public final Completable onErrorComplete() {
        return onErrorComplete(t -> true);
    }

    /**
     * Transform errors emitted on this {@link Completable} which match {@code type} into a
     * {@link Subscriber#onComplete()} signal (e.g. swallows the error).
     * <p>
     * This method provides a data transformation in sequential programming similar to:
     * <pre>{@code
     *     try {
     *         resultOfThisCompletable();
     *     } catch (Throwable cause) {
     *         if (!type.isInstance(cause)) {
     *           throw cause;
     *         }
     *     }
     * }</pre>
     * @param type The {@link Throwable} type to filter, operator will not apply for errors which don't match this type.
     * @param <E> The {@link Throwable} type.
     * @return A {@link Completable} which transform errors emitted on this {@link Completable} which match {@code type}
     * into a {@link Subscriber#onComplete()} signal (e.g. swallows the error).
     * @see <a href="http://reactivex.io/documentation/operators/catch.html">ReactiveX catch operator.</a>
     */
    public final <E extends Throwable> Completable onErrorComplete(Class<E> type) {
        return onErrorComplete(type::isInstance);
    }

    /**
     * Transform errors emitted on this {@link Completable} which match {@code predicate} into a
     * {@link Subscriber#onComplete()} signal (e.g. swallows the error).
     * <p>
     * This method provides a data transformation in sequential programming similar to:
     * <pre>{@code
     *     try {
     *         resultOfThisCompletable();
     *     } catch (Throwable cause) {
     *         if (!predicate.test(cause)) {
     *           throw cause;
     *         }
     *     }
     * }</pre>
     * @param predicate returns {@code true} if the {@link Throwable} should be transformed to and
     * {@link Subscriber#onComplete()} signal. Returns {@code false} to propagate the error.
     * @return A {@link Completable} which transform errors emitted on this {@link Completable} which match
     * {@code predicate} into a {@link Subscriber#onComplete()} signal (e.g. swallows the error).
     * @see <a href="http://reactivex.io/documentation/operators/catch.html">ReactiveX catch operator.</a>
     */
    public final Completable onErrorComplete(Predicate<? super Throwable> predicate) {
        return new OnErrorCompleteCompletable(this, predicate);
    }

    /**
     * Transform errors emitted on this {@link Completable} into a different error.
     * <p>
     * This method provides a data transformation in sequential programming similar to:
     * <pre>{@code
     *     try {
     *         resultOfThisCompletable();
     *     } catch (Throwable cause) {
     *         throw mapper.apply(cause);
     *     }
     * }</pre>
     * @param mapper returns the error used to terminate the returned {@link Completable}.
     * @return A {@link Completable} which transform errors emitted on this {@link Completable} into a different error.
     * @see <a href="http://reactivex.io/documentation/operators/catch.html">ReactiveX catch operator.</a>
     */
    public final Completable onErrorMap(Function<? super Throwable, ? extends Throwable> mapper) {
        return onErrorMap(t -> true, mapper);
    }

    /**
     * Transform errors emitted on this {@link Completable} which match {@code type} into a different error.
     * <p>
     * This method provides a data transformation in sequential programming similar to:
     * <pre>{@code
     *     try {
     *         resultOfThisCompletable();
     *     } catch (Throwable cause) {
     *         if (type.isInstance(cause)) {
     *           throw mapper.apply(cause);
     *         } else {
     *           throw cause;
     *         }
     *     }
     * }</pre>
     * @param type The {@link Throwable} type to filter, operator will not apply for errors which don't match this type.
     * @param mapper returns the error used to terminate the returned {@link Completable}.
     * @param <E> The type of {@link Throwable} to transform.
     * @return A {@link Completable} which transform errors emitted on this {@link Completable} into a different error.
     * @see <a href="http://reactivex.io/documentation/operators/catch.html">ReactiveX catch operator.</a>
     */
    public final <E extends Throwable> Completable onErrorMap(
            Class<E> type, Function<? super E, ? extends Throwable> mapper) {
        @SuppressWarnings("unchecked")
        final Function<Throwable, Throwable> rawMapper = (Function<Throwable, Throwable>) mapper;
        return onErrorMap(type::isInstance, rawMapper);
    }

    /**
     * Transform errors emitted on this {@link Completable} which match {@code predicate} into a different error.
     * <p>
     * This method provides a data transformation in sequential programming similar to:
     * <pre>{@code
     *     try {
     *         resultOfThisCompletable();
     *     } catch (Throwable cause) {
     *         if (predicate.test(cause)) {
     *           throw mapper.apply(cause);
     *         } else {
     *           throw cause;
     *         }
     *     }
     * }</pre>
     * @param predicate returns {@code true} if the {@link Throwable} should be transformed via {@code mapper}. Returns
     * {@code false} to propagate the original error.
     * @param mapper returns the error used to terminate the returned {@link Completable}.
     * @return A {@link Completable} which transform errors emitted on this {@link Completable} into a different error.
     * @see <a href="http://reactivex.io/documentation/operators/catch.html">ReactiveX catch operator.</a>
     */
    public final Completable onErrorMap(Predicate<? super Throwable> predicate,
                                        Function<? super Throwable, ? extends Throwable> mapper) {
        return new OnErrorMapCompletable(this, predicate, mapper);
    }

    /**
     * Recover from any error emitted by this {@link Completable} by using another {@link Completable} provided by the
     * passed {@code nextFactory}.
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
     * @return A {@link Completable} that recovers from an error from this {@link Completable} by using another
     * {@link Completable} provided by the passed {@code nextFactory}.
     */
    public final Completable onErrorResume(Function<? super Throwable, ? extends Completable> nextFactory) {
        return onErrorResume(t -> true, nextFactory);
    }

    /**
     * Recover from errors emitted by this {@link Completable} which match {@code type} by using another
     * {@link Completable} provided by the passed {@code nextFactory}.
     * <p>
     * This method provides similar capabilities to a try/catch block in sequential programming:
     * <pre>{@code
     *     try {
     *         resultOfThisCompletable();
     *     } catch (Throwable cause) {
     *         if (type.isInstance(cause)) {
     *           // Note nextFactory returning a error Completable is like re-throwing (nextFactory shouldn't throw).
     *           results = nextFactory.apply(cause);
     *         } else {
     *           throw cause;
     *         }
     *     }
     * }</pre>
     *
     * @param type The {@link Throwable} type to filter, operator will not apply for errors which don't match this type.
     * @param nextFactory Returns the next {@link Completable}, when this {@link Completable} emits an error.
     * @param <E> The type of {@link Throwable} to transform.
     * @return A {@link Completable} that recovers from an error from this {@code Publisher} by using another
     * {@link Completable} provided by the passed {@code nextFactory}.
     * @see <a href="http://reactivex.io/documentation/operators/catch.html">ReactiveX catch operator.</a>
     */
    public final <E extends Throwable> Completable onErrorResume(
            Class<E> type, Function<? super E, ? extends Completable> nextFactory) {
        @SuppressWarnings("unchecked")
        Function<Throwable, ? extends Completable> rawNextFactory =
                (Function<Throwable, ? extends Completable>) nextFactory;
        return onErrorResume(type::isInstance, rawNextFactory);
    }

    /**
     * Recover from errors emitted by this {@link Completable} which match {@code predicate} by using another
     * {@link Completable} provided by the passed {@code nextFactory}.
     * <p>
     * This method provides similar capabilities to a try/catch block in sequential programming:
     * <pre>{@code
     *     try {
     *         resultOfThisCompletable();
     *     } catch (Throwable cause) {
     *         if (predicate.test(cause)) {
     *           // Note that nextFactory returning a error Publisher is like re-throwing (nextFactory shouldn't throw).
     *           results = nextFactory.apply(cause);
     *         } else {
     *           throw cause;
     *         }
     *     }
     * }</pre>
     *
     * @param predicate returns {@code true} if the {@link Throwable} should be transformed via {@code nextFactory}.
     * Returns {@code false} to propagate the original error.
     * @param nextFactory Returns the next {@link Completable}, when this {@link Completable} emits an error.
     * @return A {@link Completable} that recovers from an error from this {@link Completable} by using another
     * {@link Completable} provided by the passed {@code nextFactory}.
     * @see <a href="http://reactivex.io/documentation/operators/catch.html">ReactiveX catch operator.</a>
     */
    public final Completable onErrorResume(Predicate<? super Throwable> predicate,
                                           Function<? super Throwable, ? extends Completable> nextFactory) {
        return new OnErrorResumeCompletable(this, predicate, nextFactory);
    }

    /**
     * Invokes the {@code onComplete} {@link Runnable} argument when {@link Subscriber#onComplete()} is called for
     * {@link Subscriber}s of the returned {@link Completable}.
     * <p>
     * The order in which {@code onComplete} will be invoked relative to {@link Subscriber#onComplete()} is
     * undefined. If you need strict ordering see {@link #beforeOnComplete(Runnable)} and
     * {@link #afterOnComplete(Runnable)}.
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
     * @see #beforeOnComplete(Runnable)
     * @see #afterOnComplete(Runnable)
     */
    public final Completable whenOnComplete(Runnable onComplete) {
        return beforeOnComplete(onComplete);
    }

    /**
     * Invokes the {@code onError} {@link Consumer} argument when {@link Subscriber#onError(Throwable)} is called for
     * {@link Subscriber}s of the returned {@link Completable}.
     * <p>
     * The order in which {@code onError} will be invoked relative to {@link Subscriber#onError(Throwable)} is
     * undefined. If you need strict ordering see {@link #beforeOnError(Consumer)} and
     * {@link #afterOnError(Consumer)}.
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
     * @see #beforeOnError(Consumer)
     * @see #afterOnError(Consumer)
     */
    public final Completable whenOnError(Consumer<Throwable> onError) {
        return beforeOnError(onError);
    }

    /**
     * Invokes the {@code whenFinally} {@link Runnable} argument exactly once, when any of the following terminal
     * methods are called:
     * <ul>
     *     <li>{@link Subscriber#onComplete()}</li>
     *     <li>{@link Subscriber#onError(Throwable)}</li>
     *     <li>{@link Cancellable#cancel()}</li>
     * </ul>
     * for Subscriptions/{@link Subscriber}s of the returned {@link Completable}.
     * <p>
     * The order in which {@code whenFinally} will be invoked relative to the above methods is undefined. If you need
     * strict ordering see {@link #beforeFinally(Runnable)} and {@link #afterFinally(Runnable)}.
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
     * @see #beforeFinally(Runnable)
     * @see #afterFinally(Runnable)
     */
    public final Completable whenFinally(Runnable doFinally) {
        return beforeFinally(doFinally);
    }

    /**
     * Invokes the corresponding method on {@code whenFinally} {@link TerminalSignalConsumer} argument when any of the
     * following terminal methods are called:
     * <ul>
     *     <li>{@link Subscriber#onComplete()} - invokes {@link TerminalSignalConsumer#onComplete()}</li>
     *     <li>{@link Subscriber#onError(Throwable)} - invokes {@link TerminalSignalConsumer#onError(Throwable)}</li>
     *     <li>{@link Cancellable#cancel()} - invokes {@link TerminalSignalConsumer#cancel()}</li>
     * </ul>
     * for Subscriptions/{@link Subscriber}s of the returned {@link Completable}.
     * <p>
     * The order in which {@code whenFinally} will be invoked relative to the above methods is undefined. If you need
     * strict ordering see {@link #beforeFinally(TerminalSignalConsumer)} and
     * {@link #afterFinally(TerminalSignalConsumer)}.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *  try {
     *      resultOfThisCompletable();
     *  } catch(Throwable t) {
     *      // NOTE: The order of operations here is not guaranteed by this method!
     *      nextOperation(); // Maybe notifying of cancellation, or termination
     *      doFinally.onError(t);
     *      return;
     *  }
     *  // NOTE: The order of operations here is not guaranteed by this method!
     *  nextOperation(); // Maybe notifying of cancellation, or termination
     *  doFinally.onComplete();
     * }</pre>
     *
     * @param doFinally For each subscribe of the returned {@link Completable}, at most one method of this
     * {@link TerminalSignalConsumer} will be invoked.
     * @return The new {@link Completable}.
     * @see #beforeFinally(TerminalSignalConsumer)
     * @see #afterFinally(TerminalSignalConsumer)
     */
    public final Completable whenFinally(TerminalSignalConsumer doFinally) {
        return beforeFinally(doFinally);
    }

    /**
     * Invokes the {@code onCancel} {@link Runnable} argument when {@link Cancellable#cancel()} is called for
     * Subscriptions of the returned {@link Completable}.
     * <p>
     * The order in which {@code whenFinally} will be invoked relative to {@link Cancellable#cancel()} is undefined. If
     * you need strict ordering see {@link #beforeCancel(Runnable)} and {@link #afterCancel(Runnable)}.

     * @param onCancel Invoked when {@link Cancellable#cancel()} is called for Subscriptions of the
     * returned {@link Completable}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Completable}.
     * @see #beforeCancel(Runnable)
     * @see #afterCancel(Runnable)
     */
    public final Completable whenCancel(Runnable onCancel) {
        return beforeCancel(onCancel);
    }

    /**
     * Creates a new {@link Completable} that will mimic the signals of this {@link Completable} but will terminate
     * with a {@link TimeoutException} if time {@code duration} elapses between subscribe and termination.
     * The timer starts when the returned {@link Completable} is subscribed.
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
        return timeout(duration, unit, immediate());
    }

    /**
     * Creates a new {@link Completable} that will mimic the signals of this {@link Completable} but will terminate
     * with a {@link TimeoutException} if time {@code duration} elapses between subscribe and termination.
     * The timer starts when the returned {@link Completable} is subscribed.
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
    public final Completable timeout(long duration, TimeUnit unit,
                                     io.servicetalk.concurrent.Executor timeoutExecutor) {
        return new TimeoutCompletable(this, duration, unit, timeoutExecutor);
    }

    /**
     * Creates a new {@link Completable} that will mimic the signals of this {@link Completable} but will terminate
     * with a {@link TimeoutException} if time {@code duration} elapses between subscribe and termination.
     * The timer starts when the returned {@link Completable} is subscribed.
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
        return timeout(duration, immediate());
    }

    /**
     * Creates a new {@link Completable} that will mimic the signals of this {@link Completable} but will terminate
     * with a {@link TimeoutException} if time {@code duration} elapses between subscribe and termination.
     * The timer starts when the returned {@link Completable} is subscribed.
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
    public final Completable timeout(Duration duration, io.servicetalk.concurrent.Executor timeoutExecutor) {
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
    public final Completable concat(Completable next) {
        return new CompletableConcatWithCompletable(this, next);
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
    public final <T> Single<T> concat(Single<? extends T> next) {
        return new CompletableConcatWithSingle<>(this, next);
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
    public final <T> Publisher<T> concat(Publisher<? extends T> next) {
        return new CompletableConcatWithPublisher<>(this, next);
    }

    /**
     * Merges this {@link Completable} with the {@code other} {@link Completable} so that the resulting
     * {@link Completable} terminates successfully when both of these complete or either terminates with an error.
     * <p>
     * This method provides a means to merge multiple asynchronous sources, fails-fast in the presence of any errors,
     * and in sequential programming is similar to:
     * <pre>{@code
     *     ExecutorService e = ...;
     *     List<Future<Void>> futures = ...;
     *     futures.add(e.submit(() -> resultOfThisCompletable()));
     *     futures.add(e.submit(() -> resultOfCompletable(other));
     *     // This is an approximation, this operator does not provide any ordering guarantees for the results.
     *     for (Future<Void> future : futures) {
     *         future.get(); // Throws if the processing for this item failed.
     *     }
     * }</pre>
     *
     * @param other {@link Completable}s to merge.
     * @return {@link Completable} that terminates successfully when this and {@code other} {@link Completable}s
     * complete or terminates with an error when either terminates with an error.
     */
    public final Completable merge(Completable other) {
        return new MergeOneCompletable(false, this, other);
    }

    /**
     * Merges this {@link Completable} with the {@code other} {@link Completable}s so that the resulting
     * {@link Completable} terminates successfully when all of these complete or any one terminates with an error.
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
        return MergeCompletable.newInstance(false, this, other);
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
        return new IterableMergeCompletable(false, this, other);
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
    public final <T> Publisher<T> merge(Publisher<? extends T> mergeWith) {
        return new CompletableMergeWithPublisher<>(this, mergeWith, false);
    }

    /**
     * Merges the passed {@link Publisher} with this {@link Completable}.
     * <p>
     * The resulting {@link Publisher} emits all items emitted by the passed {@link Publisher} and terminates when both
     * this {@link Completable} and the passed {@link Publisher} terminate. If either terminates with an error then the
     * error will be propagated to the return value.
     * <pre>{@code
     *     ExecutorService e = ...;
     *     List<Future<Void>> futures = ...;
     *     futures.add(e.submit(() -> resultOfThisCompletable()));
     *     futures.add(e.submit(() -> resultOfMergeWithStream());
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
     * @param mergeWith the {@link Publisher} to merge in
     * @param <T> The value type of the resulting {@link Publisher}.
     * @return {@link Publisher} emits all items emitted by the passed {@link Publisher} and terminates when both this
     * {@link Completable} and the passed {@link Publisher} terminate. If either terminates with an error then the
     * error will be propagated to the return value.
     *
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX merge operator.</a>
     */
    public final <T> Publisher<T> mergeDelayError(Publisher<? extends T> mergeWith) {
        return new CompletableMergeWithPublisher<>(this, mergeWith, true);
    }

    /**
     * Merges this {@link Completable} with the {@code other} {@link Completable}, and delays error notification until
     * all involved {@link Completable}s terminate.
     * <p>
     * Use {@link #merge(Completable)} if any error should immediately terminate the returned {@link Completable}.
     * <p>
     * This method provides a means to merge multiple asynchronous sources, delays throwing in the presence of any
     * errors, and in sequential programming is similar to:
     * <pre>{@code
     *     ExecutorService e = ...;
     *     List<Future<Void>> futures = ...;
     *     futures.add(e.submit(() -> resultOfThisCompletable()));
     *     futures.add(e.submit(() -> resultOfCompletable(other));
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
     * @param other {@link Completable} to merge.
     * @return {@link Completable} that terminates after {@code this} {@link Completable} and {@code other}
     * {@link Completable}. If all involved {@link Completable}s terminate successfully then the return value will
     * terminate successfully. If any {@link Completable} terminates in an error, then the return value will also
     * terminate in an error.
     */
    public final Completable mergeDelayError(Completable other) {
        return new MergeOneCompletable(true, this, other);
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
        return MergeCompletable.newInstance(true, this, other);
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
        return new IterableMergeCompletable(true, this, other);
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
        return toSingle().retry(shouldRetry).ignoreElement();
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
    public final Completable retryWhen(BiIntFunction<Throwable, ? extends Completable> retryWhen) {
        return toSingle().retryWhen(retryWhen).ignoreElement();
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
     * repeated
     * @return A {@link Publisher} that emits the value returned by the passed {@link Supplier} everytime this
     * {@link Completable} completes.
     *
     * @see <a href="http://reactivex.io/documentation/operators/repeat.html">ReactiveX repeat operator.</a>
     */
    public final Publisher<Void> repeat(IntPredicate shouldRepeat) {
        return toSingle().repeat(shouldRepeat);
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
     * @return A {@link Completable} that completes after all re-subscriptions completes.
     *
     * @see <a href="http://reactivex.io/documentation/operators/retry.html">ReactiveX retry operator.</a>
     */
    public final Publisher<Void> repeatWhen(IntFunction<? extends Completable> repeatWhen) {
        return toSingle().repeatWhen(repeatWhen);
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
    public final Completable beforeOnSubscribe(Consumer<Cancellable> onSubscribe) {
        return beforeSubscriber(doOnSubscribeSupplier(onSubscribe));
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
    public final Completable beforeOnComplete(Runnable onComplete) {
        return beforeSubscriber(doOnCompleteSupplier(onComplete));
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
    public final Completable beforeOnError(Consumer<Throwable> onError) {
        return beforeSubscriber(doOnErrorSupplier(onError));
    }

    /**
     * Invokes the {@code onCancel} {@link Runnable} argument <strong>before</strong> {@link Cancellable#cancel()} is
     * called for Subscriptions of the returned {@link Completable}.
     *
     * @param onCancel Invoked <strong>before</strong> {@link Cancellable#cancel()} is called for Subscriptions of the
     * returned {@link Completable}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Completable}.
     */
    public final Completable beforeCancel(Runnable onCancel) {
        return new DoCancellableCompletable(this, onCancel::run, true);
    }

    /**
     * Invokes the {@code whenFinally} {@link Runnable} argument <strong>before</strong> any of the following terminal
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
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX do operator.</a>
     */
    public final Completable beforeFinally(Runnable doFinally) {
        return beforeFinally(new RunnableTerminalSignalConsumer(doFinally));
    }

    /**
     * Invokes the corresponding method on {@code beforeFinally} {@link TerminalSignalConsumer} argument
     * <strong>before</strong> any of the following terminal methods are called:
     * <ul>
     *     <li>{@link Subscriber#onComplete()} - invokes {@link TerminalSignalConsumer#onComplete()}</li>
     *     <li>{@link Subscriber#onError(Throwable)} - invokes {@link TerminalSignalConsumer#onError(Throwable)}</li>
     *     <li>{@link Cancellable#cancel()} - invokes {@link TerminalSignalConsumer#cancel()}</li>
     * </ul>
     * for Subscriptions/{@link Subscriber}s of the returned {@link Completable}.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *  try {
     *      resultOfThisCompletable();
     *  } catch(Throwable t) {
     *      doFinally.onError(t);
     *      nextOperation(); // Maybe notifying of cancellation, or termination
     *      return;
     *  }
     *  doFinally.onComplete();
     *  nextOperation(); // Maybe notifying of cancellation, or termination
     * }</pre>
     *
     * @param doFinally For each subscribe of the returned {@link Completable}, at most one method of this
     * {@link TerminalSignalConsumer} will be invoked.
     * @return The new {@link Completable}.
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX do operator.</a>
     */
    public final Completable beforeFinally(TerminalSignalConsumer doFinally) {
        return new BeforeFinallyCompletable(this, doFinally);
    }

    /**
     * Creates a new {@link Subscriber} (via the {@code subscriberSupplier} argument) on each call to
     * subscribe and invokes all the {@link Subscriber} methods <strong>before</strong> the {@link Subscriber}s of the
     * returned {@link Completable}.
     *
     * @param subscriberSupplier Creates a new {@link Subscriber} on each call to subscribe and invokes all the
     * {@link Subscriber} methods <strong>before</strong> the {@link Subscriber}s of the returned {@link Completable}.
     * {@link Subscriber} methods <strong>MUST NOT</strong> throw.
     * @return The new {@link Completable}.
     */
    public final Completable beforeSubscriber(Supplier<? extends Subscriber> subscriberSupplier) {
        return new BeforeSubscriberCompletable(this, subscriberSupplier);
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
    public final Completable afterOnSubscribe(Consumer<Cancellable> onSubscribe) {
        return afterSubscriber(doOnSubscribeSupplier(onSubscribe));
    }

    /**
     * Invokes the {@code onSubscribe} {@link Consumer} argument when
     * {@link Subscriber#onSubscribe(Cancellable)} is called for {@link Subscriber}s of the returned
     * {@link Completable}.
     * <p>
     * The order in which {@code onSubscribe} will be invoked relative to
     * {@link Subscriber#onSubscribe(Cancellable)} is undefined. If you need strict ordering see
     * {@link #beforeOnSubscribe(Consumer)} and {@link #afterOnSubscribe(Consumer)}.
     *
     * @param onSubscribe Invoked when {@link Subscriber#onSubscribe(Cancellable)} is called for
     * {@link Subscriber}s of the returned {@link Completable}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Completable}.
     *
     * @see #beforeOnSubscribe(Consumer)
     * @see #afterOnSubscribe(Consumer)
     */
    public final Completable whenOnSubscribe(Consumer<Cancellable> onSubscribe) {
        return beforeOnSubscribe(onSubscribe);
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
    public final Completable afterOnComplete(Runnable onComplete) {
        return afterSubscriber(doOnCompleteSupplier(onComplete));
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
    public final Completable afterOnError(Consumer<Throwable> onError) {
        return afterSubscriber(doOnErrorSupplier(onError));
    }

    /**
     * Invokes the {@code onCancel} {@link Runnable} argument <strong>after</strong> {@link Cancellable#cancel()} is
     * called for Subscriptions of the returned {@link Completable}.
     *
     * @param onCancel Invoked <strong>after</strong> {@link Cancellable#cancel()} is called for Subscriptions of the
     * returned {@link Completable}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Completable}.
     */
    public final Completable afterCancel(Runnable onCancel) {
        return new DoCancellableCompletable(this, onCancel::run, false);
    }

    /**
     * Invokes the {@code whenFinally} {@link Runnable} argument <strong>after</strong> any of the following terminal
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
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX do operator.</a>
     */
    public final Completable afterFinally(Runnable doFinally) {
        return afterFinally(new RunnableTerminalSignalConsumer(doFinally));
    }

    /**
     * Invokes the corresponding method on {@code afterFinally} {@link TerminalSignalConsumer} argument
     * <strong>after</strong> any of the following terminal methods are called:
     * <ul>
     *     <li>{@link Subscriber#onComplete()} - invokes {@link TerminalSignalConsumer#onComplete()}</li>
     *     <li>{@link Subscriber#onError(Throwable)} - invokes {@link TerminalSignalConsumer#onError(Throwable)}</li>
     *     <li>{@link Cancellable#cancel()} - invokes {@link TerminalSignalConsumer#cancel()}</li>
     * </ul>
     * for Subscriptions/{@link Subscriber}s of the returned {@link Completable}.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *  try {
     *      resultOfThisCompletable();
     *  } catch(Throwable t) {
     *      nextOperation(); // Maybe notifying of cancellation, or termination
     *      doFinally.onError(t);
     *      return;
     *  }
     *  nextOperation(); // Maybe notifying of cancellation, or termination
     *  doFinally.onComplete();
     * }</pre>
     *
     * @param doFinally For each subscribe of the returned {@link Completable}, at most one method of this
     * {@link TerminalSignalConsumer} will be invoked.
     * @return The new {@link Completable}.
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX do operator.</a>
     */
    public final Completable afterFinally(TerminalSignalConsumer doFinally) {
        return new AfterFinallyCompletable(this, doFinally);
    }

    /**
     * Creates a new {@link Subscriber} (via the {@code subscriberSupplier} argument) on each call to
     * subscribe and invokes all the {@link Subscriber} methods <strong>after</strong> the {@link Subscriber}s of the
     * returned {@link Completable}.
     *
     * @param subscriberSupplier Creates a new {@link Subscriber} on each call to subscribe and invokes all the
     * {@link Subscriber} methods <strong>after</strong> the {@link Subscriber}s of the returned {@link Completable}.
     * {@link Subscriber} methods <strong>MUST NOT</strong> throw.
     * @return The new {@link Completable}.
     */
    public final Completable afterSubscriber(Supplier<? extends Subscriber> subscriberSupplier) {
        return new AfterSubscriberCompletable(this, subscriberSupplier);
    }

    /**
     * Creates a new {@link Subscriber} (via the {@code subscriberSupplier} argument) for each new subscribe and
     * invokes methods on that {@link Subscriber} when the corresponding methods are called for {@link Subscriber}s of
     * the returned {@link Publisher}.
     *
     * @param subscriberSupplier Creates a new {@link Subscriber} for each new subscribe and invokes methods on that
     * {@link Subscriber} when the corresponding methods are called for {@link Subscriber}s of the returned
     * {@link Publisher}. {@link Subscriber} methods <strong>MUST NOT</strong> throw.
     * @return The new {@link Completable}.
     */
    public final Completable whenSubscriber(Supplier<? extends Subscriber> subscriberSupplier) {
        return beforeSubscriber(subscriberSupplier);
    }

    /**
     * <strong>This method requires advanced knowledge of building operators. Before using this method please attempt
     * to compose existing operator(s) to satisfy your use case.</strong>
     * <p>
     * Returns a {@link Completable} which will wrap the {@link Subscriber} using the provided {@code operator} argument
     * before subscribing to this {@link Completable}.
     * <pre>{@code
     *     Completable<X> pub = ...;
     *     pub.map(..) // A
     *        .liftSync(original -> modified)
     *        .afterFinally(..) // B
     * }</pre>
     * The {@code original -> modified} "operator" <strong>MUST</strong> be "synchronous" in that it does not interact
     * with the original {@link Subscriber} from outside the modified {@link Subscriber} or {@link Cancellable}
     * threads. That is to say this operator will not impact the {@link Executor} constraints already in place between
     * <i>A</i> and <i>B</i> above. If you need asynchronous behavior, or are unsure, see
     * {@link #liftAsync(CompletableOperator)}.
     *
     * @param operator The custom operator logic. The input is the "original" {@link Subscriber} to this
     * {@link Completable} and the return is the "modified" {@link Subscriber} that provides custom operator business
     * logic.
     * @return a {@link Completable} that when subscribed, the {@code operator} argument will be used to wrap the
     * {@link Subscriber} before subscribing to this {@link Completable}.
     * @see #liftAsync(CompletableOperator)
     */
    public final Completable liftSync(CompletableOperator operator) {
        return new LiftSynchronousCompletableOperator(this, operator);
    }

    /**
     * <strong>This method requires advanced knowledge of building operators. Before using this method please attempt
     * to compose existing operator(s) to satisfy your use case.</strong>
     * <p>
     * Returns a {@link Completable} which will wrap the {@link Subscriber} using the provided {@code operator} argument
     * before subscribing to this {@link Completable}.
     * <pre>{@code
     *     Publisher<X> pub = ...;
     *     pub.map(..) // A
     *        .liftAsync(original -> modified)
     *        .afterFinally(..) // B
     * }</pre>
     *
     * The {@code original -> modified} "operator" <strong>MAY</strong> be "asynchronous" in that it may interact with
     * the original {@link Subscriber} from outside the modified {@link Subscriber} or {@link Cancellable} threads. More
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
     * @return a {@link Completable} that when subscribed, the {@code operator} argument will be used to wrap the
     * {@link Subscriber} before subscribing to this {@link Completable}.
     * @see #liftSync(CompletableOperator)
     */
    public final Completable liftAsync(CompletableOperator operator) {
        return new LiftAsynchronousCompletableOperator(this, operator);
    }

    /**
     * Creates a new {@link Completable} that will use the passed {@link Executor} to invoke all {@link Subscriber}
     * methods.
     * This method does <strong>not</strong> override preceding {@link Executor}s, if any, specified for {@code this}
     * {@link Completable}. Only subsequent operations, if any, added in this execution chain will use this
     * {@link Executor}.
     * <p>
     * Note: unlike {@link #publishOn(Executor, BooleanSupplier)}, current operator always enforces offloading to the
     * passed {@link Executor}.
     *
     * @param executor {@link Executor} to use.
     * @return A new {@link Completable} that will use the passed {@link Executor} to invoke all {@link Subscriber}
     * methods.
     * @see #publishOn(Executor, BooleanSupplier)
     */
    public final Completable publishOn(Executor executor) {
        return PublishAndSubscribeOnCompletables.publishOn(this, Boolean.TRUE::booleanValue, executor);
    }

    /**
     * Creates a new {@link Completable} that may use the passed {@link Executor} to invoke all {@link Subscriber}
     * methods.
     * This method does <strong>not</strong> override preceding {@link Executor}s, if any, specified for {@code this}
     * {@link Completable}. Only subsequent operations, if any, added in this execution chain will use this
     * {@link Executor}.
     * <p>
     * Note: unlike {@link #publishOn(Executor)}, current operator may skip offloading to the passed {@link Executor},
     * depending on the result of the {@link BooleanSupplier} hint.
     *
     * @param executor {@link Executor} to use.
     * @param shouldOffload Provides a hint whether offloading to the executor can be omitted or not. Offloading may
     * still occur even if {@code false} is returned in order to preserve signal ordering.
     * @return A new {@link Completable} that may use the passed {@link Executor} to invoke all {@link Subscriber}
     * methods.
     * @see #publishOn(Executor)
     */
    public final Completable publishOn(Executor executor, BooleanSupplier shouldOffload) {
        return PublishAndSubscribeOnCompletables.publishOn(this, shouldOffload, executor);
    }

    /**
     * Creates a new {@link Completable} that will use the passed {@link Executor} to invoke the following methods:
     * <ul>
     *     <li>All {@link Cancellable} methods.</li>
     *     <li>The {@link #handleSubscribe(CompletableSource.Subscriber)} method.</li>
     * </ul>
     * This method does <strong>not</strong> override preceding {@link Executor}s, if any, specified for {@code this}
     * {@link Completable}. Only subsequent operations, if any, added in this execution chain will use this
     * {@link Executor}.
     * <p>
     * Note: unlike {@link #subscribeOn(Executor, BooleanSupplier)}, current operator always enforces offloading to the
     * passed {@link Executor}.
     *
     * @param executor {@link Executor} to use.
     * @return A new {@link Completable} that will use the passed {@link Executor} to invoke all methods of
     * {@link Cancellable} and {@link #handleSubscribe(CompletableSource.Subscriber)}.
     * @see #subscribeOn(Executor, BooleanSupplier)
     */
    public final Completable subscribeOn(Executor executor) {
        return PublishAndSubscribeOnCompletables.subscribeOn(this, Boolean.TRUE::booleanValue, executor);
    }

    /**
     * Creates a new {@link Completable} that may use the passed {@link Executor} to invoke the following methods:
     * <ul>
     *     <li>All {@link Cancellable} methods.</li>
     *     <li>The {@link #handleSubscribe(CompletableSource.Subscriber)} method.</li>
     * </ul>
     * This method does <strong>not</strong> override preceding {@link Executor}s, if any, specified for {@code this}
     * {@link Completable}. Only subsequent operations, if any, added in this execution chain will use this
     * {@link Executor}.
     * <p>
     * Note: unlike {@link #subscribeOn(Executor)}, current operator may skip offloading to the passed {@link Executor},
     * depending on the result of the {@link BooleanSupplier} hint.
     *
     * @param executor {@link Executor} to use.
     * @param shouldOffload Provides a hint whether offloading to the executor can be omitted or not. Offloading may
     * still occur even if {@code false} is returned in order to preserve signal ordering.
     * @return A new {@link Completable} that may use the passed {@link Executor} to invoke all methods of
     * {@link Cancellable} and {@link #handleSubscribe(CompletableSource.Subscriber)}.
     * @see #subscribeOn(Executor)
     */
    public final Completable subscribeOn(Executor executor, BooleanSupplier shouldOffload) {
        return PublishAndSubscribeOnCompletables.subscribeOn(this, shouldOffload, executor);
    }

    /**
     * Signifies that when the returned {@link Completable} is subscribed to, the {@link AsyncContext} will be shared
     * instead of making a {@link AsyncContextMap#copy() copy}.
     * <p>
     * This operator only impacts behavior if the returned {@link Completable} is subscribed directly after this
     * operator, that means this must be the "last operator" in the chain for this to have an impact.
     *
     * @return A {@link Completable} that will share the {@link AsyncContext} instead of making a
     * {@link AsyncContextMap#copy() copy} when subscribed to.
     */
    public final Completable subscribeShareContext() {
        return new CompletableSubscribeShareContext(this);
    }

    /**
     * Creates a new {@link Completable} that terminates with the result (either success or error) of either this
     * {@link Completable} or the passed {@code other} {@link Completable}, whichever terminates first. Therefore the
     * result is said to be <strong>ambiguous</strong> relative to which source it originated from. After the first
     * source terminates the non-terminated source will be cancelled.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *      for (Future<T> ft: futures) { // Provided Futures (analogous to the Completables here)
     *          // This is an approximation, this operator will pick the first result from either of the futures.
     *          return ft.get();
     *      }
     * }</pre>
     *
     * @param other {@link Completable} to subscribe to and race with this {@link Completable} to propagate to the
     * return value.
     * @return A new {@link Completable} that terminates with the result (either success or error) of either this
     * {@link Completable} or the passed {@code other} {@link Completable}, whichever terminates first. Therefore the
     * result is said to be <strong>ambiguous</strong> relative to which source it originated from.
     * @see <a href="http://reactivex.io/documentation/operators/amb.html">ReactiveX amb operator.</a>
     */
    public final Completable ambWith(final Completable other) {
        return toSingle().ambWith(other.toSingle()).ignoreElement();
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
     * No {@link io.servicetalk.concurrent.PublisherSource.Subscriber#onNext(Object)} signals will be delivered to the
     * returned {@link Publisher}. Only terminal signals will be delivered. If you need more control you should consider
     * using {@link #concat(Publisher)}.
     * @param <T> The value type of the resulting {@link Publisher}.
     * @return A {@link Publisher} that mirrors the terminal signal from this {@link Completable}.
     */
    public final <T> Publisher<T> toPublisher() {
        return new CompletableToPublisher<>(this);
    }

    /**
     * Converts this {@code Completable} to a {@link Single}.
     *
     * @return A {@link Single} that mirrors the terminal signal from this {@link Completable}.
     */
    public final Single<Void> toSingle() {
        return new CompletableToSingle<>(this);
    }

    /**
     * Converts this {@code Completable} to a {@link CompletionStage}.
     *
     * @return A {@link CompletionStage} that mirrors the terminal signal from this {@link Completable}.
     */
    public final CompletionStage<Void> toCompletionStage() {
        return toSingle().toCompletionStage();
    }

    /**
     * Converts this {@code Completable} to a {@link Future}.
     *
     * @return A {@link Future} that mirrors the terminal signal from this {@link Completable}.
     */
    public final Future<Void> toFuture() {
        return CompletableToFuture.createAndSubscribe(this);
    }

    //
    // Conversion Operators End
    //

    /**
     * Returns the {@link AsyncContextMap} to be used for a subscribe.
     *
     * @param provider The {@link AsyncContextProvider} which is the source of the map
     * @return {@link AsyncContextMap} for this subscribe operation.
     */
    AsyncContextMap contextForSubscribe(AsyncContextProvider provider) {
        // the default behavior is to copy the map. Some operators may want to use shared map
        return provider.contextMap().copy();
    }

    /**
     * A internal subscribe method similar to {@link CompletableSource#subscribe(Subscriber)} which can be used by
     * different implementations to subscribe.
     *
     * @param subscriber {@link Subscriber} to subscribe for the result.
     */
    protected final void subscribeInternal(Subscriber subscriber) {
        AsyncContextProvider contextProvider = AsyncContext.provider();
        AsyncContextMap contextMap = contextForSubscribe(contextProvider);
        subscribeWithContext(subscriber, contextProvider, contextMap);
    }

    /**
     * Subscribe to this {@link Completable} and log any {@link Subscriber#onError(Throwable)}.
     *
     * @return {@link Cancellable} used to invoke {@link Cancellable#cancel()} on the parameter of
     * {@link Subscriber#onSubscribe(Cancellable)} for this {@link Completable}.
     */
    public final Cancellable subscribe() {
        SimpleCompletableSubscriber subscriber = new SimpleCompletableSubscriber();
        subscribeInternal(subscriber);
        return subscriber;
    }

    /**
     * Subscribe to this {@link Completable} and log any {@link Subscriber#onError(Throwable)}. Passed {@link Runnable}
     * is invoked when this {@link Completable} terminates successfully.
     *
     * @param onComplete {@link Runnable} to invoke when this {@link Completable} terminates successfully.
     * @return {@link Cancellable} used to invoke {@link Cancellable#cancel()} on the parameter of
     * {@link Subscriber#onSubscribe(Cancellable)} for this {@link Completable}.
     */
    public final Cancellable subscribe(Runnable onComplete) {
        SimpleCompletableSubscriber subscriber = new SimpleCompletableSubscriber(onComplete);
        subscribeInternal(subscriber);
        return subscriber;
    }

    /**
     * Handles a subscriber to this {@code Completable}.
     * <p>
     * This method is invoked internally by {@link Completable} for every call to the
     * {@link Completable#subscribeInternal(CompletableSource.Subscriber)} method.
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
     * Creates a {@link Completable} which when subscribed will invoke {@link Runnable#run()} on the passed
     * {@link Runnable} and emit the value returned by that invocation from the returned {@link Completable}. Any error
     * emitted by the {@link Runnable} will terminate the returned {@link Completable} with the same error.
     * <p>
     * Blocking inside {@link Runnable#run()} will in turn block the subscribe call to the returned {@link Completable}.
     * If this behavior is undesirable then the returned {@link Completable} should be offloaded using
     * {@link #subscribeOn(Executor)} which offloads the subscribe call.
     *
     * @param runnable {@link Runnable} which is invoked before completion.
     * @return A new {@code Completable}.
     */
    public static Completable fromRunnable(final Runnable runnable) {
        return new RunnableCompletable(runnable);
    }

    /**
     * Creates a realized failed {@code Completable}.
     *
     * @param cause error that the returned {@code Completable} completes with.
     * @return A new {@code Completable}.
     */
    public static Completable failed(Throwable cause) {
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
     * {@link #subscribeInternal(CompletableSource.Subscriber)} to the returned {@link Completable}.
     * @return A new {@link Completable} that creates a new {@link Completable} using {@code completableFactory}
     * for every call to {@link #subscribeInternal(CompletableSource.Subscriber)} and forwards
     * the termination signal from the newly created {@link Completable} to its {@link Subscriber}.
     */
    public static Completable defer(Supplier<? extends Completable> completableSupplier) {
        return new CompletableDefer(completableSupplier);
    }

    /**
     * Convert from a {@link Future} to a {@link Completable} via {@link Future#get()}.
     * <p>
     * Note that because {@link Future} only presents blocking APIs to extract the result, so the process of getting the
     * results will block. The caller of {@link #subscribeInternal(CompletableSource.Subscriber)} is responsible for
     * offloading if necessary, and also offloading if {@link Cancellable#cancel()} will be called if this operation may
     * block.
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
     * defaults, {@link #mergeAll(Iterable, int)}.
     * <p>
     * If any of the {@link Completable}s terminate with an error, returned {@link Completable} will immediately
     * terminate with that error. In such a case, any in-progress {@link Completable}s will be cancelled. In order to
     * delay error termination use {@link #mergeAllDelayError(Iterable)}.
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
    public static Completable mergeAll(Iterable<? extends Completable> completables) {
        return fromIterable(completables).flatMapCompletable(identity());
    }

    /**
     * Returns a {@link Completable} that terminates when all the passed {@link Completable} terminate.
     * <p>
     * This will actively subscribe to a default number of {@link Completable}s concurrently, in order to alter the
     * defaults, {@link #mergeAll(int, Completable...)} should be used.
     * <p>
     * If any of the {@link Completable}s terminate with an error, returned {@link Completable} will immediately
     * terminate with that error. In such a case, any in-progress {@link Completable}s will be cancelled.
     *  In order to delay error termination use {@link #mergeAllDelayError(Completable...)}.
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
    public static Completable mergeAll(Completable... completables) {
        return from(completables).flatMapCompletable(identity());
    }

    /**
     * Returns a {@link Completable} that terminates when all the passed {@link Completable} terminate.
     * <p>
     * If any of the {@link Completable}s terminate with an error, returned {@link Completable} will immediately
     * terminate with that error. In such a case, any in-progress {@link Completable}s will be cancelled. In order to
     * delay error termination use {@link #mergeAllDelayError(Iterable, int)}.
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
    public static Completable mergeAll(Iterable<? extends Completable> completables, int maxConcurrency) {
        return fromIterable(completables).flatMapCompletable(identity(), maxConcurrency);
    }

    /**
     * Returns a {@link Completable} that terminates when all the passed {@link Completable} terminate.
     * <p>
     * If any of the {@link Completable}s terminate with an error, returned {@link Completable} will immediately
     * terminate with that error. In such a case, any in-progress {@link Completable}s will be cancelled.
     *  In order to delay error termination use {@link #mergeAllDelayError(int, Completable...)}.
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
    public static Completable mergeAll(int maxConcurrency, Completable... completables) {
        return from(completables).flatMapCompletable(identity(), maxConcurrency);
    }

    /**
     * Returns a {@link Completable} that terminates when all the passed {@link Completable} terminate.
     * <p>
     * This will actively subscribe to a default number of {@link Completable}s concurrently, in order to alter the
     * defaults, {@link #mergeAllDelayError(Iterable, int)} should be used.
     * <p>
     * If any of the {@link Completable}s terminate with an error, returned {@link Completable} will wait for
     * termination till all the other {@link Completable}s have been subscribed and terminated. If it is expected for
     * the returned {@link Completable} to terminate on the first failing {@link Completable},
     * {@link #mergeAll(Iterable)} should be used.
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
    public static Completable mergeAllDelayError(Iterable<? extends Completable> completables) {
        return fromIterable(completables).flatMapCompletableDelayError(identity());
    }

    /**
     * Returns a {@link Completable} that terminates when all the passed {@link Completable} terminate.
     * <p>
     * This will actively subscribe to a limited number of {@link Single}s concurrently, in order to alter the defaults,
     * {@link #mergeAll(int, Completable...)} should be used.
     * <p>
     * If any of the {@link Single}s terminate with an error, returned {@link Single} will wait for termination till all
     * the other {@link Single}s have been subscribed and terminated. If it is expected for the returned {@link Single}
     * to terminate on the first failing {@link Single}, {@link #mergeAll(Completable...)} should be used.
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
    public static Completable mergeAllDelayError(Completable... completables) {
        return from(completables).flatMapCompletableDelayError(identity());
    }

    /**
     * Returns a {@link Completable} that terminates when all the passed {@link Completable} terminate.
     * <p>
     * If any of the {@link Single}s terminate with an error, returned {@link Single} will wait for termination till all
     * the other {@link Single}s have been subscribed and terminated. If it is expected for the returned {@link Single}
     * to terminate on the first failing {@link Single}, {@link #mergeAll(Iterable, int)} should be used.
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
    public static Completable mergeAllDelayError(Iterable<? extends Completable> completables, int maxConcurrency) {
        return fromIterable(completables).flatMapCompletableDelayError(identity(), maxConcurrency);
    }

    /**
     * Returns a {@link Completable} that terminates when all the passed {@link Completable} terminate.
     * <p>
     * If any of the {@link Single}s terminate with an error, returned {@link Single} will wait for termination till all
     * the other {@link Single}s have been subscribed and terminated. If it is expected for the returned {@link Single}
     * to terminate on the first failing {@link Single}, {@link #mergeAll(Iterable, int)} should be used.
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
    public static Completable mergeAllDelayError(int maxConcurrency, Completable... completables) {
        return from(completables).flatMapCompletableDelayError(identity(), maxConcurrency);
    }

    /**
     * Creates a new {@link Completable} that terminates with the result (either success or error) of whichever amongst
     * the passed {@code completables} that terminates first. Therefore the result is said to be
     * <strong>ambiguous</strong> relative to which source it originated from. After the first source terminates the
     * non-terminated sources will be cancelled.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *      for (Future<T> ft: futures) { // Provided Futures (analogous to the Completables here)
     *          // This is an approximation, this operator will pick the first result from any of the futures.
     *          return ft.get();
     *      }
     * }</pre>
     *
     * @param completables {@link Completable}s to subscribe to and race to propagate to the return value.
     * @return A new {@link Completable} that terminates with the result (either success or error) of whichever amongst
     * the passed {@code completables} that terminates first. Therefore the result is said to be
     * <strong>ambiguous</strong> relative to which source it originated from.
     * @see <a href="http://reactivex.io/documentation/operators/amb.html">ReactiveX amb operator.</a>
     */
    public static Completable amb(final Completable... completables) {
        return Single.amb(stream(spliterator(completables), false)
                .map(Completable::toSingle).collect(toList())).ignoreElement();
    }

    /**
     * Creates a new {@link Completable} that terminates with the result (either success or error) of whichever amongst
     * the passed {@code completables} that terminates first. After the first source terminates the non-terminated
     * sources will be cancelled.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *      for (Future<T> ft: futures) { // Provided Futures (analogous to the Completables here)
     *          // This is an approximation, this operator will pick the first result from any of the futures.
     *          return ft.get();
     *      }
     * }</pre>
     *
     * @param completables {@link Completable}s to subscribe to and race to propagate to the return value.
     * @return A new {@link Completable} that terminates with the result (either success or error) of whichever amongst
     * the passed {@code completables} that terminates first. Therefore the result is said to be
     * <strong>ambiguous</strong> relative to which source it originated from.
     * @see <a href="http://reactivex.io/documentation/operators/amb.html">ReactiveX amb operator.</a>
     */
    public static Completable amb(final Iterable<Completable> completables) {
        return Single.amb(stream(completables.spliterator(), false)
                .map(Completable::toSingle).collect(toList())).ignoreElement();
    }

    /**
     * Creates a new {@link Completable} that terminates with the result (either success or error) of whichever amongst
     * the passed {@code completables} that terminates first.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *      for (Future<T> ft: futures) { // Provided Futures (analogous to the Completables here)
     *          // This is an approximation, this operator will pick the first result from any of the futures.
     *          return ft.get();
     *      }
     * }</pre>
     *
     * @param completables {@link Completable}s which to subscribe to and race to propagate to the return value.
     * @return A new {@link Completable} that terminates with the result (either success or error) of whichever amongst
     * the passed {@code completables} that terminates first.
     * @see <a href="http://reactivex.io/documentation/operators/amb.html">ReactiveX amb operator.</a>
     */
    public static Completable anyOf(final Completable... completables) {
        return amb(completables);
    }

    /**
     * Creates a new {@link Completable} that terminates with the result (either success or error) of whichever amongst
     * the passed {@code completables} that terminates first.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *      for (Future<T> ft: futures) { // Provided Futures (analogous to the Completables here)
     *          // This is an approximation, this operator will pick the first result from any of the futures.
     *          return ft.get();
     *      }
     * }</pre>
     *
     * @param completables {@link Completable}s which to subscribe to and race to propagate to the return value.
     * @return A new {@link Completable} that terminates with the result (either success or error) of whichever amongst
     * the passed {@code completables} that terminates first.
     * that result.
     * @see <a href="http://reactivex.io/documentation/operators/amb.html">ReactiveX amb operator.</a>
     */
    public static Completable anyOf(final Iterable<Completable> completables) {
        return amb(completables);
    }

    //
    // Static Utility Methods End
    //

    //
    // Internal Methods Begin
    //

    /**
     * Delegate subscribe calls in an operator chain. This method is used by operators to subscribe to the upstream
     * source.
     *
     * @param subscriber the subscriber.
     * @param contextMap the {@link AsyncContextMap} to use for this {@link Subscriber}.
     * @param contextProvider the {@link AsyncContextProvider} used to wrap any objects to preserve
     * {@link AsyncContextMap}.
     */
    final void delegateSubscribe(Subscriber subscriber,
                                 AsyncContextMap contextMap, AsyncContextProvider contextProvider) {
        handleSubscribe(subscriber, contextMap, contextProvider);
    }

    private void subscribeWithContext(Subscriber subscriber,
                                      AsyncContextProvider contextProvider, AsyncContextMap contextMap) {
        requireNonNull(subscriber);
        Subscriber wrapped = contextProvider.wrapCancellable(subscriber, contextMap);
        if (contextProvider.contextMap() == contextMap) {
            // No need to wrap as we sharing the AsyncContext
            handleSubscribe(wrapped, contextMap, contextProvider);
        } else {
            // Ensure that AsyncContext used for handleSubscribe() is the contextMap for the subscribe()
            contextProvider.wrapRunnable(() -> handleSubscribe(wrapped, contextMap, contextProvider), contextMap).run();
        }
    }

    /**
     * Override for {@link #handleSubscribe(CompletableSource.Subscriber)}.
     * <p>
     * Operators that do not wish to wrap the passed {@link Subscriber} can override this method and omit the wrapping.
     * @param subscriber the subscriber.
     * @param contextMap the {@link AsyncContextMap} to use for this {@link Subscriber}.
     * @param contextProvider the {@link AsyncContextProvider} used to wrap any objects to preserve
     */
    void handleSubscribe(Subscriber subscriber, AsyncContextMap contextMap, AsyncContextProvider contextProvider) {
        try {
            Subscriber wrapped = contextProvider.wrapCompletableSubscriber(subscriber, contextMap);
            handleSubscribe(wrapped);
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
            deliverErrorFromSource(subscriber, t);
        }
    }

    //
    // Internal Methods End
    //
}
