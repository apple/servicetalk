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

import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.BlockingIterator;
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.api.BufferStrategy.Accumulator;
import io.servicetalk.concurrent.internal.SignalOffloader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.EmptyPublisher.emptyPublisher;
import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.NeverPublisher.neverPublisher;
import static io.servicetalk.concurrent.api.PublisherDoOnUtils.doOnCancelSupplier;
import static io.servicetalk.concurrent.api.PublisherDoOnUtils.doOnCompleteSupplier;
import static io.servicetalk.concurrent.api.PublisherDoOnUtils.doOnErrorSupplier;
import static io.servicetalk.concurrent.api.PublisherDoOnUtils.doOnNextSupplier;
import static io.servicetalk.concurrent.api.PublisherDoOnUtils.doOnRequestSupplier;
import static io.servicetalk.concurrent.api.PublisherDoOnUtils.doOnSubscribeSupplier;
import static io.servicetalk.concurrent.internal.SignalOffloaders.newOffloaderFor;
import static io.servicetalk.concurrent.internal.SubscriberUtils.deliverErrorFromSource;
import static io.servicetalk.utils.internal.DurationUtils.toNanos;
import static java.util.Objects.requireNonNull;

/**
 * An asynchronous computation that produces 0, 1 or more elements and may or may not terminate successfully or with
 * an error.
 *
 * <h2>How to subscribe?</h2>
 *
 * This class does not provide a way to subscribe using a {@link PublisherSource.Subscriber} as such calls are
 * ambiguous about the intent whether the subscribe is part of the same source (a.k.a an operator) or it is a terminal
 * subscribe. If it is required to subscribe to a source, then a {@link SourceAdapters source adapter} can be used to
 * convert to a {@link PublisherSource}.
 *
 * @param <T> Type of items emitted.
 */
