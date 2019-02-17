/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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

import static io.servicetalk.concurrent.api.EmptyPublisher.emptyPublisher;
import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.NeverPublisher.neverPublisher;
import static io.servicetalk.concurrent.api.PublisherDoOnUtils.doOnCancelSupplier;
import static io.servicetalk.concurrent.api.PublisherDoOnUtils.doOnCompleteSupplier;
import static io.servicetalk.concurrent.api.PublisherDoOnUtils.doOnErrorSupplier;
import static io.servicetalk.concurrent.api.PublisherDoOnUtils.doOnNextSupplier;
import static io.servicetalk.concurrent.api.PublisherDoOnUtils.doOnRequestSupplier;
import static io.servicetalk.concurrent.api.PublisherDoOnUtils.doOnSubscribeSupplier;
import static io.servicetalk.concurrent.internal.EmptySubscription.EMPTY_SUBSCRIPTION;
import static io.servicetalk.concurrent.internal.SignalOffloaders.newOffloaderFor;
import static java.util.Objects.requireNonNull;

/**
 * An asynchronous computation that produces 0, 1 or more elements and may or may not terminate successfully or with
 * an error.
 *
 * @param <T> Type of items emitted.
 */
public abstract class Publisher<T> implements PublisherSource<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(Publisher.class);

    private final Executor executor;

    static {
        AsyncContext.autoEnable();
    }

    /**
     * New instance.
     */
    protected Publisher() {
        this(immediate());
    }

    /**
     * New instance.
     *
     * @param executor {@link Executor} to use for this {@link Publisher}.
     */
    Publisher(Executor executor) {
        this.executor = requireNonNull(executor);
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
        return new MapPublisher<>(this, mapper, executor);
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
    public final Publisher<T> filter(Predicate<T> predicate) {
        return new FilterPublisher<>(this, predicate, executor);
    }

    /**
     * Ignores any error returned by this {@link Publisher} and resume to a new {@link Publisher}.
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
     * @return A {@link Publisher} that ignores error from this {@code Publisher} and resume with the {@link Publisher}
     * produced by {@code nextFactory}.
     * @see <a href="http://reactivex.io/documentation/operators/catch.html">ReactiveX catch operator.</a>
     */
    public final Publisher<T> onErrorResume(Function<? super Throwable, Publisher<? extends T>> nextFactory) {
        return new ResumePublisher<>(this, nextFactory, executor);
    }

    /**
     * Turns every item emitted by this {@link Publisher} into a {@link Single} and emits the items emitted by each of
     * those {@link Single}s.
     * <p>
     * To control the amount of concurrent processing done by this operator see {@link #flatMapSingle(Function, int)}.
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
     * @param mapper Function to convert each item emitted by this {@link Publisher} into a {@link Single}.
     * @param <R> Type of items emitted by the returned {@link Publisher}.
     * @return A new {@link Publisher} that emits all items emitted by each single produced by {@code mapper}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/flatmap.html">ReactiveX flatMap operator.</a>
     * @see #flatMapSingle(Function, int)
     */
    public final <R> Publisher<R> flatMapSingle(Function<? super T, Single<? extends R>> mapper) {
        return new PublisherFlatMapSingle<>(this, mapper, false, executor);
    }

    /**
     * Turns every item emitted by this {@link Publisher} into a {@link Single} and emits the items emitted by each of
     * those {@link Single}s.
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
     * @param mapper Function to convert each item emitted by this {@link Publisher} into a {@link Single}.
     * @param maxConcurrency Maximum active {@link Single}s at any time.
     * Even if the number of items requested by a {@link Subscriber} is more than this number, this will never request
     * more than this number at any point.
     * @param <R> Type of items emitted by the returned {@link Publisher}.
     * @return A new {@link Publisher} that emits all items emitted by each single produced by {@code mapper}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/flatmap.html">ReactiveX flatMap operator.</a>
     */
    public final <R> Publisher<R> flatMapSingle(Function<? super T, Single<? extends R>> mapper, int maxConcurrency) {
        return new PublisherFlatMapSingle<>(this, mapper, maxConcurrency, false, executor);
    }

    /**
     * Turns every item emitted by this {@link Publisher} into a {@link Single} and emits the items emitted by each of
     * those {@link Single}s. This is the same as {@link #flatMapSingle(Function, int)} just that if any {@link Single}
     * returned by {@code mapper}, terminates with an error, the returned {@link Publisher} will not immediately
     * terminate. Instead, it will wait for this {@link Publisher} and all {@link Single}s to terminate and then
     * terminate the returned {@link Publisher} with all errors emitted by the {@link Single}s produced by the
     * {@code mapper}.
     * <p>
     * To control the amount of concurrent processing done by this operator see
     * {@link #flatMapSingleDelayError(Function, int)}.
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
     * @param mapper Function to convert each item emitted by this {@link Publisher} into a {@link Single}.
     * @param <R> Type of items emitted by the returned {@link Publisher}.
     * @return A new {@link Publisher} that emits all items emitted by each single produced by {@code mapper}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX merge operator.</a>
     * @see #flatMapSingleDelayError(Function, int)
     */
    public final <R> Publisher<R> flatMapSingleDelayError(Function<? super T, Single<? extends R>> mapper) {
        return new PublisherFlatMapSingle<>(this, mapper, true, executor);
    }

    /**
     * Turns every item emitted by this {@link Publisher} into a {@link Single} and emits the items emitted by each of
     * those {@link Single}s. This is the same as {@link #flatMapSingle(Function, int)} just that if any {@link Single}
     * returned by {@code mapper}, terminates with an error, the returned {@link Publisher} will not immediately
     * terminate. Instead, it will wait for this {@link Publisher} and all {@link Single}s to terminate and then
     * terminate the returned {@link Publisher} with all errors emitted by the {@link Single}s produced by the
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
     * @param mapper Function to convert each item emitted by this {@link Publisher} into a {@link Single}.
     * @param maxConcurrency Maximum active {@link Single}s at any time.
     * Even if the number of items requested by a {@link Subscriber} is more than this number,
     * this will never request more than this number at any point.
     * @param <R> Type of items emitted by the returned {@link Publisher}.
     * @return A new {@link Publisher} that emits all items emitted by each single produced by {@code mapper}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX merge operator.</a>
     */
    public final <R> Publisher<R> flatMapSingleDelayError(Function<? super T, Single<? extends R>> mapper,
                                                          int maxConcurrency) {
        return new PublisherFlatMapSingle<>(this, mapper, maxConcurrency, true, executor);
    }

    /**
     * Turns every item emitted by this {@link Publisher} into a {@link Completable} and terminate the returned
     * {@link Completable} when all the intermediate {@link Completable}s have terminated successfully or any one of
     * them has terminated with a failure.
     * If the returned {@link Completable} should wait for the termination of all remaining {@link Completable}s when
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
     * @param mapper Function to convert each item emitted by this {@link Publisher} into a {@link Completable}.
     * @return A new {@link Completable} that terminates successfully if all the intermediate {@link Completable}s have
     * terminated successfully or any one of them has terminated with a failure.
     *
     * @see <a href="http://reactivex.io/documentation/operators/flatmap.html">ReactiveX flatMap operator.</a>
     * @see #flatMapCompletable(Function, int)
     * @see #flatMapCompletableDelayError(Function)
     */
    public final Completable flatMapCompletable(Function<? super T, Completable> mapper) {
        return flatMapSingle(t -> mapper.apply(t).toSingle()).ignoreElements();
    }

    /**
     * Turns every item emitted by this {@link Publisher} into a {@link Completable} and terminate the returned
     * {@link Completable} when all the intermediate {@link Completable}s have terminated successfully or any one of
     * them has terminated with a failure.
     * If the returned {@link Completable} should wait for the termination of all remaining {@link Completable}s when
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
    public final Completable flatMapCompletable(Function<? super T, Completable> mapper, int maxConcurrency) {
        return flatMapSingle(t -> mapper.apply(t).toSingle(), maxConcurrency).ignoreElements();
    }

    /**
     * Turns every item emitted by this {@link Publisher} into a {@link Completable} and terminate the returned
     * {@link Completable} when all the intermediate {@link Completable}s have terminated. If any {@link Completable}
     * returned by {@code mapper}, terminates with an error, the returned {@link Completable} will not immediately
     * terminate. Instead, it will wait for this {@link Publisher} and all {@link Completable}s to terminate and then
     * terminate the returned {@link Completable} with all errors emitted by the {@link Completable}s produced by the
     * {@code mapper}.
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
     * @see #flatMapSingleDelayError(Function, int)
     */
    public final Completable flatMapCompletableDelayError(Function<? super T, Completable> mapper) {
        return flatMapSingleDelayError(t -> mapper.apply(t).toSingle()).ignoreElements();
    }

    /**
     * Turns every item emitted by this {@link Publisher} into a {@link Completable} and terminate the returned
     * {@link Completable} when all the intermediate {@link Completable}s have terminated.If any {@link Completable}
     * returned by {@code mapper}, terminates with an error, the returned {@link Completable} will not immediately
     * terminate. Instead, it will wait for this {@link Publisher} and all {@link Completable}s to terminate and then
     * terminate the returned {@link Completable} with all errors emitted by the {@link Completable}s produced by the
     * {@code mapper}.
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
     * @see #flatMapSingleDelayError(Function, int)
     */
    public final Completable flatMapCompletableDelayError(Function<? super T, Completable> mapper, int maxConcurrency) {
        return flatMapSingleDelayError(t -> mapper.apply(t).toSingle(), maxConcurrency).ignoreElements();
    }

    /**
     * Create a {@link Publisher} that flattens each element returned by the {@link Iterable#iterator()} from
     * {@code mapper}.
     * <p>
     * Note that {@code flatMap} operators may process input in parallel, provide results as they become available, and
     * may interleave results from multiple {@link Iterator}s. If ordering is required see
     * {@link #concatMapIterable(Function)}.
     * <p>
     * This method provides similar capabilities as expanding each result into a collection and concatenating each
     * collection concurrently in sequential programming:
     * <pre>{@code
     *     ExecutorService e = ...;
     *     List<Future<List<R>> futures = ...; // assume this is thread safe
     *     for (T t : resultOfThisPublisher()) {
     *         // Note that flatMap process results in parallel.
     *         futures.add(e.submit(() -> {
     *             List<R> results = new ArrayList<>();
     *             Iterable<? extends R> itr = mapper.apply(t);
     *             itr.forEach(results::add);
     *             return results;
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
     * @param mapper A {@link Function} that returns an {@link Iterable} for each element.
     * @param <R> The elements returned by the {@link Iterable}.
     * @return a {@link Publisher} that flattens each element returned by the {@link Iterable#iterator()} from
     * {@code mapper}. Data is processed concurrently, the results are not necessarily ordered, and will depend upon
     * completion order of the {@link Iterator}s.
     * @see <a href="http://reactivex.io/documentation/operators/flatmap.html">ReactiveX FlatMap operator.</a>
     */
    public final <R> Publisher<R> flatMapIterable(Function<? super T, ? extends Iterable<? extends R>> mapper) {
        // TODO(scott): implement the flatMap variant.
        return concatMapIterable(mapper);
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
    public final <R> Publisher<R> concatMapIterable(Function<? super T, ? extends Iterable<? extends R>> mapper) {
        return new PublisherConcatMapIterable<>(this, mapper, executor);
    }

    /**
     * Invokes the {@code onNext} {@link Consumer} argument when {@link Subscriber#onNext(Object)} is called for
     * {@link Subscriber}s of the returned {@link Publisher}.
     * <p>
     * The order in which {@code onNext} will be invoked relative to {@link Subscriber#onNext(Object)} is undefined. If
     * you need strict ordering see {@link #doBeforeNext(Consumer)} and {@link #doAfterNext(Consumer)}.
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
     * @see #doBeforeNext(Consumer)
     * @see #doAfterNext(Consumer)
     */
    public final Publisher<T> doOnNext(Consumer<T> onNext) {
        return doAfterNext(onNext);
    }

    /**
     * Invokes the {@code onComplete} {@link Runnable} argument when {@link Subscriber#onComplete()} is called for
     * {@link Subscriber}s of the returned {@link Publisher}.
     * <p>
     * The order in which {@code onComplete} will be invoked relative to {@link Subscriber#onComplete()} is undefined.
     * If you need strict ordering see {@link #doBeforeComplete(Runnable)} and {@link #doAfterComplete(Runnable)}.
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
     * @see #doBeforeComplete(Runnable)
     * @see #doAfterComplete(Runnable)
     */
    public final Publisher<T> doOnComplete(Runnable onComplete) {
        return doAfterComplete(onComplete);
    }

    /**
     * Invokes the {@code onError} {@link Consumer} argument when {@link Subscriber#onError(Throwable)} is called for
     * {@link Subscriber}s of the returned {@link Publisher}.
     * <p>
     * The order in which {@code onError} will be invoked relative to {@link Subscriber#onError(Throwable)} is
     * undefined. If you need strict ordering see {@link #doBeforeError(Consumer)} and
     * {@link #doAfterError(Consumer)}.
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
     * @see #doBeforeError(Consumer)
     * @see #doAfterError(Consumer)
     */
    public final Publisher<T> doOnError(Consumer<Throwable> onError) {
        return doAfterError(onError);
    }

    /**
     * Invokes the {@code doFinally} {@link Runnable} argument exactly once, when any of the following terminal methods
     * are called:
     * <ul>
     *     <li>{@link Subscriber#onComplete()}</li>
     *     <li>{@link Subscriber#onError(Throwable)}</li>
     *     <li>{@link Subscription#cancel()}</li>
     * </ul>
     * for {@link Subscription}s/{@link Subscriber}s of the returned {@link Publisher}.
     * <p>
     * The order in which {@code doFinally} will be invoked relative to the above methods is undefined. If you need
     * strict ordering see {@link #doBeforeFinally(Runnable)} and {@link #doAfterFinally(Runnable)}.
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
     * @see #doAfterFinally(Runnable)
     * @see #doBeforeFinally(Runnable)
     */
    public final Publisher<T> doFinally(Runnable doFinally) {
        return doAfterFinally(doFinally);
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
    public final Publisher<T> doOnRequest(LongConsumer onRequest) {
        return doAfterRequest(onRequest);
    }

    /**
     * Invokes the {@code onCancel} {@link Runnable} argument when {@link Subscription#cancel()} is called for
     * Subscriptions of the returned {@link Publisher}.
     * <p>
     * The order in which {@code doFinally} will be invoked relative to {@link Subscription#cancel()} is undefined. If
     * you need strict ordering see {@link #doBeforeCancel(Runnable)} and {@link #doAfterCancel(Runnable)}.
     * @param onCancel Invoked when {@link Subscription#cancel()} is called for Subscriptions of the returned
     * {@link Publisher}. <strong>MUST NOT</strong> throw.
     * @return The new {@link Publisher}.
     * @see #doBeforeCancel(Runnable)
     * @see #doAfterCancel(Runnable)
     */
    public final Publisher<T> doOnCancel(Runnable onCancel) {
        return doAfterCancel(onCancel);
    }

    /**
     * Creates a new {@link Publisher} that will mimic the signals of this {@link Publisher} but will terminate with a
     * {@link TimeoutException} if time {@code duration} elapses between adjacent {@link Subscriber#onNext(Object)}
     * calls. The timer starts when the returned {@link Publisher} is {@link #subscribe(Subscriber) subscribed} to.
     * <p>
     * In the event of timeout any {@link Subscription} from
     * {@link Subscriber#onSubscribe(PublisherSource.Subscription)} will be {@link Subscription#cancel() cancelled} and
     * the associated {@link Subscriber} will be {@link Subscriber#onError(Throwable) terminated}.
     * @param duration The time duration which is allowed to elapse between {@link Subscriber#onNext(Object)} calls.
     * @param unit The units for {@code duration}.
     * @return a new {@link Publisher} that will mimic the signals of this {@link Publisher} but will terminate with a
     * {@link TimeoutException} if time {@code duration} elapses between {@link Subscriber#onNext(Object)} calls.
     * @see <a href="http://reactivex.io/documentation/operators/timeout.html">ReactiveX timeout operator.</a>
     * @see #timeout(long, TimeUnit, Executor)
     */
    public final Publisher<T> timeout(long duration, TimeUnit unit) {
        return new TimeoutPublisher<>(this, executor, duration, unit);
    }

    /**
     * Creates a new {@link Publisher} that will mimic the signals of this {@link Publisher} but will terminate with a
     * {@link TimeoutException} if time {@code duration} elapses between adjacent {@link Subscriber#onNext(Object)}
     * calls. The timer starts when the returned {@link Publisher} is {@link #subscribe(Subscriber) subscribed} to.
     * <p>
     * In the event of timeout any {@link Subscription} from
     * {@link Subscriber#onSubscribe(PublisherSource.Subscription)} will be {@link Subscription#cancel() cancelled}
     * and the associated {@link Subscriber} will be {@link Subscriber#onError(Throwable) terminated}.
     * @param duration The time duration which is allowed to elapse between {@link Subscriber#onNext(Object)} calls.
     * @return a new {@link Publisher} that will mimic the signals of this {@link Publisher} but will terminate with a
     * {@link TimeoutException} if time {@code duration} elapses between {@link Subscriber#onNext(Object)} calls.
     * @see <a href="http://reactivex.io/documentation/operators/timeout.html">ReactiveX timeout operator.</a>
     * @see #timeout(long, TimeUnit, Executor)
     */
    public final Publisher<T> timeout(Duration duration) {
        return new TimeoutPublisher<>(this, executor, duration);
    }

    /**
     * Creates a new {@link Publisher} that will mimic the signals of this {@link Publisher} but will terminate with a
     * {@link TimeoutException} if time {@code duration} elapses between adjacent {@link Subscriber#onNext(Object)}
     * calls. The timer starts when the returned {@link Publisher} is {@link #subscribe(Subscriber) subscribed} to.
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
    public final Publisher<T> timeout(long duration, TimeUnit unit, Executor timeoutExecutor) {
        return new TimeoutPublisher<>(this, executor, duration, unit, timeoutExecutor);
    }

    /**
     * Creates a new {@link Publisher} that will mimic the signals of this {@link Publisher} but will terminate with a
     * {@link TimeoutException} if time {@code duration} elapses between adjacent {@link Subscriber#onNext(Object)}
     * calls. The timer starts when the returned {@link Publisher} is {@link #subscribe(Subscriber) subscribed} to.
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
    public final Publisher<T> timeout(Duration duration, Executor timeoutExecutor) {
        return new TimeoutPublisher<>(this, executor, duration, timeoutExecutor);
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
    public final Publisher<T> concatWith(Publisher<? extends T> next) {
        return new ConcatPublisher<>(this, next, executor);
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
    public final Publisher<T> concatWith(Single<? extends T> next) {
        return new ConcatPublisher<>(this, next.toPublisher(), executor);
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
    public final Publisher<T> concatWith(Completable next) {
        // We can not use next.toPublisher() here as that returns Publisher<Void> which can not be concatenated with
        // Publisher<T>
        return new ConcatPublisher<>(this, next.concatWith(empty()), executor);
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
                (retryCount, terminalNotification) -> terminalNotification.getCause() != null &&
                        shouldRetry.test(retryCount, terminalNotification.getCause()),
                executor);
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
    public final Publisher<T> retryWhen(BiIntFunction<Throwable, Completable> retryWhen) {
        return new RedoWhenPublisher<>(this, (retryCount, notification) -> {
            if (notification.getCause() == null) {
                return Completable.completed();
            }
            return retryWhen.apply(retryCount, notification.getCause());
        }, true, executor);
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
                (repeatCount, terminalNotification) -> terminalNotification.getCause() == null &&
                        shouldRepeat.test(repeatCount),
                executor);
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
    public final Publisher<T> repeatWhen(IntFunction<Completable> repeatWhen) {
        return new RedoWhenPublisher<>(this, (retryCount, notification) -> {
            if (notification.getCause() != null) {
                return Completable.completed();
            }
            return repeatWhen.apply(retryCount);
        }, false, executor);
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
    public final Publisher<T> take(long numElements) {
        return new TakeNPublisher<>(this, numElements, executor);
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
    public final Publisher<T> takeWhile(Predicate<T> predicate) {
        return new TakeWhilePublisher<>(this, predicate, executor);
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
        return new TakeUntilPublisher<>(this, until, executor);
    }

    /**
     * Splits items from this {@link Publisher} into dynamically generated {@link GroupedPublisher}s.
     * Item to group association is done by {@code keySelector} {@link Function}. If the selector selects a key which is
     * previously seen and its associated {@link Subscriber} has not yet cancelled its {@link Subscription}, this item
     * is sent to that {@link Subscriber}. Otherwise a new {@link GroupedPublisher} is created and emitted from the
     * returned {@link Publisher}.
     *
     * <h2>Flow control</h2>
     * Multiple {@link Subscriber}s (for multiple {@link GroupedPublisher}s) request items individually from this
     * {@link Publisher}. Since, there is no way for a {@link Subscriber} to only request elements for its group,
     * elements requested by one group may end up producing items for a different group, which has not yet requested
     * enough. This will cause items to be queued per group which can not be emitted due to lack of demand. This queue
     * size can be controlled with the {@code maxQueuePerGroup} argument.
     *
     * <h2>Cancellation</h2>
     *
     * If the {@link Subscriber} of the returned {@link Publisher} cancels its {@link Subscription}, then all active
     * {@link GroupedPublisher}s will be terminated with an error and the {@link Subscription} to this {@link Publisher}
     * will be cancelled.
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
     * @param groupMaxQueueSize Maximum number of new groups that will be queued due to the {@link Subscriber} of the
     * {@link Publisher} returned from this method not requesting enough via {@link Subscription#request(long)}.
     * @param <Key> Type of {@link GroupedPublisher} keys.
     * @return A {@link Publisher} that emits {@link GroupedPublisher}s for new {@code key}s as emitted by
     * {@code keySelector} {@link Function}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/groupby.html">ReactiveX groupBy operator.</a>
     */
    public final <Key> Publisher<GroupedPublisher<Key, T>> groupBy(Function<T, Key> keySelector,
                                                                   int groupMaxQueueSize) {
        return new PublisherGroupBy<>(this, keySelector, groupMaxQueueSize, executor);
    }

    /**
     * Splits items from this {@link Publisher} into dynamically generated {@link GroupedPublisher}s. Item to group
     * association is done by {@code keySelector} {@link Function}. If the selector selects a key which is previously
     * seen and its associated {@link Subscriber} has not yet cancelled its {@link Subscription}, this item is sent to
     * that {@link Subscriber}. Otherwise a new {@link GroupedPublisher} is created and emitted from the returned
     * {@link Publisher}.
     *
     * <h2>Flow control</h2>
     * Multiple {@link Subscriber}s (for multiple {@link GroupedPublisher}s) request items individually from this
     * {@link Publisher}. Since, there is no way for a {@link Subscriber} to only request elements for its group,
     * elements requested by one group may end up producing items for a different group, which has not yet requested
     * enough. This will cause items to be queued per group which can not be emitted due to lack of demand. This queue
     * size can be controlled with the {@code maxQueuePerGroup} argument.
     *
     * <h2>Cancellation</h2>
     *
     * If the {@link Subscriber} of the returned {@link Publisher} cancels its {@link Subscription}, then all active
     * {@link GroupedPublisher}s will be terminated with an error and the {@link Subscription} to this {@link Publisher}
     * will be cancelled.
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
     * @param groupMaxQueueSize Maximum number of new groups that will be queued due to the {@link Subscriber} of the
     * {@link Publisher} returned from this method not requesting enough via {@link Subscription#request(long)}.
     * @param expectedGroupCountHint Expected number of groups that would be emitted by {@code this} {@link Publisher}.
     * This is just a hint for internal data structures and does not have to be precise.
     * @param <Key> Type of {@link GroupedPublisher} keys.
     * @return A {@link Publisher} that emits {@link GroupedPublisher}s for new {@code key}s as emitted by
     * {@code keySelector} {@link Function}.
     * @see <a href="http://reactivex.io/documentation/operators/groupby.html">ReactiveX groupBy operator.</a>
     */
    public final <Key> Publisher<GroupedPublisher<Key, T>> groupBy(Function<T, Key> keySelector, int groupMaxQueueSize,
                                                                   int expectedGroupCountHint) {
        return new PublisherGroupBy<>(this, keySelector, groupMaxQueueSize, expectedGroupCountHint, executor);
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
     * @param groupMaxQueueSize Maximum number of new groups that will be queued due to the {@link Subscriber} of the
     * {@link Publisher} returned from this method not requesting enough via {@link Subscription#request(long)}.
     * @param <Key> Type of {@link GroupedPublisher} keys.
     * @return A {@link Publisher} that emits {@link GroupedPublisher}s for new {@code key}s as emitted by
     * {@code keySelector} {@link Function}.
     * @see #groupBy(Function, int)
     */
    public final <Key> Publisher<GroupedPublisher<Key, T>> groupByMulti(Function<T, Iterator<Key>> keySelector,
                                                                        int groupMaxQueueSize) {
        return new PublisherGroupByMulti<>(this, keySelector, groupMaxQueueSize, executor);
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
     * @param groupMaxQueueSize Maximum number of new groups that will be queued due to the {@link Subscriber} of the
     * {@link Publisher} returned from this method not requesting enough via {@link Subscription#request(long)}.
     * @param expectedGroupCountHint Expected number of groups that would be emitted by {@code this} {@link Publisher}.
     * This is just a hint for internal data structures and does not have to be precise.
     * @param <Key> Type of {@link GroupedPublisher} keys.
     * @return A {@link Publisher} that emits {@link GroupedPublisher}s for new {@code key}s as emitted by
     * {@code keySelector} {@link Function}.
     * @see #groupBy(Function, int)
     */
    public final <Key> Publisher<GroupedPublisher<Key, T>> groupByMulti(Function<T, Iterator<Key>> keySelector,
                                                                        int groupMaxQueueSize,
                                                                        int expectedGroupCountHint) {
        return new PublisherGroupByMulti<>(this, keySelector, groupMaxQueueSize, expectedGroupCountHint, executor);
    }

    /**
     * Create a {@link Publisher} that allows exactly {@code expectedSubscribers} calls to {@link #subscribe(Subscriber)}.
     * The events from this {@link Publisher} object will be delivered to each {@link Subscriber}.
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
     *
     * @param expectedSubscribers The number of expected {@link #subscribe(Subscriber)} calls required on the returned
     * {@link Publisher} before calling {@link #subscribe(Subscriber)} on this {@link Publisher}.
     * @return a {@link Publisher} that allows exactly {@code expectedSubscribers} calls to
     * {@link #subscribe(Subscriber)}.
     */
    public final Publisher<T> multicast(int expectedSubscribers) {
        return new MulticastPublisher<>(this, expectedSubscribers, executor);
    }

    /**
     * Create a {@link Publisher} that allows exactly {@code expectedSubscribers} calls to
     * {@link #subscribe(Subscriber)}. The events from this {@link Publisher} object will be delivered to each
     * {@link Subscriber}.
     * <p>
     * Depending on {@link Subscription#request(long)} demand it is possible that data maybe queued before being
     * delivered to each {@link Subscriber}! For example if there are 2 {@link Subscriber}s and the first calls
     * {@link Subscription#request(long) request(10)}, and the second only calls
     * {@link Subscription#request(long) request(10)}, then 9 elements will be queued to deliver to second when more
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
     *
     * @param expectedSubscribers The number of expected {@link #subscribe(Subscriber)} calls required on the returned
     *          {@link Publisher} before calling {@link #subscribe(Subscriber)} on this {@link Publisher}.
     * @param maxQueueSize The maximum number of {@link Subscriber#onNext(Object)} events that will be queued if there
     * is no demand for data before the {@link Subscriber} will be discarded.
     * @return a {@link Publisher} that allows exactly {@code expectedSubscribers} calls to
     * {@link #subscribe(Subscriber)}.
     */
    public final Publisher<T> multicast(int expectedSubscribers, int maxQueueSize) {
        return new MulticastPublisher<>(this, expectedSubscribers, maxQueueSize, executor);
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
    public final Publisher<T> doBeforeSubscribe(Consumer<Subscription> onSubscribe) {
        return doBeforeSubscriber(doOnSubscribeSupplier(onSubscribe));
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
    public final Publisher<T> doBeforeNext(Consumer<T> onNext) {
        return doBeforeSubscriber(doOnNextSupplier(onNext));
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
    public final Publisher<T> doBeforeError(Consumer<Throwable> onError) {
        return doBeforeSubscriber(doOnErrorSupplier(onError));
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
    public final Publisher<T> doBeforeComplete(Runnable onComplete) {
        return doBeforeSubscriber(doOnCompleteSupplier(onComplete));
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
    public final Publisher<T> doBeforeRequest(LongConsumer onRequest) {
        return doBeforeSubscription(doOnRequestSupplier(onRequest));
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
    public final Publisher<T> doBeforeCancel(Runnable onCancel) {
        return doBeforeSubscription(doOnCancelSupplier(onCancel));
    }

    /**
     * Invokes the {@code doFinally} {@link Runnable} argument <strong>before</strong> any of the following terminal
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
     *
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX do operator.</a>
     */
    public final Publisher<T> doBeforeFinally(Runnable doFinally) {
        return new DoBeforeFinallyPublisher<>(this, doFinally, executor);
    }

    /**
     * Creates a new {@link Subscriber} (via the {@code subscriberSupplier} argument) on each call to
     * {@link #subscribe(Subscriber)} and invokes all the {@link Subscriber} methods <strong>before</strong> the
     * {@link Subscriber}s of the returned {@link Publisher}.
     *
     * @param subscriberSupplier Creates a new {@link Subscriber} on each call to {@link #subscribe(Subscriber)} and
     * invokes all the {@link Subscriber} methods <strong>before</strong> the {@link Subscriber}s of the returned
     * {@link Publisher}. {@link Subscriber} methods <strong>MUST NOT</strong> throw.
     * @return The new {@link Publisher}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX do operator.</a>
     */
    public final Publisher<T> doBeforeSubscriber(Supplier<Subscriber<? super T>> subscriberSupplier) {
        return new DoBeforeSubscriberPublisher<>(this, subscriberSupplier, executor);
    }

    /**
     * Creates a new {@link Subscription} (via the {@code subscriberSupplier} argument) on each call to
     * {@link #subscribe(Subscriber)} and invokes all the {@link Subscription} methods <strong>before</strong> the
     * {@link Subscription}s of the returned {@link Publisher}.
     *
     * @param subscriptionSupplier Creates a new {@link Subscription} on each call to {@link #subscribe(Subscriber)} and
     * invokes all the {@link Subscription} methods <strong>before</strong> the {@link Subscription}s of the returned
     * {@link Publisher}. {@link Subscription} methods <strong>MUST NOT</strong> throw.
     * @return The new {@link Publisher}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX do operator.</a>
     */
    public final Publisher<T> doBeforeSubscription(Supplier<Subscription> subscriptionSupplier) {
        return new DoSubscriptionPublisher<>(this, subscriptionSupplier, true, executor);
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
    public final Publisher<T> doAfterSubscribe(Consumer<Subscription> onSubscribe) {
        return doAfterSubscriber(doOnSubscribeSupplier(onSubscribe));
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
    public final Publisher<T> doAfterNext(Consumer<T> onNext) {
        return doAfterSubscriber(doOnNextSupplier(onNext));
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
    public final Publisher<T> doAfterError(Consumer<Throwable> onError) {
        return doAfterSubscriber(doOnErrorSupplier(onError));
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
    public final Publisher<T> doAfterComplete(Runnable onComplete) {
        return doAfterSubscriber(doOnCompleteSupplier(onComplete));
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
    public final Publisher<T> doAfterRequest(LongConsumer onRequest) {
        return doAfterSubscription(doOnRequestSupplier(onRequest));
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
    public final Publisher<T> doAfterCancel(Runnable onCancel) {
        return doAfterSubscription(doOnCancelSupplier(onCancel));
    }

    /**
     * Invokes the {@code doFinally} {@link Runnable} argument <strong>after</strong> any of the following terminal
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
     *
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX do operator.</a>
     */
    public final Publisher<T> doAfterFinally(Runnable doFinally) {
        return new DoAfterFinallyPublisher<>(this, doFinally, executor);
    }

    /**
     * Creates a new {@link Subscriber} (via the {@code subscriberSupplier} argument) on each call to
     * {@link #subscribe(Subscriber)} and invokes all the {@link Subscriber} methods <strong>after</strong> the
     * {@link Subscriber}s of the returned {@link Publisher}.
     *
     * @param subscriberSupplier Creates a new {@link Subscriber} on each call to {@link #subscribe(Subscriber)} and
     * invokes all the {@link Subscriber} methods <strong>after</strong> the {@link Subscriber}s of the returned
     * {@link Publisher}. {@link Subscriber} methods <strong>MUST NOT</strong> throw.
     * @return The new {@link Publisher}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX do operator.</a>
     */
    public final Publisher<T> doAfterSubscriber(Supplier<Subscriber<? super T>> subscriberSupplier) {
        return new DoAfterSubscriberPublisher<>(this, subscriberSupplier, executor);
    }

    /**
     * Creates a new {@link Subscription} (via the {@code subscriberSupplier} argument) on each call to
     * {@link #subscribe(Subscriber)} and invokes all the {@link Subscription} methods <strong>after</strong> the
     * {@link Subscription}s of the returned {@link Publisher}.
     *
     * @param subscriptionSupplier Creates a new {@link Subscription} on each call to {@link #subscribe(Subscriber)} and
     * invokes all the {@link Subscription} methods <strong>after</strong> the {@link Subscription}s of the returned
     * {@link Publisher}. {@link Subscription} methods <strong>MUST NOT</strong> throw.
     * @return The new {@link Publisher}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX do operator.</a>
     */
    public final Publisher<T> doAfterSubscription(Supplier<Subscription> subscriptionSupplier) {
        return new DoSubscriptionPublisher<>(this, subscriptionSupplier, false, executor);
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
    public final Cancellable forEach(Consumer<T> forEach) {
        ForEachSubscriber<T> subscriber = new ForEachSubscriber<>(forEach);
        subscribe(subscriber);
        return subscriber;
    }

    /**
     * Creates a new {@link Publisher} that will use the passed {@link Executor} to invoke all {@link Subscriber}
     * methods.
     * This method does <strong>not</strong> override preceding {@link Executor}s, if any, specified for {@code this}
     * {@link Publisher}. Only subsequent operations, if any, added in this execution chain will use this
     * {@link Executor}. If such an override is required, {@link #publishOnOverride(Executor)} can be used.
     *
     * @param executor {@link Executor} to use.
     * @return A new {@link Publisher} that will use the passed {@link Executor} to invoke all methods of
     * {@link Subscriber}.
     */
    public final Publisher<T> publishOn(Executor executor) {
        return PublishAndSubscribeOnPublishers.publishOn(this, executor);
    }

    /**
     * Creates a new {@link Publisher} that will use the passed {@link Executor} to invoke all {@link Subscriber}
     * methods.
     * This method overrides preceding {@link Executor}s, if any, specified for {@code this} {@link Publisher}.
     * That is to say preceding and subsequent operations for this execution chain will use this {@link Executor}.
     * If such an override is not required, {@link #publishOn(Executor)} can be used.
     *
     * @param executor {@link Executor} to use.
     * @return A new {@link Publisher} that will use the passed {@link Executor} to invoke all methods of
     * {@link Subscriber} both for the returned {@link Publisher} as well as {@code this} {@link Publisher}.
     */
    public final Publisher<T> publishOnOverride(Executor executor) {
        return PublishAndSubscribeOnPublishers.publishOnOverride(this, executor);
    }

    /**
     * Creates a new {@link Publisher} that will use the passed {@link Executor} to invoke the following methods:
     * <ul>
     *     <li>All {@link Subscription} methods.</li>
     *     <li>The {@link #handleSubscribe(PublisherSource.Subscriber)} method.</li>
     * </ul>
     * This method does <strong>not</strong> override preceding {@link Executor}s, if any, specified for {@code this}
     * {@link Publisher}. Only subsequent operations, if any, added in this execution chain will use this
     * {@link Executor}. If such an override is required, {@link #subscribeOnOverride(Executor)} can be used.
     *
     * @param executor {@link Executor} to use.
     * @return A new {@link Publisher} that will use the passed {@link Executor} to invoke all methods of
     * {@link Subscription} and {@link #handleSubscribe(PublisherSource.Subscriber)}.
     */
    public final Publisher<T> subscribeOn(Executor executor) {
        return PublishAndSubscribeOnPublishers.subscribeOn(this, executor);
    }

    /**
     * Creates a new {@link Publisher} that will use the passed {@link Executor} to invoke the following methods:
     * <ul>
     *     <li>All {@link Subscription} methods.</li>
     *     <li>The {@link #handleSubscribe(PublisherSource.Subscriber)} method.</li>
     * </ul>
     * This method overrides preceding {@link Executor}s, if any, specified for {@code this} {@link Publisher}.
     * That is to say preceding and subsequent operations for this execution chain will use this {@link Executor}.
     * If such an override is not required, {@link #subscribeOn(Executor)} can be used.
     *
     * @param executor {@link Executor} to use.
     * @return A new {@link Publisher} that will use the passed {@link Executor} to invoke all methods of
     * {@link Subscription} and {@link #handleSubscribe(PublisherSource.Subscriber)} both for the returned
     * {@link Publisher} as well as {@code this} {@link Publisher}.
     */
    public final Publisher<T> subscribeOnOverride(Executor executor) {
        return PublishAndSubscribeOnPublishers.subscribeOnOverride(this, executor);
    }

    /**
     * Creates a new {@link Publisher} that will use the passed {@link Executor} to invoke the following methods:
     * <ul>
     *     <li>All {@link Subscriber} methods.</li>
     *     <li>All {@link Subscription} methods.</li>
     *     <li>The {@link #handleSubscribe(PublisherSource.Subscriber)} method.</li>
     * </ul>
     * This method does <strong>not</strong> override preceding {@link Executor}s, if any, specified for {@code this}
     * {@link Publisher}. Only subsequent operations, if any, added in this execution chain will use this
     * {@link Executor}. If such an override is required, {@link #publishAndSubscribeOnOverride(Executor)} can be used.
     *
     * @param executor {@link Executor} to use.
     * @return A new {@link Publisher} that will use the passed {@link Executor} to invoke all methods
     * {@link Subscriber}, {@link Subscription} and {@link #handleSubscribe(PublisherSource.Subscriber)}.
     */
    public final Publisher<T> publishAndSubscribeOn(Executor executor) {
        return PublishAndSubscribeOnPublishers.publishAndSubscribeOn(this, executor);
    }

    /**
     * Creates a new {@link Publisher} that will use the passed {@link Executor} to invoke the following methods:
     * <ul>
     *     <li>All {@link Subscriber} methods.</li>
     *     <li>All {@link Subscription} methods.</li>
     *     <li>The {@link #handleSubscribe(PublisherSource.Subscriber)} method.</li>
     * </ul>
     * This method overrides preceding {@link Executor}s, if any, specified for {@code this} {@link Publisher}.
     * That is to say preceding and subsequent operations for this execution chain will use this {@link Executor}.
     * If such an override is not required, {@link #publishAndSubscribeOn(Executor)} can be used.
     *
     * @param executor {@link Executor} to use.
     * @return A new {@link Publisher} that will use the passed {@link Executor} to invoke all methods of
     * {@link Subscriber}, {@link Subscription} and {@link #handleSubscribe(PublisherSource.Subscriber)} both for the
     * returned {@link Publisher} as well as {@code this} {@link Publisher}.
     */
    public final Publisher<T> publishAndSubscribeOnOverride(Executor executor) {
        return PublishAndSubscribeOnPublishers.publishAndSubscribeOnOverride(this, executor);
    }

    /**
     * Signifies that when {@link #subscribe(Subscriber)} is invoked on the returned {@link Publisher} that the
     * {@link AsyncContext} will be shared instead of making a {@link AsyncContextMap#copy() copy}.
     * <p>
     * This operator only impacts behavior if {@link #subscribe(Subscriber)} is directly called on the return value,
     * that means this must be the "last operator" in the chain for this to have an impact.
     *
     * @return A {@link Publisher} that will share the {@link AsyncContext} instead of making a
     * {@link AsyncContextMap#copy() copy} when {@link #subscribe(Subscriber)} is called.
     */
    public final Publisher<T> subscribeShareContext() {
        return new PublisherSubscribeShareContext<>(this);
    }

    /**
     * <strong>This method requires advanced knowledge of building operators. Before using this method please attempt
     * to compose existing operator(s) to satisfy your use case.</strong>
     * <p>
     * Returns a {@link Publisher} that when {@link Publisher#subscribe(Subscriber)} is called the {@code operator}
     * argument will be used to wrap the {@link Subscriber} before subscribing to this {@link Publisher}.
     * <pre>{@code
     *     Publisher<X> pub = ...;
     *     pub.map(..) // A
     *        .liftSynchronous(original -> modified)
     *        .filter(..) // B
     * }</pre>
     *
     * The {@code original -> modified} "operator" <strong>MUST</strong> be "synchronous" in that it does not interact
     * with the original {@link Subscriber} from outside the modified {@link Subscriber} or {@link Subscription}
     * threads. That is to say this operator will not impact the {@link Executor} constraints already in place between
     * <i>A</i> and <i>B</i> above. If you need asynchronous behavior, or are unsure, see
     * {@link #liftAsynchronous(PublisherOperator)}.
     * @param operator The custom operator logic. The input is the "original" {@link Subscriber} to this
     * {@link Publisher} and the return is the "modified" {@link Subscriber} that provides custom operator business
     * logic.
     * @param <R> Type of the items emitted by the returned {@link Publisher}.
     * @return a {@link Publisher} that when {@link Publisher#subscribe(Subscriber)} is called the {@code operator}
     * argument will be used to wrap the {@link Subscriber} before subscribing to this {@link Publisher}.
     * @see #liftAsynchronous(PublisherOperator)
     */
    public final <R> Publisher<R> liftSynchronous(PublisherOperator<? super T, ? extends R> operator) {
        return new LiftSynchronousPublisherOperator<>(this, operator, executor);
    }

    /**
     * <strong>This method requires advanced knowledge of building operators. Before using this method please attempt
     * to compose existing operator(s) to satisfy your use case.</strong>
     * <p>
     * Returns a {@link Publisher} that when {@link Publisher#subscribe(Subscriber)} is called the {@code operator}
     * argument will be used to wrap the {@link Subscriber} before subscribing to this {@link Publisher}.
     * <pre>{@code
     *     Publisher<X> pub = ...;
     *     pub.map(..) // A
     *        .liftAsynchronous(original -> modified)
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
     * @return a {@link Publisher} that when {@link Publisher#subscribe(Subscriber)} is called the {@code operator}
     * argument will be used to wrap the {@link Subscriber} before subscribing to this {@link Publisher}.
     * @see #liftSynchronous(PublisherOperator)
     */
    public final <R> Publisher<R> liftAsynchronous(PublisherOperator<? super T, ? extends R> operator) {
        return new LiftAsynchronousPublisherOperator<>(this, operator, executor);
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
     * @return A {@link Single} that will contain the first item emitted from the this {@link Publisher}.
     * If the source {@link Publisher} does not emit any item, then the returned {@link Single} will terminate with
     * {@link NoSuchElementException}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/first.html">ReactiveX first operator.</a>
     */
    public final Single<T> first() {
        return new PubToSingle<>(this);
    }

    /**
     * Ignores all elements emitted by this {@link Publisher} and forwards the termination signal to the returned
     * {@link Completable}.
     *
     * @return A {@link Completable} that mirrors the terminal signal from this {@code Publisher}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/ignoreelements.html">ReactiveX ignoreElements operator.</a>
     */
    public final Completable ignoreElements() {
        return new PubToCompletable<>(this);
    }

    /**
     * Reduces the stream into a single item.
     *
     * @param resultFactory Factory for the result which collects all items emitted by this {@link Publisher}.
     * This will be called every time the returned {@link Single} is subscribed.
     * @param reducer Invoked for every item emitted by the source {@link Publisher} and returns the same or altered
     * {@code result} object.
     * @param <R> Type of the reduced item.
     * @return A {@link Single} that completes with the single {@code result} or any error emitted by the source
     * {@link Publisher}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/reduce.html">ReactiveX reduce operator.</a>
     */
    public final <R> Single<R> reduce(Supplier<R> resultFactory, BiFunction<R, ? super T, R> reducer) {
        return new ReduceSingle<>(this, resultFactory, reducer);
    }

    /**
     * Convert this {@link Publisher} into a {@link Future} with a {@link Collection} containing the elements of this
     * {@link Publisher} upon successful termination. If this {@link Publisher} terminates in an error, then the
     * intermediate {@link Collection} will be discarded and the {@link Future} will complete exceptionally.
     * @return a {@link Future} with a {@link Collection} containing the elements of this {@link Publisher} upon
     * successful termination.
     * @see #toFuture(Supplier, BiFunction)
     */
    public final Future<? extends Collection<T>> toFuture() {
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
    public final <R> Future<R> toFuture(Supplier<R> resultFactory, BiFunction<R, ? super T, R> reducer) {
        return reduce(resultFactory, reducer).toFuture();
    }

    /**
     * Convert this {@link Publisher} into a {@link CompletionStage} with a {@link Collection} containing the elements
     * of this {@link Publisher} upon successful termination. If this {@link Publisher} terminates in an error, then the
     * intermediate {@link Collection} will be discarded and the {@link CompletionStage} will complete exceptionally.
     * @return a {@link CompletionStage} with a {@link Collection} containing the elements of this {@link Publisher}
     * upon successful termination.
     * @see #toCompletionStage(Supplier, BiFunction)
     */
    public final CompletionStage<? extends Collection<T>> toCompletionStage() {
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
    public final <R> CompletionStage<R> toCompletionStage(Supplier<R> resultFactory,
                                                          BiFunction<R, ? super T, R> reducer) {
        return reduce(resultFactory, reducer).toCompletionStage();
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
    public final InputStream toInputStream(Function<T, byte[]> serializer) {
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
    public final InputStream toInputStream(Function<T, byte[]> serializer, int queueCapacity) {
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

    @Override
    public final void subscribe(Subscriber<? super T> subscriber) {
        subscribeCaptureContext(subscriber, AsyncContext.provider());
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
    public static <T> Publisher<T> just(@Nullable T value) {
        return new JustPublisher<>(value);
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
     * Create a new {@link Publisher} that on {@link Publisher#subscribe(Subscriber)} will get an {@link Iterator} via
     * {@link Iterable#iterator()} and emit all values to the {@link Subscriber} and then
     * {@link Subscriber#onComplete()}.
     * <p>
     * The Reactive Streams specification provides two criteria (
     * <a href="https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.2/README.md#3.4">3.4</a>, and
     * <a href="https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.2/README.md#3.5">3.5</a>) stating
     * the {@link Subscription} should be "responsive". The responsiveness of the associated {@link Subscription}s will
     * depend upon the behavior of the {@code iterable} below. Make sure the {@link Executor} for this execution chain
     * can tolerate this responsiveness and any blocking behavior.
     * @param <T> Type of items emitted by the returned {@link Publisher}.
     * @param iterable used to obtain instances of {@link Iterator} to extract data from. {@link Iterable#iterator()}
     * must not return {@code null}. If this is of type {@link BlockingIterable} then any generated
     * {@link BlockingIterator}s will have their {@link BlockingIterator#close()} method called if an error
     * occurs.
     * @return a new {@link Publisher} that on {@link Publisher#subscribe(Subscriber)} will get an {@link Iterator} via
     * {@link Iterable#iterator()} and emit all values to the {@link Subscriber} and then
     * {@link Subscriber#onComplete()}.
     */
    public static <T> Publisher<T> from(Iterable<T> iterable) {
        return new FromIterablePublisher<>(iterable);
    }

    /**
     * Create a new {@link Publisher} that on {@link Publisher#subscribe(Subscriber)} will get an {@link Iterator} via
     * {@link Iterable#iterator()} and emit all values to the {@link Subscriber} and then
     * {@link Subscriber#onComplete()}.
     * <p>
     * The Reactive Streams specification provides two criteria (
     * <a href="https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.2/README.md#3.4">3.4</a>, and
     * <a href="https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.2/README.md#3.5">3.5</a>) stating
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
     * @return a new {@link Publisher} that on {@link Publisher#subscribe(Subscriber)} will get an {@link Iterator} via
     * {@link Iterable#iterator()} and emit all values to the {@link Subscriber} and then
     * {@link Subscriber#onComplete()}.
     */
    public static <T> Publisher<T> from(BlockingIterable<T> iterable, LongSupplier timeoutSupplier, TimeUnit unit) {
        return new FromBlockingIterablePublisher<>(iterable, timeoutSupplier, unit);
    }

    /**
     * Create a new {@link Publisher} that on {@link Publisher#subscribe(Subscriber)} will emit all data from the
     * {@link InputStream} to the {@link Subscriber} and then {@link Subscriber#onComplete()}.
     * <p>
     * The Reactive Streams specification provides two criteria (
     * <a href="https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.2/README.md#3.4">3.4</a>, and
     * <a href="https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.2/README.md#3.5">3.5</a>) stating
     * the {@link Subscription} should be "responsive". The responsiveness of the associated {@link Subscription}s will
     * depend upon the behavior of the {@code stream} below. Make sure the {@link Executor} for this execution chain
     * can tolerate this responsiveness and any blocking behavior.
     * @param stream provides the data in the form of {@code byte[]} to be emitted to the {@link Subscriber} to the
     * returned {@link Publisher}. Given the blocking nature of {@link InputStream}, assume {@link
     * Subscription#request(long)} can block when the underlying {@link InputStream} blocks on {@link
     * InputStream#read(byte[], int, int)}.
     * @return a new {@link Publisher} that on {@link Publisher#subscribe(Subscriber)} will emit all data from the
     * {@link InputStream} to the {@link Subscriber} and then {@link Subscriber#onComplete()}.
     */
    public static Publisher<byte[]> from(InputStream stream) {
        return new FromInputStreamPublisher(stream);
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
    public static <T> Publisher<T> error(Throwable cause) {
        return new ErrorPublisher<>(cause);
    }

    /**
     * Defers creation of a {@link Publisher} till it is subscribed.
     *
     * @param publisherSupplier {@link Supplier} to create a new {@link Publisher} for every call to
     * {@link #subscribe(Subscriber)} to the returned {@link Publisher}.
     * @param <T> Type of items emitted by the returned {@link Publisher}.
     * @return A new {@link Publisher} that creates a new {@link Publisher} using {@code publisherFactory} for every
     * call to {@link #subscribe(Subscriber)} and forwards all items
     * and terminal events from the newly created {@link Publisher} to its {@link Subscriber}.
     *
     * @see <a href="http://reactivex.io/documentation/operators/defer.html">ReactiveX defer operator.</a>
     */
    public static <T> Publisher<T> defer(Supplier<? extends Publisher<T>> publisherSupplier) {
        return new PublisherDefer<>(publisherSupplier);
    }

    //
    // Static Utility Methods End
    //

    //
    // Internal Methods Begin
    //

    /**
     * Replicating a call to {@link #subscribe(Subscriber)} but allows an override of the {@link AsyncContextMap}.
     * @param subscriber the subscriber.
     * @param provider the {@link AsyncContextProvider} used to wrap any objects to preserve
     * {@link AsyncContextMap}.
     */
    void subscribeCaptureContext(Subscriber<? super T> subscriber, AsyncContextProvider provider) {
        // Each Subscriber chain should have an isolated AsyncContext due to a new asynchronous scope starting. This is
        // why we copy the AsyncContext upon external user facing subscribe operations.
        subscribeWithContext(subscriber, provider.contextMap().copy(), provider);
    }

    /**
     * Replicating a call to {@link #subscribe(Subscriber)} but with a materialized {@link AsyncContextMap}.
     * @param subscriber the subscriber.
     * @param contextMap the {@link AsyncContextMap} to use for this {@link Subscriber}.
     * @param contextProvider the {@link AsyncContextProvider} used to wrap any objects to preserve
     * {@link AsyncContextMap}.
     */
    final void subscribeWithContext(Subscriber<? super T> subscriber, AsyncContextMap contextMap,
                                    AsyncContextProvider contextProvider) {
        requireNonNull(subscriber);
        final SignalOffloader signalOffloader;
        final Subscriber<? super T> offloadedSubscriber;
        try {
            // This is a user-driven subscribe i.e. there is no SignalOffloader override, so create a new
            // SignalOffloader to use.
            signalOffloader = newOffloaderFor(executor);
            // Since this is a user-driven subscribe (end of the execution chain), offload subscription methods
            // We also want to make sure the AsyncContext is saved/restored for all interactions with the Subscription.
            offloadedSubscriber = signalOffloader.offloadSubscription(
                    contextProvider.wrapSubscription(subscriber, contextMap));
        } catch (Throwable t) {
            subscriber.onSubscribe(EMPTY_SUBSCRIPTION);
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
    final void subscribeWithOffloaderAndContext(Subscriber<? super T> subscriber, SignalOffloader signalOffloader,
                                                AsyncContextMap contextMap, AsyncContextProvider contextProvider) {
        // In the event that user code is called synchronously from handleSubscribe (e.g. in overriden method for an
        // operator implementation) we need to make sure the static AsyncContext is set correctly.
        AsyncContextMap currentContext = contextProvider.contextMap();
        try {
            contextProvider.contextMap(contextMap);
            handleSubscribe(subscriber, signalOffloader, contextMap, contextProvider);
        } finally {
            contextProvider.contextMap(currentContext);
        }
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
        Subscriber<? super T> offloaded = signalOffloader.offloadSubscriber(
                contextProvider.wrap(subscriber, contextMap));
        // TODO(scott): we have to wrap this method to preserve AsyncContext, and we also have to wrap the
        // safeHandleSubscribe method in the event the offloader calls the safeHandleSubscribe method on another thread.
        // However if the offloader executes the method synchronously the safeHandleSubscribe wrapping is unnecessary.
        signalOffloader.offloadSubscribe(offloaded, contextProvider.wrap(this::safeHandleSubscribe, contextMap));
    }

    private void safeHandleSubscribe(Subscriber<? super T> subscriber) {
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
            subscriber.onSubscribe(EMPTY_SUBSCRIPTION);
            subscriber.onError(t);
        }
    }

    /**
     * Returns the {@link Executor} used for this {@link Publisher}.
     *
     * @return {@link Executor} used for this {@link Publisher} via {@link #Publisher(Executor)}.
     */
    final Executor getExecutor() {
        return executor;
    }

    //
    // Internal Methods End
    //
}
