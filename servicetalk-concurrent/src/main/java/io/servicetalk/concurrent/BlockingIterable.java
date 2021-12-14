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
package io.servicetalk.concurrent;

import io.servicetalk.concurrent.PublisherSource.Subscriber;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import javax.annotation.Nullable;

import static java.lang.System.nanoTime;
import static java.util.Objects.requireNonNull;
import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * An {@link Iterable} which supports generation of {@link BlockingIterator}s.
 * <p>
 * This interface is meant to be the synchronous API equivalent of {@link PublisherSource}. Each call to
 * {@link #iterator()} is equivalent to calling {@link PublisherSource#subscribe(Subscriber)} and typically has the
 * same characteristics in terms of being able to call the method multiple times and data availability in memory.
 * @param <T> the type of elements returned by the {@link BlockingIterator}.
 */
public interface BlockingIterable<T> extends CloseableIterable<T> {
    @Override
    BlockingIterator<T> iterator();

    /**
     * Mimics the behavior of {@link #forEach(Consumer)} but uses the {@code timeoutSupplier} to determine the timeout
     * value for interactions with the {@link BlockingIterator}.
     * <p>
     * By default the {@code timeoutSupplier} will be used for each interaction with
     * {@link BlockingIterator#hasNext(long, TimeUnit)} and {@link BlockingIterator#next(long, TimeUnit)}. However
     * implementations of {@link BlockingIterable} may decide to only apply the timeout when they are not sure if
     * an interaction with the {@link BlockingIterator} will block or not.
     * <p>
     * Note: This method can sneaky-throw an {@link InterruptedException} when a blocking operation internally does so.
     * The reason it's not declared is that the {@link java.util.Iterator} and {@link AutoCloseable} interfaces do not
     * declare checked exceptions and {@link BlockingIterator} extends them to allow use in try-with-resources
     * and enhanced for loop.
     * @param action The action to be performed for each element.
     * @param timeoutSupplier A {@link LongSupplier} that provides the timeout duration for the next call to
     * {@link BlockingIterator#hasNext(long, TimeUnit)} and {@link BlockingIterator#next(long, TimeUnit)}. These
     * methods should be consulted for the meaning of non-positive timeout durations.
     * @param unit The units for the duration of time.
     * @throws TimeoutException If an individual call to {@link BlockingIterator#hasNext(long, TimeUnit)} takes
     * longer than the {@code timeout} duration.
     */
    default void forEach(Consumer<? super T> action, LongSupplier timeoutSupplier, TimeUnit unit)
            throws TimeoutException {
        requireNonNull(action);
        BlockingIterator<T> iterator = iterator();
        while (iterator.hasNext(timeoutSupplier.getAsLong(), unit)) {
            action.accept(iterator.next(timeoutSupplier.getAsLong(), unit));
        }
    }

    /**
     * Mimics the behavior of {@link #forEach(Consumer)} but applies a {@code timeout} duration for the overall
     * completion of this method. The {@code timeout} is adjusted for each interaction with the
     * {@link BlockingIterator} which may block.
     * <p>
     * Note that the {@code timeout} duration is an approximation and this duration maybe
     * exceeded if data is available without blocking.
     * <p>
     * By default the {@code timeout} will be used for each interaction with
     * {@link BlockingIterator#hasNext(long, TimeUnit)} and {@link BlockingIterator#next(long, TimeUnit)}. However
     * implementations of {@link BlockingIterable} may decide to only apply the timeout when they are not be sure if
     * an interaction with the {@link BlockingIterator} will block or not.
     * <p>
     * Note: This method can sneaky-throw an {@link InterruptedException} when a blocking operation internally does so.
     * The reason it's not declared is that the {@link java.util.Iterator} and {@link AutoCloseable} interfaces do not
     * declare checked exceptions and {@link BlockingIterator} extends them to allow use in try-with-resources
     * and enhanced for loop.
     * @param action The action to be performed for each element.
     * @param timeout An approximate total duration for the overall completion of this method. This value is used to
     * approximate because the actual duration maybe longer if data is available without blocking.
     * @param unit The units for the duration of time.
     * @throws TimeoutException If the total iteration time as determined by
     * {@link BlockingIterator#hasNext(long, TimeUnit)} takes longer than the {@code timeout} duration.
     */
    default void forEach(Consumer<? super T> action, long timeout, TimeUnit unit) throws TimeoutException {
        requireNonNull(action);
        BlockingIterator<T> iterator = iterator();
        long remainingTimeoutNanos = unit.toNanos(timeout);
        long timeStampANanos = nanoTime();
        while (iterator.hasNext(remainingTimeoutNanos, NANOSECONDS)) {
            final long timeStampBNanos = nanoTime();
            remainingTimeoutNanos -= timeStampBNanos - timeStampANanos;
            // We do not check for timeout expiry here and instead let hasNext(), next() determine what a timeout of
            // <= 0 means. It may be that those methods decide to throw a TimeoutException or provide a fallback value.
            action.accept(iterator.next(remainingTimeoutNanos, NANOSECONDS));

            timeStampANanos = nanoTime();
            remainingTimeoutNanos -= timeStampANanos - timeStampBNanos;
        }
    }

    /**
     * The same behavior as {@link Iterable#spliterator()}, but returns a {@link BlockingSpliterator} view.
     * <p>
     * Calling {@link BlockingSpliterator#close()} may result in closing of the underlying {@link BlockingIterator}.
     * @return a {@link BlockingSpliterator} over the elements described by this
     * {@link BlockingIterable}.
     */
    @Override
    default BlockingSpliterator<T> spliterator() {
        BlockingIterator<T> iterator = iterator();
        return new SpliteratorToBlockingSpliterator<>(iterator, spliteratorUnknownSize(iterator, 0));
    }

    /**
     * A {@link BlockingIterable} that supports to dynamically emitting items using {@link #next(Object)}.
     * <p>
     * If multiple {@link BlockingIterator}s are created by this {@link BlockingIterable} then an implementation
     * will choose how to distribute the items emitted from {@link #next(Object)} to those {@link BlockingIterator}s.
     * There is no common guarantee about the nature of that distribution.
     * <h2>Lifetime</h2>
     * There are two aspects of the lifetime of this {@code Processor}, one from the producer side and one from the
     * consumer side ({@link BlockingIterator}.
     *
     * <h3>Producer Lifetime</h3>
     * A producer <strong>MUST</strong> invoke either {@link #close()} (successful termination) or
     * {@link #fail(Throwable)} (unsuccessful termination) to correctly terminate the producer side of this
     * {@code Processor}.
     *
     * <h3>Consumer Lifetime</h3>
     * A consumer can prematurely indicate termination by calling {@link BlockingIterator#close()}. However, if a
     * consumer receives a termination from the producer end ({@link BlockingIterator#hasNext(long, TimeUnit)} returns
     * {@code false}), then it need not call {@link BlockingIterator#close()}.
     *
     * @param <T> the type of elements returned by the {@link BlockingIterator}.
     */
    interface Processor<T> extends BlockingIterable<T>, AutoCloseable {

        /**
         * Emits the passed {@code nextItem} from the {@link BlockingIterator} when called.
         *
         * @param nextItem to emit from the {@link BlockingIterator} when called.
         * @throws Exception If the item could not be emitted.
         */
        void next(@Nullable T nextItem) throws Exception;

        /**
         * Terminates this {@link BlockingIterable} and all the current or future {@link BlockingIterator}s with a
         * failure.
         * <p>
         * After this method returns, any subsequent calls to {@link #next(Object)} <strong>MUST</strong> throw an
         * {@link Exception}. All current and future {@link BlockingIterator}s created by this {@link BlockingIterable}
         * <strong>MUST</strong> eventually throw an {@link Exception} which is the same as passed {@code cause} or
         * wraps the same.
         *
         * @param cause for the failure.
         * @throws Exception If this {@link BlockingIterable} can not be terminated with a failure.
         */
        void fail(Throwable cause) throws Exception;

        /**
         * Closes this {@link BlockingIterable} and all the current or future {@link BlockingIterator}s.
         * <p>
         * After this method returns, any subsequent calls to {@link #next(Object)} <strong>MUST</strong> throw an
         * {@link Exception}. All current and future {@link BlockingIterator}s created by this {@link BlockingIterable}
         * <strong>MUST</strong> eventually return {@code false} from the various {@code hasNext} methods.
         *
         * @throws Exception If closure failed.
         */
        @Override
        void close() throws Exception;
    }
}