public abstract class Publisher<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(Publisher.class);

    static {
        AsyncContext.autoEnable();
    }

    /**
     * New instance.
     */
    protected Publisher() {
    }

    //
    // Operators Begin
    //

    /**
     * Transforms elements emitted by this {@link Publisher} into a different type.
     * <p>
     * This method provides a data transformation in sequential programming similar to:
     * <pre>{@code
     *     List<R> results = ...;
     *     for (T t : resultOfThisPublisher()) {
     *         results.add(mapper.apply(t));
     *     }
     *     return results;
     * }</pre>
     *
     * @param mapper Function to transform each item emitted by this {@link Publisher}.
     * @param <R> Type of the items emitted by the returned {@link Publisher}.
     * @return A {@link Publisher} that transforms elements emitted by this {@link Publisher} into a different type.
     *
     * @see <a href="http://reactivex.io/documentation/operators/map.html">ReactiveX map operator.</a>
     */
    public final <R> Publisher<R> map(Function<? super T, ? extends R> mapper) {
        return new MapPublisher<>(this, mapper);
    }

    /**
     * Filters items emitted by this {@link Publisher}.
     * <p>
     * This method provides a data transformation in sequential programming similar to:
     * <pre>{@code
     *     List<T> results = ...;
     *     for (T t : resultOfThisPublisher()) {
     *         if (predicate.test(t)) {
     *             results.add(t);
     *         }
     *     }
     *     return results;
     * }</pre>
     *
     * @param predicate for the filter.
     * @return A {@link Publisher} that only emits the items that pass the {@code predicate}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/filter.html">ReactiveX filter operator.</a>
     */
    public final Publisher<T> filter(Predicate<? super T> predicate) {
        return new FilterPublisher<>(this, predicate);
    }

    /**
     * Apply a {@link BiFunction} to each {@link Subscriber#onNext(Object)} emitted by this {@link Publisher} and an
     * accumulated state.
     * <p>
     * This method provides a data transformation in sequential programming similar to:
     * <pre>{@code
     *     List<R> results = ...;
     *     R state = initial.get();
     *     for (T t : resultOfThisPublisher()) {
     *       state = accumulator.apply(state, t);
     *       results.add(state);
     *     }
     *     return results;
     * }</pre>
     * @param initial Invoked on each {@link PublisherSource#subscribe(Subscriber)} and provides the initial state for
     * each {@link Subscriber}.
     * @param accumulator Used to accumulate the current state in combination with each
     * {@link Subscriber#onNext(Object)} from this {@link Publisher}.
     * @param <R> Type of the items emitted by the returned {@link Publisher}.
     * @return A {@link Publisher} that transforms elements emitted by this {@link Publisher} into a different type.
     * @see <a href="http://reactivex.io/documentation/operators/scan.html">ReactiveX scan operator.</a>
     */
    public final <R> Publisher<R> scanWith(Supplier<R> initial, BiFunction<R, ? super T, R> accumulator) {
        return new ScanWithPublisher<>(this, initial, accumulator);
    }

    /**
     * Apply a function to each {@link Subscriber#onNext(Object)} emitted by this {@link Publisher} as well as
     * optionally concat one {@link Subscriber#onNext(Object)} signal before the terminal signal is emitted downstream.
     * <p>
     * This method provides a data transformation in sequential programming similar to:
     * <pre>{@code
     *     List<R> results = ...;
     *     ScanWithMapper<T, R> mapper = mapperSupplier.get();
     *     try {
     *       for (T t : resultOfThisPublisher()) {
     *         results.add(mapper.mapOnNext(t));
     *       }
     *     } catch (Throwable cause) {
     *       if (mapTerminal.test(state)) {
     *         results.add(mapper.mapOnError(cause));
     *         return;
     *       }
     *       throw cause;
     *     }
     *     if (mapTerminal.test(state)) {
     *       results.add(mapper.mapOnComplete());
     *     }
     *     return results;
     * }</pre>
     * @param mapperSupplier Invoked on each {@link PublisherSource#subscribe(Subscriber)} and maintains any necessary
     * state for the mapping/accumulation for each {@link Subscriber}.
     * @param <R> Type of the items emitted by the returned {@link Publisher}.
     * @return A {@link Publisher} that transforms elements emitted by this {@link Publisher} into a different type.
     * @see <a href="http://reactivex.io/documentation/operators/scan.html">ReactiveX scan operator.</a>
     */
    public final <R> Publisher<R> scanWith(Supplier<? extends ScanWithMapper<? super T, ? extends R>> mapperSupplier) {
        return new ScanWithPublisher<>(this, mapperSupplier);
    }

    /**
     * Apply a function to each {@link Subscriber#onNext(Object)} emitted by this {@link Publisher} as well as
     * optionally concat one {@link Subscriber#onNext(Object)} signal before the terminal signal is emitted downstream.
     * Additionally the {@link ScanWithLifetimeMapper#afterFinally()} method will be invoked on terminal or cancel
     * signals which enables cleanup of state (if required). This provides a similar lifetime management as
     * {@link TerminalSignalConsumer}.
     *
     * <p>
     * This method provides a data transformation in sequential programming similar to:
     * <pre>{@code
     *     List<R> results = ...;
     *     ScanWithLifetimeMapper<T, R> mapper = mapperSupplier.get();
     *     try {
     *         try {
     *           for (T t : resultOfThisPublisher()) {
     *             results.add(mapper.mapOnNext(t));
     *           }
     *         } catch (Throwable cause) {
     *           if (mapTerminal.test(state)) {
     *             results.add(mapper.mapOnError(cause));
     *             return;
     *           }
     *           throw cause;
     *         }
     *         if (mapTerminal.test(state)) {
     *           results.add(mapper.mapOnComplete());
     *         }
     *     } finally {
     *       mapper.afterFinally();
     *     }
     *     return results;
     * }</pre>
     * @param mapperSupplier Invoked on each {@link PublisherSource#subscribe(Subscriber)} and maintains any necessary
     * state for the mapping/accumulation for each {@link Subscriber}.
     * @param <R> Type of the items emitted by the returned {@link Publisher}.
     * @return A {@link Publisher} that transforms elements emitted by this {@link Publisher} into a different type.
     * @see <a href="http://reactivex.io/documentation/operators/scan.html">ReactiveX scan operator.</a>
     */
    public final <R> Publisher<R> scanWithLifetime(
            Supplier<? extends ScanWithLifetimeMapper<? super T, ? extends R>> mapperSupplier) {
        return new ScanWithLifetimePublisher<>(this, mapperSupplier);
    }

    /**
     * Transform errors emitted on this {@link Publisher} into a {@link Subscriber#onComplete()} signal
     * (e.g. swallows the error).
     * <p>
     * This method provides a data transformation in sequential programming similar to:
     * <pre>{@code
     *     List<T> results = resultOfThisPublisher();
     *     try {
     *         terminalOfThisPublisher();
     *     } catch (Throwable cause) {
     *         // ignored
     *     }
     *     return results;
     * }</pre>
     * @return A {@link Publisher} which transform errors emitted on this {@link Publisher} into a
     * {@link Subscriber#onComplete()} signal (e.g. swallows the error).
     * @see <a href="http://reactivex.io/documentation/operators/catch.html">ReactiveX catch operator.</a>
     */
    public final Publisher<T> onErrorComplete() {
        return onErrorComplete(t -> true);
    }

    /**
     * Transform errors emitted on this {@link Publisher} which match {@code type} into a
     * {@link Subscriber#onComplete()} signal (e.g. swallows the error).
     * <p>
     * This method provides a data transformation in sequential programming similar to:
     * <pre>{@code
     *     List<T> results = resultOfThisPublisher();
     *     try {
     *         terminalOfThisPublisher();
     *     } catch (Throwable cause) {
     *         if (!type.isInstance(cause)) {
     *           throw cause;
     *         }
     *     }
     *     return results;
     * }</pre>
     * @param type The {@link Throwable} type to filter, operator will not apply for errors which don't match this type.
     * @param <E> The {@link Throwable} type.
     * @return A {@link Publisher} which transform errors emitted on this {@link Publisher} which match {@code type}
     * into a {@link Subscriber#onComplete()} signal (e.g. swallows the error).
     * @see <a href="http://reactivex.io/documentation/operators/catch.html">ReactiveX catch operator.</a>
     */
    public final <E extends Throwable> Publisher<T> onErrorComplete(Class<E> type) {
        return onErrorComplete(type::isInstance);
    }

    /**
     * Transform errors emitted on this {@link Publisher} which match {@code predicate} into a
     * {@link Subscriber#onComplete()} signal (e.g. swallows the error).
     * <p>
     * This method provides a data transformation in sequential programming similar to:
     * <pre>{@code
     *     List<T> results = resultOfThisPublisher();
     *     try {
     *         terminalOfThisPublisher();
     *     } catch (Throwable cause) {
     *         if (!predicate.test(cause)) {
     *           throw cause;
     *         }
     *     }
     *     return results;
     * }</pre>
     * @param predicate returns {@code true} if the {@link Throwable} should be transformed to and
     * {@link Subscriber#onComplete()} signal. Returns {@code false} to propagate the error.
     * @return A {@link Publisher} which transform errors emitted on this {@link Publisher} which match
     * {@code predicate} into a {@link Subscriber#onComplete()} signal (e.g. swallows the error).
     * @see <a href="http://reactivex.io/documentation/operators/catch.html">ReactiveX catch operator.</a>
     */
    public final Publisher<T> onErrorComplete(Predicate<? super Throwable> predicate) {
        return new OnErrorCompletePublisher<>(this, predicate);
    }

    /**
     * Transform errors emitted on this {@link Publisher} into {@link Subscriber#onNext(Object)} then
     * {@link Subscriber#onComplete()} signals (e.g. swallows the error).
     * <p>
     * This method provides a data transformation in sequential programming similar to:
     * <pre>{@code
     *     List<T> results = resultOfThisPublisher();
     *     try {
     *         terminalOfThisPublisher();
     *     } catch (Throwable cause) {
     *         results.add(itemSupplier.apply(cause));
     *     }
     *     return results;
     * }</pre>
     * @param itemSupplier returns the element to emit to {@link Subscriber#onNext(Object)}.
     * @return A {@link Publisher} which transform errors emitted on this {@link Publisher} into
     * {@link Subscriber#onNext(Object)} then {@link Subscriber#onComplete()} signals (e.g. swallows the error).
     * @see <a href="http://reactivex.io/documentation/operators/catch.html">ReactiveX catch operator.</a>
     */
    public final Publisher<T> onErrorReturn(Function<? super Throwable, ? extends T> itemSupplier) {
        return onErrorReturn(t -> true, itemSupplier);
    }

    /**
     * Transform errors emitted on this {@link Publisher} which match {@code type} into
     * {@link Subscriber#onNext(Object)} then {@link Subscriber#onComplete()} signals (e.g. swallows the error).
     * <p>
     * This method provides a data transformation in sequential programming similar to:
     * <pre>{@code
     *     List<T> results = resultOfThisPublisher();
     *     try {
     *         terminalOfThisPublisher();
     *     } catch (Throwable cause) {
     *         if (!type.isInstance(cause)) {
     *           throw cause;
     *         }
     *         results.add(itemSupplier.apply(cause));
     *     }
     *     return results;
     * }</pre>
     * @param type The {@link Throwable} type to filter, operator will not apply for errors which don't match this type.
     * @param itemSupplier returns the element to emit to {@link Subscriber#onNext(Object)}.
     * @param <E> The type of {@link Throwable} to transform.
     * @return A {@link Publisher} which transform errors emitted on this {@link Publisher} into
     * {@link Subscriber#onNext(Object)} then {@link Subscriber#onComplete()} signals (e.g. swallows the error).
     * @see <a href="http://reactivex.io/documentation/operators/catch.html">ReactiveX catch operator.</a>
     */
    public final <E extends Throwable> Publisher<T> onErrorReturn(
            Class<E> type, Function<? super E, ? extends T> itemSupplier) {
        @SuppressWarnings("unchecked")
        final Function<Throwable, ? extends T> rawSupplier = (Function<Throwable, ? extends T>) itemSupplier;
        return onErrorReturn(type::isInstance, rawSupplier);
    }

    /**
     * Transform errors emitted on this {@link Publisher} which match {@code predicate} into
     * {@link Subscriber#onNext(Object)} then {@link Subscriber#onComplete()} signals (e.g. swallows the error).
     * <p>
     * This method provides a data transformation in sequential programming similar to:
     * <pre>{@code
     *     List<T> results = resultOfThisPublisher();
     *     try {
     *         terminalOfThisPublisher();
     *     } catch (Throwable cause) {
     *         if (!predicate.test(cause)) {
     *           throw cause;
     *         }
     *         results.add(itemSupplier.apply(cause));
     *     }
     *     return result;
     * }</pre>
     * @param predicate returns {@code true} if the {@link Throwable} should be transformed to
     * {@link Subscriber#onNext(Object)} then {@link Subscriber#onComplete()} signals. Returns {@code false} to
     * propagate the error.
     * @param itemSupplier returns the element to emit to {@link Subscriber#onNext(Object)}.
     * @return A {@link Publisher} which transform errors emitted on this {@link Publisher} into
     * {@link Subscriber#onNext(Object)} then {@link Subscriber#onComplete()} signals (e.g. swallows the error).
     * @see <a href="http://reactivex.io/documentation/operators/catch.html">ReactiveX catch operator.</a>
     */
    public final Publisher<T> onErrorReturn(Predicate<? super Throwable> predicate,
                                            Function<? super Throwable, ? extends T> itemSupplier) {
        requireNonNull(itemSupplier);
        return onErrorResume(predicate, t -> Publisher.from(itemSupplier.apply(t)));
    }

    /**
     * Transform errors emitted on this {@link Publisher} into a different error.
     * <p>
     * This method provides a data transformation in sequential programming similar to:
     * <pre>{@code
     *     List<T> results = resultOfThisPublisher();
     *     try {
     *         terminalOfThisPublisher();
     *     } catch (Throwable cause) {
     *         throw mapper.apply(cause);
     *     }
     *     return results;
     * }</pre>
     * @param mapper returns the error used to terminate the returned {@link Publisher}.
     * @return A {@link Publisher} which transform errors emitted on this {@link Publisher} into a different error.
     * @see <a href="http://reactivex.io/documentation/operators/catch.html">ReactiveX catch operator.</a>
     */
    public final Publisher<T> onErrorMap(Function<? super Throwable, ? extends Throwable> mapper) {
        return onErrorMap(t -> true, mapper);
    }

    /**
     * Transform errors emitted on this {@link Publisher} which match {@code type} into a different error.
     * <p>
     * This method provides a data transformation in sequential programming similar to:
     * <pre>{@code
     *     List<T> results = resultOfThisPublisher();
     *     try {
     *         terminalOfThisPublisher();
     *     } catch (Throwable cause) {
     *         if (type.isInstance(cause)) {
     *           throw mapper.apply(cause);
     *         } else {
     *           throw cause;
     *         }
     *     }
     *     return results;
     * }</pre>
     * @param type The {@link Throwable} type to filter, operator will not apply for errors which don't match this type.
     * @param mapper returns the error used to terminate the returned {@link Publisher}.
     * @param <E> The type of {@link Throwable} to transform.
     * @return A {@link Publisher} which transform errors emitted on this {@link Publisher} into a different error.
     * @see <a href="http://reactivex.io/documentation/operators/catch.html">ReactiveX catch operator.</a>
     */
    public final <E extends Throwable> Publisher<T> onErrorMap(
            Class<E> type, Function<? super E, ? extends Throwable> mapper) {
        @SuppressWarnings("unchecked")
        final Function<Throwable, Throwable> rawMapper = (Function<Throwable, Throwable>) mapper;
        return onErrorMap(type::isInstance, rawMapper);
    }

    /**
     * Transform errors emitted on this {@link Publisher} which match {@code predicate} into a different error.
     * <p>
     * This method provides a data transformation in sequential programming similar to:
     * <pre>{@code
     *     List<T> results = resultOfThisPublisher();
     *     try {
     *         terminalOfThisPublisher();
     *     } catch (Throwable cause) {
     *         if (predicate.test(cause)) {
     *           throw mapper.apply(cause);
     *         } else {
     *           throw cause;
     *         }
     *     }
     *     return results;
     * }</pre>
     * @param predicate returns {@code true} if the {@link Throwable} should be transformed via {@code mapper}. Returns
     * {@code false} to propagate the original error.
     * @param mapper returns the error used to terminate the returned {@link Publisher}.
     * @return A {@link Publisher} which transform errors emitted on this {@link Publisher} into a different error.
     * @see <a href="http://reactivex.io/documentation/operators/catch.html">ReactiveX catch operator.</a>
     */
    public final Publisher<T> onErrorMap(Predicate<? super Throwable> predicate,
                                         Function<? super Throwable, ? extends Throwable> mapper) {
        return new OnErrorMapPublisher<>(this, predicate, mapper);
    }

    /**
     * Recover from any error emitted by this {@link Publisher} by using another {@link Publisher} provided by the
     * passed {@code nextFactory}.
     * <p>
     * This method provides similar capabilities to a try/catch block in sequential programming:
     * <pre>{@code
     *     List<T> results;
     *     try {
     *         results = resultOfThisPublisher();
     *     } catch (Throwable cause) {
     *         // Note that nextFactory returning a error Publisher is like re-throwing (nextFactory shouldn't throw).
     *         results = nextFactory.apply(cause);
     *     }
     *     return results;
     * }</pre>
     *
     * @param nextFactory Returns the next {@link Publisher}, when this {@link Publisher} emits an error.
     * @return A {@link Publisher} that recovers from an error from this {@link Publisher} by using another
     * {@link Publisher} provided by the passed {@code nextFactory}.
     * @see <a href="http://reactivex.io/documentation/operators/catch.html">ReactiveX catch operator.</a>
     */
    public final Publisher<T> onErrorResume(Function<? super Throwable, ? extends Publisher<? extends T>> nextFactory) {
        return onErrorResume(t -> true, nextFactory);
    }

    /**
     * Recover from errors emitted by this {@link Publisher} which match {@code type} by using another {@link Publisher}
     * provided by the passed {@code nextFactory}.
     * <p>
     * This method provides similar capabilities to a try/catch block in sequential programming:
     * <pre>{@code
     *     List<T> results;
     *     try {
     *         results = resultOfThisPublisher();
     *     } catch (Throwable cause) {
     *         if (type.isInstance(cause)) {
     *           // Note that nextFactory returning a error Publisher is like re-throwing (nextFactory shouldn't throw).
     *           results = nextFactory.apply(cause);
     *         } else {
     *           throw cause;
     *         }
     *     }
     *     return results;
     * }</pre>
     *
     * @param type The {@link Throwable} type to filter, operator will not apply for errors which don't match this type.
     * @param nextFactory Returns the next {@link Publisher}, when this {@link Publisher} emits an error.
     * @param <E> The type of {@link Throwable} to transform.
     * @return A {@link Publisher} that recovers from an error from this {@link Publisher} by using another
     * {@link Publisher} provided by the passed {@code nextFactory}.
     * @see <a href="http://reactivex.io/documentation/operators/catch.html">ReactiveX catch operator.</a>
     */
    public final <E extends Throwable> Publisher<T> onErrorResume(
            Class<E> type, Function<? super E, ? extends Publisher<? extends T>> nextFactory) {
        @SuppressWarnings("unchecked")
        Function<Throwable, ? extends Publisher<? extends T>> rawNextFactory =
                (Function<Throwable, ? extends Publisher<? extends T>>) nextFactory;
        return onErrorResume(type::isInstance, rawNextFactory);
    }

    /**
     * Recover from errors emitted by this {@link Publisher} which match {@code predicate} by using another
     * {@link Publisher} provided by the passed {@code nextFactory}.
     * <p>
     * This method provides similar capabilities to a try/catch block in sequential programming:
     * <pre>{@code
     *     List<T> results;
     *     try {
     *         results = resultOfThisPublisher();
     *     } catch (Throwable cause) {
     *         if (predicate.test(cause)) {
     *           // Note that nextFactory returning a error Publisher is like re-throwing (nextFactory shouldn't throw).
     *           results = nextFactory.apply(cause);
     *         } else {
     *           throw cause;
     *         }
     *     }
     *     return results;
     * }</pre>
     *
     * @param predicate returns {@code true} if the {@link Throwable} should be transformed via {@code nextFactory}.
     * Returns {@code false} to propagate the original error.
     * @param nextFactory Returns the next {@link Publisher}, when this {@link Publisher} emits an error.
     * @return A {@link Publisher} that recovers from an error from this {@link Publisher} by using another
     * {@link Publisher} provided by the passed {@code nextFactory}.
     * @see <a href="http://reactivex.io/documentation/operators/catch.html">ReactiveX catch operator.</a>
     */
    public final Publisher<T> onErrorResume(Predicate<? super Throwable> predicate,
                                            Function<? super Throwable, ? extends Publisher<? extends T>> nextFactory) {
        return new OnErrorResumePublisher<>(this, predicate, nextFactory);
    }

    /**
     * Map each element of this {@link Publisher} into a {@link Publisher}&lt;{@link R}&gt; and flatten all signals
     * emitted from each mapped {@link Publisher}&lt;{@link R}&gt; into the returned
     * {@link Publisher}&lt;{@link R}&gt;.
     * <p>
     * To control the amount of concurrent processing done by this operator see {@link #flatMapMerge(Function, int)}.
     * <p>
     * This method is similar to {@link #map(Function)} but the result is an asynchronous stream, and provides a data
     * transformation in sequential programming similar to:
     * <pre>{@code
     *     ExecutorService e = ...;
     *     List<Future<List<R>>> futures = ...; // assume this is thread safe
     *     for (T t : resultOfThisPublisher()) {
     *         // Note that flatMap process results in parallel.
     *         futures.add(e.submit(() -> {
     *             return mapper.apply(t); // Asynchronous result is flatten into a value by this operator.
     *         }));
     *     }
     *     List<R> results = new ArrayList<>(futures.size());
     *     // This is an approximation, this operator does not provide any ordering guarantees for the results.
     *     for (Future<List<R>> future : futures) {
     *         List<R> rList = future.get(); // Throws if the processing for this item failed.
     *         results.addAll(rList);
     *     }
     *     return results;
     * }</pre>
     * @param mapper Convert each item emitted by this {@link Publisher} into another {@link Publisher}.
     * each mapped {@link Publisher}.
     * @param <R> The type of mapped {@link Publisher}.
     * @return A new {@link Publisher} which flattens the emissions from all mapped {@link Publisher}s.
     * @see <a href="http://reactivex.io/documentation/operators/flatmap.html">ReactiveX flatMap operator.</a>
     */
    public final <R> Publisher<R> flatMapMerge(Function<? super T, ? extends Publisher<? extends R>> mapper) {
        return new PublisherFlatMapMerge<>(this, mapper, false);
    }

    /**
     * Map each element of this {@link Publisher} into a {@link Publisher}&lt;{@link R}&gt; and flatten all signals
     * emitted from each mapped {@link Publisher}&lt;{@link R}&gt; into the returned
     * {@link Publisher}&lt;{@link R}&gt;.
     * <p>
     * This method is similar to {@link #map(Function)} but the result is an asynchronous stream, and provides a data
     * transformation in sequential programming similar to:
     * <pre>{@code
     *     ExecutorService e = ...;
     *     List<Future<List<R>>> futures = ...; // assume this is thread safe
     *     for (T t : resultOfThisPublisher()) {
     *         // Note that flatMap process results in parallel.
     *         futures.add(e.submit(() -> {
     *             return mapper.apply(t); // Asynchronous result is flatten into a value by this operator.
     *         }));
     *     }
     *     List<R> results = new ArrayList<>(futures.size());
     *     // This is an approximation, this operator does not provide any ordering guarantees for the results.
     *     for (Future<List<R>> future : futures) {
     *         List<R> rList = future.get(); // Throws if the processing for this item failed.
     *         results.addAll(rList);
     *     }
     *     return results;
     * }</pre>
     * @param mapper Convert each item emitted by this {@link Publisher} into another {@link Publisher}.
     * @param maxConcurrency Maximum amount of outstanding upstream {@link Subscription#request(long) demand}.
     * @param <R> The type of mapped {@link Publisher}.
     * @return A new {@link Publisher} which flattens the emissions from all mapped {@link Publisher}s.
     * @see <a href="http://reactivex.io/documentation/operators/flatmap.html">ReactiveX flatMap operator.</a>
     */
    public final <R> Publisher<R> flatMapMerge(Function<? super T, ? extends Publisher<? extends R>> mapper,
                                               int maxConcurrency) {
        return new PublisherFlatMapMerge<>(this, mapper, false, maxConcurrency);
    }

    /**
     * Map each element of this {@link Publisher} into a {@link Publisher}&lt;{@link R}&gt; and flatten all signals
     * emitted from each mapped {@link Publisher}&lt;{@link R}&gt; into the returned
     * {@link Publisher}&lt;{@link R}&gt;.
     * <p>
     * This is the same as {@link #flatMapMerge(Function)} just that if any mapped {@link Publisher} returned by
     * {@code mapper}, terminates with an error, the returned {@link Publisher} will not immediately terminate. Instead,
     * it will wait for this {@link Publisher} and all mapped {@link Publisher}s to terminate and then terminate the
     * returned {@link Publisher} with all errors emitted by the mapped {@link Publisher}s.
     * <p>
     * To control the amount of concurrent processing done by this operator see
     * {@link #flatMapMergeDelayError(Function, int)}.
     * <p>
     * This method is similar to {@link #map(Function)} but the result is an asynchronous stream, and provides a data
     * transformation in sequential programming similar to:
     * <pre>{@code
     *     Executor e = ...;
     *     List<T> tResults = resultOfThisPublisher();
     *     List<R> rResults = ...; // assume this is thread safe
     *     List<Throwable> errors = ...;  // assume this is thread safe
     *     CountDownLatch latch =  new CountDownLatch(tResults.size());
     *     for (T t : resultOfThisPublisher()) {
     *         // Note that flatMap process results in parallel.
     *         e.execute(() -> {
     *             try {
     *                 List<R> rList = mapper.apply(t); // Asynchronous result is flatten into a value by this operator.
     *                 rResults.addAll(rList);
     *             } catch (Throwable cause) {
     *                 errors.add(cause);  // Asynchronous error is flatten into an error by this operator.
     *             } finally {
     *                 latch.countdown();
     *             }
     *         });
     *     }
     *     latch.await();
     *     if (errors.isEmpty()) {
     *         return rResults;
     *     }
     *     createAndThrowACompositeException(errors);
     * }</pre>
     * @param mapper Convert each item emitted by this {@link Publisher} into another {@link Publisher}.
     * each mapped {@link Publisher}.
     * @param <R> The type of mapped {@link Publisher}.
     * @return A new {@link Publisher} which flattens the emissions from all mapped {@link Publisher}s.
     * @see <a href="http://reactivex.io/documentation/operators/flatmap.html">ReactiveX flatMap operator.</a>
     */
    public final <R> Publisher<R> flatMapMergeDelayError(Function<? super T, ? extends Publisher<? extends R>> mapper) {
        return new PublisherFlatMapMerge<>(this, mapper, true);
    }

    /**
     * Map each element of this {@link Publisher} into a {@link Publisher}&lt;{@link R}&gt; and flatten all signals
     * emitted from each mapped {@link Publisher}&lt;{@link R}&gt; into the returned
     * {@link Publisher}&lt;{@link R}&gt;.
     * <p>
     * This is the same as {@link #flatMapMerge(Function)} just that if any mapped {@link Publisher} returned by
     * {@code mapper}, terminates with an error, the returned {@link Publisher} will not immediately terminate. Instead,
     * it will wait for this {@link Publisher} and all mapped {@link Publisher}s to terminate and then terminate the
     * returned {@link Publisher} with all errors emitted by the mapped {@link Publisher}s.
     * <p>
     * This method is similar to {@link #map(Function)} but the result is an asynchronous stream, and provides a data
     * transformation in sequential programming similar to:
     * <pre>{@code
     *     Executor e = ...;
     *     List<T> tResults = resultOfThisPublisher();
     *     List<R> rResults = ...; // assume this is thread safe
     *     List<Throwable> errors = ...;  // assume this is thread safe
     *     CountDownLatch latch =  new CountDownLatch(tResults.size());
     *     for (T t : resultOfThisPublisher()) {
     *         // Note that flatMap process results in parallel.
     *         e.execute(() -> {
     *             try {
     *                 List<R> rList = mapper.apply(t); // Asynchronous result is flatten into a value by this operator.
     *                 rResults.addAll(rList);
     *             } catch (Throwable cause) {
     *                 errors.add(cause);  // Asynchronous error is flatten into an error by this operator.
     *             } finally {
     *                 latch.countdown();
     *             }
     *         });
     *     }
     *     latch.await();
     *     if (errors.isEmpty()) {
     *         return rResults;
     *     }
     *     createAndThrowACompositeException(errors);
     * }</pre>
     * @param mapper Convert each item emitted by this {@link Publisher} into another {@link Publisher}.
     * @param maxConcurrency Maximum amount of outstanding upstream {@link Subscription#request(long) demand}.
     * @param <R> The type of mapped {@link Publisher}.
     * @return A new {@link Publisher} which flattens the emissions from all mapped {@link Publisher}s.
     * @see <a href="http://reactivex.io/documentation/operators/flatmap.html">ReactiveX flatMap operator.</a>
     */
    public final <R> Publisher<R> flatMapMergeDelayError(Function<? super T, ? extends Publisher<? extends R>> mapper,
                                                         int maxConcurrency) {
        return new PublisherFlatMapMerge<>(this, mapper, true, maxConcurrency);
    }

    /**
     * Map each element of this {@link Publisher} into a {@link Publisher}&lt;{@link R}&gt; and flatten all signals
     * emitted from each mapped {@link Publisher}&lt;{@link R}&gt; into the returned
     * {@link Publisher}&lt;{@link R}&gt;.
     * <p>
     * This is the same as {@link #flatMapMerge(Function)} just that if any mapped {@link Publisher} returned by
     * {@code mapper}, terminates with an error, the returned {@link Publisher} will not immediately terminate. Instead,
     * it will wait for this {@link Publisher} and all mapped {@link Publisher}s to terminate and then terminate the
     * returned {@link Publisher} with all errors emitted by the mapped {@link Publisher}s.
     * <p>
     * This method is similar to {@link #map(Function)} but the result is an asynchronous stream, and provides a data
     * transformation in sequential programming similar to:
     * <pre>{@code
     *     Executor e = ...;
     *     List<T> tResults = resultOfThisPublisher();
     *     List<R> rResults = ...; // assume this is thread safe
     *     List<Throwable> errors = ...;  // assume this is thread safe
     *     CountDownLatch latch =  new CountDownLatch(tResults.size());
     *     for (T t : resultOfThisPublisher()) {
     *         // Note that flatMap process results in parallel.
     *         e.execute(() -> {
     *             try {
     *                 List<R> rList = mapper.apply(t); // Asynchronous result is flatten into a value by this operator.
     *                 rResults.addAll(rList);
     *             } catch (Throwable cause) {
     *                 errors.add(cause);  // Asynchronous error is flatten into an error by this operator.
     *             } finally {
     *                 latch.countdown();
     *             }
     *         });
     *     }
     *     latch.await();
     *     if (errors.isEmpty()) {
     *         return rResults;
     *     }
     *     createAndThrowACompositeException(errors);
     * }</pre>
     * @param mapper Convert each item emitted by this {@link Publisher} into another {@link Publisher}.
     * @param maxConcurrency Maximum amount of outstanding upstream {@link Subscription#request(long) demand}.
     * @param maxDelayedErrorsHint The maximum amount of errors that will be queued. After this point exceptions maybe
     * discarded to reduce memory consumption.
     * @param <R> The type of mapped {@link Publisher}.
     * @return A new {@link Publisher} which flattens the emissions from all mapped {@link Publisher}s.
     * @see <a href="http://reactivex.io/documentation/operators/flatmap.html">ReactiveX flatMap operator.</a>
     */
    public final <R> Publisher<R> flatMapMergeDelayError(Function<? super T, ? extends Publisher<? extends R>> mapper,
                                                         int maxConcurrency, int maxDelayedErrorsHint) {
        if (maxDelayedErrorsHint <= 0) {
            throw new IllegalArgumentException("maxDelayedErrorsHint " + maxDelayedErrorsHint + " (expected >0)");
        }
        return new PublisherFlatMapMerge<>(this, mapper, maxDelayedErrorsHint, maxConcurrency);
    }

    /**
     * Map each element of this {@link Publisher} into a {@link Single}&lt;{@link R}&gt; and flatten all signals
     * emitted from each mapped {@link Single}&lt;{@link R}&gt; into the returned
     * {@link Publisher}&lt;{@link R}&gt;.
     * <p>
     * To control the amount of concurrent processing done by this operator see
     * {@link #flatMapMergeSingle(Function, int)}.
     * <p>
     * This method is similar to {@link #map(Function)} but the result is asynchronous, and provides a data
     * transformation in sequential programming similar to:
     * <pre>{@code
     *     ExecutorService e = ...;
     *     List<Future<R>> futures = ...; // assume this is thread safe
     *     for (T t : resultOfThisPublisher()) {
     *         // Note that flatMap process results in parallel.
     *         futures.add(e.submit(() -> {
     *             return mapper.apply(t); // Asynchronous result is flatten into a value by this operator.
     *         }));
     *     }
     *     List<R> results = new ArrayList<>(futures.size());
     *     // This is an approximation, this operator does not provide any ordering guarantees for the results.
     *     for (Future<R> future : futures) {
     *         R r = future.get(); // Throws if the processing for this item failed.
     *         results.add(r);
     *     }
     *     return results;
     * }</pre>
     *
     * @param mapper {@link Function} to convert each item emitted by this {@link Publisher} into a {@link Single}.
     * @param <R> Type of items emitted by the returned {@link Publisher}.
     * @return A new {@link Publisher} that emits all items emitted by each single produced by {@code mapper}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/flatmap.html">ReactiveX flatMap operator.</a>
     * @see #flatMapMergeSingle(Function, int)
     */
    public final <R> Publisher<R> flatMapMergeSingle(Function<? super T, ? extends Single<? extends R>> mapper) {
        return new PublisherFlatMapSingle<>(this, mapper, false);
    }

    /**
     * Map each element of this {@link Publisher} into a {@link Single}&lt;{@link R}&gt; and flatten all signals
     * emitted from each mapped {@link Single}&lt;{@link R}&gt; into the returned
     * {@link Publisher}&lt;{@link R}&gt;.
     * <p>
     * This method is similar to {@link #map(Function)} but the result is asynchronous, and provides a data
     * transformation in sequential programming similar to:
     * <pre>{@code
     *     ExecutorService e = ...;
     *     List<Future<R>> futures = ...; // assume this is thread safe
     *     for (T t : resultOfThisPublisher()) {
     *         // Note that flatMap process results in parallel.
     *         futures.add(e.submit(() -> {
     *             return mapper.apply(t); // Asynchronous result is flatten into a value by this operator.
     *         }));
     *     }
     *     List<R> results = new ArrayList<>(futures.size());
     *     // This is an approximation, this operator does not provide any ordering guarantees for the results.
     *     for (Future<R> future : futures) {
     *         R r = future.get(); // Throws if the processing for this item failed.
     *         results.add(r);
     *     }
     *     return results;
     * }</pre>
     *
     * @param mapper {@link Function} to convert each item emitted by this {@link Publisher} into a {@link Single}.
     * @param maxConcurrency Maximum active {@link Single}s at any time.
     * Even if the number of items requested by a {@link Subscriber} is more than this number, this will never request
     * more than this number at any point.
     * @param <R> Type of items emitted by the returned {@link Publisher}.
     * @return A new {@link Publisher} that emits all items emitted by each single produced by {@code mapper}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/flatmap.html">ReactiveX flatMap operator.</a>
     */
    public final <R> Publisher<R> flatMapMergeSingle(Function<? super T, ? extends Single<? extends R>> mapper,
                                                     int maxConcurrency) {
        return new PublisherFlatMapSingle<>(this, mapper, false, maxConcurrency);
    }

    /**
     * Map each element of this {@link Publisher} into a {@link Single}&lt;{@link R}&gt; and flatten all signals
     * emitted from each mapped {@link Single}&lt;{@link R}&gt; into the returned
     * {@link Publisher}&lt;{@link R}&gt;.
     * <p>
     * The behavior is the same as {@link #flatMapMergeSingle(Function, int)} with the exception that if any
     * {@link Single} returned by {@code mapper}, terminates with an error, the returned {@link Publisher} will not
     * immediately terminate. Instead, it will wait for this {@link Publisher} and all {@link Single}s to terminate and
     * then terminate the returned {@link Publisher} with all errors emitted by the {@link Single}s produced by the
     * {@code mapper}.
     * <p>
     * To control the amount of concurrent processing done by this operator see
     * {@link #flatMapMergeSingleDelayError(Function, int)}.
     * <p>
     * This method is similar to {@link #map(Function)} but the result is asynchronous, and provides a data
     * transformation in sequential programming similar to:
     * <pre>{@code
     *     Executor e = ...;
     *     List<T> tResults = resultOfThisPublisher();
     *     List<R> rResults = ...; // assume this is thread safe
     *     List<Throwable> errors = ...;  // assume this is thread safe
     *     CountDownLatch latch =  new CountDownLatch(tResults.size());
     *     for (T t : tResults) {
     *         // Note that flatMap process results in parallel.
     *         e.execute(() -> {
     *             try {
     *                 R r = mapper.apply(t); // Asynchronous result is flatten into a value by this operator.
     *                 rResults.add(r);
     *             } catch (Throwable cause) {
     *                 errors.add(cause);  // Asynchronous error is flatten into an error by this operator.
     *             } finally {
     *                 latch.countdown();
     *             }
     *         });
     *     }
     *     latch.await();
     *     if (errors.isEmpty()) {
     *         return rResults;
     *     }
     *     createAndThrowACompositeException(errors);
     * }</pre>
     *
     * @param mapper {@link Function} to convert each item emitted by this {@link Publisher} into a {@link Single}.
     * @param <R> Type of items emitted by the returned {@link Publisher}.
     * @return A new {@link Publisher} that emits all items emitted by each single produced by {@code mapper}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX merge operator.</a>
     * @see #flatMapMergeSingleDelayError(Function, int)
     */
    public final <R> Publisher<R> flatMapMergeSingleDelayError(
            Function<? super T, ? extends Single<? extends R>> mapper) {
        return new PublisherFlatMapSingle<>(this, mapper, true);
    }

    /**
     * Map each element of this {@link Publisher} into a {@link Single}&lt;{@link R}&gt; and flatten all signals
     * emitted from each mapped {@link Single}&lt;{@link R}&gt; into the returned
     * {@link Publisher}&lt;{@link R}&gt;.
     * <p>
     * The behavior is the same as {@link #flatMapMergeSingle(Function, int)} with the exception that if any
     * {@link Single} returned by {@code mapper}, terminates with an error, the returned {@link Publisher} will not
     * immediately terminate. Instead, it will wait for this {@link Publisher} and all {@link Single}s to terminate and
     * then terminate the returned {@link Publisher} with all errors emitted by the {@link Single}s produced by the
     * {@code mapper}.
     * <p>
     * This method is similar to {@link #map(Function)} but the result is asynchronous, and provides a data
     * transformation in sequential programming similar to:
     * <pre>{@code
     *     Executor e = ...;
     *     List<T> tResults = resultOfThisPublisher();
     *     List<R> rResults = ...; // assume this is thread safe
     *     List<Throwable> errors = ...;  // assume this is thread safe
     *     CountDownLatch latch =  new CountDownLatch(tResults.size());
     *     for (T t : tResults) {
     *         // Note that flatMap process results in parallel.
     *         e.execute(() -> {
     *             try {
     *                 R r = mapper.apply(t); // Asynchronous result is flatten into a value by this operator.
     *                 rResults.add(r);
     *             } catch (Throwable cause) {
     *                 errors.add(cause);  // Asynchronous error is flatten into an error by this operator.
     *             } finally {
     *                 latch.countdown();
     *             }
     *         });
     *     }
     *     latch.await();
     *     if (errors.isEmpty()) {
     *         return rResults;
     *     }
     *     createAndThrowACompositeException(errors);
     * }</pre>
     *
     * @param mapper {@link Function} to convert each item emitted by this {@link Publisher} into a {@link Single}.
     * @param maxConcurrency Maximum active {@link Single}s at any time.
     * Even if the number of items requested by a {@link Subscriber} is more than this number,
     * this will never request more than this number at any point.
     * @param <R> Type of items emitted by the returned {@link Publisher}.
     * @return A new {@link Publisher} that emits all items emitted by each single produced by {@code mapper}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX merge operator.</a>
     */
    public final <R> Publisher<R> flatMapMergeSingleDelayError(
            Function<? super T, ? extends Single<? extends R>> mapper, int maxConcurrency) {
        return new PublisherFlatMapSingle<>(this, mapper, true, maxConcurrency);
    }

    /**
     * Map each element of this {@link Publisher} into a {@link Single}&lt;{@link R}&gt; and flatten all signals
     * emitted from each mapped {@link Single}&lt;{@link R}&gt; into the returned
     * {@link Publisher}&lt;{@link R}&gt;.
     * <p>
     * The behavior is the same as {@link #flatMapMergeSingle(Function, int)} with the exception that if any
     * {@link Single} returned by {@code mapper}, terminates with an error, the returned {@link Publisher} will not
     * immediately terminate. Instead, it will wait for this {@link Publisher} and all {@link Single}s to terminate and
     * then terminate the returned {@link Publisher} with all errors emitted by the {@link Single}s produced by the
     * {@code mapper}.
     * <p>
     * This method is similar to {@link #map(Function)} but the result is asynchronous, and provides a data
     * transformation in sequential programming similar to:
     * <pre>{@code
     *     Executor e = ...;
     *     List<T> tResults = resultOfThisPublisher();
     *     List<R> rResults = ...; // assume this is thread safe
     *     List<Throwable> errors = ...;  // assume this is thread safe
     *     CountDownLatch latch =  new CountDownLatch(tResults.size());
     *     for (T t : tResults) {
     *         // Note that flatMap process results in parallel.
     *         e.execute(() -> {
     *             try {
     *                 R r = mapper.apply(t); // Asynchronous result is flatten into a value by this operator.
     *                 rResults.add(r);
     *             } catch (Throwable cause) {
     *                 errors.add(cause);  // Asynchronous error is flatten into an error by this operator.
     *             } finally {
     *                 latch.countdown();
     *             }
     *         });
     *     }
     *     latch.await();
     *     if (errors.isEmpty()) {
     *         return rResults;
     *     }
     *     createAndThrowACompositeException(errors);
     * }</pre>
     *
     * @param mapper {@link Function} to convert each item emitted by this {@link Publisher} into a {@link Single}.
     * @param maxConcurrency Maximum active {@link Single}s at any time.
     * Even if the number of items requested by a {@link Subscriber} is more than this number,
     * this will never request more than this number at any point.
     * @param maxDelayedErrorsHint The maximum amount of errors that will be queued. After this point exceptions maybe
     * discarded to reduce memory consumption.
     * @param <R> Type of items emitted by the returned {@link Publisher}.
     * @return A new {@link Publisher} that emits all items emitted by each single produced by {@code mapper}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX merge operator.</a>
     */
    public final <R> Publisher<R> flatMapMergeSingleDelayError(
            Function<? super T, ? extends Single<? extends R>> mapper, int maxConcurrency, int maxDelayedErrorsHint) {
        if (maxDelayedErrorsHint <= 0) {
            throw new IllegalArgumentException("maxDelayedErrorsHint " + maxDelayedErrorsHint + " (expected >0)");
        }
        return new PublisherFlatMapSingle<>(this, mapper, maxDelayedErrorsHint, maxConcurrency);
    }

    /**
     * Map each element of this {@link Publisher} into a {@link Completable} and flatten all signals
     * such that the returned {@link Completable} terminates when all mapped {@link Completable}s have terminated
     * successfully or any one of them has terminated with a failure.
     * <p>
     * If the returned {@link Completable} should wait for the termination of all mapped {@link Completable}s when
     * any one of them terminates with a failure, {@link #flatMapCompletableDelayError(Function)} should be used.
     * <p>
     * To control the amount of concurrent processing done by this operator see
     * {@link #flatMapCompletable(Function, int)}.
     * <p>
     * This method is similar to {@link #map(Function)} but the result is asynchronous, and provides a data
     * transformation in sequential programming similar to:
     * <pre>{@code
     *     ExecutorService e = ...;
     *     List<Future<Void>> futures = ...; // assume this is thread safe
     *     for (T t : resultOfThisPublisher()) {
     *         // Note that flatMap process results in parallel.
     *         futures.add(e.submit(() -> {
     *             return mapper.apply(t); // Asynchronous result is flatten into a value by this operator.
     *         }));
     *     }
     *     // This is an approximation, this operator does not provide any ordering guarantees for the results.
     *     for (Future<R> future : futures) {
     *         future.get(); // Throws if the processing for this item failed.
     *     }
     * }</pre>
     *
     * @param mapper {@link Function} to convert each item emitted by this {@link Publisher} into a {@link Completable}.
     * @return A new {@link Completable} that terminates successfully if all the intermediate {@link Completable}s have
     * terminated successfully or any one of them has terminated with a failure.
     *
     * @see <a href="http://reactivex.io/documentation/operators/flatmap.html">ReactiveX flatMap operator.</a>
     * @see #flatMapCompletable(Function, int)
     * @see #flatMapCompletableDelayError(Function)
     */
    public final Completable flatMapCompletable(Function<? super T, ? extends Completable> mapper) {
        return flatMapMergeSingle(t -> mapper.apply(t).toSingle()).ignoreElements();
    }

    /**
     * Map each element of this {@link Publisher} into a {@link Completable} and flatten all signals
     * such that the returned {@link Completable} terminates when all mapped {@link Completable}s have terminated
     * successfully or any one of them has terminated with a failure.
     * <p>
     * If the returned {@link Completable} should wait for the termination of all mapped {@link Completable}s when
     * any one of them terminates with a failure, {@link #flatMapCompletableDelayError(Function)} should be used.
     * <p>
     * This method is similar to {@link #map(Function)} but the result is asynchronous, and provides a data
     * transformation in sequential programming similar to:
     * <pre>{@code
     *     ExecutorService e = ...;
     *     List<Future<Void>> futures = ...; // assume this is thread safe
     *     for (T t : resultOfThisPublisher()) {
     *         // Note that flatMap process results in parallel.
     *         futures.add(e.submit(() -> {
     *             return mapper.apply(t); // Asynchronous result is flatten into a value by this operator.
     *         }));
     *     }
     *     // This is an approximation, this operator does not provide any ordering guarantees for the results.
     *     for (Future<R> future : futures) {
     *         future.get(); // Throws if the processing for this item failed.
     *     }
     * }</pre>
     *
     * @param mapper Function to convert each item emitted by this {@link Publisher} into a {@link Completable}.
     * @param maxConcurrency Maximum active {@link Completable}s at any time.
     * @return A new {@link Completable} that terminates successfully if all the intermediate {@link Completable}s have
     * terminated successfully or any one of them has terminated with a failure.
     *
     * @see <a href="http://reactivex.io/documentation/operators/flatmap.html">ReactiveX flatMap operator.</a>
     * @see #flatMapCompletable(Function)
     * @see #flatMapCompletableDelayError(Function, int)
     */
    public final Completable flatMapCompletable(Function<? super T, ? extends Completable> mapper, int maxConcurrency) {
        return flatMapMergeSingle(t -> mapper.apply(t).toSingle(), maxConcurrency).ignoreElements();
    }

    /**
     * Map each element of this {@link Publisher} into a {@link Completable} and flatten all signals
     * such that the returned {@link Completable} terminates when all mapped {@link Completable}s have terminated
     * successfully or any one of them has terminated with a failure.
     * <p>
     * If any mapped {@link Completable} terminates with an error the returned {@link Completable} will not immediately
     * terminate. Instead, it will wait for this {@link Publisher} and all mapped {@link Completable}s to terminate.
     * <p>
     * To control the amount of concurrent processing done by this operator see
     * {@link #flatMapCompletableDelayError(Function, int)}.
     * <p>
     * This method is similar to {@link #map(Function)} but the result is asynchronous, and provides a data
     * transformation in sequential programming similar to:
     * <pre>{@code
     *     Executor e = ...;
     *     List<Throwable> errors = ...;  // assume this is thread safe
     *     CountDownLatch latch =  new CountDownLatch(tResults.size());
     *     for (T t : tResults) {
     *         // Note that flatMap process results in parallel.
     *         e.execute(() -> {
     *             try {
     *                 mapper.apply(t); // Asynchronous result is flattened by this operator.
     *             } catch (Throwable cause) {
     *                 errors.add(cause);  // Asynchronous error is flatten into an error by this operator.
     *             } finally {
     *                 latch.countdown();
     *             }
     *         });
     *     }
     *     latch.await();
     *     if (!errors.isEmpty()) {
     *         createAndThrowACompositeException(errors);
     *     }
     * }</pre>
     *
     * @param mapper Function to convert each item emitted by this {@link Publisher} into a {@link Completable}.
     * @return A new {@link Completable} that terminates successfully if all the intermediate {@link Completable}s have
     * terminated successfully or any one of them has terminated with a failure.
     *
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX merge operator.</a>
     * @see #flatMapMergeSingleDelayError(Function, int)
     */
    public final Completable flatMapCompletableDelayError(Function<? super T, ? extends Completable> mapper) {
        return flatMapMergeSingleDelayError(t -> mapper.apply(t).toSingle()).ignoreElements();
    }

    /**
     * Map each element of this {@link Publisher} into a {@link Completable} and flatten all signals
     * such that the returned {@link Completable} terminates when all mapped {@link Completable}s have terminated
     * successfully or any one of them has terminated with a failure.
     * <p>
     * If any mapped {@link Completable} terminates with an error the returned {@link Completable} will not immediately
     * terminate. Instead, it will wait for this {@link Publisher} and all mapped {@link Completable}s to terminate.
     * <p>
     * This method is similar to {@link #map(Function)} but the result is asynchronous, and provides a data
     * transformation in sequential programming similar to:
     * <pre>{@code
     *     Executor e = ...;
     *     List<Throwable> errors = ...;  // assume this is thread safe
     *     CountDownLatch latch =  new CountDownLatch(tResults.size());
     *     for (T t : tResults) {
     *         // Note that flatMap process results in parallel.
     *         e.execute(() -> {
     *             try {
     *                 mapper.apply(t); // Asynchronous result is flattened by this operator.
     *             } catch (Throwable cause) {
     *                 errors.add(cause);  // Asynchronous error is flatten into an error by this operator.
     *             } finally {
     *                 latch.countdown();
     *             }
     *         });
     *     }
     *     latch.await();
     *     if (!errors.isEmpty()) {
     *         createAndThrowACompositeException(errors);
     *     }
     * }</pre>
     *
     * @param mapper Function to convert each item emitted by this {@link Publisher} into a {@link Completable}.
     * @param maxConcurrency Maximum active {@link Completable}s at any time.
     * @return A new {@link Completable} that terminates successfully if all the intermediate {@link Completable}s have
     * terminated successfully or any one of them has terminated with a failure.
     *
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX merge operator.</a>
     * @see #flatMapMergeSingleDelayError(Function, int)
     */
    public final Completable flatMapCompletableDelayError(Function<? super T, ? extends Completable> mapper,
                                                          int maxConcurrency) {
        return flatMapMergeSingleDelayError(t -> mapper.apply(t).toSingle(), maxConcurrency).ignoreElements();
    }

    /**
     * Map each element of this {@link Publisher} into a {@link Completable} and flatten all signals
     * such that the returned {@link Completable} terminates when all mapped {@link Completable}s have terminated
     * successfully or any one of them has terminated with a failure.
     * <p>
     * If any mapped {@link Completable} terminates with an error the returned {@link Completable} will not immediately
     * terminate. Instead, it will wait for this {@link Publisher} and all mapped {@link Completable}s to terminate.
     * <p>
     * This method is similar to {@link #map(Function)} but the result is asynchronous, and provides a data
     * transformation in sequential programming similar to:
     * <pre>{@code
     *     Executor e = ...;
     *     List<Throwable> errors = ...;  // assume this is thread safe
     *     CountDownLatch latch =  new CountDownLatch(tResults.size());
     *     for (T t : tResults) {
     *         // Note that flatMap process results in parallel.
     *         e.execute(() -> {
     *             try {
     *                 mapper.apply(t); // Asynchronous result is flattened by this operator.
     *             } catch (Throwable cause) {
     *                 errors.add(cause);  // Asynchronous error is flatten into an error by this operator.
     *             } finally {
     *                 latch.countdown();
     *             }
     *         });
     *     }
     *     latch.await();
     *     if (!errors.isEmpty()) {
     *         createAndThrowACompositeException(errors);
     *     }
     * }</pre>
     *
     * @param mapper Function to convert each item emitted by this {@link Publisher} into a {@link Completable}.
     * @param maxConcurrency Maximum active {@link Completable}s at any time.
     * @param maxDelayedErrorsHint The maximum amount of errors that will be queued. After this point exceptions maybe
     * discarded to reduce memory consumption.
     * @return A new {@link Completable} that terminates successfully if all the intermediate {@link Completable}s have
     * terminated successfully or any one of them has terminated with a failure.
     *
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX merge operator.</a>
     * @see #flatMapMergeSingleDelayError(Function, int)
     */
    public final Completable flatMapCompletableDelayError(Function<? super T, ? extends Completable> mapper,
                                                          int maxConcurrency, int maxDelayedErrorsHint) {
        return flatMapMergeSingleDelayError(t -> mapper.apply(t).toSingle(), maxConcurrency, maxDelayedErrorsHint)
                .ignoreElements();
    }

    /**
     * Create a {@link Publisher} that flattens each element returned by the {@link Iterable#iterator()} from
     * {@code mapper}.
     * <p>
     * The mapper {@link Function} will only be called when the previously returned {@link Iterator} has returned
     * {@code false} from {@link Iterator#hasNext()}.
     * <p>
     * This method provides similar capabilities as expanding each result into a collection and concatenating each
     * collection in sequential programming:
     * <pre>{@code
     *     List<R> results = ...;
     *     for (T t : resultOfThisPublisher()) {
     *         Iterable<? extends R> itr = mapper.apply(t);
     *         itr.forEach(results::add);
     *     }
     *     return results;
     * }</pre>
     *
     * @param mapper A {@link Function} that returns an {@link Iterable} for each element.
     * @param <R> The elements returned by the {@link Iterable}.
     * @return a {@link Publisher} that flattens each element returned by the {@link Iterable#iterator()} from
     * {@code mapper}. The results will be sequential for each {@link Iterator}, and overall for all calls to
     * {@link Iterable#iterator()}
     */
    public final <R> Publisher<R> flatMapConcatIterable(Function<? super T, ? extends Iterable<? extends R>> mapper) {
        return new PublisherConcatMapIterable<>(this, mapper);
    }

    /**
     * Invokes the {@code onSubscribe} {@link Consumer} argument when
     * {@link Subscriber#onSubscribe(PublisherSource.Subscription)} is called for {@link Subscriber}s of the returned
     * {@link Publisher}.
     * <p>
     * The order in which {@code onSubscribe} will be invoked relative to
     * {@link Subscriber#onSubscribe(PublisherSource.Subscription)} is undefined. If you need strict ordering see
     * {@link #beforeOnSubscribe(Consumer)} and {@link #afterOnSubscribe(Consumer)}.
     *
     * @param onSubscribe Invoked when {@link Subscriber#onSubscribe(PublisherSource.Subscription)} is called for
     * {@link Subscriber}s of the returned {@link Publisher}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Publisher}.
     * @see #beforeOnNext(Consumer)
     * @see #afterOnNext(Consumer)
     */
    public final Publisher<T> whenOnSubscribe(Consumer<? super Subscription> onSubscribe) {
        return beforeOnSubscribe(onSubscribe);
    }

    /**
     * Invokes the {@code onNext} {@link Consumer} argument when {@link Subscriber#onNext(Object)} is called for
     * {@link Subscriber}s of the returned {@link Publisher}.
     * <p>
     * The order in which {@code onNext} will be invoked relative to {@link Subscriber#onNext(Object)} is undefined. If
     * you need strict ordering see {@link #beforeOnNext(Consumer)} and {@link #afterOnNext(Consumer)}.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *  for (T t: resultOfThisPublisher()) {
     *      // NOTE: The order of operations here is not guaranteed by this method!
     *      processNext(t);
     *      onNext.accept(t);
     *  }
     * }</pre>
     *
     * @param onNext Invoked when {@link Subscriber#onNext(Object)} is called for {@link Subscriber}s of the returned
     * {@link Publisher}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Publisher}.
     * @see #beforeOnNext(Consumer)
     * @see #afterOnNext(Consumer)
     */
    public final Publisher<T> whenOnNext(Consumer<? super T> onNext) {
        return beforeOnNext(onNext);
    }

    /**
     * Invokes the {@code onComplete} {@link Runnable} argument when {@link Subscriber#onComplete()} is called for
     * {@link Subscriber}s of the returned {@link Publisher}.
     * <p>
     * The order in which {@code onComplete} will be invoked relative to {@link Subscriber#onComplete()} is undefined.
     * If you need strict ordering see {@link #beforeOnComplete(Runnable)} and {@link #afterOnComplete(Runnable)}.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *  List<T> results = resultOfThisPublisher();
     *  // NOTE: The order of operations here is not guaranteed by this method!
     *  onSuccess.accept(results);
     *  nextOperation(results);
     * }</pre>
     *
     * @param onComplete Invoked when {@link Subscriber#onComplete()} is called for {@link Subscriber}s of the
     * returned {@link Publisher}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Publisher}.
     * @see #beforeOnComplete(Runnable)
     * @see #afterOnComplete(Runnable)
     */
    public final Publisher<T> whenOnComplete(Runnable onComplete) {
        return beforeOnComplete(onComplete);
    }

    /**
     * Invokes the {@code onError} {@link Consumer} argument when {@link Subscriber#onError(Throwable)} is called for
     * {@link Subscriber}s of the returned {@link Publisher}.
     * <p>
     * The order in which {@code onError} will be invoked relative to {@link Subscriber#onError(Throwable)} is
     * undefined. If you need strict ordering see {@link #beforeOnError(Consumer)} and
     * {@link #afterOnError(Consumer)}.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *  try {
     *    List<T> results = resultOfThisPublisher();
     *  } catch (Throwable cause) {
     *      // NOTE: The order of operations here is not guaranteed by this method!
     *      nextOperation(cause);
     *      onError.accept(cause);
     *  }
     * }</pre>
     *
     * @param onError Invoked <strong>before</strong> {@link Subscriber#onError(Throwable)} is called for
     * {@link Subscriber}s of the returned {@link Publisher}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Publisher}.
     * @see #beforeOnError(Consumer)
     * @see #afterOnError(Consumer)
     */
    public final Publisher<T> whenOnError(Consumer<Throwable> onError) {
        return beforeOnError(onError);
    }

    /**
     * Invokes the {@code whenFinally} {@link Runnable} argument exactly once, when any of the following terminal
     * methods are called:
     * <ul>
     *     <li>{@link Subscriber#onComplete()}</li>
     *     <li>{@link Subscriber#onError(Throwable)}</li>
     *     <li>{@link Subscription#cancel()}</li>
     * </ul>
     * for {@link Subscription}s/{@link Subscriber}s of the returned {@link Publisher}.
     * <p>
     * The order in which {@code whenFinally} will be invoked relative to the above methods is undefined. If you need
     * strict ordering see {@link #beforeFinally(Runnable)} and {@link #afterFinally(Runnable)}.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *  try {
     *      List<T> results = resultOfThisPublisher();
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
     * for {@link Subscription}s/{@link Subscriber}s of the returned {@link Publisher}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Publisher}.
     * @see #beforeFinally(Runnable)
     * @see #afterFinally(Runnable)
     */
    public final Publisher<T> whenFinally(Runnable doFinally) {
        return beforeFinally(doFinally);
    }

    /**
     * Invokes the corresponding method on {@code whenFinally} {@link TerminalSignalConsumer} argument when any of the
     * following terminal methods are called:
     * <ul>
     *     <li>{@link Subscriber#onComplete()} - invokes {@link TerminalSignalConsumer#onComplete()}</li>
     *     <li>{@link Subscriber#onError(Throwable)} - invokes {@link TerminalSignalConsumer#onError(Throwable)}</li>
     *     <li>{@link Subscription#cancel()} - invokes {@link TerminalSignalConsumer#cancel()}</li>
     * </ul>
     * for {@link Subscription}s/{@link Subscriber}s of the returned {@link Publisher}.
     * <p>
     * The order in which {@code whenFinally} will be invoked relative to the above methods is undefined. If you need
     * strict ordering see {@link #beforeFinally(TerminalSignalConsumer)} and
     * {@link #afterFinally(TerminalSignalConsumer)}.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *  try {
     *      List<T> results = resultOfThisPublisher();
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
     * @param doFinally For each subscribe of the returned {@link Publisher}, at most one method of this
     * {@link TerminalSignalConsumer} will be invoked.
     * @return The new {@link Publisher}.
     * @see #beforeFinally(TerminalSignalConsumer)
     * @see #afterFinally(TerminalSignalConsumer)
     */
    public final Publisher<T> whenFinally(TerminalSignalConsumer doFinally) {
        return beforeFinally(doFinally);
    }

    /**
     * Invokes the {@code onRequest} {@link LongConsumer} argument when {@link Subscription#request(long)} is called for
     * {@link Subscription}s of the returned {@link Publisher}.
     * @param onRequest Invoked when {@link Subscription#request(long)} is called for {@link Subscription}s of the
     * returned {@link Publisher}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Publisher}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX do operator.</a>
     */
    public final Publisher<T> whenRequest(LongConsumer onRequest) {
        return beforeRequest(onRequest);
    }

    /**
     * Invokes the {@code onCancel} {@link Runnable} argument when {@link Subscription#cancel()} is called for
     * Subscriptions of the returned {@link Publisher}.
     * <p>
     * The order in which {@code whenFinally} will be invoked relative to {@link Subscription#cancel()} is undefined. If
     * you need strict ordering see {@link #beforeCancel(Runnable)} and {@link #afterCancel(Runnable)}.
     * @param onCancel Invoked when {@link Subscription#cancel()} is called for Subscriptions of the returned
     * {@link Publisher}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Publisher}.
     * @see #beforeCancel(Runnable)
     * @see #afterCancel(Runnable)
     */
    public final Publisher<T> whenCancel(Runnable onCancel) {
        return beforeCancel(onCancel);
    }

    /**
     * Creates a new {@link Publisher} that will mimic the signals of this {@link Publisher} but will terminate with a
     * {@link TimeoutException} if time {@code duration} elapses between adjacent {@link Subscriber#onNext(Object)}
     * calls. The timer starts when the returned {@link Publisher} is subscribed.
     * <p>
     * In the event of timeout any {@link Subscription} from
     * {@link Subscriber#onSubscribe(PublisherSource.Subscription)} will be {@link Subscription#cancel() cancelled} and
     * the associated {@link Subscriber} will be {@link Subscriber#onError(Throwable) terminated}.
     * @param duration The time duration which is allowed to elapse between {@link Subscriber#onNext(Object)} calls.
     * @param unit The units for {@code duration}.
     * @return a new {@link Publisher} that will mimic the signals of this {@link Publisher} but will terminate with a
     * {@link TimeoutException} if time {@code duration} elapses between {@link Subscriber#onNext(Object)} calls.
     * @see <a href="http://reactivex.io/documentation/operators/timeout.html">ReactiveX timeout operator.</a>
     * @see #timeout(long, TimeUnit, io.servicetalk.concurrent.Executor)
     */
    public final Publisher<T> timeout(long duration, TimeUnit unit) {
        return timeout(duration, unit, executor());
    }

    /**
     * Creates a new {@link Publisher} that will mimic the signals of this {@link Publisher} but will terminate with a
     * {@link TimeoutException} if time {@code duration} elapses between adjacent {@link Subscriber#onNext(Object)}
     * calls. The timer starts when the returned {@link Publisher} is subscribed.
     * <p>
     * In the event of timeout any {@link Subscription} from
     * {@link Subscriber#onSubscribe(PublisherSource.Subscription)} will be {@link Subscription#cancel() cancelled}
     * and the associated {@link Subscriber} will be {@link Subscriber#onError(Throwable) terminated}.
     * @param duration The time duration which is allowed to elapse between {@link Subscriber#onNext(Object)} calls.
     * @return a new {@link Publisher} that will mimic the signals of this {@link Publisher} but will terminate with a
     * {@link TimeoutException} if time {@code duration} elapses between {@link Subscriber#onNext(Object)} calls.
     * @see <a href="http://reactivex.io/documentation/operators/timeout.html">ReactiveX timeout operator.</a>
     * @see #timeout(long, TimeUnit, io.servicetalk.concurrent.Executor)
     */
    public final Publisher<T> timeout(Duration duration) {
        return timeout(duration, executor());
    }

    /**
     * Creates a new {@link Publisher} that will mimic the signals of this {@link Publisher} but will terminate with a
     * {@link TimeoutException} if time {@code duration} elapses between adjacent {@link Subscriber#onNext(Object)}
     * calls. The timer starts when the returned {@link Publisher} is subscribed.
     * <p>
     * In the event of timeout any {@link Subscription} from
     * {@link Subscriber#onSubscribe(PublisherSource.Subscription)} will be {@link Subscription#cancel() cancelled} and
     * the associated {@link Subscriber} will be {@link Subscriber#onError(Throwable) terminated}.
     * @param duration The time duration which is allowed to elapse between {@link Subscriber#onNext(Object)} calls.
     * @param unit The units for {@code duration}.
     * @param timeoutExecutor The {@link Executor} to use for managing the timer notifications.
     * @return a new {@link Publisher} that will mimic the signals of this {@link Publisher} but will terminate with a
     * {@link TimeoutException} if time {@code duration} elapses between {@link Subscriber#onNext(Object)} calls.
     * @see <a href="http://reactivex.io/documentation/operators/timeout.html">ReactiveX timeout operator.</a>
     */
    public final Publisher<T> timeout(long duration, TimeUnit unit,
                                      io.servicetalk.concurrent.Executor timeoutExecutor) {
        return new TimeoutPublisher<>(this, duration, unit, true, timeoutExecutor);
    }

    /**
     * Creates a new {@link Publisher} that will mimic the signals of this {@link Publisher} but will terminate with a
     * {@link TimeoutException} if time {@code duration} elapses between adjacent {@link Subscriber#onNext(Object)}
     * calls. The timer starts when the returned {@link Publisher} is subscribed.
     * <p>
     * In the event of timeout any {@link Subscription} from
     * {@link Subscriber#onSubscribe(PublisherSource.Subscription)} will be {@link Subscription#cancel() cancelled} and
     * the associated {@link Subscriber} will be {@link Subscriber#onError(Throwable) terminated}.
     * @param duration The time duration which is allowed to elapse between {@link Subscriber#onNext(Object)} calls.
     * @param timeoutExecutor The {@link Executor} to use for managing the timer notifications.
     * @return a new {@link Publisher} that will mimic the signals of this {@link Publisher} but will terminate with a
     * {@link TimeoutException} if time {@code duration} elapses between {@link Subscriber#onNext(Object)} calls.
     * @see <a href="http://reactivex.io/documentation/operators/timeout.html">ReactiveX timeout operator.</a>
     */
    public final Publisher<T> timeout(Duration duration, io.servicetalk.concurrent.Executor timeoutExecutor) {
        return timeout(toNanos(duration), TimeUnit.NANOSECONDS, timeoutExecutor);
    }

    /**
     * Creates a new {@link Publisher} that will mimic the signals of this {@link Publisher} but will terminate with a
     * {@link TimeoutException} if time {@code duration} elapses between subscribe and termination. The timer starts
     * when the returned {@link Publisher} is subscribed.
     * <p>
     * In the event of timeout any {@link Subscription} from
     * {@link Subscriber#onSubscribe(PublisherSource.Subscription)} will be {@link Subscription#cancel() cancelled} and
     * the associated {@link Subscriber} will be {@link Subscriber#onError(Throwable) terminated}.
     * @param duration The time duration during which the Publisher must complete.
     * @return a new {@link Publisher} that will mimic the signals of this {@link Publisher} but will terminate with a
     * {@link TimeoutException} if time {@code duration} elapses between subscribe and termination.
     * @see <a href="http://reactivex.io/documentation/operators/timeout.html">ReactiveX timeout operator.</a>
     */
    public final Publisher<T> timeoutTerminal(Duration duration) {
        return timeoutTerminal(duration, executor());
    }

    /**
     * Creates a new {@link Publisher} that will mimic the signals of this {@link Publisher} but will terminate with a
     * {@link TimeoutException} if time {@code duration} elapses between subscribe and termination. The timer starts
     * when the returned {@link Publisher} is subscribed.
     * <p>
     * In the event of timeout any {@link Subscription} from
     * {@link Subscriber#onSubscribe(PublisherSource.Subscription)} will be {@link Subscription#cancel() cancelled} and
     * the associated {@link Subscriber} will be {@link Subscriber#onError(Throwable) terminated}.
     * @param duration The time duration during which the Publisher must complete.
     * @param timeoutExecutor The {@link Executor} to use for managing the timer notifications.
     * @return a new {@link Publisher} that will mimic the signals of this {@link Publisher} but will terminate with a
     * {@link TimeoutException} if time {@code duration} elapses between subscribe and termination.
     * @see <a href="http://reactivex.io/documentation/operators/timeout.html">ReactiveX timeout operator.</a>
     */
    public final Publisher<T> timeoutTerminal(Duration duration, io.servicetalk.concurrent.Executor timeoutExecutor) {
        return timeoutTerminal(toNanos(duration), TimeUnit.NANOSECONDS, timeoutExecutor);
    }

    /**
     * Creates a new {@link Publisher} that will mimic the signals of this {@link Publisher} but will terminate with a
     * {@link TimeoutException} if time {@code duration} elapses between subscribe and termination. The timer starts
     * when the returned {@link Publisher} is subscribed.
     * <p>
     * In the event of timeout any {@link Subscription} from
     * {@link Subscriber#onSubscribe(PublisherSource.Subscription)} will be {@link Subscription#cancel() cancelled} and
     * the associated {@link Subscriber} will be {@link Subscriber#onError(Throwable) terminated}.
     * @param duration The time duration during which the Publisher must complete.
     * @param unit The units for {@code duration}.
     * @return a new {@link Publisher} that will mimic the signals of this {@link Publisher} but will terminate with a
     * {@link TimeoutException} if time {@code duration} elapses between subscribe and termination.
     * @see <a href="http://reactivex.io/documentation/operators/timeout.html">ReactiveX timeout operator.</a>
     */
    public final Publisher<T> timeoutTerminal(long duration, TimeUnit unit) {
        return timeoutTerminal(duration, unit, executor());
    }

    /**
     * Creates a new {@link Publisher} that will mimic the signals of this {@link Publisher} but will terminate with a
     * {@link TimeoutException} if time {@code duration} elapses between subscribe and termination. The timer starts
     * when the returned {@link Publisher} is subscribed.
     * <p>
     * In the event of timeout any {@link Subscription} from
     * {@link Subscriber#onSubscribe(PublisherSource.Subscription)} will be {@link Subscription#cancel() cancelled} and
     * the associated {@link Subscriber} will be {@link Subscriber#onError(Throwable) terminated}.
     * @param duration The time duration during which the Publisher must complete.
     * @param unit The units for {@code duration}.
     * @param timeoutExecutor The {@link Executor} to use for managing the timer notifications.
     * @return a new {@link Publisher} that will mimic the signals of this {@link Publisher} but will terminate with a
     * {@link TimeoutException} if time {@code duration} elapses between subscribe and termination.
     * @see <a href="http://reactivex.io/documentation/operators/timeout.html">ReactiveX timeout operator.</a>
     */
    public final Publisher<T> timeoutTerminal(long duration, TimeUnit unit,
                                              io.servicetalk.concurrent.Executor timeoutExecutor) {
        return new TimeoutPublisher<>(this, duration, unit, false, timeoutExecutor);
    }

    /**
     * Emits items emitted by {@code next} {@link Publisher} after {@code this} {@link Publisher} terminates
     * successfully.
     * <p>
     * This method provides a means to sequence the execution of two asynchronous sources and in sequential programming
     * is similar to:
     * <pre>{@code
     *     List<T> results = resultOfThisPublisher();
     *     results.addAll(resultOfPublisher(next));
     *     return results;
     * }</pre>
     *
     * @param next {@link Publisher}'s items that are emitted after {@code this} {@link Publisher} terminates
     * successfully.
     * @return A {@link Publisher} that emits all items from this {@link Publisher} and {@code next} {@link Publisher}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/concat.html">ReactiveX concat operator.</a>
     */
    public final Publisher<T> concat(Publisher<? extends T> next) {
        return new ConcatPublisher<>(this, next);
    }

    /**
     * Listens and emits the result of {@code next} {@link Single} after {@code this} {@link Publisher} terminates
     * successfully.
     * <p>
     * This method provides a means to sequence the execution of two asynchronous sources and in sequential programming
     * is similar to:
     * <pre>{@code
     *     List<T> results = resultOfThisPublisher();
     *     results.add(resultOfSingle(next));
     *     return results;
     * }</pre>
     *
     * @param next {@link Single}'s result that is emitted after {@code this} {@link Publisher} terminates successfully.
     * @return A {@link Publisher} that emits all items from this {@link Publisher} and the result of {@code next}
     * {@link Single}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/concat.html">ReactiveX concat operator.</a>
     */
    public final Publisher<T> concat(Single<? extends T> next) {
        return new PublisherConcatWithSingle<>(this, next);
    }

    /**
     * Listens for completion of {@code next} {@link Completable} after {@code this} {@link Publisher} terminates
     * successfully. Any error from {@code this} {@link Publisher} and {@code next} {@link Completable} is forwarded to
     * the returned {@link Publisher}.
     * <p>
     * This method provides a means to sequence the execution of two asynchronous sources and in sequential programming
     * is similar to:
     * <pre>{@code
     *     List<T> results = resultOfThisPublisher();
     *     resultOfCompletable(next);
     *     return results;
     * }</pre>
     *
     * @param next {@link Completable} to wait for completion after {@code this} {@link Publisher} terminates
     * successfully.
     * @return A {@link Publisher} that emits all items from this {@link Publisher} and then awaits successful
     * completion of {@code next} {@link Completable}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/concat.html">ReactiveX concat operator.</a>
     */
    public final Publisher<T> concat(Completable next) {
        return new PublisherConcatWithCompletable<>(this, next);
    }

    /**
     * Re-subscribes to this {@link Publisher} if an error is emitted and the passed {@link BiIntPredicate} returns
     * {@code true}.
     * <p>
     * This method provides a means to retry an operation under certain failure conditions and in sequential programming
     * is similar to:
     * <pre>{@code
     *     public List<T> execute() {
     *         List<T> results = ...;
     *         return execute(0, results);
     *     }
     *
     *     private List<T> execute(int attempts, List<T> results) {
     *         try {
     *             Iterator<T> itr = resultOfThisPublisher();
     *             while (itr.hasNext()) {
     *                 T t = itr.next(); // Any iteration with the Iterator may throw
     *                 results.add(t);
     *             }
     *             return results;
     *         } catch (Throwable cause) {
     *             if (shouldRetry.apply(attempts + 1, cause)) {
     *                 return execute(attempts + 1, results);
     *             } else {
     *                 throw cause;
     *             }
     *         }
     *     }
     * }</pre>
     *
     * @param shouldRetry {@link BiIntPredicate} that given the retry count and the most recent {@link Throwable}
     * emitted from this
     * {@link Publisher} determines if the operation should be retried.
     * @return A {@link Publisher} that emits all items from this {@link Publisher} and re-subscribes if an error is
     * emitted if the passed {@link BiIntPredicate} returned {@code true}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/retry.html">ReactiveX retry operator.</a>
     */
    public final Publisher<T> retry(BiIntPredicate<Throwable> shouldRetry) {
        return new RedoPublisher<>(this,
                (retryCount, terminalNotification) -> terminalNotification.cause() != null &&
                        shouldRetry.test(retryCount, terminalNotification.cause())
        );
    }

    /**
     * Re-subscribes to this {@link Publisher} if an error is emitted and the {@link Completable} returned by the
     * supplied {@link BiIntFunction} completes successfully. If the returned {@link Completable} emits an error, the
     * returned {@link Publisher} terminates with that error.
     * <p>
     * This method provides a means to retry an operation under certain failure conditions in an asynchronous fashion
     * and in sequential programming is similar to:
     * <pre>{@code
     *     public List<T> execute() {
     *         List<T> results = ...;
     *         return execute(0, results);
     *     }
     *
     *     private List<T> execute(int attempts, List<T> results) {
     *         try {
     *             Iterator<T> itr = resultOfThisPublisher();
     *             while (itr.hasNext()) {
     *                 T t = itr.next(); // Any iteration with the Iterator may throw
     *                 results.add(t);
     *             }
     *             return results;
     *         } catch (Throwable cause) {
     *             try {
     *                 shouldRetry.apply(attempts + 1, cause); // Either throws or completes normally
     *                 execute(attempts + 1, results);
     *             } catch (Throwable ignored) {
     *                 throw cause;
     *             }
     *         }
     *     }
     * }</pre>
     *
     * @param retryWhen {@link BiIntFunction} that given the retry count and the most recent {@link Throwable} emitted
     * from this {@link Publisher} returns a {@link Completable}. If this {@link Completable} emits an error, that error
     * is emitted from the returned {@link Publisher}, otherwise, original {@link Publisher} is re-subscribed when this
     * {@link Completable} completes.
     * @return A {@link Publisher} that emits all items from this {@link Publisher} and re-subscribes if an error is
     * emitted and {@link Completable} returned by {@link BiIntFunction} completes successfully.
     *
     * @see <a href="http://reactivex.io/documentation/operators/retry.html">ReactiveX retry operator.</a>
     */
    public final Publisher<T> retryWhen(BiIntFunction<Throwable, ? extends Completable> retryWhen) {
        return new RedoWhenPublisher<>(this, (retryCount, notification) -> {
            if (notification.cause() == null) {
                return completed();
            }
            return retryWhen.apply(retryCount, notification.cause());
        }, true);
    }

    /**
     * Re-subscribes to this {@link Publisher} when it completes and the passed {@link IntPredicate} returns
     * {@code true}.
     * <p>
     * This method provides a means to repeat an operation multiple times and in sequential programming is similar to:
     * <pre>{@code
     *     List<T> results = new ...;
     *     int i = 0;
     *     do {
     *         results.addAll(resultOfThisPublisher());
     *     } while (shouldRepeat.test(++i));
     *     return results;
     * }</pre>
     *
     * @param shouldRepeat {@link IntPredicate} that given the repeat count determines if the operation should be
     * repeated.
     * @return A {@link Publisher} that emits all items from this {@link Publisher} and re-subscribes when it completes
     * if the passed {@link IntPredicate} returns {@code true}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/repeat.html">ReactiveX repeat operator.</a>
     */
    public final Publisher<T> repeat(IntPredicate shouldRepeat) {
        return new RedoPublisher<>(this,
                (repeatCount, terminalNotification) -> terminalNotification.cause() == null &&
                        shouldRepeat.test(repeatCount)
        );
    }

    /**
     * Re-subscribes to this {@link Publisher} when it completes and the {@link Completable} returned by the supplied
     * {@link IntFunction} completes successfully. If the returned {@link Completable} emits an error, the returned
     * {@link Publisher} is completed.
     * <p>
     * This method provides a means to repeat an operation multiple times when in an asynchronous fashion and in
     * sequential programming is similar to:
     * <pre>{@code
     *     List<T> results = new ...;
     *     int i = 0;
     *     while (true) {
     *         results.addAll(resultOfThisPublisher());
     *         try {
     *             repeatWhen.apply(++i); // Either throws or completes normally
     *         } catch (Throwable cause) {
     *             break;
     *         }
     *     }
     *     return results;
     * }</pre>
     *
     * @param repeatWhen {@link IntFunction} that given the repeat count returns a {@link Completable}.
     * If this {@link Completable} emits an error repeat is terminated, otherwise, original {@link Publisher} is
     * re-subscribed when this {@link Completable} completes.
     * @return A {@link Publisher} that emits all items from this {@link Publisher} and re-subscribes if an error is
     * emitted and {@link Completable} returned by {@link IntFunction} completes successfully.
     *
     * @see <a href="http://reactivex.io/documentation/operators/retry.html">ReactiveX retry operator.</a>
     */
    public final Publisher<T> repeatWhen(IntFunction<? extends Completable> repeatWhen) {
        return new RedoWhenPublisher<>(this, (retryCount, notification) -> {
            if (notification.cause() != null) {
                return completed();
            }
            return repeatWhen.apply(retryCount);
        }, false);
    }

    /**
     * Takes at most {@code numElements} elements from {@code this} {@link Publisher}.
     * <p>
     * If no terminal event is received before receiving {@code numElements} elements, {@link Subscription} for the
     * {@link Subscriber} is cancelled.
     * <p>
     * This method provides a means to take a limited number of results from this {@link Publisher} and in sequential
     * programming is similar to:
     * <pre>{@code
     *     List<T> results = ...;
     *     int i = 0;
     *     for (T t : resultOfThisPublisher()) {
     *         if (++i > numElements) {
     *             break;
     *         }
     *         results.add(t);
     *     }
     *     return results;
     * }</pre>
     *
     * @param numElements Number of elements to take.
     * @return A {@link Publisher} that emits at most {@code numElements} elements from {@code this} {@link Publisher}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/take.html">ReactiveX take operator.</a>
     */
    public final Publisher<T> takeAtMost(long numElements) {
        return new TakeNPublisher<>(this, numElements);
    }

    /**
     * Takes elements while {@link Predicate} is {@code true} and then cancel {@link Subscription} of this
     * {@link Publisher} once it returns {@code false}.
     * <p>
     * This method provides a means to take a limited number of results from this {@link Publisher} and in sequential
     * programming is similar to:
     * <pre>{@code
     *     List<T> results = ...;
     *     for (T t : resultOfThisPublisher()) {
     *         if (!predicate.test(result)) {
     *             break;
     *         }
     *         results.add(t);
     *     }
     *     return results;
     * }</pre>
     *
     * @param predicate {@link Predicate} that is checked before emitting any item to a {@link Subscriber}.
     * If this predicate returns {@code true}, that item is emitted, else {@link Subscription} is cancelled.
     * @return A {@link Publisher} that only emits the items as long as the {@link Predicate#test(Object)} method
     * returns {@code true}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/takewhile.html">ReactiveX takeWhile operator.</a>
     */
    public final Publisher<T> takeWhile(Predicate<? super T> predicate) {
        return new TakeWhilePublisher<>(this, predicate);
    }

    /**
     * Takes elements until {@link Completable} is terminated successfully or with failure.
     * <p>
     * This method provides a means to take a limited number of results from this {@link Publisher} and in sequential
     * programming is similar to:
     * <pre>{@code
     *     List<T> results = ...;
     *     for (T t : resultOfThisPublisher()) {
     *         if (isCompleted(until)) {
     *             break;
     *         }
     *         takeResults.add(t);
     *     }
     *     return results;
     * }</pre>
     *
     * @param until {@link Completable}, termination of which, terminates the returned {@link Publisher}.
     * @return A {@link Publisher} that only emits the items till {@code until} {@link Completable} is completed.
     *
     * @see <a href="http://reactivex.io/documentation/operators/takeuntil.html">ReactiveX takeUntil operator.</a>
     */
    public final Publisher<T> takeUntil(Completable until) {
        return new TakeUntilPublisher<>(this, until);
    }

    /**
     * Splits items from this {@link Publisher} into dynamically generated {@link GroupedPublisher}s.
     * Item to group association is done by {@code keySelector} {@link Function}. If the selector selects a key which is
     * previously seen and its associated {@link Subscriber} has not yet cancelled its {@link Subscription}, this item
     * is sent to that {@link Subscriber}. Otherwise a new {@link GroupedPublisher} is created and emitted from the
     * returned {@link Publisher}.
     * <p>
     * Flow control
     * <p>
     * Multiple {@link Subscriber}s (for multiple {@link GroupedPublisher}s) request items individually from this
     * {@link Publisher}. Since, there is no way for a {@link Subscriber} to only request elements for its group,
     * elements requested by one group may end up producing items for a different group, which has not yet requested
     * enough. This will cause items to be queued per group which can not be emitted due to lack of demand. This queue
     * size can be controlled via {@link #groupBy(Function, int)}.
     * <p>
     * Cancellation
     * <p>
     * If the {@link Subscriber} of the returned {@link Publisher} cancels its {@link Subscription}, and there are no
     * active {@link GroupedPublisher}s {@link Subscriber}s then upstream will be cancelled.
     * <p>
     * {@link Subscriber}s of individual {@link GroupedPublisher}s can cancel their {@link Subscription}s at any point.
     * If any new item is emitted for the cancelled {@link GroupedPublisher}, a new {@link GroupedPublisher} will be
     * emitted from the returned {@link Publisher}. Any queued items for a cancelled {@link Subscriber} for a
     * {@link GroupedPublisher} will be discarded and hence will not be emitted if the same {@link GroupedPublisher} is
     * emitted again.
     * <p>
     * In sequential programming this is similar to the following:
     * <pre>{@code
     *     Map<Key, List<T>> results = ...;
     *     for (T t : resultOfThisPublisher()) {
     *         Key k = keySelector.apply(result);
     *         List<T> v = results.get(k);
     *         if (v == null) {
     *             v = // new List
     *             results.put(k, v);
     *         }
     *         v.add(result);
     *     }
     *     return results;
     * }</pre>
     *
     * @param keySelector {@link Function} to assign an item emitted by this {@link Publisher} to a
     * {@link GroupedPublisher}.
     * @param <Key> Type of {@link GroupedPublisher} keys.
     * @return A {@link Publisher} that emits {@link GroupedPublisher}s for new {@code key}s as emitted by
     * {@code keySelector} {@link Function}.
     * @see <a href="http://reactivex.io/documentation/operators/groupby.html">ReactiveX groupBy operator.</a>
     */
    public final <Key> Publisher<GroupedPublisher<Key, T>> groupBy(Function<? super T, ? extends Key> keySelector) {
        return groupBy(keySelector, 64);
    }

    /**
     * Splits items from this {@link Publisher} into dynamically generated {@link GroupedPublisher}s.
     * Item to group association is done by {@code keySelector} {@link Function}. If the selector selects a key which is
     * previously seen and its associated {@link Subscriber} has not yet cancelled its {@link Subscription}, this item
     * is sent to that {@link Subscriber}. Otherwise a new {@link GroupedPublisher} is created and emitted from the
     * returned {@link Publisher}.
     * <p>
     * Flow control
     * <p>
     * Multiple {@link Subscriber}s (for multiple {@link GroupedPublisher}s) request items individually from this
     * {@link Publisher}. Since, there is no way for a {@link Subscriber} to only request elements for its group,
     * elements requested by one group may end up producing items for a different group, which has not yet requested
     * enough. This will cause items to be queued per group which can not be emitted due to lack of demand. This queue
     * size can be controlled with the {@code queueLimit} argument.
     * <p>
     * Cancellation
     * <p>
     * If the {@link Subscriber} of the returned {@link Publisher} cancels its {@link Subscription}, and there are no
     * active {@link GroupedPublisher}s {@link Subscriber}s then upstream will be cancelled.
     * <p>
     * {@link Subscriber}s of individual {@link GroupedPublisher}s can cancel their {@link Subscription}s at any point.
     * If any new item is emitted for the cancelled {@link GroupedPublisher}, a new {@link GroupedPublisher} will be
     * emitted from the returned {@link Publisher}. Any queued items for a cancelled {@link Subscriber} for a
     * {@link GroupedPublisher} will be discarded and hence will not be emitted if the same {@link GroupedPublisher} is
     * emitted again.
     * <p>
     * In sequential programming this is similar to the following:
     * <pre>{@code
     *     Map<Key, List<T>> results = ...;
     *     for (T t : resultOfThisPublisher()) {
     *         Key k = keySelector.apply(result);
     *         List<T> v = results.get(k);
     *         if (v == null) {
     *             v = // new List
     *             results.put(k, v);
     *         }
     *         v.add(result);
     *     }
     *     return results;
     * }</pre>
     *
     * @param keySelector {@link Function} to assign an item emitted by this {@link Publisher} to a
     * {@link GroupedPublisher}.
     * @param queueLimit The number of elements which will be queued for each grouped {@link Subscriber} in order to
     * compensate for unequal demand. This also applies to the returned {@link Publisher} which may also have to queue
     * signals.
     * @param <Key> Type of {@link GroupedPublisher} keys.
     * @return A {@link Publisher} that emits {@link GroupedPublisher}s for new {@code key}s as emitted by
     * {@code keySelector} {@link Function}.
     * @see <a href="http://reactivex.io/documentation/operators/groupby.html">ReactiveX groupBy operator.</a>
     */
    public final <Key> Publisher<GroupedPublisher<Key, T>> groupBy(Function<? super T, ? extends Key> keySelector,
                                                                   int queueLimit) {
        return new PublisherGroupBy<>(this, keySelector, queueLimit);
    }

    /**
     * Splits items from this {@link Publisher} into dynamically generated {@link GroupedPublisher}s.
     * Item to group association is done by {@code keySelector} {@link Function}. If the selector selects a key which is
     * previously seen and its associated {@link Subscriber} has not yet cancelled its {@link Subscription}, this item
     * is sent to that {@link Subscriber}. Otherwise a new {@link GroupedPublisher} is created and emitted from the
     * returned {@link Publisher}.
     * <p>
     * Flow control
     * <p>
     * Multiple {@link Subscriber}s (for multiple {@link GroupedPublisher}s) request items individually from this
     * {@link Publisher}. Since, there is no way for a {@link Subscriber} to only request elements for its group,
     * elements requested by one group may end up producing items for a different group, which has not yet requested
     * enough. This will cause items to be queued per group which can not be emitted due to lack of demand. This queue
     * size can be controlled with the {@code queueLimit} argument.
     * <p>
     * Cancellation
     * <p>
     * If the {@link Subscriber} of the returned {@link Publisher} cancels its {@link Subscription}, and there are no
     * active {@link GroupedPublisher}s {@link Subscriber}s then upstream will be cancelled.
     * <p>
     * {@link Subscriber}s of individual {@link GroupedPublisher}s can cancel their {@link Subscription}s at any point.
     * If any new item is emitted for the cancelled {@link GroupedPublisher}, a new {@link GroupedPublisher} will be
     * emitted from the returned {@link Publisher}. Any queued items for a cancelled {@link Subscriber} for a
     * {@link GroupedPublisher} will be discarded and hence will not be emitted if the same {@link GroupedPublisher} is
     * emitted again.
     * <p>
     * In sequential programming this is similar to the following:
     * <pre>{@code
     *     Map<Key, List<T>> results = ...;
     *     for (T t : resultOfThisPublisher()) {
     *         Key k = keySelector.apply(result);
     *         List<T> v = results.get(k);
     *         if (v == null) {
     *             v = // new List
     *             results.put(k, v);
     *         }
     *         v.add(result);
     *     }
     *     return results;
     * }</pre>
     *
     * @param keySelector {@link Function} to assign an item emitted by this {@link Publisher} to a
     * {@link GroupedPublisher}.
     * @param queueLimit The number of elements which will be queued for each grouped {@link Subscriber} in order to
     * compensate for unequal demand. This also applies to the returned {@link Publisher} which may also have to queue
     * signals.
     * @param expectedGroupCountHint Expected number of groups that would be emitted by {@code this} {@link Publisher}.
     * This is just a hint for internal data structures and does not have to be precise.
     * @param <Key> Type of {@link GroupedPublisher} keys.
     * @return A {@link Publisher} that emits {@link GroupedPublisher}s for new {@code key}s as emitted by
     * {@code keySelector} {@link Function}.
     * @see <a href="http://reactivex.io/documentation/operators/groupby.html">ReactiveX groupBy operator.</a>
     */
    public final <Key> Publisher<GroupedPublisher<Key, T>> groupBy(Function<? super T, ? extends Key> keySelector,
                                                                   int queueLimit, int expectedGroupCountHint) {
        return new PublisherGroupBy<>(this, keySelector, queueLimit, expectedGroupCountHint);
    }

    /**
     * The semantics are identical to {@link #groupBy(Function, int)} except that the {@code keySelector} can map each
     * data to multiple keys.
     * <p>
     * In sequential programming this is similar to the following:
     * <pre>{@code
     *     Map<Key, List<T>> results = ...;
     *     for (T t : resultOfThisPublisher()) {
     *         Iterator<Key> keys = keySelector.apply(result);
     *         for (Key key : keys) {
     *             List<T> v = results.get(key);
     *             if (v == null) {
     *                 v = // new List
     *                 results.put(key, v);
     *             }
     *             v.add(result);
     *         }
     *     }
     *     return results;
     * }</pre>
     *
     * @param keySelector {@link Function} to assign an item emitted by this {@link Publisher} to multiple
     * {@link GroupedPublisher}s.
     * @param queueLimit The number of elements which will be queued for each grouped {@link Subscriber} in order to
     * compensate for unequal demand. This also applies to the returned {@link Publisher} which may also have to queue
     * signals.
     * {@link Publisher} returned from this method not requesting enough via {@link Subscription#request(long)}.
     * @param <Key> Type of {@link GroupedPublisher} keys.
     * @return A {@link Publisher} that emits {@link GroupedPublisher}s for new {@code key}s as emitted by
     * {@code keySelector} {@link Function}.
     * @see #groupBy(Function, int)
     */
    public final <Key> Publisher<GroupedPublisher<Key, T>> groupToMany(
            Function<? super T, ? extends Iterator<? extends Key>> keySelector, int queueLimit) {
        return new PublisherGroupToMany<>(this, keySelector, queueLimit);
    }

    /**
     * The semantics are identical to {@link #groupBy(Function, int)} except that the {@code keySelector} can map each
     * data to multiple keys.
     * <p>
     * In sequential programming this is similar to the following:
     * <pre>{@code
     *     Map<Key, List<T>> results = ...;
     *     for (T t : resultOfThisPublisher()) {
     *         Iterator<Key> keys = keySelector.apply(result);
     *         for (Key key : keys) {
     *             List<T> v = results.get(key);
     *             if (v == null) {
     *                 v = // new List
     *                 results.put(key, v);
     *             }
     *             v.add(result);
     *         }
     *     }
     *     return results;
     * }</pre>
     *
     * @param keySelector {@link Function} to assign an item emitted by this {@link Publisher} to multiple
     * {@link GroupedPublisher}s.
     * @param queueLimit The number of elements which will be queued for each grouped {@link Subscriber} in order to
     * compensate for unequal demand. This also applies to the returned {@link Publisher} which may also have to queue
     * signals.
     * @param expectedGroupCountHint Expected number of groups that would be emitted by {@code this} {@link Publisher}.
     * This is just a hint for internal data structures and does not have to be precise.
     * @param <Key> Type of {@link GroupedPublisher} keys.
     * @return A {@link Publisher} that emits {@link GroupedPublisher}s for new {@code key}s as emitted by
     * {@code keySelector} {@link Function}.
     * @see #groupBy(Function, int)
     */
    public final <Key> Publisher<GroupedPublisher<Key, T>> groupToMany(
            Function<? super T, ? extends Iterator<? extends Key>> keySelector, int queueLimit,
            int expectedGroupCountHint) {
        return new PublisherGroupToMany<>(this, keySelector, queueLimit, expectedGroupCountHint);
    }

    /**
     * Create a {@link Publisher} that multicasts all the signals to exactly {@code expectedSubscribers}.
     * <p>
     * Depending on {@link Subscription#request(long)} demand it is possible that data maybe queued before being
     * delivered to each {@link Subscriber}! For example if there are 2 {@link Subscriber}s and the first calls
     * {@link Subscription#request(long) request(10)}, and the second only calls
     * {@link Subscription#request(long) request(1)}, then 9 elements will be queued to deliver to second when more
     * {@link Subscription#request(long)} demand is made.
     * <p>
     * In sequential programming this is similar to the following:
     * <pre>{@code
     *     List<T> results = resultOfThisPublisher();
     *     List<List<T>> multiResults = ...;
     *     for (int i = 0; i < expectedSubscribers; ++i) {
     *         multiResults.add(results);
     *     }
     *     return multiResults;
     * }</pre>
     * @deprecated Use {@link #multicast(int)}.
     * @param expectedSubscribers The number of expected subscribe calls required on the returned {@link Publisher}
     * before subscribing to this {@link Publisher}.
     * @return a {@link Publisher} that allows exactly {@code expectedSubscribers} subscribes.
     */
    @Deprecated
    public final Publisher<T> multicastToExactly(int expectedSubscribers) {
        return multicastToExactly(expectedSubscribers, 64);
    }

    /**
     * Create a {@link Publisher} that allows exactly {@code expectedSubscribers} subscribes.
     * The events from this {@link Publisher} object will be delivered to each
     * {@link Subscriber}.
     * <p>
     * Depending on {@link Subscription#request(long)} demand it is possible that data maybe queued before being
     * delivered to each {@link Subscriber}! For example if there are 2 {@link Subscriber}s and the first calls
     * {@link Subscription#request(long) request(10)}, and the second only calls
     * {@link Subscription#request(long) request(1)}, then 9 elements will be queued to deliver to second when more
     * {@link Subscription#request(long)} demand is made.
     * <p>
     * In sequential programming this is similar to the following:
     * <pre>{@code
     *     List<T> results = resultOfThisPublisher();
     *     List<List<T>> multiResults = ...;
     *     for (int i = 0; i < expectedSubscribers; ++i) {
     *         multiResults.add(results);
     *     }
     *     return multiResults;
     * }</pre>
     * @deprecated Use {@link #multicast(int, int)}.
     * @param expectedSubscribers The number of expected subscribe calls required on the returned {@link Publisher}
     * before subscribing to this {@link Publisher}.
     * @param queueLimit The number of elements which will be queued for each {@link Subscriber} in order to compensate
     * for unequal demand.
     * @return a {@link Publisher} that allows exactly {@code expectedSubscribers} subscribes.
     */
    @Deprecated
    public final Publisher<T> multicastToExactly(int expectedSubscribers, int queueLimit) {
        return new MulticastPublisher<>(this, expectedSubscribers, true, queueLimit, t -> completed());
    }

    /**
     * Create a {@link Publisher} that subscribes a single time upstream but allows for multiple downstream
     * {@link Subscriber}s. Signals from upstream will be multicast to each downstream {@link Subscriber}.
     * <p>
     * Downstream {@link Subscriber}s may subscribe after the upstream subscribe, but signals that were delivered before
     * the downstream {@link Subscriber} subscribed will not be queued.
     * <p>
     * Upstream outstanding {@link Subscription#request(long) Subscription demand} may be limited to provide an upper
     * bound on queue sizes (e.g. demand from downstream {@link Subscriber}s will vary).
     * In sequential programming this is similar to the following:
     * <pre>{@code
     *     List<T> results = resultOfThisPublisher();
     *     List<List<T>> multiResults = ...; // simulating multiple Subscribers
     *     for (int i = 0; i < expectedSubscribers; ++i) {
     *         multiResults.add(results);
     *     }
     *     return multiResults;
     * }</pre>
     * @param minSubscribers The upstream subscribe operation will not happen until after this many {@link Subscriber}
     * subscribe to the return value.
     * @return a {@link Publisher} that subscribes a single time upstream but allows for multiple downstream
     * {@link Subscriber}s. Signals from upstream will be multicast to each downstream {@link Subscriber}.
     * @see <a href="http://reactivex.io/documentation/operators/publish.html">ReactiveX multicast operator</a>
     */
    public final Publisher<T> multicast(int minSubscribers) {
        return multicast(minSubscribers, 64);
    }

    /**
     * Create a {@link Publisher} that subscribes a single time upstream but allows for multiple downstream
     * {@link Subscriber}s. Signals from upstream will be multicast to each downstream {@link Subscriber}.
     * <p>
     * Downstream {@link Subscriber}s may subscribe after the upstream subscribe, but signals that were delivered before
     * the downstream {@link Subscriber} subscribed will not be queued.
     * <p>
     * Upstream outstanding {@link Subscription#request(long) Subscription demand} may be limited to provide an upper
     * bound on queue sizes (e.g. demand from downstream {@link Subscriber}s will vary).
     * In sequential programming this is similar to the following:
     * <pre>{@code
     *     List<T> results = resultOfThisPublisher();
     *     List<List<T>> multiResults = ...; // simulating multiple Subscribers
     *     for (int i = 0; i < expectedSubscribers; ++i) {
     *         multiResults.add(results);
     *     }
     *     return multiResults;
     * }</pre>
     * @param minSubscribers The upstream subscribe operation will not happen until after this many {@link Subscriber}
     * subscribe to the return value.
     * @param queueLimit The number of elements which will be queued for each {@link Subscriber} in order to compensate
     * for unequal demand.
     * @return a {@link Publisher} that subscribes a single time upstream but allows for multiple downstream
     * {@link Subscriber}s. Signals from upstream will be multicast to each downstream {@link Subscriber}.
     * @see <a href="http://reactivex.io/documentation/operators/publish.html">ReactiveX multicast operator</a>
     */
    public final Publisher<T> multicast(int minSubscribers, int queueLimit) {
        return multicast(minSubscribers, queueLimit, t -> completed());
    }

    /**
     * Create a {@link Publisher} that subscribes a single time upstream but allows for multiple downstream
     * {@link Subscriber}s. Signals from upstream will be multicast to each downstream {@link Subscriber}.
     * <p>
     * Downstream {@link Subscriber}s may subscribe after the upstream subscribe, but signals that were delivered before
     * the downstream {@link Subscriber} subscribed will not be queued.
     * <p>
     * Upstream outstanding {@link Subscription#request(long) Subscription demand} may be limited to provide an upper
     * bound on queue sizes (e.g. demand from downstream {@link Subscriber}s will vary).
     * In sequential programming this is similar to the following:
     * <pre>{@code
     *     List<T> results = resultOfThisPublisher();
     *     List<List<T>> multiResults = ...; // simulating multiple Subscribers
     *     for (int i = 0; i < expectedSubscribers; ++i) {
     *         multiResults.add(results);
     *     }
     *     return multiResults;
     * }</pre>
     * @param minSubscribers The upstream subscribe operation will not happen until after this many {@link Subscriber}
     * subscribe to the return value.
     * @param queueLimit The number of elements which will be queued for each {@link Subscriber} in order to compensate
     * for unequal demand.
     * @param terminalResubscribe A {@link Function} that is invoked when a terminal signal arrives from upstream, and
     * returns a {@link Completable} whose termination resets the state of the returned {@link Publisher} and allows
     * for downstream resubscribing. The argument to this function is as follows:
     * <ul>
     *     <li>{@code null} if upstream terminates with {@link Subscriber#onComplete()}</li>
     *     <li>otherwise the {@link Throwable} from {@link Subscriber#onError(Throwable)}</li>
     * </ul>
     * @return a {@link Publisher} that subscribes a single time upstream but allows for multiple downstream
     * {@link Subscriber}s. Signals from upstream will be multicast to each downstream {@link Subscriber}.
     * @see <a href="http://reactivex.io/documentation/operators/publish.html">ReactiveX multicast operator</a>
     */
    public final Publisher<T> multicast(int minSubscribers, int queueLimit,
                                        Function<Throwable, Completable> terminalResubscribe) {
        return new MulticastPublisher<>(this, minSubscribers, false, queueLimit, terminalResubscribe);
    }

    /**
     * Create a {@link Publisher} that buffers items from this {@link Publisher} and emit those buffers instead of the
     * individual items.
     * <p>
     * In sequential programming this is similar to the following:
     * <pre>{@code
     *     List accumulators = strategy.boundaries();
     *     List buffers = ...;
     *     BC currentAccumulator;
     *     for (T t : resultOfThisPublisher()) {
     *         // This is an approximation; accumulators are emitted asynchronously.
     *         BC nextAccumulator = accumulators.remove(0).get();
     *         buffers.add(currentAccumulator.finish());
     *         currentAccumulator = nextAccumulator;
     *         currentAccumulator.add(t);
     *     }
     *     return buffers;
     * }</pre>
     * Notes:
     * <ol>
     *     <li>If this {@link Publisher} does not emit items within the {@link BufferStrategy#boundaries() boundary},
     *     it's expected it will emit an empty {@link Accumulator#finish() accumulated value} as the result of
     *     accumulating nothing. Use {@link #filter(Predicate)} operator if empty accumulations have to be discarded.
     *     </li>
     *     <li>If more than one {@link BufferStrategy#boundaries() boundary} is emitted while this operator
     *     {@link Accumulator#accumulate(Object) accumulates} or emits the
     *     {@link PublisherSource.Subscriber#onNext(Object) next} result of accumulation, those boundaries will be
     *     discarded without invoking {@link Accumulator#finish()} method.</li>
     * </ol>
     *
     * @param strategy A {@link BufferStrategy} to use for buffering items from this {@link Publisher}.
     * @param <BC> Type of the {@link Accumulator} to buffer items from this {@link Publisher}.
     * @param <B> Type of the buffer emitted from the returned {@link Publisher}.
     * @return a {@link Publisher} that buffers items from this {@link Publisher} and emit those buffers instead of the
     * individual items.
     *
     * @see <a href="http://reactivex.io/documentation/operators/buffer.html">ReactiveX buffer operator.</a>
     */
    public final <BC extends Accumulator<T, B>, B> Publisher<B> buffer(final BufferStrategy<T, BC, B> strategy) {
        return new PublisherBuffer<>(this, strategy);
    }

    /**
     * Invokes the {@code onSubscribe} {@link Consumer} argument <strong>before</strong>
     * {@link Subscriber#onSubscribe(PublisherSource.Subscription)} is called for {@link Subscriber}s of the returned
     * {@link Publisher}.
     *
     * @param onSubscribe Invoked <strong>before</strong>
     * {@link Subscriber#onSubscribe(PublisherSource.Subscription)} is called for {@link Subscriber}s of the returned
     * {@link Publisher}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Publisher}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX do operator.</a>
     */
    public final Publisher<T> beforeOnSubscribe(Consumer<? super Subscription> onSubscribe) {
        return beforeSubscriber(doOnSubscribeSupplier(onSubscribe));
    }

    /**
     * Invokes the {@code onNext} {@link Consumer} argument <strong>before</strong> {@link Subscriber#onNext(Object)} is
     * called for {@link Subscriber}s of the returned {@link Publisher}.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *  List<T> results = resultOfThisPublisher();
     *  for (T result : results) {
     *      onNext.accept(result);
     *  }
     *  nextOperation(results);
     * }</pre>
     *
     * @param onNext Invoked <strong>before</strong> {@link Subscriber#onNext(Object)} is called for {@link Subscriber}s
     * of the returned {@link Publisher}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Publisher}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX do operator.</a>
     */
    public final Publisher<T> beforeOnNext(Consumer<? super T> onNext) {
        return beforeSubscriber(doOnNextSupplier(onNext));
    }

    /**
     * Invokes the {@code onError} {@link Consumer} argument <strong>before</strong>
     * {@link Subscriber#onError(Throwable)} is called for {@link Subscriber}s of the returned {@link Publisher}.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *  try {
     *    List<T> results = resultOfThisPublisher();
     *  } catch (Throwable cause) {
     *      onError.accept(cause);
     *      nextOperation(cause);
     *  }
     * }</pre>
     *
     * @param onError Invoked <strong>before</strong> {@link Subscriber#onError(Throwable)} is called for
     * {@link Subscriber}s of the returned {@link Publisher}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Publisher}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX do operator.</a>
     */
    public final Publisher<T> beforeOnError(Consumer<Throwable> onError) {
        return beforeSubscriber(doOnErrorSupplier(onError));
    }

    /**
     * Invokes the {@code onComplete} {@link Runnable} argument <strong>before</strong> {@link Subscriber#onComplete()}
     * is called for {@link Subscriber}s of the returned {@link Publisher}.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *   List<T> results = resultOfThisPublisher();
     *   onComplete.run();
     *   nextOperation(results);
     * }</pre>
     *
     * @param onComplete Invoked <strong>before</strong> {@link Subscriber#onComplete()} is called for
     * {@link Subscriber}s of the returned {@link Publisher}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Publisher}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX do operator.</a>
     */
    public final Publisher<T> beforeOnComplete(Runnable onComplete) {
        return beforeSubscriber(doOnCompleteSupplier(onComplete));
    }

    /**
     * Invokes the {@code onRequest} {@link LongConsumer} argument <strong>before</strong>
     * {@link Subscription#request(long)} is called for {@link Subscription}s of the returned {@link Publisher}.
     *
     * @param onRequest Invoked <strong>before</strong> {@link Subscription#request(long)} is called for
     * {@link Subscription}s of the returned {@link Publisher}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Publisher}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX do operator.</a>
     */
    public final Publisher<T> beforeRequest(LongConsumer onRequest) {
        return beforeSubscription(doOnRequestSupplier(onRequest));
    }

    /**
     * Invokes the {@code onCancel} {@link Runnable} argument <strong>before</strong> {@link Subscription#cancel()} is
     * called for {@link Subscription}s of the returned {@link Publisher}.
     *
     * @param onCancel Invoked <strong>before</strong> {@link Subscription#cancel()} is called for
     * {@link Subscription}s of the returned {@link Publisher}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Publisher}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX do operator.</a>
     */
    public final Publisher<T> beforeCancel(Runnable onCancel) {
        return beforeSubscription(doOnCancelSupplier(onCancel));
    }

    /**
     * Invokes the {@code beforeFinally} {@link Runnable} argument <strong>before</strong> any of the following terminal
     * methods are called:
     * <ul>
     *     <li>{@link Subscriber#onComplete()}</li>
     *     <li>{@link Subscriber#onError(Throwable)}</li>
     *     <li>{@link Subscription#cancel()}</li>
     * </ul>
     * for {@link Subscription}s/{@link Subscriber}s of the returned {@link Publisher}.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *  try {
     *      List<T> results = resultOfThisPublisher();
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
     *     <li>{@link Subscription#cancel()}</li>
     * </ul>
     * for {@link Subscription}s/{@link Subscriber}s of the returned {@link Publisher}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Publisher}.
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX do operator.</a>
     */
    public final Publisher<T> beforeFinally(Runnable doFinally) {
        return beforeFinally(new RunnableTerminalSignalConsumer(doFinally));
    }

    /**
     * Invokes the corresponding method on {@code beforeFinally} {@link TerminalSignalConsumer} argument
     * <strong>before</strong> any of the following terminal methods are called:
     * <ul>
     *     <li>{@link Subscriber#onComplete()} - invokes {@link TerminalSignalConsumer#onComplete()}</li>
     *     <li>{@link Subscriber#onError(Throwable)} - invokes {@link TerminalSignalConsumer#onError(Throwable)}</li>
     *     <li>{@link Subscription#cancel()} - invokes {@link TerminalSignalConsumer#cancel()}</li>
     * </ul>
     * for {@link Subscription}s/{@link Subscriber}s of the returned {@link Publisher}.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *  try {
     *      List<T> results = resultOfThisPublisher();
     *  } catch(Throwable t) {
     *      doFinally.onError(t);
     *      nextOperation(); // Maybe notifying of cancellation, or termination
     *      return;
     *  }
     *  doFinally.onComplete();
     *  nextOperation(); // Maybe notifying of cancellation, or termination
     * }</pre>
     *
     * @param doFinally For each subscribe of the returned {@link Publisher}, at most one method of this
     * {@link TerminalSignalConsumer} will be invoked.
     * @return The new {@link Publisher}.
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX do operator.</a>
     */
    public final Publisher<T> beforeFinally(TerminalSignalConsumer doFinally) {
        return new BeforeFinallyPublisher<>(this, doFinally);
    }

    /**
     * Creates a new {@link Subscriber} (via the {@code subscriberSupplier} argument) on each call to subscribe and
     * invokes all the {@link Subscriber} methods <strong>before</strong> the {@link Subscriber}s of the returned
     * {@link Publisher}.
     *
     * @param subscriberSupplier Creates a new {@link Subscriber} on each call to subscribe and invokes all the
     * {@link Subscriber} methods <strong>before</strong> the {@link Subscriber}s of the returned {@link Publisher}.
     * {@link Subscriber} methods <strong>MUST NOT</strong> throw.
     * @return The new {@link Publisher}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX do operator.</a>
     */
    public final Publisher<T> beforeSubscriber(Supplier<? extends Subscriber<? super T>> subscriberSupplier) {
        return new BeforeSubscriberPublisher<>(this, subscriberSupplier);
    }

    /**
     * Creates a new {@link Subscription} (via the {@code subscriptionSupplier} argument) on each call to
     * subscribe and invokes all the {@link Subscription} methods <strong>before</strong> the {@link Subscription}s of
     * the returned {@link Publisher}.
     *
     * @param subscriptionSupplier Creates a new {@link Subscription} on each call to subscribe and invokes all the
     * {@link Subscription} methods <strong>before</strong> the {@link Subscription}s of the returned
     * {@link Publisher}. {@link Subscription} methods <strong>MUST NOT</strong> throw.
     * @return The new {@link Publisher}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX do operator.</a>
     */
    public final Publisher<T> beforeSubscription(Supplier<? extends Subscription> subscriptionSupplier) {
        return new WhenSubscriptionPublisher<>(this, subscriptionSupplier, true);
    }

    /**
     * Invokes the {@code onSubscribe} {@link Consumer} argument <strong>after</strong>
     * {@link Subscriber#onSubscribe(PublisherSource.Subscription)} is called for {@link Subscriber}s of the returned
     * {@link Publisher}.
     *
     * @param onSubscribe Invoked <strong>after</strong>
     * {@link Subscriber#onSubscribe(PublisherSource.Subscription)} is called for
     * {@link Subscriber}s of the returned {@link Publisher}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Publisher}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX do operator.</a>
     */
    public final Publisher<T> afterOnSubscribe(Consumer<? super Subscription> onSubscribe) {
        return afterSubscriber(doOnSubscribeSupplier(onSubscribe));
    }

    /**
     * Invokes the {@code onNext} {@link Consumer} argument <strong>after</strong> {@link Subscriber#onNext(Object)} is
     * called for {@link Subscriber}s of the returned {@link Publisher}.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *  List<T> results = resultOfThisPublisher();
     *  nextOperation(results);
     *  for (T result : results) {
     *      onNext.accept(result);
     *  }
     * }</pre>
     *
     * @param onNext Invoked <strong>after</strong> {@link Subscriber#onNext(Object)} is called for {@link Subscriber}s
     * of the returned {@link Publisher}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Publisher}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX do operator.</a>
     */
    public final Publisher<T> afterOnNext(Consumer<? super T> onNext) {
        return afterSubscriber(doOnNextSupplier(onNext));
    }

    /**
     * Invokes the {@code onError} {@link Consumer} argument <strong>after</strong>
     * {@link Subscriber#onError(Throwable)} is called for {@link Subscriber}s of the returned {@link Publisher}.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *  try {
     *    List<T> results = resultOfThisPublisher();
     *  } catch (Throwable cause) {
     *      nextOperation(cause);
     *      onError.accept(cause);
     *  }
     * }</pre>
     *
     * @param onError Invoked <strong>after</strong> {@link Subscriber#onError(Throwable)} is called for
     * {@link Subscriber}s of the returned {@link Publisher}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Publisher}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX do operator.</a>
     */
    public final Publisher<T> afterOnError(Consumer<Throwable> onError) {
        return afterSubscriber(doOnErrorSupplier(onError));
    }

    /**
     * Invokes the {@code onComplete} {@link Runnable} argument <strong>after</strong> {@link Subscriber#onComplete()}
     * is called for {@link Subscriber}s of the returned {@link Publisher}.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *   List<T> results = resultOfThisPublisher();
     *   nextOperation(results);
     *   onComplete.run();
     * }</pre>
     *
     * @param onComplete Invoked <strong>after</strong> {@link Subscriber#onComplete()} is called for
     * {@link Subscriber}s of the returned {@link Publisher}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Publisher}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX do operator.</a>
     */
    public final Publisher<T> afterOnComplete(Runnable onComplete) {
        return afterSubscriber(doOnCompleteSupplier(onComplete));
    }

    /**
     * Invokes the {@code onRequest} {@link LongConsumer} argument <strong>after</strong>
     * {@link Subscription#request(long)} is called for {@link Subscription}s of the returned {@link Publisher}.
     *
     * @param onRequest Invoked <strong>after</strong> {@link Subscription#request(long)} is called for
     * {@link Subscription}s of the returned {@link Publisher}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Publisher}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX do operator.</a>
     */
    public final Publisher<T> afterRequest(LongConsumer onRequest) {
        return afterSubscription(doOnRequestSupplier(onRequest));
    }

    /**
     * Invokes the {@code onCancel} {@link Runnable} argument <strong>after</strong> {@link Subscription#cancel()} is
     * called for {@link Subscription}s of the returned {@link Publisher}.
     *
     * @param onCancel Invoked <strong>after</strong> {@link Subscription#cancel()} is called for {@link Subscription}s
     * of the returned {@link Publisher}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Publisher}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX do operator.</a>
     */
    public final Publisher<T> afterCancel(Runnable onCancel) {
        return afterSubscription(doOnCancelSupplier(onCancel));
    }

    /**
     * Invokes the {@code afterFinally} {@link Runnable} argument <strong>after</strong> any of the following terminal
     * methods are called:
     * <ul>
     *     <li>{@link Subscriber#onComplete()}</li>
     *     <li>{@link Subscriber#onError(Throwable)}</li>
     *     <li>{@link Subscription#cancel()}</li>
     * </ul>
     * for {@link Subscription}s/{@link Subscriber}s of the returned {@link Publisher}.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *  try {
     *      List<T> results = resultOfThisPublisher();
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
     *     <li>{@link Subscription#cancel()}</li>
     * </ul>
     * for {@link Subscription}s/{@link Subscriber}s of the returned {@link Publisher}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Publisher}.
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX do operator.</a>
     */
    public final Publisher<T> afterFinally(Runnable doFinally) {
        return afterFinally(new RunnableTerminalSignalConsumer(doFinally));
    }

    /**
     * Invokes the corresponding method on {@code afterFinally} {@link TerminalSignalConsumer} argument
     * <strong>after</strong> any of the following terminal methods are called:
     * <ul>
     *     <li>{@link Subscriber#onComplete()} - invokes {@link TerminalSignalConsumer#onComplete()}</li>
     *     <li>{@link Subscriber#onError(Throwable)} - invokes {@link TerminalSignalConsumer#onError(Throwable)}</li>
     *     <li>{@link Subscription#cancel()} - invokes {@link TerminalSignalConsumer#cancel()}</li>
     * </ul>
     * for {@link Subscription}s/{@link Subscriber}s of the returned {@link Publisher}.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *  try {
     *      List<T> results = resultOfThisPublisher();
     *  } catch(Throwable t) {
     *      nextOperation(); // Maybe notifying of cancellation, or termination
     *      doFinally.onError(t);
     *      return;
     *  }
     *  nextOperation(); // Maybe notifying of cancellation, or termination
     *  doFinally.onComplete();
     * }</pre>
     *
     * @param doFinally For each subscribe of the returned {@link Publisher}, at most one method of this
     * {@link TerminalSignalConsumer} will be invoked.
     * @return The new {@link Publisher}.
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX do operator.</a>
     */
    public final Publisher<T> afterFinally(TerminalSignalConsumer doFinally) {
        return new AfterFinallyPublisher<>(this, doFinally);
    }

    /**
     * Creates a new {@link Subscriber} (via the {@code subscriberSupplier} argument) for each new subscribe and
     * invokes all the {@link Subscriber} methods <strong>after</strong> the {@link Subscriber}s of the returned
     * {@link Publisher}.
     *
     * @param subscriberSupplier Creates a new {@link Subscriber} for each new subscribe and invokes all the
     * {@link Subscriber} methods <strong>after</strong> the {@link Subscriber}s of the returned {@link Publisher}.
     * {@link Subscriber} methods <strong>MUST NOT</strong> throw.
     * @return The new {@link Publisher}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX do operator.</a>
     */
    public final Publisher<T> afterSubscriber(Supplier<? extends Subscriber<? super T>> subscriberSupplier) {
        return new AfterSubscriberPublisher<>(this, subscriberSupplier);
    }

    /**
     * Creates a new {@link Subscriber} (via the {@code subscriberSupplier} argument) for each new subscribe and
     * invokes methods on that {@link Subscriber} when the corresponding methods are called for {@link Subscriber}s of
     * the returned {@link Publisher}.
     *
     * @param subscriberSupplier Creates a new {@link Subscriber} for each new subscribe and invokes methods on that
     * {@link Subscriber} when the corresponding methods are called for {@link Subscriber}s of the returned
     * {@link Publisher}. {@link Subscriber} methods <strong>MUST NOT</strong> throw.
     *  @return The new {@link Publisher}.
     */
    public final Publisher<T> whenSubscriber(Supplier<? extends Subscriber<? super T>> subscriberSupplier) {
        return beforeSubscriber(subscriberSupplier);
    }

    /**
     * Creates a new {@link Subscription} (via the {@code subscriptionSupplier} argument) for each new subscribe and
     * invokes all the {@link Subscription} methods <strong>after</strong> the {@link Subscription}s of the returned
     * {@link Publisher}.
     *
     * @param subscriptionSupplier Creates a new {@link Subscription} for each new subscribe and invokes all the
     * {@link Subscription} methods <strong>after</strong> the {@link Subscription}s of the returned {@link Publisher}.
     * {@link Subscription} methods <strong>MUST NOT</strong> throw.
     * @return The new {@link Publisher}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX do operator.</a>
     */
    public final Publisher<T> afterSubscription(Supplier<? extends Subscription> subscriptionSupplier) {
        return new WhenSubscriptionPublisher<>(this, subscriptionSupplier, false);
    }

    /**
     * Creates a new {@link Subscription} (via the {@code subscriptionSupplier} argument) for each new subscribe and
     * invokes all the {@link Subscription} methods when the corresponding methods are called for {@link Subscription}s
     * of the returned {@link Publisher}.
     *
     * @param subscriptionSupplier Creates a new {@link Subscription} for each new subscribe and invokes all the
     * {@link Subscription} methods when the {@link Subscription}s of the returned {@link Publisher}.
     * {@link Subscription} methods <strong>MUST NOT</strong> throw.
     * @return The new {@link Publisher}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX do operator.</a>
     */
    public final Publisher<T> whenSubscription(Supplier<? extends Subscription> subscriptionSupplier) {
        return beforeSubscription(subscriptionSupplier);
    }

    /**
     * Subscribes to this {@link Publisher} and invokes {@code forEach} {@link Consumer} for each item emitted by this
     * {@link Publisher}.
     * <p>
     * This will request {@link Long#MAX_VALUE} from the {@link Subscription}.
     * <p>
     * From a sequential programming point of view this method is roughly equivalent to the following:
     * <pre>{@code
     *  List<T> results = resultOfThisPublisher();
     *  results.iterator().forEachRemaining(forEach);
     * }</pre>
     *
     * @param forEach {@link Consumer} to invoke for each {@link Subscriber#onNext(Object)}.
     * @return {@link Cancellable} used to invoke {@link Subscription#cancel()} on the parameter of
     * {@link Subscriber#onSubscribe(PublisherSource.Subscription)} for this {@link Publisher}.
     * */
    public final Cancellable forEach(Consumer<? super T> forEach) {
        ForEachSubscriber<T> subscriber = new ForEachSubscriber<>(forEach);
        subscribeInternal(subscriber);
        return subscriber;
    }

    /**
     * Creates a new {@link Publisher} that will use the passed {@link Executor} to invoke all {@link Subscriber}
     * methods.
     * This method does <strong>not</strong> override preceding {@link Executor}s, if any, specified for {@code this}
     * {@link Publisher}. Only subsequent operations, if any, added in this execution chain will use this
     * {@link Executor}.
     *
     * @param executor {@link Executor} to use.
     * @return A new {@link Publisher} that will use the passed {@link Executor} to invoke all methods of
     * {@link Subscriber}.
     */
    public final Publisher<T> publishOn(Executor executor) {
        return PublishAndSubscribeOnPublishers.publishOn(this, executor);
    }

    /**
     * Creates a new {@link Publisher} that will use the passed {@link Executor} to invoke the following methods:
     * <ul>
     *     <li>All {@link Subscription} methods.</li>
     *     <li>The {@link #handleSubscribe(PublisherSource.Subscriber)} method.</li>
     * </ul>
     * This method does <strong>not</strong> override preceding {@link Executor}s, if any, specified for {@code this}
     * {@link Publisher}. Only subsequent operations, if any, added in this execution chain will use this
     * {@link Executor}.
     *
     * @param executor {@link Executor} to use.
     * @return A new {@link Publisher} that will use the passed {@link Executor} to invoke all methods of
     * {@link Subscription} and {@link #handleSubscribe(PublisherSource.Subscriber)}.
     */
    public final Publisher<T> subscribeOn(Executor executor) {
        return PublishAndSubscribeOnPublishers.subscribeOn(this, executor);
    }

    /**
     * Signifies that when the returned {@link Publisher} is subscribed to, the {@link AsyncContext} will be shared
     * instead of making a {@link AsyncContextMap#copy() copy}.
     * <p>
     * This operator only impacts behavior if the returned {@link Publisher} is subscribed directly after this operator,
     * that means this must be the "last operator" in the chain for this to have an impact.
     *
     * @return A {@link Publisher} that will share the {@link AsyncContext} instead of making a
     * {@link AsyncContextMap#copy() copy} when subscribed to.
     */
    public final Publisher<T> subscribeShareContext() {
        return new PublisherSubscribeShareContext<>(this);
    }

    /**
     * <strong>This method requires advanced knowledge of building operators. Before using this method please attempt
     * to compose existing operator(s) to satisfy your use case.</strong>
     * <p>
     * Returns a {@link Publisher} which when subscribed, the {@code operator} argument will be used to wrap the
     * {@link Subscriber} before subscribing to this {@link Publisher}.
     * <pre>{@code
     *     Publisher<X> pub = ...;
     *     pub.map(..) // A
     *        .liftSync(original -> modified)
     *        .filter(..) // B
     * }</pre>
     *
     * The {@code original -> modified} "operator" <strong>MUST</strong> be "synchronous" in that it does not interact
     * with the original {@link Subscriber} from outside the modified {@link Subscriber} or {@link Subscription}
     * threads. That is to say this operator will not impact the {@link Executor} constraints already in place between
     * <i>A</i> and <i>B</i> above. If you need asynchronous behavior, or are unsure, see
     * {@link #liftAsync(PublisherOperator)}.
     * @param operator The custom operator logic. The input is the "original" {@link Subscriber} to this
     * {@link Publisher} and the return is the "modified" {@link Subscriber} that provides custom operator business
     * logic.
     * @param <R> Type of the items emitted by the returned {@link Publisher}.
     * @return a {@link Publisher} which when subscribed, the {@code operator} argument will be used to wrap the
     * {@link Subscriber} before subscribing to this {@link Publisher}.
     * @see #liftAsync(PublisherOperator)
     */
    public final <R> Publisher<R> liftSync(PublisherOperator<? super T, ? extends R> operator) {
        return new LiftSynchronousPublisherOperator<>(this, operator);
    }

    /**
     * <strong>This method requires advanced knowledge of building operators. Before using this method please attempt
     * to compose existing operator(s) to satisfy your use case.</strong>
     * <p>
     * Returns a {@link Single} which when subscribed, the {@code operator} argument will be used to convert between the
     * {@link SingleSource.Subscriber} to a {@link Subscriber} before subscribing to this {@link Publisher}.
     * <pre>{@code
     *     Publisher<X> pub = ...;
     *     pub.map(..) // A
     *        .liftSync(original -> modified)
     *        .filter(..) // B - we have converted to Single now!
     * }</pre>
     *
     * The {@code original -> modified} "operator" <strong>MUST</strong> be "synchronous" in that it does not interact
     * with the original {@link Subscriber} from outside the modified {@link Subscriber} or {@link Subscription}
     * threads. That is to say this operator will not impact the {@link Executor} constraints already in place between
     * <i>A</i> and <i>B</i> above. If you need asynchronous behavior, or are unsure, don't use this operator.
     * @param operator The custom operator logic. The input is the "original" {@link SingleSource.Subscriber} to the
     * returned {@link Single} and the return is the "modified" {@link Subscriber} that provides custom operator
     * business logic on this {@link Publisher}.
     * @param <R> Type of the items emitted by the returned {@link Single}.
     * @return a {@link Single} which when subscribed, the {@code operator} argument will be used to convert the
     * {@link SingleSource.Subscriber} to a {@link Subscriber} before subscribing to this {@link Publisher}.
     */
    public final <R> Single<R> liftSyncToSingle(PublisherToSingleOperator<? super T, ? extends R> operator) {
        return new LiftSynchronousPublisherToSingle<>(this, operator);
    }

    /**
     * <strong>This method requires advanced knowledge of building operators. Before using this method please attempt
     * to compose existing operator(s) to satisfy your use case.</strong>
     * <p>
     * Returns a {@link Publisher} which will wrap the {@link PublisherSource.Subscriber} using the provided
     * {@code operator} argument before subscribing to this {@link Publisher}.
     * <pre>{@code
     *     Publisher<X> pub = ...;
     *     pub.map(..) // A
     *        .liftAsync(original -> modified)
     *        .filter(..) // B
     * }</pre>
     * The {@code original -> modified} "operator" MAY be "asynchronous" in that it may interact with the original
     * {@link Subscriber} from outside the modified {@link Subscriber} or {@link Subscription} threads. More
     * specifically:
     * <ul>
     *  <li>all of the {@link Subscriber} invocations going "downstream" (i.e. from <i>A</i> to <i>B</i> above) MAY be
     *  offloaded via an {@link Executor}</li>
     *  <li>all of the {@link Subscription} invocations going "upstream" (i.e. from <i>B</i> to <i>A</i> above) MAY be
     *  offloaded via an {@link Executor}</li>
     * </ul>
     * This behavior exists to prevent blocking code negatively impacting the thread that powers the upstream source of
     * data (e.g. an EventLoop).
     *
     * @param operator The custom operator logic. The input is the "original" {@link Subscriber} to this
     * {@link Publisher} and the return is the "modified" {@link Subscriber} that provides custom operator business
     * logic.
     * @param <R> Type of the items emitted by the returned {@link Publisher}.
     * @return a {@link Publisher} which when subscribed, the {@code operator} argument will be used to wrap the
     * {@link Subscriber} before subscribing to this {@link Publisher}.
     * @see #liftSync(PublisherOperator)
     */
    public final <R> Publisher<R> liftAsync(PublisherOperator<? super T, ? extends R> operator) {
        return new LiftAsynchronousPublisherOperator<>(this, operator);
    }

    //
    // Operators End
    //

    //
    // Conversion Operators Begin
    //

    /**
     * Converts this {@link Publisher} to a {@link Single}.
     *
     * @param defaultValueSupplier A {@link Supplier} of default value if this {@link Publisher} did not emit any item.
     * @return A {@link Single} that will contain the first item emitted from the this {@link Publisher}.
     * If the source {@link Publisher} does not emit any item, then the returned {@link Single} will contain the value
     * as returned by the passed {@link Supplier}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/first.html">ReactiveX first operator.</a>
     */
    public final Single<T> firstOrElse(Supplier<T> defaultValueSupplier) {
        return new PubToSingleFirstOrElse<>(this, defaultValueSupplier);
    }

    /**
     * Ensures that this {@link Publisher} emits exactly a single {@link Subscriber#onNext(Object)} to its
     * {@link Subscriber}. If this {@link Publisher} terminates without emitting any
     * items a {@link NoSuchElementException} will be signaled and if this {@link Publisher} emits more than one item,
     * an {@link IllegalArgumentException} will be signaled. Any error emitted by this {@link Publisher} will be
     * forwarded to the returned {@link Single}.
     *
     * @return A {@link Single} that will contain the first item emitted from the this {@link Publisher}.
     * If the source {@link Publisher} does not emit any item, then the returned {@link Single} will terminate with
     * {@link NoSuchElementException}.
     */
    public final Single<T> firstOrError() {
        return new PubFirstOrError<>(this);
    }

    /**
     * Ignores all elements emitted by this {@link Publisher} and forwards the termination signal to the returned
     * {@link Completable}.
     *
     * @return A {@link Completable} that mirrors the terminal signal from this {@code Publisher}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/ignoreelements.html">
     *     ReactiveX ignoreElements operator.</a>
     */
    public final Completable ignoreElements() {
        return new PubToCompletableIgnore<>(this);
    }

    /**
     * Converts this {@link Publisher} to a {@link Completable}. If this {@link Publisher} emits any
     * {@link Subscriber#onNext(Object)} signals the resulting {@link Completable} will be terminated with a
     * {@link IllegalArgumentException}.
     * @return A {@link Completable} that mirrors the terminal signal from this {@code Publisher}, and terminates in
     * error if an {@link Subscriber#onNext(Object)} signals.
     */
    public final Completable completableOrError() {
        return new PubCompletableOrError<>(this);
    }

    /**
     * Collects all items emitted by this {@link Publisher} into a single item.
     *
     * @param resultFactory Factory for the result which collects all items emitted by this {@link Publisher}.
     * This will be called every time the returned {@link Single} is subscribed.
     * @param collector Invoked for every item emitted by the source {@link Publisher} and returns the same or altered
     * {@code result} object.
     * @param <R> Type of the reduced item.
     * @return A {@link Single} that completes with the single {@code result} or any error emitted by the source
     * {@link Publisher}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/reduce.html">ReactiveX reduce operator.</a>
     */
    public final <R> Single<R> collect(Supplier<? extends R> resultFactory,
                                       BiFunction<? super R, ? super T, R> collector) {
        return new ReduceSingle<>(this, resultFactory, collector);
    }

    /**
     * Convert this {@link Publisher} into a {@link Future} with a {@link Collection} containing the elements of this
     * {@link Publisher} upon successful termination. If this {@link Publisher} terminates in an error, then the
     * intermediate {@link Collection} will be discarded and the {@link Future} will complete exceptionally.
     * @return a {@link Future} with a {@link Collection} containing the elements of this {@link Publisher} upon
     * successful termination.
     * @see #toFuture(Supplier, BiFunction)
     */
    public final Future<Collection<T>> toFuture() {
        return toFuture(ArrayList::new, (list, next) -> {
            list.add(next);
            return list;
        });
    }

    /**
     * Convert this {@link Publisher} into a {@link Future} of type {@link R} which represents all elements of this
     * {@link Publisher} upon successful termination. If this {@link Publisher} terminates in an error, then the
     * intermediate {@link R} will be discarded and the {@link Future} will complete exceptionally.
     * @param resultFactory Factory for the result which collects all items emitted by this {@link Publisher}.
     * @param reducer Invoked for every item emitted by the source {@link Publisher} and returns the same or altered
     * {@code result} object.
     * @param <R> Type of the reduced item.
     * @return a {@link Future} of type {@link R} which represents all elements of this {@link Publisher} upon
     * successful termination.
     */
    public final <R> Future<R> toFuture(Supplier<? extends R> resultFactory,
                                        BiFunction<? super R, ? super T, R> reducer) {
        return collect(resultFactory, reducer).toFuture();
    }

    /**
     * Convert this {@link Publisher} into a {@link CompletionStage} with a {@link Collection} containing the elements
     * of this {@link Publisher} upon successful termination. If this {@link Publisher} terminates in an error, then the
     * intermediate {@link Collection} will be discarded and the {@link CompletionStage} will complete exceptionally.
     * @return a {@link CompletionStage} with a {@link Collection} containing the elements of this {@link Publisher}
     * upon successful termination.
     * @see #toCompletionStage(Supplier, BiFunction)
     */
    public final CompletionStage<Collection<T>> toCompletionStage() {
        return toCompletionStage(ArrayList::new, (list, next) -> {
            list.add(next);
            return list;
        });
    }

    /**
     * Convert this {@link Publisher} into a {@link CompletionStage} of type {@link R} which represents all elements of
     * this {@link Publisher} upon successful termination. If this {@link Publisher} terminates in an error, then the
     * intermediate {@link R} will be discarded and the {@link CompletionStage} will complete exceptionally.
     * @param resultFactory Factory for the result which collects all items emitted by this {@link Publisher}.
     * @param reducer Invoked for every item emitted by the source {@link Publisher} and returns the same or altered
     * {@code result} object.
     * @param <R> Type of the reduced item.
     * @return a {@link CompletionStage} of type {@link R} which represents all elements of this {@link Publisher} upon
     * successful termination.
     */
    public final <R> CompletionStage<R> toCompletionStage(Supplier<? extends R> resultFactory,
                                                          BiFunction<? super R, ? super T, R> reducer) {
        return collect(resultFactory, reducer).toCompletionStage();
    }

    /**
     * Subscribes to {@code this} {@link Publisher} and converts all signals received by the {@link Subscriber} to the
     * returned {@link InputStream} following the below rules:
     * <ul>
     *     <li>{@link Subscription} received by {@link Subscriber#onSubscribe(PublisherSource.Subscription)} is used to
     *     request more data when required. If the returned {@link InputStream} is closed, {@link Subscription} is
     *     cancelled and any unread data is disposed.</li>
     *     <li>Any items received by {@link Subscriber#onNext(Object)} are converted to a {@code byte[]} using the
     *     passed {@code serializer}. These {@code byte}s are available to be read from the {@link InputStream}</li>
     *     <li>Any {@link Throwable} received by {@link Subscriber#onError(Throwable)} is thrown (wrapped in an
     *     {@link IOException}) when data is read from the returned {@link InputStream}. This error will be thrown
     *     only after draining all queued data, if any.</li>
     *     <li>When {@link Subscriber#onComplete()} is called, returned {@link InputStream}'s read methods will return
     *     {@code -1} to indicate end of stream after emitting all received data.</li>
     * </ul>
     *
     * <h2>Flow control</h2>
     * This operator may pre-fetch may pre-fetch items from {@code this} {@link Publisher} if available to reduce
     * blocking for read operations from the returned {@link InputStream}. In order to increase responsiveness of the
     * {@link InputStream} some amount of buffering may be done. Use {@link #toInputStream(Function, int)} to manage
     * capacity of this buffer.
     *
     * @param serializer {@link Function} that is invoked for every item emitted by {@code this} {@link Publisher}.
     * @return {@link InputStream} that emits all data emitted by {@code this} {@link Publisher}. If {@code this}
     * {@link Publisher} terminates with an error, same error is thrown (wrapped in an {@link IOException}) from the
     * returned {@link InputStream}s read methods after emitting all received data.
     */
    public final InputStream toInputStream(Function<? super T, byte[]> serializer) {
        return new CloseableIteratorAsInputStream<>(new PublisherAsBlockingIterable<>(this).iterator(), serializer);
    }

    /**
     * Subscribes to {@code this} {@link Publisher} and converts all signals received by the {@link Subscriber} to the
     * returned {@link InputStream} following the below rules:
     * <ul>
     *     <li>{@link Subscription} received by {@link Subscriber#onSubscribe(PublisherSource.Subscription)} is used to
     *     request more data when required. If the returned {@link InputStream} is closed, {@link Subscription} is
     *     cancelled and any unread data is disposed.</li>
     *     <li>Any items received by {@link Subscriber#onNext(Object)} are convertedto a {@code byte[]} using the
     *     passed {@code serializer}. These {@code byte}s are available to be read from the {@link InputStream}</li>
     *     <li>Any {@link Throwable} received by {@link Subscriber#onError(Throwable)} is thrown (wrapped in an
     *     {@link IOException}) when data is read from the returned {@link InputStream}. This error will be thrown
     *     only after draining all queued data, if any.</li>
     *     <li>When {@link Subscriber#onComplete()} is called, returned {@link InputStream}'s read methods will return
     *     {@code -1} to indicate end of stream after emitting all received data.</li>
     * </ul>
     *
     * <h2>Flow control</h2>
     * This operator may pre-fetch items from {@code this} {@link Publisher} if available to reduce blocking for read
     * operations from the returned {@link InputStream}. In order to increase responsiveness of the {@link InputStream}
     * some amount of buffering may be done. {@code queueCapacity} can be used to bound this buffer.
     *
     * @param serializer {@link Function} that is invoked for every item emitted by {@code this} {@link Publisher}.
     * @param queueCapacity Hint for the capacity of the intermediary queue that stores items that are emitted by
     * {@code this} {@link Publisher} but has not yet been read from the returned {@link InputStream}.
     * @return {@link InputStream} that emits all data emitted by {@code this} {@link Publisher}. If {@code this}
     * {@link Publisher} terminates with an error, same error is thrown (wrapped in an {@link IOException}) from the
     * returned {@link InputStream}s read methods after emitting all received data.
     */
    public final InputStream toInputStream(Function<? super T, byte[]> serializer, int queueCapacity) {
        return new CloseableIteratorAsInputStream<>(new PublisherAsBlockingIterable<>(this, queueCapacity).iterator(),
                serializer);
    }

    /**
     * Converts {@code this} {@link Publisher} to an {@link BlockingIterable}. Every time
     * {@link BlockingIterable#iterator()} is called on the returned {@link BlockingIterable}, {@code this}
     * {@link Publisher} is subscribed following the below rules:
     * <ul>
     *     <li>{@link Subscription} received by {@link Subscriber#onSubscribe(PublisherSource.Subscription)} is used to
     *     request more data when required.</li>
     *     <li>Any items received by {@link Subscriber#onNext(Object)} is returned from a call to
     *     {@link BlockingIterator#next()}.</li>
     *     <li>Any {@link Throwable} received by {@link Subscriber#onError(Throwable)} is thrown (wrapped in a
     *     {@link RuntimeException} if required) when {@link BlockingIterator#next()} is called. This error will be
     *     thrown only after draining all queued data, if any.</li>
     *     <li>When {@link Subscriber#onComplete()} is called, returned {@link BlockingIterator}s
     *     {@link BlockingIterator#hasNext()} will return {@code false} {@link BlockingIterator#next()} will throw
     *     {@link NoSuchElementException}. This error will be thrown only after draining all queued data, if any.</li>
     * </ul>
     *
     * <h2>Flow control</h2>
     * This operator may pre-fetch items from {@code this} {@link Publisher} if available to reduce blocking of
     * {@link Iterator#hasNext()} from the returned {@link BlockingIterable}. In order to increase responsiveness of
     * the {@link Iterator} some amount of buffering may be done. Use {@link #toIterable(int)} to manage capacity of
     * this buffer.
     *
     * <h2>Blocking</h2>
     * The returned {@link BlockingIterator} from the returned {@link BlockingIterable} will block on
     * {@link BlockingIterator#hasNext()} and {@link BlockingIterator#next()} if no data is available. This operator may
     * try to reduce this blocking by requesting data ahead of time.
     *
     * @return {@link BlockingIterable} representing {@code this} {@link Publisher}. Every time
     * {@link BlockingIterable#iterator()} is invoked on the {@link BlockingIterable}, {@code this} {@link Publisher}
     * is subscribed. {@link BlockingIterator}s returned from this {@link BlockingIterable} do not support
     * {@link BlockingIterator#remove()}.
     */
    public final BlockingIterable<T> toIterable() {
        return new PublisherAsBlockingIterable<>(this);
    }

    /**
     * Converts {@code this} {@link Publisher} to an {@link BlockingIterable}. Every time
     * {@link BlockingIterable#iterator()} is called on the returned {@link BlockingIterable}, {@code this}
     * {@link Publisher} is subscribed following the below rules:
     * <ul>
     *     <li>{@link Subscription} received by {@link Subscriber#onSubscribe(PublisherSource.Subscription)} is used to
     *     request more data when required.</li>
     *     <li>Any items received by {@link Subscriber#onNext(Object)} is returned from a call to
     *     {@link BlockingIterator#next()}.</li>
     *     <li>Any {@link Throwable} received by {@link Subscriber#onError(Throwable)} is thrown (wrapped in a
     *     {@link RuntimeException} if required) when {@link BlockingIterator#next()}. This error will be thrown
     *      only after draining all queued data, if any.</li>
     *     <li>When {@link Subscriber#onComplete()} is called, returned {@link BlockingIterator}s
     *     {@link BlockingIterator#hasNext()} will return {@code false} and {@link BlockingIterator#next()} will throw
     *     {@link NoSuchElementException}. This error will be thrown only after draining all queued data, if any.</li>
     * </ul>
     *
     * <h2>Flow control</h2>
     * This operator may pre-fetch items from {@code this} {@link Publisher} if available to reduce blocking of
     * {@link BlockingIterator#hasNext()} from the returned {@link BlockingIterable}. In order to increase
     * responsiveness of the {@link BlockingIterator} some amount of buffering may be done. {@code queueCapacityHint}
     * can be used to bound this buffer.
     *
     * <h2>Blocking</h2>
     * The returned {@link BlockingIterator} from the returned {@link BlockingIterable} will block on
     * {@link BlockingIterator#hasNext()} and {@link BlockingIterator#next()} if no data is available. This operator may
     * try to reduce this blocking by requesting data ahead of time.
     *
     * @param queueCapacityHint Hint for the capacity of the intermediary queue that stores items that are emitted by
     * {@code this} {@link Publisher} but has not yet been returned from the {@link BlockingIterator}.
     *
     * @return {@link BlockingIterable} representing {@code this} {@link Publisher}. Every time
     * {@link BlockingIterable#iterator()} is invoked on the {@link BlockingIterable}, {@code this} {@link Publisher}
     * is subscribed. {@link BlockingIterator}s returned from this {@link BlockingIterable} do not support
     * {@link BlockingIterator#remove()}.
     */
    public final BlockingIterable<T> toIterable(int queueCapacityHint) {
        return new PublisherAsBlockingIterable<>(this, queueCapacityHint);
    }

    //
    // Conversion Operators End
    //

    /**
     * A internal subscribe method similar to {@link PublisherSource#subscribe(Subscriber)} which can be used by
     * different implementations to subscribe.
     *
     * @param subscriber {@link Subscriber} to subscribe for the result.
     */
    protected final void subscribeInternal(Subscriber<? super T> subscriber) {
        AsyncContextProvider provider = AsyncContext.provider();
        subscribeWithContext(subscriber, provider,
                shareContextOnSubscribe() ? provider.contextMap() : provider.contextMap().copy());
    }

    /**
     * Handles a subscriber to this {@code Publisher}.
     *
     * @param subscriber the subscriber.
     */
    protected abstract void handleSubscribe(Subscriber<? super T> subscriber);

    //
    // Static Utility Methods Begin
    //

    /**
     * Creates a new {@link Publisher} that emits {@code value} to its {@link Subscriber} and then
     * {@link Subscriber#onComplete()}.
     *
     * @param value Value that the returned {@link Publisher} will emit.
     * @param <T> Type of items emitted by the returned {@link Publisher}.
     *
     * @return A new {@link Publisher} that emits {@code value} to its {@link Subscriber} and then
     * {@link Subscriber#onComplete()}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/just.html">ReactiveX just operator.</a>
     */
    public static <T> Publisher<T> from(@Nullable T value) {
        return new FromSingleItemPublisher<>(value);
    }

    /**
     * Creates a new {@link Publisher} that emits {@code v1} and {@code v2} to its {@link Subscriber} and then
     * {@link Subscriber#onComplete()}.
     *
     * @param v1 The first value that the returned {@link Publisher} will emit.
     * @param v2 The second value that the returned {@link Publisher} will emit.
     * @param <T> Type of items emitted by the returned {@link Publisher}.
     *
     * @return A new {@link Publisher} that emits {@code v1} and {@code v2} to its {@link Subscriber} and then
     * {@link Subscriber#onComplete()}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/just.html">ReactiveX just operator.</a>
     */
    public static <T> Publisher<T> from(@Nullable T v1, @Nullable T v2) {
        return new FromNPublisher<>(v1, v2);
    }

    /**
     * Creates a new {@link Publisher} that emits {@code v1}, {@code v2}, and {@code v3} to its {@link Subscriber} and
     * then {@link Subscriber#onComplete()}.
     *
     * @param v1 The first value that the returned {@link Publisher} will emit.
     * @param v2 The second value that the returned {@link Publisher} will emit.
     * @param v3 The third value that the returned {@link Publisher} will emit.
     * @param <T> Type of items emitted by the returned {@link Publisher}.
     *
     * @return A new {@link Publisher} that emits {@code v1}, {@code v2}, and {@code v3} to its {@link Subscriber} and
     * then {@link Subscriber#onComplete()}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/just.html">ReactiveX just operator.</a>
     */
    public static <T> Publisher<T> from(@Nullable T v1, @Nullable T v2, @Nullable T v3) {
        return new FromNPublisher<>(v1, v2, v3);
    }

    /**
     * Creates a new {@link Publisher} that emits all {@code values} to its {@link Subscriber} and then
     * {@link Subscriber#onComplete()}.
     *
     * @param values Values that the returned {@link Publisher} will emit.
     * @param <T> Type of items emitted by the returned {@link Publisher}.
     *
     * @return A new {@link Publisher} that emits all {@code values} to its {@link Subscriber} and then
     * {@link Subscriber#onComplete()}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/from.html">ReactiveX from operator.</a>
     */
    @SafeVarargs
    public static <T> Publisher<T> from(T... values) {
        return new FromArrayPublisher<>(values);
    }

    /**
     * Create a new {@link Publisher} that when subscribed will get an {@link Iterator} via {@link Iterable#iterator()}
     * and emit all values to the {@link Subscriber} and then {@link Subscriber#onComplete()}.
     * <p>
     * The Reactive Streams specification provides two criteria (
     * <a href="https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.3/README.md#3.4">3.4</a>, and
     * <a href="https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.3/README.md#3.5">3.5</a>) stating
     * the {@link Subscription} should be "responsive". The responsiveness of the associated {@link Subscription}s will
     * depend upon the behavior of the {@code iterable} below. Make sure the {@link Executor} for this execution chain
     * can tolerate this responsiveness and any blocking behavior.
     * @param <T> Type of items emitted by the returned {@link Publisher}.
     * @param iterable used to obtain instances of {@link Iterator} to extract data from. {@link Iterable#iterator()}
     * must not return {@code null}. If this is of type {@link BlockingIterable} then any generated
     * {@link BlockingIterator}s will have their {@link BlockingIterator#close()} method called if an error
     * occurs.
     * @return a new {@link Publisher} that when subscribed will get an {@link Iterator} via {@link Iterable#iterator()}
     * and emit all values to the {@link Subscriber} and then {@link Subscriber#onComplete()}.
     */
    public static <T> Publisher<T> fromIterable(Iterable<? extends T> iterable) {
        return new FromIterablePublisher<>(iterable);
    }

    /**
     * Create a new {@link Publisher} that when subscribed will get a {@link BlockingIterator} via
     * {@link BlockingIterable#iterator()} and emit all values to the {@link Subscriber} and then
     * {@link Subscriber#onComplete()}.
     * <p>
     * The Reactive Streams specification provides two criteria (
     * <a href="https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.3/README.md#3.4">3.4</a>, and
     * <a href="https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.3/README.md#3.5">3.5</a>) stating
     * the {@link Subscription} should be "responsive". The responsiveness of the associated {@link Subscription}s will
     * depend upon the behavior of the {@code iterable} below. Make sure the {@link Executor} for this execution chain
     * can tolerate this responsiveness and any blocking behavior.
     * @param <T> Type of items emitted by the returned {@link Publisher}.
     * @param iterable used to obtain instances of {@link Iterator} to extract data from. {@link Iterable#iterator()}
     * must not return {@code null}. Any generated {@link BlockingIterator}s will have their
     * {@link BlockingIterator#close()} method called if an error occurs.
     * @param timeoutSupplier A {@link LongSupplier} which provides the time duration to wait for each
     * interaction with {@code iterable}.
     * @param unit The time units for the {@code timeout} duration.
     * @return a new {@link Publisher} that when subscribed will get a {@link BlockingIterator} via
     * {@link BlockingIterable#iterator()} and emit all values to the {@link Subscriber} and then
     * {@link Subscriber#onComplete()}.
     */
    public static <T> Publisher<T> fromBlockingIterable(BlockingIterable<? extends T> iterable,
                                                        LongSupplier timeoutSupplier,
                                                        TimeUnit unit) {
        return new FromBlockingIterablePublisher<>(iterable, timeoutSupplier, unit);
    }

    /**
     * Create a new {@link Publisher} that when subscribed will emit all data from the {@link InputStream} to the
     * {@link Subscriber} and then {@link Subscriber#onComplete()}.
     * <p>
     * The Reactive Streams specification provides two criteria (
     * <a href="https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.3/README.md#3.4">3.4</a>, and
     * <a href="https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.3/README.md#3.5">3.5</a>) stating
     * the {@link Subscription} should be "responsive". The responsiveness of the associated {@link Subscription}s will
     * depend upon the behavior of the {@code stream} below. Make sure the {@link Executor} for this execution chain
     * can tolerate this responsiveness and any blocking behavior.
     * @param stream provides the data in the form of {@code byte[]} to be emitted to the {@link Subscriber} by the
     * returned {@link Publisher}. Given the blocking nature of {@link InputStream}, assume {@link
     * Subscription#request(long)} can block when the underlying {@link InputStream} blocks on {@link
     * InputStream#read(byte[], int, int)}.
     * @return a new {@link Publisher} that when subscribed will emit all data from the {@link InputStream} to the
     * {@link Subscriber} and then {@link Subscriber#onComplete()}.
     */
    public static Publisher<byte[]> fromInputStream(InputStream stream) {
        return new FromInputStreamPublisher(stream);
    }

    /**
     * Create a new {@link Publisher} that when subscribed will emit all data from the {@link InputStream} to the
     * {@link Subscriber} and then {@link Subscriber#onComplete()}.
     * <p>
     * The Reactive Streams specification provides two criteria (
     * <a href="https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.3/README.md#3.4">3.4</a>, and
     * <a href="https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.3/README.md#3.5">3.5</a>) stating
     * the {@link Subscription} should be "responsive". The responsiveness of the associated {@link Subscription}s will
     * depend upon the behavior of the {@code stream} below. Make sure the {@link Executor} for this execution chain
     * can tolerate this responsiveness and any blocking behavior.
     * @param stream provides the data in the form of {@code byte[]} to be emitted to the {@link Subscriber} by the
     * returned {@link Publisher}. Given the blocking nature of {@link InputStream}, assume {@link
     * Subscription#request(long)} can block when the underlying {@link InputStream} blocks on {@link
     * InputStream#read(byte[], int, int)}.
     * @param readChunkSize the maximum length of {@code byte[]} chunks which will be read from the {@link InputStream}
     * and emitted by the returned {@link Publisher}.
     * @return a new {@link Publisher} that when subscribed will emit all data from the {@link InputStream} to the
     * {@link Subscriber} and then {@link Subscriber#onComplete()}.
     */
    public static Publisher<byte[]> fromInputStream(InputStream stream, int readChunkSize) {
        return new FromInputStreamPublisher(stream, readChunkSize);
    }

    /**
     * Create a new {@link Publisher} that when subscribed will emit all {@link Integer}s within the range of
     * [{@code begin}, {@code end}).
     * @param begin The beginning of the range (inclusive).
     * @param end The end of the range (exclusive).
     * @return a new {@link Publisher} that when subscribed will emit all {@link Integer}s within the range of
     * [{@code begin}, {@code end}).
     * @see <a href="http://reactivex.io/documentation/operators/range.html">Range.</a>
     */
    public static Publisher<Integer> range(int begin, int end) {
        return new RangeIntPublisher(begin, end);
    }

    /**
     * Create a new {@link Publisher} that when subscribed will emit all {@link Integer}s within the range of
     * [{@code begin}, {@code end}) with an increment of {@code stride} between each signal.
     * @param begin The beginning of the range (inclusive).
     * @param end The end of the range (exclusive).
     * @param stride The amount to increment in between each signal.
     * @return a new {@link Publisher} that when subscribed will emit all {@link Integer}s within the range of
     * [{@code begin}, {@code end}) with an increment of {@code stride} between each signal.
     * @see <a href="http://reactivex.io/documentation/operators/range.html">Range.</a>
     */
    public static Publisher<Integer> range(int begin, int end, int stride) {
        return new RangeIntPublisher(begin, end, stride);
    }

    /**
     * Creates a new {@link Publisher} that completes when subscribed without emitting any item to its
     * {@link Subscriber}.
     *
     * @param <T> Type of items emitted by the returned {@link Publisher}.
     * @return A new {@link Publisher} that completes when subscribed without emitting any item to its
     * {@link Subscriber}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/empty-never-throw.html">ReactiveX empty operator.</a>
     */
    public static <T> Publisher<T> empty() {
        return emptyPublisher();
    }

    /**
     * Creates a new {@link Publisher} that never emits any item to its {@link Subscriber} and never call any terminal
     * methods on it.
     *
     * @param <T> Type of items emitted by the returned {@link Publisher}.
     * @return A new {@link Publisher} that never emits any item to its {@link Subscriber} and never call any terminal
     * methods on it.
     *
     * @see <a href="http://reactivex.io/documentation/operators/empty-never-throw.html">ReactiveX never operator.</a>
     */
    public static <T> Publisher<T> never() {
        return neverPublisher();
    }

    /**
     * Creates a new {@link Publisher} that terminates its {@link Subscriber} with an error without emitting any item to
     * it.
     *
     * @param <T> Type of items emitted by the returned {@link Publisher}.
     * @param cause The {@link Throwable} that is used to terminate the {@link Subscriber}.
     * @return A new {@link Publisher} that terminates its {@link Subscriber} with an error without emitting any item to
     * it.
     *
     * @see <a href="http://reactivex.io/documentation/operators/empty-never-throw.html">ReactiveX error operator.</a>
     */
    public static <T> Publisher<T> failed(Throwable cause) {
        return new ErrorPublisher<>(cause);
    }

    /**
     * Defers creation of a {@link Publisher} till it is subscribed.
     *
     * @param publisherSupplier {@link Supplier} to create a new {@link Publisher} every time the returned
     * {@link Publisher} is subscribed.
     * @param <T> Type of items emitted by the returned {@link Publisher}.
     * @return A new {@link Publisher} that creates a new {@link Publisher} using {@code publisherSupplier} every time
     * it is subscribed and forwards all items and terminal events from the newly created {@link Publisher} to its
     * {@link Subscriber}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/defer.html">ReactiveX defer operator.</a>
     */
    public static <T> Publisher<T> defer(Supplier<? extends Publisher<? extends T>> publisherSupplier) {
        return new PublisherDefer<>(publisherSupplier);
    }

    //
    // Static Utility Methods End
    //

    //
    // Internal Methods Begin
    //

    /**
     * Subscribes to this {@link Single} and shares the current context.
     *
     * @param subscriber the subscriber.
     */
    final void subscribeWithSharedContext(Subscriber<? super T> subscriber) {
        AsyncContextProvider provider = AsyncContext.provider();
        subscribeWithContext(subscriber, provider, provider.contextMap());
    }

    /**
     * Delegate subscribe calls in an operator chain. This method is used by operators to subscribe to the upstream
     * source.
     *
     * @param subscriber the subscriber.
     * @param signalOffloader {@link SignalOffloader} to use for this {@link Subscriber}.
     * @param contextMap the {@link AsyncContextMap} to use for this {@link Subscriber}.
     * @param contextProvider the {@link AsyncContextProvider} used to wrap any objects to preserve
     * {@link AsyncContextMap}.
     */
    final void delegateSubscribe(Subscriber<? super T> subscriber, SignalOffloader signalOffloader,
                                 AsyncContextMap contextMap, AsyncContextProvider contextProvider) {
        handleSubscribe(subscriber, signalOffloader, contextMap, contextProvider);
    }

    private void subscribeWithContext(Subscriber<? super T> subscriber, AsyncContextProvider provider,
                                      AsyncContextMap contextMap) {
        requireNonNull(subscriber);
        final SignalOffloader signalOffloader;
        final Subscriber<? super T> offloadedSubscriber;
        try {
            // This is a user-driven subscribe i.e. there is no SignalOffloader override, so create a new
            // SignalOffloader to use.
            signalOffloader = newOffloaderFor(executor());
            // Since this is a user-driven subscribe (end of the execution chain), offload subscription methods
            // We also want to make sure the AsyncContext is saved/restored for all interactions with the Subscription.
            offloadedSubscriber =
                    signalOffloader.offloadSubscription(provider.wrapSubscription(subscriber, contextMap));
        } catch (Throwable t) {
            deliverErrorFromSource(subscriber, t);
            return;
        }
        signalOffloader.offloadSubscribe(offloadedSubscriber, provider.wrapConsumer(
                s -> handleSubscribe(s, signalOffloader, contextMap, provider), contextMap));
    }

    /**
     * Override for {@link #handleSubscribe(PublisherSource.Subscriber)} to offload the
     * {@link #handleSubscribe(PublisherSource.Subscriber)} call to the passed {@link SignalOffloader}.
     * <p>
     * This method wraps the passed {@link Subscriber} using {@link SignalOffloader#offloadSubscriber(Subscriber)} and
     * then calls {@link #handleSubscribe(PublisherSource.Subscriber)} using
     * {@link SignalOffloader#offloadSubscribe(Subscriber, Consumer)}.
     * Operators that do not wish to wrap the passed {@link Subscriber} can override this method and omit the wrapping.
     *
     * @param subscriber the subscriber.
     * @param signalOffloader {@link SignalOffloader} to use for this {@link Subscriber}.
     * @param contextMap the {@link AsyncContextMap} to use for this {@link Subscriber}.
     * @param contextProvider the {@link AsyncContextProvider} used to wrap any objects to preserve
     * {@link AsyncContextMap}.
     */
    void handleSubscribe(Subscriber<? super T> subscriber, SignalOffloader signalOffloader, AsyncContextMap contextMap,
                         AsyncContextProvider contextProvider) {
        try {
            Subscriber<? super T> offloaded =
                    signalOffloader.offloadSubscriber(contextProvider.wrapPublisherSubscriber(subscriber, contextMap));
            handleSubscribe(offloaded);
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

    /**
     * Returns the {@link Executor} used for this {@link Publisher}.
     *
     * @return {@link Executor} used for this {@link Publisher}.
     */
    Executor executor() {
        return immediate();
    }

    /**
     * Returns true if the async context should be shared on subscribe otherwise false if the async context will be
     * copied.
     *
     * @return true if the async context should be shared on subscribe
     */
    boolean shareContextOnSubscribe() {
        return false;
    }

    //
    // Internal Methods End
    //
}
