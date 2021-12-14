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
import io.servicetalk.concurrent.PublisherSource.Subscription;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;

/**
 * An {@link Iterator} that is also an {@link AutoCloseable} and whose blocking operations support timeout durations.
 * <p>
 * This interface is meant to be the synchronous API equivalent of {@link Subscriber} coupled with a
 * {@link Subscription}. If the data is not completely consumed from this {@link Iterable} then {@link #close()}
 * <strong>MUST</strong> be called. If the data is completely consumed (e.g. {@link #hasNext()} and/or
 * {@link #hasNext(long, TimeUnit)} return {@code false}) then this object is implicitly {@link #close() closed}.
 * @param <T> the type of elements returned by this {@link Iterator}.
 */
public interface BlockingIterator<T> extends CloseableIterator<T> {
    /**
     * The equivalent of {@link #hasNext()} but only waits for {@code timeout} duration amount of time.
     * <p>
     * Note that {@link #close()} is not required to interrupt this call.
     * <p>
     * Also note, this method can sneaky-throw an {@link InterruptedException} when a blocking operation internally
     * does so. The reason it's not declared is that the {@link java.util.Iterator} and {@link AutoCloseable}
     * interfaces do not declare checked exceptions and {@link BlockingIterator} extends them to allow use in
     * try-with-resources and enhanced for loop.
     * @param timeout The duration of time to wait. If this value is non-positive that means the timeout expiration is
     * immediate or in the past. In this case, if this implementation cannot determine if there is more data immediately
     * (e.g. without external dependencies) then a {@link TimeoutException} should be thrown, otherwise the method can
     * return the known status.
     * @param unit The units for the duration of time.
     * @return See the return value for {@link #hasNext()}. If this value is {@code false} then this object is
     * implicitly {@link #close() closed}.
     * @throws TimeoutException if the wait timed out. This object is implicitly {@link #close() closed} if this
     * occurs.
     */
    boolean hasNext(long timeout, TimeUnit unit) throws TimeoutException;

    /**
     * The equivalent of {@link #next()} but only waits for {@code timeout} duration of time.
     * <p>
     * Note that {@link #close()} is not required to interrupt this call.
     * <p>
     * Also note, this method can sneaky-throw an {@link InterruptedException} when a blocking operation internally
     * does so. The reason it's not declared is that the {@link java.util.Iterator} and {@link AutoCloseable}
     * interfaces do not declare checked exceptions and {@link BlockingIterator} extends them to allow use in
     * try-with-resources and enhanced for loop.
     * @param timeout The duration of time to wait. If this value is non-positive that means the timeout expiration is
     * immediate or in the past. In this case, if this implementation cannot determine if there is more data immediately
     * (e.g. without external dependencies) then a {@link TimeoutException} should be thrown, otherwise the method can
     * return the known status.
     * @param unit The units for the duration of time.
     * @return See the return value for {@link #next()}.
     * @throws NoSuchElementException if the iteration has no more elements.
     * @throws TimeoutException if the wait timed out. This object is implicitly {@link #close() closed} if this
     * occurs.
     */
    @Nullable
    T next(long timeout, TimeUnit unit) throws TimeoutException;

    /**
     * {@inheritDoc}
     * <p>
     * Note, this method can sneaky-throw an {@link InterruptedException} when a blocking operation internally
     * does so. The reason it's not declared is that the {@link java.util.Iterator} and {@link AutoCloseable}
     * interfaces do not declare checked exceptions and {@link BlockingIterator} extends them to allow use in
     * try-with-resources and enhanced for loop.
     */
    @Nullable
    @Override
    T next();

    /**
     * This method is used to communicate that you are no longer interested in consuming data.
     * This provides a "best effort" notification to the producer of data that you are no longer interested in data
     * from this {@link Iterator}. It is still possible that data may be obtained after calling this method, for example
     * if there is data currently queued in memory.
     * <p>
     * If all the data has not been consumed (e.g. {@link #hasNext()} and/or {@link #hasNext(long, TimeUnit)}
     * have not yet returned {@code false}) this may have transport implications (e.g. if the source of data comes from
     * a socket or file descriptor). If all data has been consumed, or this {@link BlockingIterator} has previously
     * been closed this should be a noop.
     */
    @Override
    void close() throws Exception;
}
