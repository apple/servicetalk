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
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.SingleSource.Subscriber;
import io.servicetalk.concurrent.api.SourceToFuture.SingleToFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.NeverSingle.neverSingle;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Publisher.fromIterable;
import static io.servicetalk.concurrent.api.SingleDoOnUtils.doOnErrorSupplier;
import static io.servicetalk.concurrent.api.SingleDoOnUtils.doOnSubscribeSupplier;
import static io.servicetalk.concurrent.api.SingleDoOnUtils.doOnSuccessSupplier;
import static io.servicetalk.concurrent.internal.SubscriberUtils.deliverErrorFromSource;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;

/**
 * An asynchronous computation that either completes with success giving the result or completes with an error.
 *
 * <h2>How to subscribe?</h2>
 *
 * This class does not provide a way to subscribe using a {@link Subscriber} as such calls are
 * ambiguous about the intent whether the subscribe is part of the same source (a.k.a an operator) or it is a terminal
 * subscribe. If it is required to subscribe to a source, then a {@link SourceAdapters source adapter} can be used to
 * convert to a {@link SingleSource}.
 *
 * @param <T> Type of the result of the single.
 */
public abstract class Single<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(Single.class);

    static {
        AsyncContext.autoEnable();
    }

    /**
     * New instance.
     *
     */
    protected Single() {
    }

    //
    // Operators Begin
    //

    /**
     * Maps the result of this single to a different type. Error, if any is forwarded to the returned {@link Single}.
     * <p>
     * This method provides a data transformation in sequential programming similar to:
     * <pre>{@code
     *     T tResult = resultOfThisSingle();
     *     R rResult = mapper.apply(tResult);
     * }</pre>
     * @param mapper To convert this result to other.
     * @param <R>    Type of the returned {@code Single}.
     * @return A new {@link Single} that will now have the result of type {@link R}.
     */
    public final <R> Single<R> map(Function<? super T, ? extends R> mapper) {
        return new MapSingle<>(this, mapper);
    }

    /**
     * Transform errors emitted on this {@link Single} into {@link Subscriber#onSuccess(Object)} signal
     * (e.g. swallows the error).
     * <p>
     * This method provides a data transformation in sequential programming similar to:
     * <pre>{@code
     *     T result = resultOfThisSingle();
     *     try {
     *         terminalOfThisSingle();
     *     } catch (Throwable cause) {
     *         return itemSupplier.apply(cause);
     *     }
     *     return result;
     * }</pre>
     * @param itemSupplier returns the element to emit to {@link Subscriber#onSuccess(Object)}.
     * @return A {@link Single} which transform errors emitted on this {@link Single} into
     * {@link Subscriber#onSuccess(Object)} signal (e.g. swallows the error).
     * @see <a href="http://reactivex.io/documentation/operators/catch.html">ReactiveX catch operator.</a>
     */
    public final Single<T> onErrorReturn(Function<? super Throwable, ? extends T> itemSupplier) {
        return onErrorReturn(t -> true, itemSupplier);
    }

    /**
     * Transform errors emitted on this {@link Single} which match {@code type} into
     * {@link Subscriber#onSuccess(Object)} signal (e.g. swallows the error).
     * <p>
     * This method provides a data transformation in sequential programming similar to:
     * <pre>{@code
     *     T result = resultOfThisSingle();
     *     try {
     *         terminalOfThisSingle();
     *     } catch (Throwable cause) {
     *         if (!type.isInstance(cause)) {
     *           throw cause;
     *         }
     *         return itemSupplier.apply(cause);
     *     }
     *     return result;
     * }</pre>
     * @param type The {@link Throwable} type to filter, operator will not apply for errors which don't match this type.
     * @param itemSupplier returns the element to emit to {@link Subscriber#onSuccess(Object)}.
     * @param <E> The type of {@link Throwable} to transform.
     * @return A {@link Single} which transform errors emitted on this {@link Single} into
     * {@link Subscriber#onSuccess(Object)} signal (e.g. swallows the error).
     * @see <a href="http://reactivex.io/documentation/operators/catch.html">ReactiveX catch operator.</a>
     */
    public final <E extends Throwable> Single<T> onErrorReturn(
            Class<E> type, Function<? super E, ? extends T> itemSupplier) {
        @SuppressWarnings("unchecked")
        final Function<Throwable, ? extends T> rawSupplier = (Function<Throwable, ? extends T>) itemSupplier;
        return onErrorReturn(type::isInstance, rawSupplier);
    }

    /**
     * Transform errors emitted on this {@link Single} which match {@code predicate} into
     * {@link Subscriber#onSuccess(Object)} signal (e.g. swallows the error).
     * <p>
     * This method provides a data transformation in sequential programming similar to:
     * <pre>{@code
     *     T result = resultOfThisSingle();
     *     try {
     *         terminalOfThisSingle();
     *     } catch (Throwable cause) {
     *         if (!predicate.test(cause)) {
     *           throw cause;
     *         }
     *         return itemSupplier.apply(cause);
     *     }
     *     return result;
     * }</pre>
     * @param predicate returns {@code true} if the {@link Throwable} should be transformed to
     * {@link Subscriber#onSuccess(Object)} signal. Returns {@code false} to propagate the error.
     * @param itemSupplier returns the element to emit to {@link Subscriber#onSuccess(Object)}.
     * @return A {@link Single} which transform errors emitted on this {@link Single} into
     * {@link Subscriber#onSuccess(Object)} signal (e.g. swallows the error).
     * @see <a href="http://reactivex.io/documentation/operators/catch.html">ReactiveX catch operator.</a>
     */
    public final Single<T> onErrorReturn(Predicate<? super Throwable> predicate,
                                         Function<? super Throwable, ? extends T> itemSupplier) {
        requireNonNull(itemSupplier);
        return onErrorResume(predicate, t -> succeeded(itemSupplier.apply(t)));
    }

    /**
     * Transform errors emitted on this {@link Single} into a different error.
     * <p>
     * This method provides a data transformation in sequential programming similar to:
     * <pre>{@code
     *     T result = resultOfThisSingle();
     *     try {
     *         terminalOfThisSingle();
     *     } catch (Throwable cause) {
     *         throw mapper.apply(cause);
     *     }
     *     return result;
     * }</pre>
     * @param mapper returns the error used to terminate the returned {@link Single}.
     * @return A {@link Single} which transform errors emitted on this {@link Single} into a different error.
     * @see <a href="http://reactivex.io/documentation/operators/catch.html">ReactiveX catch operator.</a>
     */
    public final Single<T> onErrorMap(Function<? super Throwable, ? extends Throwable> mapper) {
        return onErrorMap(t -> true, mapper);
    }

    /**
     * Transform errors emitted on this {@link Single} which match {@code type} into a different error.
     * <p>
     * This method provides a data transformation in sequential programming similar to:
     * <pre>{@code
     *     T result = resultOfThisSingle();
     *     try {
     *         terminalOfThisSingle();
     *     } catch (Throwable cause) {
     *         if (type.isInstance(cause)) {
     *           throw mapper.apply(cause);
     *         } else {
     *           throw cause;
     *         }
     *     }
     *     return result;
     * }</pre>
     * @param type The {@link Throwable} type to filter, operator will not apply for errors which don't match this type.
     * @param mapper returns the error used to terminate the returned {@link Single}.
     * @param <E> The type of {@link Throwable} to transform.
     * @return A {@link Single} which transform errors emitted on this {@link Single} into a different error.
     * @see <a href="http://reactivex.io/documentation/operators/catch.html">ReactiveX catch operator.</a>
     */
    public final <E extends Throwable> Single<T> onErrorMap(
            Class<E> type, Function<? super E, ? extends Throwable> mapper) {
        @SuppressWarnings("unchecked")
        final Function<Throwable, Throwable> rawMapper = (Function<Throwable, Throwable>) mapper;
        return onErrorMap(type::isInstance, rawMapper);
    }

    /**
     * Transform errors emitted on this {@link Single} which match {@code predicate} into a different error.
     * <p>
     * This method provides a data transformation in sequential programming similar to:
     * <pre>{@code
     *     T results = resultOfThisSingle();
     *     try {
     *         terminalOfThisSingle();
     *     } catch (Throwable cause) {
     *         if (predicate.test(cause)) {
     *           throw mapper.apply(cause);
     *         } else {
     *           throw cause;
     *         }
     *     }
     *     return result;
     * }</pre>
     * @param predicate returns {@code true} if the {@link Throwable} should be transformed via {@code mapper}. Returns
     * {@code false} to propagate the original error.
     * @param mapper returns the error used to terminate the returned {@link Single}.
     * @return A {@link Single} which transform errors emitted on this {@link Single} into a different error.
     * @see <a href="http://reactivex.io/documentation/operators/catch.html">ReactiveX catch operator.</a>
     */
    public final Single<T> onErrorMap(Predicate<? super Throwable> predicate,
                                      Function<? super Throwable, ? extends Throwable> mapper) {
        return new OnErrorMapSingle<>(this, predicate, mapper);
    }

    /**
     * Recover from any error emitted by this {@link Single} by using another {@link Single} provided by the
     * passed {@code nextFactory}.
     * <p>
     * This method provides similar capabilities to a try/catch block in sequential programming:
     * <pre>{@code
     *     T result;
     *     try {
     *         result = resultOfThisSingle();
     *     } catch (Throwable cause) {
     *         // Note that nextFactory returning a error Single is like re-throwing (nextFactory shouldn't throw).
     *         result = nextFactory.apply(cause);
     *     }
     *     return result;
     * }</pre>
     * @param nextFactory Returns the next {@link Single}, when this {@link Single} emits an error.
     * @return A {@link Single} that recovers from an error from this {@link Single} by using another
     * {@link Single} provided by the passed {@code nextFactory}.
     */
    public final Single<T> onErrorResume(Function<? super Throwable, ? extends Single<? extends T>> nextFactory) {
        return onErrorResume(t -> true, nextFactory);
    }

    /**
     * Recover from errors emitted by this {@link Single} which match {@code type} by using another {@link Single}
     * provided by the passed {@code nextFactory}.
     * <p>
     * This method provides similar capabilities to a try/catch block in sequential programming:
     * <pre>{@code
     *     T result;
     *     try {
     *         result = resultOfThisSingle();
     *     } catch (Throwable cause) {
     *       if (type.isInstance(cause)) {
     *         // Note that nextFactory returning a error Single is like re-throwing (nextFactory shouldn't throw).
     *         result = nextFactory.apply(cause);
     *       } else {
     *           throw cause;
     *       }
     *     }
     *     return result;
     * }</pre>
     *
     * @param type The {@link Throwable} type to filter, operator will not apply for errors which don't match this type.
     * @param nextFactory Returns the next {@link Single}, when this {@link Single} emits an error.
     * @param <E> The type of {@link Throwable} to transform.
     * @return A {@link Single} that recovers from an error from this {@link Single} by using another
     * {@link Single} provided by the passed {@code nextFactory}.
     * @see <a href="http://reactivex.io/documentation/operators/catch.html">ReactiveX catch operator.</a>
     */
    public final <E extends Throwable> Single<T> onErrorResume(
            Class<E> type, Function<? super E, ? extends Single<? extends T>> nextFactory) {
        @SuppressWarnings("unchecked")
        Function<Throwable, ? extends Single<? extends T>> rawNextFactory =
                (Function<Throwable, ? extends Single<? extends T>>) nextFactory;
        return onErrorResume(type::isInstance, rawNextFactory);
    }

    /**
     * Recover from errors emitted by this {@link Single} which match {@code predicate} by using another
     * {@link Single} provided by the passed {@code nextFactory}.
     * <p>
     * This method provides similar capabilities to a try/catch block in sequential programming:
     * <pre>{@code
     *     T result;
     *     try {
     *         result = resultOfThisSingle();
     *     } catch (Throwable cause) {
     *       if (predicate.test(cause)) {
     *         // Note that nextFactory returning a error Single is like re-throwing (nextFactory shouldn't throw).
     *         result = nextFactory.apply(cause);
     *       } else {
     *           throw cause;
     *       }
     *     }
     *     return result;
     * }</pre>
     *
     * @param predicate returns {@code true} if the {@link Throwable} should be transformed via {@code nextFactory}.
     * Returns {@code false} to propagate the original error.
     * @param nextFactory Returns the next {@link Single}, when this {@link Single} emits an error.
     * @return A {@link Single} that recovers from an error from this {@link Single} by using another
     * {@link Single} provided by the passed {@code nextFactory}.
     * @see <a href="http://reactivex.io/documentation/operators/catch.html">ReactiveX catch operator.</a>
     */
    public final Single<T> onErrorResume(Predicate<? super Throwable> predicate,
                                         Function<? super Throwable, ? extends Single<? extends T>> nextFactory) {
        return new OnErrorResumeSingle<>(this, predicate, nextFactory);
    }

    /**
     * Returns a {@link Single} that mirrors emissions from the {@link Single} returned by {@code next}.
     * Any error emitted by this {@link Single} is forwarded to the returned {@link Single}.
     * <p>
     * This method is similar to {@link #map(Function)} but the result is asynchronous, and provides a data
     * transformation in sequential programming similar to:
     * <pre>{@code
     *     T tResult = resultOfThisSingle();
     *     R rResult = mapper.apply(tResult); // Asynchronous result is flatten into a value by this operator.
     * }</pre>
     * @param next Function to give the next {@link Single}.
     * @param <R>  Type of the result of the resulting {@link Single}.
     * @return New {@link Single} that switches to the {@link Single} returned by {@code next} after this {@link Single}
     * completes successfully.
     */
    public final <R> Single<R> flatMap(Function<? super T, ? extends Single<? extends R>> next) {
        return new SingleFlatMapSingle<>(this, next);
    }

    /**
     * Returns a {@link Completable} that mirrors emissions from the {@link Completable} returned by {@code next}.
     * Any error emitted by this {@link Single} is forwarded to the returned {@link Completable}.
     * <p>
     * This method is similar to {@link #map(Function)} but the result is asynchronous with either complete/error status
     * in sequential programming similar to:
     * <pre>{@code
     *     T tResult = resultOfThisSingle();
     *     mapper.apply(tResult); // Asynchronous result is flatten into a error or completion by this operator.
     * }</pre>
     * @param next Function to give the next {@link Completable}.
     * @return New {@link Completable} that switches to the {@link Completable} returned by {@code next} after this
     * {@link Single} completes successfully.
     */
    public final Completable flatMapCompletable(Function<? super T, ? extends Completable> next) {
        return new SingleFlatMapCompletable<>(this, next);
    }

    /**
     * Returns a {@link Publisher} that mirrors emissions from the {@link Publisher} returned by {@code next}.
     * Any error emitted by this {@link Single} is forwarded to the returned {@link Publisher}.
     * <p>
     * This method is similar to {@link #map(Function)} but the result is asynchronous, and provides a data
     * transformation in sequential programming similar to:
     * <pre>{@code
     *     T tResult = resultOfThisSingle();
     *     // Asynchronous result from mapper is flatten into collection of values.
     *     for (R rResult : mapper.apply(tResult)) {
     *          // process rResult
     *     }
     * }</pre>
     * @param next Function to give the next {@link Publisher}.
     * @param <R>  Type of objects emitted by the returned {@link Publisher}.
     * @return New {@link Publisher} that switches to the {@link Publisher} returned by {@code next} after this
     * {@link Single} completes successfully.
     */
    public final <R> Publisher<R> flatMapPublisher(Function<? super T, ? extends Publisher<? extends R>> next) {
        return new SingleFlatMapPublisher<>(this, next);
    }

    /**
     * Invokes the {@code onSuccess} {@link Consumer} argument when {@link Subscriber#onSuccess(Object)} is called for
     * {@link Subscriber}s of the returned {@link Single}.
     * <p>
     * The order in which {@code onSuccess} will be invoked relative to {@link Subscriber#onSuccess(Object)} is
     * undefined. If you need strict ordering see {@link #beforeOnSuccess(Consumer)} and
     * {@link #afterOnSuccess(Consumer)}.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *  T result = resultOfThisSingle();
     *  // NOTE: The order of operations here is not guaranteed by this method!
     *  nextOperation(result);
     *  onSuccess.accept(result);
     * }</pre>
     * @param onSuccess Invoked when {@link Subscriber#onSuccess(Object)} is called for
     * {@link Subscriber}s of the returned {@link Single}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Single}.
     * @see #beforeOnSuccess(Consumer)
     * @see #afterOnSuccess(Consumer)
     */
    public final Single<T> whenOnSuccess(Consumer<? super T> onSuccess) {
        return beforeOnSuccess(onSuccess);
    }

    /**
     * Invokes the {@code onError} {@link Consumer} argument when {@link Subscriber#onError(Throwable)} is called for
     * {@link Subscriber}s of the returned {@link Single}.
     * <p>
     * The order in which {@code onError} will be invoked relative to {@link Subscriber#onError(Throwable)} is
     * undefined. If you need strict ordering see {@link #beforeOnError(Consumer)} and
     * {@link #afterOnError(Consumer)}.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *  try {
     *    T result = resultOfThisSingle();
     *  } catch (Throwable cause) {
     *      // NOTE: The order of operations here is not guaranteed by this method!
     *      nextOperation(cause);
     *      onError.accept(cause);
     *  }
     * }</pre>
     * @param onError Invoked when {@link Subscriber#onError(Throwable)} is called for {@link Subscriber}s of the
     * returned {@link Single}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Single}.
     * @see #beforeOnError(Consumer)
     * @see #afterOnError(Consumer)
     */
    public final Single<T> whenOnError(Consumer<Throwable> onError) {
        return beforeOnError(onError);
    }

    /**
     * Invokes the {@code whenFinally} {@link Runnable} argument exactly once, when any of the following terminal
     * methods are called:
     * <ul>
     *     <li>{@link Subscriber#onSuccess(Object)}</li>
     *     <li>{@link Subscriber#onError(Throwable)}</li>
     *     <li>{@link Cancellable#cancel()}</li>
     * </ul>
     * for Subscriptions/{@link Subscriber}s of the returned {@link Single}.
     * <p>
     * The order in which {@code whenFinally} will be invoked relative to the above methods is undefined. If you need
     * strict ordering see {@link #beforeFinally(Runnable)} and {@link #afterFinally(Runnable)}.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *  try {
     *      T result = resultOfThisSingle();
     *  } finally {
     *      // NOTE: The order of operations here is not guaranteed by this method!
     *      nextOperation(); // Maybe notifying of cancellation, or termination
     *      doFinally.run();
     *  }
     * }</pre>
     *
     * @param doFinally Invoked exactly once, when any of the following terminal methods are called:
     * <ul>
     *     <li>{@link Subscriber#onSuccess(Object)}</li>
     *     <li>{@link Subscriber#onError(Throwable)}</li>
     *     <li>{@link Cancellable#cancel()}</li>
     * </ul>
     * for Subscriptions/{@link Subscriber}s of the returned {@link Single}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Single}.
     * @see #beforeFinally(Runnable)
     * @see #afterFinally(Runnable)
     */
    public final Single<T> whenFinally(Runnable doFinally) {
        return beforeFinally(doFinally);
    }

    /**
     * Invokes the corresponding method on {@code whenFinally} {@link TerminalSignalConsumer} argument when any
     * of the following terminal methods are called:
     * <ul>
     *     <li>{@link Subscriber#onSuccess(Object)} - invokes
     *     {@link TerminalSignalConsumer#onComplete()}</li>
     *     <li>{@link Subscriber#onError(Throwable)} - invokes
     *     {@link TerminalSignalConsumer#onError(Throwable)}</li>
     *     <li>{@link Cancellable#cancel()} - invokes {@link TerminalSignalConsumer#cancel()}</li>
     * </ul>
     * for Subscriptions/{@link Subscriber}s of the returned {@link Single}.
     * <p>
     * The order in which {@code whenFinally} will be invoked relative to the above methods is undefined. If you need
     * strict ordering see {@link #beforeFinally(TerminalSignalConsumer)} and
     * {@link #afterFinally(TerminalSignalConsumer)}.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *  T result;
     *  try {
     *      result = resultOfThisSingle();
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
     * @param doFinally For each subscribe of the returned {@link Single}, at most one method of this
     * {@link TerminalSignalConsumer} will be invoked.
     * @return The new {@link Single}.
     * @see #beforeFinally(TerminalSignalConsumer)
     * @see #afterFinally(TerminalSignalConsumer)
     */
    public final Single<T> whenFinally(TerminalSignalConsumer doFinally) {
        return beforeFinally(doFinally);
    }

    /**
     * Invokes the corresponding method on {@code whenFinally} {@link SingleTerminalSignalConsumer} argument when any
     * of the following terminal methods are called:
     * <ul>
     *     <li>{@link Subscriber#onSuccess(Object)} - invokes
     *     {@link SingleTerminalSignalConsumer#onSuccess(Object)}</li>
     *     <li>{@link Subscriber#onError(Throwable)} - invokes
     *     {@link SingleTerminalSignalConsumer#onError(Throwable)}</li>
     *     <li>{@link Cancellable#cancel()} - invokes {@link SingleTerminalSignalConsumer#cancel()}</li>
     * </ul>
     * for Subscriptions/{@link Subscriber}s of the returned {@link Single}.
     * <p>
     * The order in which {@code whenFinally} will be invoked relative to the above methods is undefined. If you need
     * strict ordering see {@link #beforeFinally(SingleTerminalSignalConsumer)} and
     * {@link #afterFinally(SingleTerminalSignalConsumer)}.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *  T result;
     *  try {
     *      result = resultOfThisSingle();
     *  } catch(Throwable t) {
     *      // NOTE: The order of operations here is not guaranteed by this method!
     *      nextOperation(); // Maybe notifying of cancellation, or termination
     *      doFinally.onError(t);
     *      return;
     *  }
     *  // NOTE: The order of operations here is not guaranteed by this method!
     *  nextOperation(); // Maybe notifying of cancellation, or termination
     *  doFinally.onSuccess(result);
     * }</pre>
     *
     * @param doFinally For each subscribe of the returned {@link Single}, at most one method of this
     * {@link SingleTerminalSignalConsumer} will be invoked.
     * @return The new {@link Single}.
     * @see #beforeFinally(SingleTerminalSignalConsumer)
     * @see #afterFinally(SingleTerminalSignalConsumer)
     */
    public final Single<T> whenFinally(SingleTerminalSignalConsumer<? super T> doFinally) {
        return beforeFinally(doFinally);
    }

    /**
     * Invokes the {@code onCancel} {@link Runnable} argument when {@link Cancellable#cancel()} is called for
     * Subscriptions of the returned {@link Single}.
     * <p>
     * The order in which {@code whenFinally} will be invoked relative to {@link Cancellable#cancel()} is undefined. If
     * you need strict ordering see {@link #beforeCancel(Runnable)} and {@link #afterCancel(Runnable)}.

     * @param onCancel Invoked when {@link Cancellable#cancel()} is called for Subscriptions of the
     * returned {@link Single}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Single}.
     * @see #beforeCancel(Runnable)
     * @see #afterCancel(Runnable)
     */
    public final Single<T> whenCancel(Runnable onCancel) {
        return beforeCancel(onCancel);
    }

    /**
     * Creates a new {@link Single} that will mimic the signals of this {@link Single} but will terminate with a
     * with a {@link TimeoutException} if time {@code duration} elapses between subscribe and
     * termination. The timer starts when the returned {@link Single} is subscribed.
     * <p>
     * In the event of timeout any {@link Cancellable} from {@link Subscriber#onSubscribe(Cancellable)} will be
     * {@link Cancellable#cancel() cancelled} and the associated {@link Subscriber} will be
     * {@link Subscriber#onError(Throwable) terminated}.
     * @param duration The time duration which is allowed to elapse before {@link Subscriber#onSuccess(Object)}.
     * @param unit The units for {@code duration}.
     * @return a new {@link Single} that will mimic the signals of this {@link Single} but will terminate with a
     * {@link TimeoutException} if time {@code duration} elapses before {@link Subscriber#onSuccess(Object)}.
     * @see <a href="http://reactivex.io/documentation/operators/timeout.html">ReactiveX timeout operator.</a>
     */
    public final Single<T> timeout(long duration, TimeUnit unit) {
        return timeout(duration, unit, immediate());
    }

    /**
     * Creates a new {@link Single} that will mimic the signals of this {@link Single} but will terminate with a
     * with a {@link TimeoutException} if time {@code duration} elapses between subscribe and
     * termination. The timer starts when the returned {@link Single} is subscribed.
     * <p>
     * In the event of timeout any {@link Cancellable} from {@link Subscriber#onSubscribe(Cancellable)} will be
     * {@link Cancellable#cancel() cancelled} and the associated {@link Subscriber} will be
     * {@link Subscriber#onError(Throwable) terminated}.
     * @param duration The time duration which is allowed to elapse before {@link Subscriber#onSuccess(Object)}.
     * @param unit The units for {@code duration}.
     * @param timeoutExecutor The {@link Executor} to use for managing the timer notifications.
     * @return a new {@link Single} that will mimic the signals of this {@link Single} but will terminate with a
     * {@link TimeoutException} if time {@code duration} elapses before {@link Subscriber#onSuccess(Object)}.
     * @see <a href="http://reactivex.io/documentation/operators/timeout.html">ReactiveX timeout operator.</a>
     */
    public final Single<T> timeout(long duration, TimeUnit unit,
                                   io.servicetalk.concurrent.Executor timeoutExecutor) {
        return new TimeoutSingle<>(this, duration, unit, timeoutExecutor);
    }

    /**
     * Creates a new {@link Single} that will mimic the signals of this {@link Single} but will terminate with a
     * with a {@link TimeoutException} if time {@code duration} elapses between subscribe and
     * termination. The timer starts when the returned {@link Single} is subscribed.
     * <p>
     * In the event of timeout any {@link Cancellable} from {@link Subscriber#onSubscribe(Cancellable)} will be
     * {@link Cancellable#cancel() cancelled} and the associated {@link Subscriber} will be
     * {@link Subscriber#onError(Throwable) terminated}.
     * {@link Subscriber} will via {@link Subscriber#onError(Throwable) terminated}.
     * @param duration The time duration which is allowed to elapse before {@link Subscriber#onSuccess(Object)}.
     * @return a new {@link Single} that will mimic the signals of this {@link Single} but will terminate with a
     * {@link TimeoutException} if time {@code duration} elapses before {@link Subscriber#onSuccess(Object)}.
     * @see <a href="http://reactivex.io/documentation/operators/timeout.html">ReactiveX timeout operator.</a>
     */
    public final Single<T> timeout(Duration duration) {
        return timeout(duration, immediate());
    }

    /**
     * Creates a new {@link Single} that will mimic the signals of this {@link Single} but will terminate with a
     * with a {@link TimeoutException} if time {@code duration} elapses between subscribe and termination.
     * The timer starts when the returned {@link Single} is subscribed.
     * <p>
     * In the event of timeout any {@link Cancellable} from {@link Subscriber#onSubscribe(Cancellable)} will be
     * {@link Cancellable#cancel() cancelled} and the associated {@link Subscriber} will be
     * {@link Subscriber#onError(Throwable) terminated}.
     * @param duration The time duration which is allowed to elapse before {@link Subscriber#onSuccess(Object)}.
     * @param timeoutExecutor The {@link Executor} to use for managing the timer notifications.
     * @return a new {@link Single} that will mimic the signals of this {@link Single} but will terminate with a
     * {@link TimeoutException} if time {@code duration} elapses before {@link Subscriber#onSuccess(Object)}.
     * @see <a href="http://reactivex.io/documentation/operators/timeout.html">ReactiveX timeout operator.</a>
     */
    public final Single<T> timeout(Duration duration, io.servicetalk.concurrent.Executor timeoutExecutor) {
        return new TimeoutSingle<>(this, duration, timeoutExecutor);
    }

    /**
     * Returns a {@link Publisher} that first emits the result of this {@link Single} and then subscribes and emits
     * result of {@code next} {@link Single}. Any error emitted by this {@link Single} or {@code next} {@link Single} is
     * forwarded to the returned {@link Publisher}.
     * <p>
     * This method provides a means to sequence the execution of two asynchronous sources and in sequential programming
     * is similar to:
     * <pre>{@code
     *     Pair<T, T> p = new Pair<>();
     *     p.first = resultOfThisSingle();
     *     p.second = nextSingle();
     *     return p;
     * }</pre>
     * @param next {@link Single} to concat.
     * @return New {@link Publisher} that first emits the result of this {@link Single} and then subscribes and emits
     * result of {@code next} {@link Single}.
     */
    public final Publisher<T> concat(Single<? extends T> next) {
        return toPublisher().concat(next);
    }

    /**
     * Returns a {@link Single} that emits the result of this {@link Single} after {@code next} {@link Completable}
     * terminates successfully.
     * {@code next} {@link Completable} will only be subscribed to after this {@link Single} terminates successfully.
     * Any error emitted by this {@link Single} or {@code next} {@link Completable} is forwarded to the returned
     * {@link Single}.
     * <p>
     * This method provides a means to sequence the execution of two asynchronous sources and in sequential programming
     * is similar to:
     * <pre>{@code
     *     T result = resultOfThisSingle();
     *     nextCompletable(); // Note this either completes successfully, or throws an error.
     *     return result;
     * }</pre>
     * @param next {@link Completable} to concat.
     * @return New {@link Single} that emits the result of this {@link Single} after {@code next} {@link Completable}
     * terminates successfully.
     */
    public final Single<T> concat(Completable next) {
        return new SingleConcatWithCompletable<>(this, next);
    }

    /**
     * Returns a {@link Publisher} that first emits the result of this {@link Single} and then subscribes and emits all
     * elements from {@code next} {@link Publisher}. Any error emitted by this {@link Single} or {@code next}
     * {@link Publisher} is forwarded to the returned {@link Publisher}.
     * <p>
     * Note: this method is an overload for {@link #concat(Publisher, boolean)} with {@code deferSubscribe} equal to
     * {@code false}, which triggers subscribe to the {@code next} {@link Publisher} as soon as {@code this}
     * {@link Single} completes successfully.
     * <p>
     * This method provides a means to sequence the execution of two asynchronous sources and in sequential programming
     * is similar to:
     * <pre>{@code
     *     List<T> results = new ...;
     *     results.add(resultOfThisSingle());
     *     results.addAll(nextStream());
     *     return results;
     * }</pre>
     * @param next {@link Publisher} to concat.
     * @return New {@link Publisher} that first emits the result of this {@link Single} and then subscribes and emits
     * all elements from {@code next} {@link Publisher}.
     * @see #concat(Publisher, boolean)
     */
    public final Publisher<T> concat(Publisher<? extends T> next) {
        return new SingleConcatWithPublisher<>(this, next, false);
    }

    /**
     * Returns a {@link Publisher} that first emits the result of this {@link Single} and then subscribes and emits all
     * elements from {@code next} {@link Publisher}. Any error emitted by this {@link Single} or {@code next}
     * {@link Publisher} is forwarded to the returned {@link Publisher}.
     * <p>
     * This method provides a means to sequence the execution of two asynchronous sources and in sequential programming
     * is similar to:
     * <pre>{@code
     *     List<T> results = new ...;
     *     results.add(resultOfThisSingle());
     *     results.addAll(nextStream());
     *     return results;
     * }</pre>
     * @param next {@link Publisher} to concat.
     * @param deferSubscribe if {@code true} subscribe to the {@code next} {@link Publisher} will be deferred until
     * demand is received. Otherwise, it subscribes to the {@code next} {@link Publisher} as soon as {@code this}
     * {@link Single} completes successfully. Choosing the deferred ({@code true}) behavior is important if the
     * {@code next} {@link Publisher} does not or might not support multiple subscribers (non-replayable). Choosing the
     * immediate subscribe ({@code false}) behavior may have better performance and may be a preferable choice for
     * replayable {@link Publisher}(s) or when eager subscribe is beneficial.
     * @return New {@link Publisher} that first emits the result of this {@link Single} and then subscribes and emits
     * all elements from {@code next} {@link Publisher}.
     */
    public final Publisher<T> concat(Publisher<? extends T> next, boolean deferSubscribe) {
        return new SingleConcatWithPublisher<>(this, next, deferSubscribe);
    }

    /**
     * Create a new {@link Single} that emits the results of a specified zipper {@link BiFunction} to items emitted by
     * {@code this} and {@code other}.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *      CompletableFuture<T> f1 = ...; // this
     *      CompletableFuture<T2> other = ...;
     *      CompletableFuture.allOf(f1, other).get(); // wait for all futures to complete
     *      return zipper.apply(f1.get(), other.get());
     * }</pre>
     * @param other The other {@link Single} to zip with.
     * @param zipper Used to combine the completed results for each item from {@code singles}.
     * @param <T2> The type of {@code other}.
     * @param <R> The result type of the zipper.
     * @return a new {@link Single} that emits the results of a specified zipper {@link BiFunction} to items emitted by
     * {@code this} and {@code other}.
     * @see <a href="http://reactivex.io/documentation/operators/zip.html">ReactiveX zip operator.</a>
     */
    public final <T2, R> Single<R> zipWith(Single<? extends T2> other,
                                           BiFunction<? super T, ? super T2, ? extends R> zipper) {
        return zip(this, other, zipper);
    }

    /**
     * Create a new {@link Single} that emits the results of a specified zipper {@link BiFunction} to items emitted by
     * {@code this} and {@code other}. If any of the {@link Single}s terminate with an error, the returned
     * {@link Single} will wait for termination till all the other {@link Single}s have been subscribed and terminated,
     * and then terminate with the first error.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *      CompletableFuture<T> f1 = ...; // this
     *      CompletableFuture<T2> other = ...;
     *      CompletableFuture.allOf(f1, other).get(); // wait for all futures to complete
     *      return zipper.apply(f1.get(), other.get());
     * }</pre>
     * @param other The other {@link Single} to zip with.
     * @param zipper Used to combine the completed results for each item from {@code singles}.
     * @param <T2> The type of {@code other}.
     * @param <R> The result type of the zipper.
     * @return a new {@link Single} that emits the results of a specified zipper {@link BiFunction} to items emitted by
     * {@code this} and {@code other}.
     * @see <a href="http://reactivex.io/documentation/operators/zip.html">ReactiveX zip operator.</a>
     */
    public final <T2, R> Single<R> zipWithDelayError(Single<? extends T2> other,
                                                     BiFunction<? super T, ? super T2, ? extends R> zipper) {
        return zipDelayError(this, other, zipper);
    }

    /**
     * Re-subscribes to this {@link Single} if an error is emitted and the passed {@link BiIntPredicate} returns
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
     *             return resultOfThisSingle();
     *         } catch (Throwable cause) {
     *             if (shouldRetry.apply(attempts + 1, cause)) {
     *                 return execute(attempts + 1);
     *             } else {
     *                 throw cause;
     *             }
     *         }
     *     }
     * }</pre>
     * @param shouldRetry {@link BiIntPredicate} that given the retry count and the most recent {@link Throwable}
     * emitted from this
     * {@link Single} determines if the operation should be retried.
     * @return A {@link Single} that emits the result from this {@link Single} or re-subscribes if an error is emitted
     * and if the passed {@link BiIntPredicate} returned {@code true}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/retry.html">ReactiveX retry operator.</a>
     */
    public final Single<T> retry(BiIntPredicate<Throwable> shouldRetry) {
        return new RetrySingle<>(this, shouldRetry);
    }

    /**
     * Re-subscribes to this {@link Single} if an error is emitted and the {@link Completable} returned by the supplied
     * {@link BiIntFunction} completes successfully. If the returned {@link Completable} emits an error, the returned
     * {@link Single} terminates with that error.
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
     *             return resultOfThisSingle();
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
     * @param retryWhen {@link BiIntFunction} that given the retry count and the most recent {@link Throwable} emitted
     * from this {@link Single} returns a {@link Completable}. If this {@link Completable} emits an error, that error is
     * emitted from the returned {@link Single}, otherwise, original {@link Single} is re-subscribed when this
     * {@link Completable} completes.
     *
     * @return A {@link Single} that emits the result from this {@link Single} or re-subscribes if an error is emitted
     * and {@link Completable} returned by {@link BiIntFunction} completes successfully.
     *
     * @see <a href="http://reactivex.io/documentation/operators/retry.html">ReactiveX retry operator.</a>
     */
    public final Single<T> retryWhen(BiIntFunction<Throwable, ? extends Completable> retryWhen) {
        return new RetryWhenSingle<>(this, retryWhen);
    }

    /**
     * Re-subscribes to this {@link Single} when it completes and the passed {@link IntPredicate} returns {@code true}.
     * <p>
     * This method provides a means to repeat an operation multiple times and in sequential programming is similar to:
     * <pre>{@code
     *     List<T> results = new ...;
     *     int i = 0;
     *     do {
     *         results.add(resultOfThisSingle());
     *     } while (shouldRepeat.test(++i));
     *     return results;
     * }</pre>
     * @param shouldRepeat {@link IntPredicate} that given the repeat count determines if the operation should be
     * repeated.
     * @return A {@link Publisher} that emits all items from this {@link Single} and from all re-subscriptions whenever
     * the operation is repeated.
     *
     * @see <a href="http://reactivex.io/documentation/operators/repeat.html">ReactiveX repeat operator.</a>
     */
    public final Publisher<T> repeat(IntPredicate shouldRepeat) {
        return toPublisher().repeat(shouldRepeat);
    }

    /**
     * Re-subscribes to this {@link Single} when it completes and the {@link Completable} returned by the supplied
     * {@link IntFunction} completes successfully. If the returned {@link Completable} emits an error, the returned
     * {@link Single} emits an error.
     * <p>
     * This method provides a means to repeat an operation multiple times when in an asynchronous fashion and in
     * sequential programming is similar to:
     * <pre>{@code
     *     List<T> results = new ...;
     *     int i = 0;
     *     while (true) {
     *         results.add(resultOfThisSingle());
     *         try {
     *             repeatWhen.apply(++i); // Either throws or completes normally
     *         } catch (Throwable cause) {
     *             break;
     *         }
     *     }
     *     return results;
     * }</pre>
     * @param repeatWhen {@link IntFunction} that given the repeat count returns a {@link Completable}.
     * If this {@link Completable} emits an error repeat is terminated, otherwise, original {@link Single} is
     * re-subscribed when this {@link Completable} completes.
     *
     * @return A {@link Publisher} that emits all items from this {@link Single} and from all re-subscriptions whenever
     * the operation is repeated.
     *
     * @see <a href="http://reactivex.io/documentation/operators/retry.html">ReactiveX retry operator.</a>
     */
    public final Publisher<T> repeatWhen(IntFunction<? extends Completable> repeatWhen) {
        return toPublisher().repeatWhen(repeatWhen);
    }

    /**
     * Invokes the {@code onSubscribe} {@link Consumer} argument <strong>before</strong>
     * {@link Subscriber#onSubscribe(Cancellable)} is called for {@link Subscriber}s of the returned {@link Single}.
     *
     * @param onSubscribe Invoked <strong>before</strong> {@link Subscriber#onSubscribe(Cancellable)} is called for
     * {@link Subscriber}s of the returned {@link Single}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Single}.
     */
    public final Single<T> beforeOnSubscribe(Consumer<Cancellable> onSubscribe) {
        return beforeSubscriber(doOnSubscribeSupplier(onSubscribe));
    }

    /**
     * Invokes the {@code onSuccess} {@link Consumer} argument <strong>before</strong>
     * {@link Subscriber#onSuccess(Object)} is called for {@link Subscriber}s of the returned {@link Single}.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *  T result = resultOfThisSingle();
     *  onSuccess.accept(result);
     *  nextOperation(result);
     * }</pre>
     * @param onSuccess Invoked <strong>before</strong> {@link Subscriber#onSuccess(Object)} is called for
     * {@link Subscriber}s of the returned {@link Single}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Single}.
     */
    public final Single<T> beforeOnSuccess(Consumer<? super T> onSuccess) {
        return beforeSubscriber(doOnSuccessSupplier(onSuccess));
    }

    /**
     * Invokes the {@code onError} {@link Consumer} argument <strong>before</strong>
     * {@link Subscriber#onError(Throwable)} is called for {@link Subscriber}s of the returned {@link Single}.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *  try {
     *    T result = resultOfThisSingle();
     *  } catch (Throwable cause) {
     *      onError.accept(cause);
     *      nextOperation(cause);
     *  }
     * }</pre>
     * @param onError Invoked <strong>before</strong> {@link Subscriber#onError(Throwable)} is called for
     * {@link Subscriber}s of the returned {@link Single}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Single}.
     */
    public final Single<T> beforeOnError(Consumer<Throwable> onError) {
        return beforeSubscriber(doOnErrorSupplier(onError));
    }

    /**
     * Invokes the {@code onCancel} {@link Runnable} argument <strong>before</strong> {@link Cancellable#cancel()} is
     * called for Subscriptions of the returned {@link Single}.
     *
     * @param onCancel Invoked <strong>before</strong> {@link Cancellable#cancel()} is called for Subscriptions of the
     * returned {@link Single}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Single}.
     */
    public final Single<T> beforeCancel(Runnable onCancel) {
        return new WhenCancellableSingle<>(this, onCancel::run, true);
    }

    /**
     * Invokes the {@code whenFinally} {@link Runnable} argument <strong>before</strong> any of the following terminal
     * methods are called:
     * <ul>
     *     <li>{@link Subscriber#onSuccess(Object)}</li>
     *     <li>{@link Subscriber#onError(Throwable)}</li>
     *     <li>{@link Cancellable#cancel()}</li>
     * </ul>
     * for Subscriptions/{@link Subscriber}s of the returned {@link Single}.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *  try {
     *      T result = resultOfThisSingle();
     *  } finally {
     *      doFinally.run();
     *      nextOperation(); // Maybe notifying of cancellation, or termination
     *  }
     * }</pre>
     *
     * @param doFinally Invoked <strong>before</strong> any of the following terminal methods are called:
     * <ul>
     *     <li>{@link Subscriber#onSuccess(Object)}</li>
     *     <li>{@link Subscriber#onError(Throwable)}</li>
     *     <li>{@link Cancellable#cancel()}</li>
     * </ul>
     * for Subscriptions/{@link Subscriber}s of the returned {@link Single}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Single}.
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX do operator.</a>
     */
    public final Single<T> beforeFinally(Runnable doFinally) {
        return beforeFinally(new RunnableSingleTerminalSignalConsumer<>(doFinally));
    }

    /**
     * Invokes the corresponding method on {@code beforeFinally} {@link TerminalSignalConsumer} argument
     * <strong>before</strong> any of the following terminal methods are called:
     * <ul>
     *     <li>{@link Subscriber#onSuccess(Object)} - invokes
     *     {@link TerminalSignalConsumer#onComplete()}</li>
     *     <li>{@link Subscriber#onError(Throwable)} - invokes
     *     {@link TerminalSignalConsumer#onError(Throwable)}</li>
     *     <li>{@link Cancellable#cancel()} - invokes {@link TerminalSignalConsumer#cancel()}</li>
     * </ul>
     * for Subscriptions/{@link Subscriber}s of the returned {@link Single}.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *  T result;
     *  try {
     *      result = resultOfThisSingle();
     *  } catch(Throwable t) {
     *      doFinally.onError(t);
     *      nextOperation(); // Maybe notifying of cancellation, or termination
     *      return;
     *  }
     *  doFinally.onComplete();
     *  nextOperation(); // Maybe notifying of cancellation, or termination
     * }</pre>
     *
     * @param doFinally For each subscribe of the returned {@link Single}, at most one method of this
     * {@link TerminalSignalConsumer} will be invoked.
     * @return The new {@link Single}.
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX do operator.</a>
     */
    public final Single<T> beforeFinally(TerminalSignalConsumer doFinally) {
        return new BeforeFinallySingle<>(this, new TerminalSingleTerminalSignalConsumer<>(doFinally));
    }

    /**
     * Invokes the corresponding method on {@code beforeFinally} {@link SingleTerminalSignalConsumer} argument
     * <strong>before</strong> any of the following terminal methods are called:
     * <ul>
     *     <li>{@link Subscriber#onSuccess(Object)} - invokes
     *     {@link SingleTerminalSignalConsumer#onSuccess(Object)}</li>
     *     <li>{@link Subscriber#onError(Throwable)} - invokes
     *     {@link SingleTerminalSignalConsumer#onError(Throwable)}</li>
     *     <li>{@link Cancellable#cancel()} - invokes {@link SingleTerminalSignalConsumer#cancel()}</li>
     * </ul>
     * for Subscriptions/{@link Subscriber}s of the returned {@link Single}.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *  T result;
     *  try {
     *      result = resultOfThisSingle();
     *  } catch(Throwable t) {
     *      doFinally.onError(t);
     *      nextOperation(); // Maybe notifying of cancellation, or termination
     *      return;
     *  }
     *  doFinally.onSuccess(result);
     *  nextOperation(); // Maybe notifying of cancellation, or termination
     * }</pre>
     *
     * @param doFinally For each subscribe of the returned {@link Single}, at most one method of this
     * {@link SingleTerminalSignalConsumer} will be invoked.
     * @return The new {@link Single}.
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX do operator.</a>
     */
    public final Single<T> beforeFinally(SingleTerminalSignalConsumer<? super T> doFinally) {
        return new BeforeFinallySingle<>(this, doFinally);
    }

    /**
     * Creates a new {@link Subscriber} (via the {@code subscriberSupplier} argument) on each call to subscribe and
     * invokes all the {@link Subscriber} methods <strong>before</strong> the {@link Subscriber}s of the returned
     * {@link Single}.
     *
     * @param subscriberSupplier Creates a new {@link Subscriber} on each call to subscribe and invokes all the
     * {@link Subscriber} methods <strong>before</strong> the {@link Subscriber}s of the returned {@link Single}.
     * {@link Subscriber} methods <strong>MUST NOT</strong> throw.
     * @return The new {@link Single}.
     */
    public final Single<T> beforeSubscriber(Supplier<? extends Subscriber<? super T>> subscriberSupplier) {
        return new BeforeSubscriberSingle<>(this, subscriberSupplier);
    }

    /**
     * Invokes the {@code onSubscribe} {@link Consumer} argument <strong>after</strong>
     * {@link Subscriber#onSubscribe(Cancellable)} is called for {@link Subscriber}s of the returned {@link Single}.
     *
     * @param onSubscribe Invoked <strong>after</strong> {@link Subscriber#onSubscribe(Cancellable)} is called for
     * {@link Subscriber}s of the returned {@link Single}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Single}.
     */
    public final Single<T> afterOnSubscribe(Consumer<Cancellable> onSubscribe) {
        return afterSubscriber(doOnSubscribeSupplier(onSubscribe));
    }

    /**
     * Invokes the {@code onSubscribe} {@link Consumer} argument when
     * {@link Subscriber#onSubscribe(Cancellable)} is called for {@link Subscriber}s of the returned {@link Single}.
     *
     * <p>
     * The order in which {@code onSubscribe} will be invoked relative to
     * {@link Subscriber#onSubscribe(Cancellable)} is undefined. If you need strict ordering see
     * {@link #beforeOnSubscribe(Consumer)} and {@link #afterOnSubscribe(Consumer)}.
     *
     * @param onSubscribe Invoked when {@link Subscriber#onSubscribe(Cancellable)} is called for
     * {@link Subscriber}s of the returned {@link Single}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Single}.
     *
     * @see #beforeOnSubscribe(Consumer)
     * @see #afterOnSubscribe(Consumer)
     */
    public final Single<T> whenOnSubscribe(Consumer<Cancellable> onSubscribe) {
        return beforeOnSubscribe(onSubscribe);
    }

    /**
     * Invokes the {@code onSuccess} {@link Consumer} argument <strong>after</strong>
     * {@link Subscriber#onSuccess(Object)} is called for {@link Subscriber}s of the returned {@link Single}.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *  T result = resultOfThisSingle();
     *  nextOperation(result);
     *  onSuccess.accept(result);
     * }</pre>
     * @param onSuccess Invoked <strong>after</strong> {@link Subscriber#onSuccess(Object)} is called for
     * {@link Subscriber}s of the returned {@link Single}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Single}.
     */
    public final Single<T> afterOnSuccess(Consumer<? super T> onSuccess) {
        return afterSubscriber(doOnSuccessSupplier(onSuccess));
    }

    /**
     * Invokes the {@code onError} {@link Consumer} argument <strong>after</strong>
     * {@link Subscriber#onError(Throwable)} is called for {@link Subscriber}s of the returned {@link Single}.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *  try {
     *    T result = resultOfThisSingle();
     *  } catch (Throwable cause) {
     *      nextOperation(cause);
     *      onError.accept(cause);
     *  }
     * }</pre>
     * @param onError Invoked <strong>after</strong> {@link Subscriber#onError(Throwable)} is called for
     * {@link Subscriber}s of the returned {@link Single}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Single}.
     */
    public final Single<T> afterOnError(Consumer<Throwable> onError) {
        return afterSubscriber(doOnErrorSupplier(onError));
    }

    /**
     * Invokes the {@code onCancel} {@link Runnable} argument <strong>after</strong> {@link Cancellable#cancel()} is
     * called for Subscriptions of the returned {@link Single}.
     *
     * @param onCancel Invoked <strong>after</strong> {@link Cancellable#cancel()} is called for Subscriptions of the
     * returned {@link Single}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Single}.
     */
    public final Single<T> afterCancel(Runnable onCancel) {
        return new WhenCancellableSingle<>(this, onCancel::run, false);
    }

    /**
     * Invokes the {@code whenFinally} {@link Runnable} argument <strong>after</strong> any of the following terminal
     * methods are called:
     * <ul>
     *     <li>{@link Subscriber#onSuccess(Object)}</li>
     *     <li>{@link Subscriber#onError(Throwable)}</li>
     *     <li>{@link Cancellable#cancel()}</li>
     * </ul>
     * for Subscriptions/{@link Subscriber}s of the returned {@link Single}.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *  try {
     *      T result = resultOfThisSingle();
     *  } finally {
     *      nextOperation(); // Maybe notifying of cancellation, or termination
     *      doFinally.run();
     *  }
     * }</pre>
     *
     * @param doFinally Invoked <strong>after</strong> any of the following terminal methods are called:
     * <ul>
     *     <li>{@link Subscriber#onSuccess(Object)}</li>
     *     <li>{@link Subscriber#onError(Throwable)}</li>
     *     <li>{@link Cancellable#cancel()}</li>
     * </ul>
     * for Subscriptions/{@link Subscriber}s of the returned {@link Single}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Single}.
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX do operator.</a>
     */
    public final Single<T> afterFinally(Runnable doFinally) {
        return afterFinally(new RunnableSingleTerminalSignalConsumer<>(doFinally));
    }

    /**
     * Invokes the corresponding method on {@code afterFinally} {@link TerminalSignalConsumer} argument
     * <strong>after</strong> any of the following terminal methods are called:
     * <ul>
     *     <li>{@link Subscriber#onSuccess(Object)} - invokes
     *     {@link TerminalSignalConsumer#onComplete()}</li>
     *     <li>{@link Subscriber#onError(Throwable)} - invokes
     *     {@link TerminalSignalConsumer#onError(Throwable)}</li>
     *     <li>{@link Cancellable#cancel()} - invokes {@link TerminalSignalConsumer#cancel()}</li>
     * </ul>
     * for Subscriptions/{@link Subscriber}s of the returned {@link Single}.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *  T result;
     *  try {
     *      result = resultOfThisSingle();
     *  } catch(Throwable t) {
     *      nextOperation(); // Maybe notifying of cancellation, or termination
     *      doFinally.onError(t);
     *      return;
     *  }
     *  nextOperation(); // Maybe notifying of cancellation, or termination
     *  doFinally.onComplete();
     * }</pre>
     *
     * @param doFinally For each subscribe of the returned {@link Single}, at most one method of this
     * {@link TerminalSignalConsumer} will be invoked.
     * @return The new {@link Single}.
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX do operator.</a>
     */
    public final Single<T> afterFinally(TerminalSignalConsumer doFinally) {
        return new AfterFinallySingle<>(this, new TerminalSingleTerminalSignalConsumer<>(doFinally));
    }

    /**
     * Invokes the corresponding method on {@code afterFinally} {@link SingleTerminalSignalConsumer} argument
     * <strong>after</strong> any of the following terminal methods are called:
     * <ul>
     *     <li>{@link Subscriber#onSuccess(Object)} - invokes
     *     {@link SingleTerminalSignalConsumer#onSuccess(Object)}</li>
     *     <li>{@link Subscriber#onError(Throwable)} - invokes
     *     {@link SingleTerminalSignalConsumer#onError(Throwable)}</li>
     *     <li>{@link Cancellable#cancel()} - invokes {@link SingleTerminalSignalConsumer#cancel()}</li>
     * </ul>
     * for Subscriptions/{@link Subscriber}s of the returned {@link Single}.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *  T result;
     *  try {
     *      result = resultOfThisSingle();
     *  } catch(Throwable t) {
     *      nextOperation(); // Maybe notifying of cancellation, or termination
     *      doFinally.onError(t);
     *      return;
     *  }
     *  nextOperation(); // Maybe notifying of cancellation, or termination
     *  doFinally.onSuccess(result);
     * }</pre>
     *
     * @param doFinally For each subscribe of the returned {@link Single}, at most one method of this
     * {@link SingleTerminalSignalConsumer} will be invoked.
     * @return The new {@link Single}.
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX do operator.</a>
     */
    public final Single<T> afterFinally(SingleTerminalSignalConsumer<? super T> doFinally) {
        return new AfterFinallySingle<>(this, doFinally);
    }

    /**
     * Creates a new {@link Subscriber} (via the {@code subscriberSupplier} argument) on each call to subscribe and
     * invokes all the {@link Subscriber} methods <strong>after</strong> the {@link Subscriber}s of the returned
     * {@link Single}.
     *
     * @param subscriberSupplier Creates a new {@link Subscriber} on each call to subscribe and invokes all the
     * {@link Subscriber} methods <strong>after</strong> the {@link Subscriber}s of the returned {@link Single}.
     * {@link Subscriber} methods <strong>MUST NOT</strong> throw.
     * @return The new {@link Single}.
     */
    public final Single<T> afterSubscriber(Supplier<? extends Subscriber<? super T>> subscriberSupplier) {
        return new AfterSubscriberSingle<>(this, subscriberSupplier);
    }

    /**
     * Creates a new {@link Subscriber} (via the {@code subscriberSupplier} argument) for each new subscribe and
     * invokes methods on that {@link Subscriber} when the corresponding methods are called for {@link Subscriber}s of
     * the returned {@link Single}.
     *
     * @param subscriberSupplier Creates a new {@link Subscriber} for each new subscribe and invokes methods on that
     * {@link Subscriber} when the corresponding methods are called for {@link Subscriber}s of the returned
     * {@link Single}. {@link Subscriber} methods <strong>MUST NOT</strong> throw.
     * @return The new {@link Single}.
     */
    public final Single<T> whenSubscriber(Supplier<? extends Subscriber<? super T>> subscriberSupplier) {
        return beforeSubscriber(subscriberSupplier);
    }

    /**
     * Creates a new {@link Single} that will use the passed {@link Executor} to invoke all {@link Subscriber} methods.
     * This method does <strong>not</strong> override preceding {@link Executor}s, if any, specified for {@code this}
     * {@link Single}. Only subsequent operations, if any, added in this execution chain will use this
     * {@link Executor}.
     * <p>
     * Note: unlike {@link #publishOn(Executor, BooleanSupplier)}, current operator always enforces offloading to the
     * passed {@link Executor}.
     *
     * @param executor {@link Executor} to use.
     * @return A new {@link Single} that will use the passed {@link Executor} to invoke all {@link Subscriber} methods.
     * @see #publishOn(Executor, BooleanSupplier)
     */
    public final Single<T> publishOn(Executor executor) {
        return PublishAndSubscribeOnSingles.publishOn(this, Boolean.TRUE::booleanValue, executor);
    }

    /**
     * Creates a new {@link Single} that may use the passed {@link Executor} to invoke all {@link Subscriber} methods.
     * This method does <strong>not</strong> override preceding {@link Executor}s, if any, specified for {@code this}
     * {@link Single}. Only subsequent operations, if any, added in this execution chain will use this
     * {@link Executor}.
     * <p>
     * Note: unlike {@link #publishOn(Executor)}, current operator may skip offloading to the passed {@link Executor},
     * depending on the result of the {@link BooleanSupplier} hint.
     *
     * @param executor {@link Executor} to use.
     * @param shouldOffload Provides a hint whether offloading to the executor can be omitted or not. Offloading may
     * still occur even if {@code false} is returned in order to preserve signal ordering.
     * @return A new {@link Single} that may use the passed {@link Executor} to invoke all {@link Subscriber} methods.
     * @see #publishOn(Executor)
     */
    public final Single<T> publishOn(Executor executor, BooleanSupplier shouldOffload) {
        return PublishAndSubscribeOnSingles.publishOn(this, shouldOffload, executor);
    }

    /**
     * Creates a new {@link Single} that will use the passed {@link Executor} to invoke the following methods:
     * <ul>
     *     <li>All {@link Cancellable} methods.</li>
     *     <li>The {@link #handleSubscribe(SingleSource.Subscriber)} method.</li>
     * </ul>
     * This method does <strong>not</strong> override preceding {@link Executor}s, if any, specified for {@code this}
     * {@link Single}. Only subsequent operations, if any, added in this execution chain will use this
     * {@link Executor}.
     * <p>
     * Note: unlike {@link #subscribeOn(Executor, BooleanSupplier)}, current operator always enforces offloading to the
     * passed{@link Executor}.
     *
     * @param executor {@link Executor} to use.
     * @return A new {@link Single} that will use the passed {@link Executor} to invoke all methods of
     * {@link Cancellable} and {@link #handleSubscribe(SingleSource.Subscriber)}.
     * @see #subscribeOn(Executor, BooleanSupplier)
     */
    public final Single<T> subscribeOn(Executor executor) {
        return PublishAndSubscribeOnSingles.subscribeOn(this, Boolean.TRUE::booleanValue, executor);
    }

    /**
     * Creates a new {@link Single} that may use the passed {@link Executor} to invoke the following methods:
     * <ul>
     *     <li>All {@link Cancellable} methods.</li>
     *     <li>The {@link #handleSubscribe(SingleSource.Subscriber)} method.</li>
     * </ul>
     * This method does <strong>not</strong> override preceding {@link Executor}s, if any, specified for {@code this}
     * {@link Single}. Only subsequent operations, if any, added in this execution chain will use this
     * {@link Executor}.
     * <p>
     * Note: unlike {@link #subscribeOn(Executor)}, current operator may skip offloading to the passed {@link Executor},
     * depending on the result of the {@link BooleanSupplier} hint.
     *
     * @param executor {@link Executor} to use.
     * @param shouldOffload Provides a hint whether offloading to the executor can be omitted or not. Offloading may
     * still occur even if {@code false} is returned in order to preserve signal ordering.
     * @return A new {@link Single} that may use the passed {@link Executor} to invoke all methods of
     * {@link Cancellable} and {@link #handleSubscribe(SingleSource.Subscriber)}.
     * @see #subscribeOn(Executor)
     */
    public final Single<T> subscribeOn(Executor executor, BooleanSupplier shouldOffload) {
        return PublishAndSubscribeOnSingles.subscribeOn(this, shouldOffload, executor);
    }

    /**
     * Signifies that when the returned {@link Single} is subscribed to, the {@link AsyncContext} will be shared
     * instead of making a {@link AsyncContextMap#copy() copy}.
     * <p>
     * This operator only impacts behavior if the returned {@link Single} is subscribed directly after this operator,
     * that means this must be the "last operator" in the chain for this to have an impact.
     *
     * @return A {@link Single} that will share the {@link AsyncContext} instead of making a
     * {@link AsyncContextMap#copy() copy} when subscribed to.
     */
    public final Single<T> subscribeShareContext() {
        return new SingleSubscribeShareContext<>(this);
    }

    /**
     * <strong>This method requires advanced knowledge of building operators. Before using this method please attempt
     * to compose existing operator(s) to satisfy your use case.</strong>
     * <p>
     * Returns a {@link Single} which will wrap the {@link SingleSource.Subscriber} using the provided {@code operator}
     * argument before subscribing to this {@link Single}.
     * <pre>{@code
     *     Single<X> pub = ...;
     *     pub.map(..) // A
     *        .liftSync(original -> modified)
     *        .afterFinally(..) // B
     * }</pre>
     * The {@code original -> modified} "operator" <strong>MUST</strong> be "synchronous" in that it does not interact
     * with the original {@link Subscriber} from outside the modified {@link Subscriber} or {@link Cancellable}
     * threads. That is to say this operator will not impact the {@link Executor} constraints already in place between
     * <i>A</i> and <i>B</i> above. If you need asynchronous behavior, or are unsure, see
     * {@link #liftAsync(SingleOperator)}.
     * @param operator The custom operator logic. The input is the "original" {@link Subscriber} to this
     * {@link Single} and the return is the "modified" {@link Subscriber} that provides custom operator business
     * logic.
     * @param <R> Type of the items emitted by the returned {@link Single}.
     * @return a {@link Single} which when subscribed, the {@code operator} argument will be used to wrap the
     * {@link Subscriber} before subscribing to this {@link Single}.
     * @see #liftAsync(SingleOperator)
     */
    public final <R> Single<R> liftSync(SingleOperator<? super T, ? extends R> operator) {
        return new LiftSynchronousSingleOperator<>(this, operator);
    }

    /**
     * <strong>This method requires advanced knowledge of building operators. Before using this method please attempt
     * to compose existing operator(s) to satisfy your use case.</strong>
     * <p>
     * Returns a {@link Single} which will wrap the {@link SingleSource.Subscriber} using the provided {@code operator}
     * argument before subscribing to this {@link Single}.
     * <pre>{@code
     *     Publisher<X> pub = ...;
     *     pub.map(..) // Aw
     *        .liftAsync(original -> modified)
     *        .afterFinally(..) // B
     * }</pre>
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
     * {@link Single} and the return is the "modified" {@link Subscriber} that provides custom operator business
     * logic.
     * @param <R> Type of the items emitted by the returned {@link Single}.
     * @return a {@link Single} which when subscribed, the {@code operator} argument will be used to wrap the
     * {@link Subscriber} before subscribing to this {@link Single}.
     * @see #liftSync(SingleOperator)
     */
    public final <R> Single<R> liftAsync(SingleOperator<? super T, ? extends R> operator) {
        return new LiftAsynchronousSingleOperator<>(this, operator);
    }

    /**
     * Creates a new {@link Single} that terminates with the result (either success or error) of either this
     * {@link Single} or the passed {@code other} {@link Single}, whichever terminates first. Therefore the result is
     * said to be <strong>ambiguous</strong> relative to which source it originated from. After the first source
     * terminates the non-terminated source will be cancelled.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *      for (Future<T> ft: futures) { // Provided Futures (analogous to the Singles here)
     *          // This is an approximation, this operator will pick the first result from either of the futures.
     *          return ft.get();
     *      }
     * }</pre>
     *
     * @param other {@link Single} to subscribe to and race with this {@link Single} to propagate to the return value.
     * @return A new {@link Single} that terminates with the result (either success or error) of either this
     * {@link Single} or the passed {@code other} {@link Single}, whichever terminates first. Therefore the result is
     * said to be <strong>ambiguous</strong> relative to which source it originated from.
     * @see <a href="http://reactivex.io/documentation/operators/amb.html">ReactiveX amb operator.</a>
     */
    public final Single<T> ambWith(final Single<T> other) {
        return new SingleAmbWith<>(this, other);
    }

    //
    // Operators End
    //

    //
    // Conversion Operators Begin
    //

    /**
     * Converts this {@code Single} to a {@link Publisher}.
     *
     * @return A {@link Publisher} that emits at most a single item which is emitted by this {@code Single}.
     */
    public final Publisher<T> toPublisher() {
        return new SingleToPublisher<>(this);
    }

    /**
     * Ignores the result of this {@link Single} and forwards the termination signal to the returned
     * {@link Completable}.
     *
     * @return A {@link Completable} that mirrors the terminal signal from this {@code Single}.
     */
    public final Completable toCompletable() {
        return new SingleToCompletable<>(this);
    }

    /**
     * Ignores the result of this {@link Single} and forwards the termination signal to the returned
     * {@link Completable}.
     *
     * @return A {@link Completable} that mirrors the terminal signal from this {@code Single}.
     */
    public final Completable ignoreElement() {
        return toCompletable();
    }

    /**
     * Convert this {@link Single} to a {@link CompletionStage}.
     *
     * @return A {@link CompletionStage} that mirrors the terminal signal from this {@link Single}.
     */
    public final CompletionStage<T> toCompletionStage() {
        return SingleToCompletableFuture.createAndSubscribe(this);
    }

    /**
     * Convert this {@link Single} to a {@link Future}.
     *
     * @return A {@link Future} that mirrors the terminal signal from this {@link Single}.
     */
    public final Future<T> toFuture() {
        return SingleToFuture.createAndSubscribe(this);
    }

    //
    // Conversion Operators End
    //

    /**
     * A internal subscribe method similar to {@link SingleSource#subscribe(Subscriber)} which can be used by
     * different implementations to subscribe.
     *
     * @param subscriber {@link Subscriber} to subscribe for the result.
     */
    protected final void subscribeInternal(Subscriber<? super T> subscriber) {
        subscribeAndReturnContext(subscriber, AsyncContext.provider());
    }

    /**
     * Subscribe to this {@link Single}, emits the result to the passed {@link Consumer} and log any
     * {@link Subscriber#onError(Throwable)}.
     *
     * @param resultConsumer {@link Consumer} to accept the result of this {@link Single}.
     *
     * @return {@link Cancellable} used to invoke {@link Cancellable#cancel()} on the parameter of
     * {@link Subscriber#onSubscribe(Cancellable)} for this {@link Single}.
     */
    public final Cancellable subscribe(Consumer<? super T> resultConsumer) {
        SimpleSingleSubscriber<T> subscriber = new SimpleSingleSubscriber<>(resultConsumer);
        subscribeInternal(subscriber);
        return subscriber;
    }

    /**
     * Handles a subscriber to this {@link Single}.
     *
     * @param subscriber the subscriber.
     */
    protected abstract void handleSubscribe(Subscriber<? super T> subscriber);

    // <editor-fold desc="Static Utility Methods">

    /**
     * Creates a realized {@link Single} which always completes successfully with the provided {@code value}.
     *
     * @param value result of the {@link Single}.
     * @param <T>   Type of the {@link Single}.
     *
     * @return A new {@link Single}.
     */
    public static <T> Single<T> succeeded(@Nullable T value) {
        return new SucceededSingle<>(value);
    }

    /**
     * Creates a {@link Single} which when subscribed will invoke {@link Callable#call()} on the passed
     * {@link Callable} and emit the value returned by that invocation from the returned {@link Single}. Any error
     * emitted by the {@link Callable} will terminate the returned {@link Single} with the same error.
     * <p>
     * Blocking inside {@link Callable#call()} will in turn block the subscribe call to the returned {@link Single}. If
     * this behavior is undesirable then the returned {@link Single} should be offloaded using
     * {@link #subscribeOn(Executor)} which offloads the subscribe call.
     *
     * @param callable {@link Callable} which supplies the result of the {@link Single}.
     * @param <T>      Type of the {@link Single}.
     *
     * @return A new {@link Single}.
     */
    public static <T> Single<T> fromCallable(final Callable<T> callable) {
        return new CallableSingle<>(callable);
    }

    /**
     * Creates a {@link Single} which when subscribed will invoke {@link Supplier#get()} on the passed
     * {@link Supplier} and emit the value returned by that invocation from the returned {@link Single}. Any error
     * emitted by the {@link Supplier} will terminate the returned {@link Single} with the same error.
     * <p>
     * Blocking inside {@link Supplier#get()} will in turn block the subscribe call to the returned {@link Single}. If
     *      * this behavior is undesirable then the returned {@link Single} should be offloaded using
     *      * {@link #subscribeOn(Executor)} which offloads the subscribe call.
     *
     * @param supplier {@link Supplier} which supplies the result of the {@link Single}.
     * @param <T>      Type of the {@link Single}.
     *
     * @return A new {@link Single}.
     */
    public static <T> Single<T> fromSupplier(final Supplier<T> supplier) {
        return fromCallable(supplier::get);
    }

    /**
     * Creates a realized {@link Single} which always completes with the provided error {@code cause}.
     *
     * @param cause result of the {@link Single}.
     * @param <T>   Type of the {@link Single}.
     *
     * @return A new {@link Single}.
     */
    public static <T> Single<T> failed(Throwable cause) {
        return new FailedSingle<>(cause);
    }

    /**
     * Creates a {@link Single} that never terminates.
     *
     * @param <T> Type of the {@link Single}.
     * @return A new {@link Single}.
     */
    public static <T> Single<T> never() {
        return neverSingle();
    }

    /**
     * Defer creation of a {@link Single} till it is subscribed to.
     *
     * @param singleSupplier {@link Supplier} to create a new {@link Single} every time the returned {@link Single} is
     * subscribed.
     * @param <T> Type of the {@link Single}.
     * @return A new {@link Single} that creates a new {@link Single} using {@code singleSupplier} every time
     * it is subscribed and forwards all items and terminal events from the newly created {@link Single} to its
     * {@link Subscriber}.
     */
    public static <T> Single<T> defer(Supplier<? extends Single<? extends T>> singleSupplier) {
        return new SingleDefer<>(singleSupplier);
    }

    /**
     * Convert from a {@link Future} to a {@link Single} via {@link Future#get()}.
     * <p>
     * Note that because {@link Future} only presents blocking APIs to extract the result, so the process of getting the
     * results will block. The caller of subscribe is responsible for offloading if necessary, and also offloading if
     * {@link Cancellable#cancel()} will be called and this operation may block.
     * <p>
     * To apply a timeout see {@link #timeout(long, TimeUnit)} and related methods.
     * @param future The {@link Future} to convert.
     * @param <T> The data type the {@link Future} provides when complete.
     * @return A {@link Single} that derives results from {@link Future}.
     * @see #timeout(long, TimeUnit)
     */
    public static <T> Single<T> fromFuture(Future<? extends T> future) {
        return new FutureToSingle<>(future);
    }

    /**
     * Asynchronously collects results of individual {@link Single}s returned by the passed {@link Iterable} into a
     * single {@link Collection}. <p>
     * This will actively subscribe to a limited number of {@link Single}s concurrently, in order to alter the defaults,
     * {@link #collectUnordered(Iterable, int)} should be used. <p>
     * If any of the {@link Single}s terminate with an error, returned {@link Single} will immediately terminate with
     * that error. In such a case, any in progress {@link Single}s will be cancelled. In order to delay error
     * termination use {@link #collectUnorderedDelayError(Iterable)}.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *      List<T> result = ...;// assume this is thread safe
     *      for (Future<T> ft: futures) { // Provided Futures (analogous to the Singles here)
     *          // This is an approximation, this operator does not provide any ordering guarantees for the results.
     *          result.add(ft.get());
     *      }
     *      return result;
     * }</pre>
     *
     * @param singles {@link Iterable} of {@link Single}s, results of which are to be collected.
     * @param <T> Type of the result of the individual {@link Single}s
     * @return A {@link Single} producing a {@link Collection} of all values produced by the individual {@link Single}s.
     * There is no guarantee of the order of the values in the produced {@link Collection} as compared to the order of
     * {@link Single}s passed to this method.
     */
    public static <T> Single<Collection<T>> collectUnordered(Iterable<? extends Single<? extends T>> singles) {
        return fromIterable(singles).flatMapMergeSingle(identity()).collect(ArrayList::new, (ts, t) -> {
            ts.add(t);
            return ts;
        });
    }

    /**
     * Asynchronously collects results of the passed {@link Single}s into a single {@link Collection}. <p>
     * This will actively subscribe to a limited number of {@link Single}s concurrently, in order to alter the defaults,
     * {@link #collectUnordered(int, Single[])} should be used. <p>
     * If any of the {@link Single}s terminate with an error, returned {@link Single} will immediately terminate with
     * that error. In such a case, any in progress {@link Single}s will be cancelled. In order to delay error
     * termination use {@link #collectUnorderedDelayError(Single[])}.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *      List<T> result = ...;// assume this is thread safe
     *      for (Future<T> ft: futures) { // Provided Futures (analogous to the Singles here)
     *          // This is an approximation, this operator does not provide any ordering guarantees for the results.
     *          result.add(ft.get());
     *      }
     *      return result;
     * }</pre>
     *
     * @param singles {@link Single}s, results of which are to be collected.
     * @param <T> Type of the result of the individual {@link Single}s
     * @return A {@link Single} producing a {@link Collection} of all values produced by the individual {@link Single}s.
     * There is no guarantee of the order of the values in the produced {@link Collection} as compared to the order of
     * {@link Single}s passed to this method.
     */
    @SafeVarargs
    public static <T> Single<Collection<T>> collectUnordered(Single<? extends T>... singles) {
        return from(singles).<T>flatMapMergeSingle(identity()).collect(() -> new ArrayList<>(singles.length),
                (ts, t) -> {
                    ts.add(t);
                    return ts;
                });
    }

    /**
     * Asynchronously collects results of individual {@link Single}s returned by the passed {@link Iterable} into a
     * single {@link Collection}. <p>
     * If any of the {@link Single}s terminate with an error, returned {@link Single} will immediately terminate with
     * that error. In such a case, any in progress {@link Single}s will be cancelled. In order to delay error
     * termination use {@link #collectUnorderedDelayError(Iterable, int)}.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *      List<T> result = ...;// assume this is thread safe
     *      for (Future<T> ft: futures) { // Provided Futures (analogous to the Singles here)
     *          // This is an approximation, this operator does not provide any ordering guarantees for the results.
     *          result.add(ft.get());
     *      }
     *      return result;
     * }</pre>
     *
     * @param singles {@link Iterable} of {@link Single}s, results of which are to be collected.
     * @param maxConcurrency Maximum number of {@link Single}s that will be active at any point in time.
     * @param <T> Type of the result of the individual {@link Single}s
     * @return A {@link Single} producing a {@link Collection} of all values produced by the individual {@link Single}s.
     * There is no guarantee of the order of the values in the produced {@link Collection} as compared to the order of
     * {@link Single}s passed to this method.
     */
    public static <T> Single<Collection<T>> collectUnordered(Iterable<? extends Single<? extends T>> singles,
                                                             int maxConcurrency) {
        return fromIterable(singles)
                .flatMapMergeSingle(identity(), maxConcurrency)
                .collect(ArrayList::new, (ts, t) -> {
                    ts.add(t);
                    return ts;
                });
    }

    /**
     * Asynchronously collects results of the passed {@link Single}s into a single {@link Collection}. <p>
     * If any of the {@link Single}s terminate with an error, returned {@link Single} will immediately terminate with
     * that error. In such a case, any in progress {@link Single}s will be cancelled. In order to delay error
     * termination use {@link #collectUnorderedDelayError(int, Single[])}.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *      List<T> result = ...;// assume this is thread safe
     *      for (Future<T> ft: futures) { // Provided Futures (analogous to the Singles here)
     *          // This is an approximation, this operator does not provide any ordering guarantees for the results.
     *          result.add(ft.get());
     *      }
     *      return result;
     * }</pre>
     *
     * @param maxConcurrency Maximum number of {@link Single}s that will be active at any point in time.
     * @param singles {@link Single}s, results of which are to be collected.
     * @param <T> Type of the result of the individual {@link Single}s
     * @return A {@link Single} producing a {@link Collection} of all values produced by the individual {@link Single}s.
     * There is no guarantee of the order of the values in the produced {@link Collection} as compared to the order of
     * {@link Single}s passed to this method.
     */
    @SafeVarargs
    public static <T> Single<Collection<T>> collectUnordered(int maxConcurrency, Single<? extends T>... singles) {
        return from(singles).<T>flatMapMergeSingle(identity(), maxConcurrency)
                .collect(() -> new ArrayList<>(singles.length), (ts, t) -> {
                    ts.add(t);
                    return ts;
                });
    }

    /**
     * Asynchronously collects results of individual {@link Single}s returned by the passed {@link Iterable} into a
     * single {@link Collection}. <p>
     * This will actively subscribe to a limited number of {@link Single}s concurrently, in order to alter the defaults,
     * {@link #collectUnorderedDelayError(Iterable, int)}. <p>
     * If any of the {@link Single}s terminate with an error, returned {@link Single} will wait for termination till all
     * the other {@link Single}s have been subscribed and terminated. If it is expected for the returned {@link Single}
     * to terminate on the first failing {@link Single}, {@link #collectUnordered(Iterable)} should be used.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *      List<T> result = ...;// assume this is thread safe
     *      List<Throwable> errors = ...;  // assume this is thread safe
     *      for (Future<T> ft: futures) { // Provided Futures (analogous to the Singles here)
     *             // This is an approximation, this operator does not provide any ordering guarantees for the results.
     *             try {
     *                result.add(ft.get());
     *             } catch(Throwable t) {
     *                errors.add(t);
     *             }
     *      }
     *     if (errors.isEmpty()) {
     *         return rResults;
     *     }
     *     createAndThrowACompositeException(errors);
     * }</pre>
     *
     * @param singles {@link Iterable} of {@link Single}s, results of which are to be collected.
     * @param <T> Type of the result of the individual {@link Single}s
     * @return A {@link Single} producing a {@link Collection} of all values produced by the individual {@link Single}s.
     * There is no guarantee of the order of the values in the produced {@link Collection} as compared to the order of
     * {@link Single}s passed to this method.
     */
    public static <T> Single<Collection<T>> collectUnorderedDelayError(
            Iterable<? extends Single<? extends T>> singles) {
        return fromIterable(singles).flatMapMergeSingleDelayError(identity()).collect(ArrayList::new, (ts, t) -> {
            ts.add(t);
            return ts;
        });
    }

    /**
     * Asynchronously collects results of the passed {@link Single}s into a single {@link Collection}. <p>
     * This will actively subscribe to a limited number of {@link Single}s concurrently, in order to alter the defaults,
     * {@link #collectUnordered(int, Single[])}. <p>
     * If any of the {@link Single}s terminate with an error, returned {@link Single} will wait for termination till all
     * the other {@link Single}s have been subscribed and terminated. If it is expected for the returned {@link Single}
     * to terminate on the first failing {@link Single}, {@link #collectUnordered(Single[])} should be used.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *      List<T> result = ...;// assume this is thread safe
     *      List<Throwable> errors = ...;  // assume this is thread safe
     *      for (Future<T> ft: futures) { // Provided Futures (analogous to the Singles here)
     *              // This is an approximation, this operator does not provide any ordering guarantees for the results.
     *             try {
     *                result.add(ft.get());
     *             } catch(Throwable t) {
     *                errors.add(t);
     *             }
     *      }
     *     if (errors.isEmpty()) {
     *         return rResults;
     *     }
     *     createAndThrowACompositeException(errors);
     * }</pre>
     *
     * @param singles {@link Single}s, results of which are to be collected.
     * @param <T> Type of the result of the individual {@link Single}s
     * @return A {@link Single} producing a {@link Collection} of all values produced by the individual {@link Single}s.
     * There is no guarantee of the order of the values in the produced {@link Collection} as compared to the order of
     * {@link Single}s passed to this method.
     */
    @SafeVarargs
    public static <T> Single<Collection<T>> collectUnorderedDelayError(Single<? extends T>... singles) {
        return from(singles).<T>flatMapMergeSingleDelayError(identity())
                .collect(() -> new ArrayList<>(singles.length), (ts, t) -> {
                    ts.add(t);
                    return ts;
                });
    }

    /**
     * Asynchronously collects results of individual {@link Single}s returned by the passed {@link Iterable} into a
     * single {@link Collection}. <p>
     * If any of the {@link Single}s terminate with an error, returned {@link Single} will wait for termination till all
     * the other {@link Single}s have been subscribed and terminated. If it is expected for the returned {@link Single}
     * to terminate on the first failing {@link Single}, {@link #collectUnordered(Iterable, int)} should be used.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *      List<T> result = ...;// assume this is thread safe
     *      List<Throwable> errors = ...;  // assume this is thread safe
     *      for (Future<T> ft: futures) { // Provided Futures (analogous to the Singles here)
     *             // This is an approximation, this operator does not provide any ordering guarantees for the results.
     *             try {
     *                result.add(ft.get());
     *             } catch(Throwable t) {
     *                errors.add(t);
     *             }
     *      }
     *     if (errors.isEmpty()) {
     *         return rResults;
     *     }
     *     createAndThrowACompositeException(errors);
     * }</pre>
     *
     * @param singles {@link Iterable} of {@link Single}s, results of which are to be collected.
     * @param maxConcurrency Maximum number of {@link Single}s that will be active at any point in time.
     * @param <T> Type of the result of the individual {@link Single}s
     * @return A {@link Single} producing a {@link Collection} of all values produced by the individual {@link Single}s.
     * There is no guarantee of the order of the values in the produced {@link Collection} as compared to the order of
     * {@link Single}s passed to this method.
     */
    public static <T> Single<Collection<T>> collectUnorderedDelayError(Iterable<? extends Single<? extends T>> singles,
                                                                       int maxConcurrency) {
        return fromIterable(singles).flatMapMergeSingleDelayError(identity(), maxConcurrency)
                .collect(ArrayList::new, (ts, t) -> {
                    ts.add(t);
                    return ts;
                });
    }

    /**
     * Asynchronously collects results of the passed {@link Single}s into a single {@link Collection}. <p>
     * If any of the {@link Single}s terminate with an error, returned {@link Single} will wait for termination till all
     * the other {@link Single}s have been subscribed and terminated. If it is expected for the returned {@link Single}
     * to terminate on the first failing {@link Single}, {@link #collectUnordered(Iterable, int)} should be used.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *      List<T> result = ...;// assume this is thread safe
     *      List<Throwable> errors = ...;  // assume this is thread safe
     *      for (Future<T> ft: futures) { // Provided Futures (analogous to the Singles here)
     *             // This is an approximation, this operator does not provide any ordering guarantees for the results.
     *             try {
     *                result.add(ft.get());
     *             } catch(Throwable t) {
     *                errors.add(t);
     *             }
     *      }
     *     if (errors.isEmpty()) {
     *         return rResults;
     *     }
     *     createAndThrowACompositeException(errors);
     * }</pre>
     *
     * @param maxConcurrency Maximum number of {@link Single}s that will be active at any point in time.
     * @param singles {@link Single}s, results of which are to be collected.
     * @param <T> Type of the result of the individual {@link Single}s
     * @return A {@link Single} producing a {@link Collection} of all values produced by the individual {@link Single}s.
     * There is no guarantee of the order of the values in the produced {@link Collection} as compared to the order of
     * {@link Single}s passed to this method.
     */
    @SafeVarargs
    public static <T> Single<Collection<T>> collectUnorderedDelayError(int maxConcurrency,
                                                                       Single<? extends T>... singles) {
        return from(singles).<T>flatMapMergeSingleDelayError(identity(), maxConcurrency)
                .collect(() -> new ArrayList<>(singles.length), (ts, t) -> {
                    ts.add(t);
                    return ts;
                });
    }

    /**
     * Convert from a {@link CompletionStage} to a {@link Single}.
     * <p>
     * A best effort is made to propagate {@link Cancellable#cancel()} to the {@link CompletionStage}. Cancellation for
     * {@link CompletionStage} implementations will result in exceptional completion and invoke user
     * callbacks. If there is any blocking code involved in the cancellation process (including invoking user callbacks)
     * you should investigate if using an {@link Executor} is appropriate.
     * @param stage The {@link CompletionStage} to convert.
     * @param <T> The data type the {@link CompletionStage} provides when complete.
     * @return A {@link Single} that derives results from {@link CompletionStage}.
     */
    public static <T> Single<T> fromStage(CompletionStage<? extends T> stage) {
        return new CompletionStageToSingle<>(stage);
    }

    /**
     * Creates a new {@link Single} that terminates with the result (either success or error) of whichever amongst the
     * passed {@code singles} that terminates first. Therefore the result is said to be <strong>ambiguous</strong>
     * relative to which source it originated from. After the first source terminates the non-terminated sources will be
     * cancelled.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *      for (Future<T> ft: futures) { // Provided Futures (analogous to the Singles here)
     *          // This is an approximation, this operator will pick the first result from any of the futures.
     *          return ft.get();
     *      }
     * }</pre>
     *
     * @param singles {@link Single}s to subscribe to and race to propagate to the return value.
     * @param <T> Type of the result of the individual {@link Single}s
     * @return A new {@link Single} that terminates with the result (either success or error) of whichever amongst the
     * passed {@code singles} that terminates first. Therefore the result is said to be <strong>ambiguous</strong>
     * relative to which source it originated from.
     * @see <a href="http://reactivex.io/documentation/operators/amb.html">ReactiveX amb operator.</a>
     */
    @SafeVarargs
    public static <T> Single<T> amb(final Single<? extends T>... singles) {
        return new AmbSingles<>(singles);
    }

    /**
     * Creates a new {@link Single} that terminates with the result (either success or error) of whichever amongst the
     * passed {@code singles} that terminates first. Therefore the result is said to be <strong>ambiguous</strong>
     * relative to which source it originated from. After the first source terminates the non-terminated sources will be
     * cancelled.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *      for (Future<T> ft: futures) { // Provided Futures (analogous to the Singles here)
     *          // This is an approximation, this operator will pick the first result from any of the futures.
     *          return ft.get();
     *      }
     * }</pre>
     *
     * @param singles {@link Single}s to subscribe to and race to propagate to the return value.
     * @param <T> Type of the result of the individual {@link Single}s
     * @return A new {@link Single} that terminates with the result (either success or error) of whichever amongst the
     * passed {@code singles} that terminates first. Therefore the result is said to be <strong>ambiguous</strong>
     * relative to which source it originated from.
     * @see <a href="http://reactivex.io/documentation/operators/amb.html">ReactiveX amb operator.</a>
     */
    public static <T> Single<T> amb(final Iterable<Single<? extends T>> singles) {
        return new AmbSingles<>(singles);
    }

    /**
     * Creates a new {@link Single} that terminates with the result (either success or error) of whichever amongst the
     * passed {@code singles} that terminates first.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *      for (Future<T> ft: futures) { // Provided Futures (analogous to the Singles here)
     *          // This is an approximation, this operator will pick the first result from any of the futures.
     *          return ft.get();
     *      }
     * }</pre>
     *
     * @param singles {@link Single}s to subscribe to and race to propagate to the return value.
     * @param <T> Type of the result of the individual {@link Single}s
     * @return A new {@link Single} that terminates with the result (either success or error) of whichever amongst the
     * passed {@code singles} that terminates first.
     * @see <a href="http://reactivex.io/documentation/operators/amb.html">ReactiveX amb operator.</a>
     */
    @SafeVarargs
    public static <T> Single<T> anyOf(final Single<? extends T>... singles) {
        return amb(singles);
    }

    /**
     * Creates a new {@link Single} that terminates with the result (either success or error) of whichever amongst the
     * passed {@code singles} that terminates first.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *      for (Future<T> ft: futures) { // Provided Futures (analogous to the Singles here)
     *          // This is an approximation, this operator will pick the first result from any of the futures.
     *          return ft.get();
     *      }
     * }</pre>
     *
     * @param singles {@link Single}s to subscribe to and race to propagate to the return value.
     * @param <T> Type of the result of the individual {@link Single}s
     * @return A new {@link Single} that terminates with the result (either success or error) of whichever amongst the
     * passed {@code singles} that terminates first.
     * @see <a href="http://reactivex.io/documentation/operators/amb.html">ReactiveX amb operator.</a>
     */
    public static <T> Single<T> anyOf(final Iterable<Single<? extends T>> singles) {
        return amb(singles);
    }

    /**
     * Create a new {@link Single} that emits the results of a specified zipper {@link BiFunction} to items emitted
     * by {@code s1} and {@code s2}.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *      CompletableFuture<T1> f1 = ...; // s1
     *      CompletableFuture<T2> f2 = ...; // s2
     *      CompletableFuture.allOf(f1, f2).get(); // wait for all futures to complete
     *      return zipper.apply(f1.get(), f2.get());
     * }</pre>
     * @param s1 The first {@link Single} to zip.
     * @param s2 The second {@link Single} to zip.
     * @param zipper Used to combine the completed results for each item from {@code singles}.
     * @param <T1> The type for the first {@link Single}.
     * @param <T2> The type for the second {@link Single}.
     * @param <R> The result type of the zipper.
     * @return a new {@link Single} that emits the results of a specified zipper {@link BiFunction} to items emitted by
     * {@code s1} and {@code s2}.
     * @see <a href="http://reactivex.io/documentation/operators/zip.html">ReactiveX zip operator.</a>
     */
    public static <T1, T2, R> Single<R> zip(Single<? extends T1> s1, Single<? extends T2> s2,
                                            BiFunction<? super T1, ? super T2, ? extends R> zipper) {
        return SingleZipper.zip(s1, s2, zipper);
    }

    /**
     * Create a new {@link Single} that emits the results of a specified zipper {@link BiFunction} to items emitted
     * by {@code s1} and {@code s2}. If any of the {@link Single}s terminate with an error, the returned {@link Single}
     * will wait for termination till all the other {@link Single}s have been subscribed and terminated, and then
     * terminate with the first error.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *      CompletableFuture<T1> f1 = ...; // s1
     *      CompletableFuture<T2> f2 = ...; // s2
     *      CompletableFuture.allOf(f1, f2).get(); // wait for all futures to complete
     *      return zipper.apply(f1.get(), f2.get());
     * }</pre>
     * @param s1 The first {@link Single} to zip.
     * @param s2 The second {@link Single} to zip.
     * @param zipper Used to combine the completed results for each item from {@code singles}.
     * @param <T1> The type for the first {@link Single}.
     * @param <T2> The type for the second {@link Single}.
     * @param <R> The result type of the zipper.
     * @return a new {@link Single} that emits the results of a specified zipper {@link BiFunction} to items emitted by
     * {@code s1} and {@code s2}.
     * @see <a href="http://reactivex.io/documentation/operators/zip.html">ReactiveX zip operator.</a>
     */
    public static <T1, T2, R> Single<R> zipDelayError(Single<? extends T1> s1, Single<? extends T2> s2,
                                                      BiFunction<? super T1, ? super T2, ? extends R> zipper) {
        return SingleZipper.zipDelayError(s1, s2, zipper);
    }

    /**
     * Create a new {@link Single} that emits the results of a specified zipper {@link Function3} to items emitted by
     * {@code s1}, {@code s2}, and {@code s3}.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *      CompletableFuture<T1> f1 = ...; // s1
     *      CompletableFuture<T2> f2 = ...; // s2
     *      CompletableFuture<T3> f3 = ...; // s3
     *      CompletableFuture.allOf(f1, f2, f3).get(); // wait for all futures to complete
     *      return zipper.apply(f1.get(), f2.get(), f3.get());
     * }</pre>
     * @param s1 The first {@link Single} to zip.
     * @param s2 The second {@link Single} to zip.
     * @param s3 The third {@link Single} to zip.
     * @param zipper Used to combine the completed results for each item from {@code singles}.
     * @param <T1> The type for the first {@link Single}.
     * @param <T2> The type for the second {@link Single}.
     * @param <T3> The type for the third {@link Single}.
     * @param <R> The result type of the zipper.
     * @return a new {@link Single} that emits the results of a specified zipper {@link Function3} to items emitted by
     * {@code s1}, {@code s2}, and {@code s3}.
     * @see <a href="http://reactivex.io/documentation/operators/zip.html">ReactiveX zip operator.</a>
     */
    public static <T1, T2, T3, R> Single<R> zip(
            Single<? extends T1> s1, Single<? extends T2> s2, Single<? extends T3> s3,
            Function3<? super T1, ? super T2, ? super T3, ? extends R> zipper) {
        return SingleZipper.zip(s1, s2, s3, zipper);
    }

    /**
     * Create a new {@link Single} that emits the results of a specified zipper {@link Function3} to items emitted by
     * {@code s1}, {@code s2}, and {@code s3}. If any of the {@link Single}s terminate with an error, the returned
     * {@link Single} will wait for termination till all the other {@link Single}s have been subscribed and terminated,
     * and then terminate with the first error.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *      CompletableFuture<T1> f1 = ...; // s1
     *      CompletableFuture<T2> f2 = ...; // s2
     *      CompletableFuture<T3> f3 = ...; // s3
     *      CompletableFuture.allOf(f1, f2, f3).get(); // wait for all futures to complete
     *      return zipper.apply(f1.get(), f2.get(), f3.get());
     * }</pre>
     * @param s1 The first {@link Single} to zip.
     * @param s2 The second {@link Single} to zip.
     * @param s3 The third {@link Single} to zip.
     * @param zipper Used to combine the completed results for each item from {@code singles}.
     * @param <T1> The type for the first {@link Single}.
     * @param <T2> The type for the second {@link Single}.
     * @param <T3> The type for the third {@link Single}.
     * @param <R> The result type of the zipper.
     * @return a new {@link Single} that emits the results of a specified zipper {@link Function3} to items emitted by
     * {@code s1}, {@code s2}, and {@code s3}.
     * @see <a href="http://reactivex.io/documentation/operators/zip.html">ReactiveX zip operator.</a>
     */
    public static <T1, T2, T3, R> Single<R> zipDelayError(
            Single<? extends T1> s1, Single<? extends T2> s2, Single<? extends T3> s3,
            Function3<? super T1, ? super T2, ? super T3, ? extends R> zipper) {
        return SingleZipper.zipDelayError(s1, s2, s3, zipper);
    }

    /**
     * Create a new {@link Single} that emits the results of a specified zipper {@link Function4} to items emitted by
     * {@code s1}, {@code s2}, {@code s3}, and {@code s4}.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *      CompletableFuture<T1> f1 = ...; // s1
     *      CompletableFuture<T2> f2 = ...; // s2
     *      CompletableFuture<T3> f3 = ...; // s3
     *      CompletableFuture<T4> f4 = ...; // s3
     *      CompletableFuture.allOf(f1, f2, f3, f4).get(); // wait for all futures to complete
     *      return zipper.apply(f1.get(), f2.get(), f3.get(), f4.get());
     * }</pre>
     * @param s1 The first {@link Single} to zip.
     * @param s2 The second {@link Single} to zip.
     * @param s3 The third {@link Single} to zip.
     * @param s4 The fourth {@link Single} to zip.
     * @param zipper Used to combine the completed results for each item from {@code singles}.
     * @param <T1> The type for the first {@link Single}.
     * @param <T2> The type for the second {@link Single}.
     * @param <T3> The type for the third {@link Single}.
     * @param <T4> The type for the fourth {@link Single}.
     * @param <R> The result type of the zipper.
     * @return a new {@link Single} that emits the results of a specified zipper {@link Function4} to items emitted by
     * {@code s1}, {@code s2}, {@code s3}, and {@code s4}.
     * @see <a href="http://reactivex.io/documentation/operators/zip.html">ReactiveX zip operator.</a>
     */
    public static <T1, T2, T3, T4, R> Single<R> zip(
            Single<? extends T1> s1, Single<? extends T2> s2, Single<? extends T3> s3, Single<? extends T4> s4,
            Function4<? super T1, ? super T2, ? super T3, ? super T4, ? extends R> zipper) {
        return SingleZipper.zip(s1, s2, s3, s4, zipper);
    }

    /**
     * Create a new {@link Single} that emits the results of a specified zipper {@link Function4} to items emitted by
     * {@code s1}, {@code s2}, {@code s3}, and {@code s4}. If any of the {@link Single}s terminate with an error, the
     * returned {@link Single}  will wait for termination till all the other {@link Single}s have been subscribed and
     * terminated, and then terminate with the first error.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *      CompletableFuture<T1> f1 = ...; // s1
     *      CompletableFuture<T2> f2 = ...; // s2
     *      CompletableFuture<T3> f3 = ...; // s3
     *      CompletableFuture<T4> f4 = ...; // s3
     *      CompletableFuture.allOf(f1, f2, f3, f4).get(); // wait for all futures to complete
     *      return zipper.apply(f1.get(), f2.get(), f3.get(), f4.get());
     * }</pre>
     * @param s1 The first {@link Single} to zip.
     * @param s2 The second {@link Single} to zip.
     * @param s3 The third {@link Single} to zip.
     * @param s4 The fourth {@link Single} to zip.
     * @param zipper Used to combine the completed results for each item from {@code singles}.
     * @param <T1> The type for the first {@link Single}.
     * @param <T2> The type for the second {@link Single}.
     * @param <T3> The type for the third {@link Single}.
     * @param <T4> The type for the fourth {@link Single}.
     * @param <R> The result type of the zipper.
     * @return a new {@link Single} that emits the results of a specified zipper {@link Function4} to items emitted by
     * {@code s1}, {@code s2}, {@code s3}, and {@code s4}.
     * @see <a href="http://reactivex.io/documentation/operators/zip.html">ReactiveX zip operator.</a>
     */
    public static <T1, T2, T3, T4, R> Single<R> zipDelayError(
            Single<? extends T1> s1, Single<? extends T2> s2, Single<? extends T3> s3, Single<? extends T4> s4,
            Function4<? super T1, ? super T2, ? super T3, ? super T4, ? extends R> zipper) {
        return SingleZipper.zipDelayError(s1, s2, s3, s4, zipper);
    }

    /**
     * Create a new {@link Single} that emits the results of a specified zipper {@link Function} to items emitted by
     * {@code singles}.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *      Function<? super CompletableFuture<?>[], ? extends R> zipper = ...;
     *      CompletableFuture<?>[] futures = ...; // Provided Futures (analogous to the Singles here)
     *      CompletableFuture.allOf(futures).get(); // wait for all futures to complete
     *      return zipper.apply(futures);
     * }</pre>
     * @param zipper Used to combine the completed results for each item from {@code singles}.
     * @param singles The collection of {@link Single}s that when complete provides the results to "zip" (aka combine)
     * together.
     * @param <R> The result type of the zipper.
     * @return a new {@link Single} that emits the results of a specified zipper {@link Function} to items emitted by
     * {@code singles}.
     * @see <a href="http://reactivex.io/documentation/operators/zip.html">ReactiveX zip operator.</a>
     */
    public static <R> Single<R> zip(Function<? super Object[], ? extends R> zipper, Single<?>... singles) {
        return SingleZipper.zip(zipper, singles);
    }

    /**
     * Create a new {@link Single} that emits the results of a specified zipper {@link Function} to items emitted by
     * {@code singles}. If any of the {@link Single}s terminate with an error, the returned {@link Single} will wait for
     * termination till all the other {@link Single}s have been subscribed and terminated, and then terminate with the
     * first error.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *      Function<? super CompletableFuture<?>[], ? extends R> zipper = ...;
     *      CompletableFuture<?>[] futures = ...; // Provided Futures (analogous to the Singles here)
     *      CompletableFuture.allOf(futures).get(); // wait for all futures to complete
     *      return zipper.apply(futures);
     * }</pre>
     * @param zipper Used to combine the completed results for each item from {@code singles}.
     * @param singles The collection of {@link Single}s that when complete provides the results to "zip" (aka combine)
     * together.
     * @param <R> The result type of the zipper.
     * @return a new {@link Single} that emits the results of a specified zipper {@link Function} to items emitted by
     * {@code singles}.
     * @see <a href="http://reactivex.io/documentation/operators/zip.html">ReactiveX zip operator.</a>
     */
    public static <R> Single<R> zipDelayError(Function<? super Object[], ? extends R> zipper, Single<?>... singles) {
        return SingleZipper.zipDelayError(zipper, singles);
    }

    // </editor-fold>

    // <editor-fold desc="Internal Methods">

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
     * Subscribes to this {@link Single} and returns the {@link AsyncContextMap} associated for this subscribe
     * operation.
     *
     * @param subscriber the subscriber.
     * @param provider {@link AsyncContextProvider} to use.
     * @return {@link AsyncContextMap} for this subscribe operation.
     */
    final AsyncContextMap subscribeAndReturnContext(Subscriber<? super T> subscriber, AsyncContextProvider provider) {
        final AsyncContextMap contextMap = contextForSubscribe(provider);
        subscribeWithContext(subscriber, provider, contextMap);
        return contextMap;
    }

    /**
     * Delegate subscribe calls in an operator chain. This method is used by operators to subscribe to the upstream
     * source.
     * @param subscriber the subscriber.
     * @param contextMap the {@link AsyncContextMap} to use for this {@link Subscriber}.
     * @param contextProvider the {@link AsyncContextProvider} used to wrap any objects to preserve
     * {@link AsyncContextMap}.
     */
    final void delegateSubscribe(Subscriber<? super T> subscriber,
                                 AsyncContextMap contextMap, AsyncContextProvider contextProvider) {
        handleSubscribe(subscriber, contextMap, contextProvider);
    }

    private void subscribeWithContext(Subscriber<? super T> subscriber,
                                      AsyncContextProvider contextProvider, AsyncContextMap contextMap) {
        requireNonNull(subscriber);
        Subscriber<? super T> wrapped = contextProvider.wrapCancellable(subscriber, contextMap);
        if (contextProvider.contextMap() == contextMap) {
            // No need to wrap as we sharing the AsyncContext
            handleSubscribe(wrapped, contextMap, contextProvider);
        } else {
            // Ensure that AsyncContext used for handleSubscribe() is the contextMap for the subscribe()
            contextProvider.wrapRunnable(() -> handleSubscribe(wrapped, contextMap, contextProvider), contextMap).run();
        }
    }

    /**
     * Override for {@link #handleSubscribe(SingleSource.Subscriber)} to perform context wrapping.
     * <p>
     * This method wraps the passed {@link Subscriber}. Operators that do not wish to wrap the passed {@link Subscriber}
     * can override this method and omit the wrapping.
     * @param subscriber the subscriber.
     * @param contextMap the {@link AsyncContextMap} to use for this {@link Subscriber}.
     * @param contextProvider the {@link AsyncContextProvider} used to wrap any objects to preserve
     * {@link AsyncContextMap}.
     */
    void handleSubscribe(Subscriber<? super T> subscriber,
                         AsyncContextMap contextMap, AsyncContextProvider contextProvider) {
        try {
            Subscriber<? super T> wrapped = contextProvider.wrapSingleSubscriber(subscriber, contextMap);
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

    // </editor-fold>
}
