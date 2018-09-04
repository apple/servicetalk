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
package io.servicetalk.transport.netty.internal;

import io.servicetalk.concurrent.api.Publisher;

import static io.servicetalk.transport.netty.internal.FlushOnEach.FLUSH_ON_EACH;
import static io.servicetalk.transport.netty.internal.FlushOnEnd.FLUSH_ON_END;
import static java.lang.Integer.MAX_VALUE;

/**
 * A strategy that defines how to flush writes on a connection.
 */
@FunctionalInterface
public interface FlushStrategy {

    /**
     * Every time a new write that requires custom flushes is initiated, this method is invoked. Passed
     * {@link FlushSender} is used to {@link FlushSender#flush() signal} to the connection that writes should now be
     * flushed.
     *
     * @param sender {@link FlushSender} to signal flushes to the connection.
     * @return {@link WriteEventsListener} that would listen to write events on the connection for which custom flushes
     * are required.
     */
    WriteEventsListener apply(FlushSender sender);

    /**
     * Returns {@code true} if pending writes, if any, MUST be flushed when the connection write buffer is full. <p>
     *     This method is expected to be idempotent.
     *
     * @return {@code true} if pending writes, if any, MUST be flushed when the connection write buffer is full.
     */
    default boolean flushOnWriteBufferFull() {
        return true;
    }

    /**
     * Creates a default {@link FlushStrategy}.
     *
     * @return Default {@link FlushStrategy}.
     */
    static FlushStrategy defaultFlushStrategy() {
        return flushOnEach();
    }

    /**
     * Creates a {@link FlushStrategy} that will {@link FlushSender#flush() flush writes} on each call to the returned
     * {@link WriteEventsListener#itemWritten()} from {@link #apply(FlushSender)}.
     *
     * @return A {@link FlushStrategy} that will {@link FlushSender#flush() flush writes} on each call to the returned
     * {@link WriteEventsListener#itemWritten()} from {@link #apply(FlushSender)}.
     */
    static FlushStrategy flushOnEach() {
        return FLUSH_ON_EACH;
    }

    /**
     * Creates a {@link FlushStrategy} that will {@link FlushSender#flush() flush writes} in a batch of
     * {@code batchSize} or on expiration of a batch duration i.e. when an item is emitted from
     * {@code durationBoundaries}.
     *
     * @param batchSize Number of items in each batch which needs flushing.
     * @param durationBoundaries Batch durations. Every time an item is emitted on this {@link Publisher}, the returned
     * {@link FlushStrategy} will {@link FlushSender#flush() flush writes}.
     * @return A {@link FlushStrategy} that will {@link FlushSender#flush() flush writes} in a batch of
     * {@code batchSize} or on expiration of a batch duration i.e. when an item is emitted from
     * {@code durationBoundaries}.
     */
    static FlushStrategy batchFlush(int batchSize, Publisher<?> durationBoundaries) {
        return new BatchFlush(durationBoundaries, batchSize);
    }

    /**
     * Creates a {@link FlushStrategy} that will {@link FlushSender#flush() flush writes} when an item is emitted from
     * {@code flushBoundaries}.
     *
     * @param flushBoundaries Flush boundaries. Every time an item is emitted on this {@link Publisher}, the returned
     * {@link FlushStrategy} will {@link FlushSender#flush() flush writes}.
     *
     * @return A {@link FlushStrategy} that will {@link FlushSender#flush() flush writes} when an item is emitted from
     * {@code flushBoundaries}.
     */
    static FlushStrategy flushWith(Publisher<?> flushBoundaries) {
        return batchFlush(MAX_VALUE, flushBoundaries);
    }

    /**
     * Creates a {@link FlushStrategy} that will {@link FlushSender#flush() flush writes} when
     * {@link WriteEventsListener#writeTerminated()} is called on the {@link WriteEventsListener} returned from
     * {@link #apply(FlushSender)}.
     *
     * @return A {@link FlushStrategy} that will {@link FlushSender#flush() flush writes} when either of
     * {@link WriteEventsListener#writeTerminated()} is called on the {@link WriteEventsListener} returned from
     * {@link #apply(FlushSender)}.
     */
    static FlushStrategy flushOnEnd() {
        return FLUSH_ON_END;
    }

    /**
     * An abstraction for a {@link FlushStrategy} to flush writes by calling {@link #flush()}.
     */
    @FunctionalInterface
    interface FlushSender {

        /**
         * Sends a flush on the associated connection. This method can be invoked from any thread, however, when a
         * deterministic write-flush ordering is required, it should be called from within a relevant method of
         * {@link WriteEventsListener}.
         */
        void flush();
    }

    /**
     * A listener of write events from the connection on which the related {@link FlushStrategy} is
     * {@link FlushStrategy#apply(FlushSender) applied}.
     * For each {@link WriteEventsListener} returned from {@link FlushStrategy#apply(FlushSender)}, following calls
     * will be made:
     * <ul>
     *     <li>Exactly one call to {@link #writeStarted()}</li>
     *     <li>Zero or more calls to {@link #itemWritten()}</li>
     *     <li>At most one call to {@link #writeTerminated()}</li>
     *     <li>At most one call to {@link #writeCancelled()}</li>
     *     <li>At least one call to either {@link #writeTerminated()} or {@link #writeCancelled()}</li>
     * </ul>
     * {@link #writeStarted()} always
     * <a href="https://docs.oracle.com/javase/specs/jls/se8/html/jls-17.html#jls-17.4.5">happens-before</a> a call to
     * any other methods. <p>
     *     None of {@link #writeStarted()}, {@link #itemWritten()} and {@link #writeTerminated()} can be called
     *     concurrently with each other but {@link #writeCancelled()} can be called concurrently with
     *     {@link #itemWritten()} or {@link #writeTerminated()}.
     */
    interface WriteEventsListener {

        /**
         * For each new {@link WriteEventsListener} returned from {@link FlushStrategy#apply(FlushSender)}, this method
         * will be called exactly once before any items are written to the connection.
         * <p>
         *     This will be followed by zero or more calls to {@link #itemWritten()} and at most one call to
         *     {@link #writeTerminated()} and {@link #writeCancelled()} or both.
         */
        void writeStarted();

        /**
         * For each new {@link WriteEventsListener} returned from {@link FlushStrategy#apply(FlushSender)}, this method
         * will be called once after any item is written to the connection.
         * <p>
         *     This will be followed by zero or more calls to this method and at most one call to
         *     {@link #writeTerminated()} and {@link #writeCancelled()} or both.
         *     {@link #writeCancelled()} can be called concurrently with this method.
         */
        void itemWritten();

        /**
         * For each new {@link WriteEventsListener} returned from {@link FlushStrategy#apply(FlushSender)}, this method
         * will be called at most once when all other items are written to the connection.
         * <p>
         *     {@link #writeCancelled()} <em>MAY</em> be called concurrently or after this method.
         */
        void writeTerminated();

        /**
         * For each new {@link WriteEventsListener} returned from {@link FlushStrategy#apply(FlushSender)}, this method
         * will be called at most once when writes are cancelled.
         * <p>
         *     {@link #itemWritten()} or {@link #writeTerminated()} <em>MAY</em> be called concurrently or after this
         *     method.
         */
        void writeCancelled();
    }
}
