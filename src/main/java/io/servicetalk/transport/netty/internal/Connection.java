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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.FlushStrategy;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

/**
 * A wrapper over a physical connection providing a way to read/write as a {@link Publisher}.
 *
 * @param <Read> Type of objects read from this connection.
 * @param <Write> Type of objects written to this connection.
 */
public interface Connection<Read, Write> extends ConnectionContext {
    /**
     * Returns a {@link Completable} that notifies when the connection has begun its closing sequence.
     *
     * @return a {@link Completable} that notifies when the connection has begun its closing sequence. A configured
     * {@link CloseHandler} will determine whether more reads or writes will be allowed on this {@link Connection}.
     */
    Completable onClosing();

    /**
     * Returns {@link Publisher} that emits all items as read from this connection.
     *
     * @return {@link Publisher} that emits all items as read from this connection.
     * Concurrent subscriptions (call {@link Publisher#subscribe(Subscriber)} when a {@link Subscriber} is already active) are disallowed
     * but sequential subscriptions (call {@link Publisher#subscribe(Subscriber)} when a previous {@link Subscriber} has terminated) are allowed.
     */
    Publisher<Read> read();

    /**
     * Returns the {@link TerminalPredicate} associated with this {@link Connection} to detect terminal messages for the
     * otherwise infinite {@link Publisher} returned by {@link #read()}.
     *
     * @return {@link TerminalPredicate} for this connection.
     */
    TerminalPredicate<Read> getTerminalMsgPredicate();

    /**
     * Writes all elements emitted by the passed {@link Publisher} on this connection.
     *
     * @param write {@link Publisher}, all objects emitted from which are written on this connection.
     * @param flushStrategy {@link FlushStrategy} to apply for the writes.
     *
     * @return {@link Completable} that terminates as follows:
     * <ul>
     *     <li>With an error, if any item emitted can not be written successfully on the connection.</li>
     *     <li>With an error, if {@link Publisher} emits an error.</li>
     *     <li>With an error, if there is a already an active write on this connection.</li>
     *     <li>Successfully when {@link Publisher} completes and all items emitted are written successfully.</li>
     * </ul>
     */
    Completable write(Publisher<Write> write, FlushStrategy flushStrategy);

    /**
     * Writes all elements emitted by the passed {@link Publisher} on this connection.
     *
     * @param write {@link Publisher}, all objects emitted from which are written on this connection.
     * @param flushStrategy {@link FlushStrategy} to apply for the writes.
     * @param requestNSupplierFactory A {@link Supplier} of {@link RequestNSupplier} for this write.
     *
     * @return {@link Completable} that terminates as follows:
     * <ul>
     *     <li>With an error, if any item emitted can not be written successfully on the connection.</li>
     *     <li>With an error, if {@link Publisher} emits an error.</li>
     *     <li>With an error, if there is a already an active write on this connection.</li>
     *     <li>Successfully when {@link Publisher} completes and all items emitted are written successfully.</li>
     * </ul>
     */
    Completable write(Publisher<Write> write, FlushStrategy flushStrategy, Supplier<RequestNSupplier> requestNSupplierFactory);

    /**
     * Write and flushes the object emitted by the passed {@link Single} on this connection.
     * @param write {@link Single}, result of which is written on this connection.
     *
     * @return {@link Completable} that terminates as follows:
     * <ul>
     *     <li>With an error, if the item emitted can not be written successfully on the connection.</li>
     *     <li>With an error, If {@link Single} emits an error.</li>
     *     <li>With an error, if there is a already an active write on this connection.</li>
     *     <li>Successfully when result of the {@link Single} is written successfully.</li>
     * </ul>
     */
    Completable writeAndFlush(Single<Write> write);

    /**
     * Write and flushes the passed {@link Buffer} on this connection.
     * @param write {@link Buffer} to write on this connection.
     *
     * @return {@link Completable} that terminates as follows:
     * <ul>
     *     <li>With an error, if {@link Buffer} could not be written successfully on the connection.</li>
     *     <li>With an error, if there is a already an active write on this connection.</li>
     *     <li>Successfully, if {@link Buffer} could was written successfully on the connection.</li>
     * </ul>
     */
    Completable writeAndFlush(Write write);

    /**
     * A supplier that provides a correct value of {@code n} for calls to {@link Subscription#request(long)} per {@link Subscription}.
     * A new {@link RequestNSupplier} is created for each {@link Publisher} that is written.
     *
     * <em>Any method of an instance of {@link RequestNSupplier} will never be called concurrently with the same or other methods of the same instance.</em>
     * This means that implementations do not have to worry about thread-safety.
     */
    interface RequestNSupplier {
        /**
         * Callback whenever an item is written on the connection.
         *<p>
         * Write buffer capacity may not correctly reflect size of the object written.
         * Hence capacity before may not necessarily be more than capacity after write.
         *
         * @param written Item that was written.
         * @param writeBufferCapacityBeforeWrite Capacity of the write buffer before this item was written.
         * @param writeBufferCapacityAfterWrite Capacity of the write buffer after this item was written.
         */
        void onItemWrite(Object written, long writeBufferCapacityBeforeWrite, long writeBufferCapacityAfterWrite);

        /**
         * Given the current capacity of the write buffer, supply how many items to request next from the associated {@link Subscription}.
         *<p>
         *     This method is invoked every time there could be a need to request more items from the write {@link Publisher}.
         *     This means that the supplied {@code writeBufferCapacityInBytes} may include the capacity for which we have already
         *     requested the write {@link Publisher}. For suppliers that must only request more when there is an increase in capacity,
         *     use {@link OverlappingCapacityAwareSupplier}.
         *
         * @param writeBufferCapacityInBytes Current write buffer capacity. This will always be non-negative and will be 0 if buffer is full.
         *
         * @return Number of items to request next from the associated {@link Subscription}.
         * Implementation may assume that whatever is retuned here is sent as-is to the associated {@link Subscription}.
         *
         * @see OverlappingCapacityAwareSupplier
         */
        long getRequestNFor(long writeBufferCapacityInBytes);

        /**
         * Returns a new instance of a default implementation of {@link RequestNSupplier}.
         *
         * @return A new instance of a default implementation of {@link RequestNSupplier}.
         */
        static RequestNSupplier newDefaultSupplier() {
            return new MaxSizeBasedRequestNSupplier();
        }
    }

    /**
     * A dynamic {@link Predicate} to detect terminal message in a stream as returned by {@link Connection#read()}.
     * Use {@link #replaceCurrent(Predicate)} for replacing the current {@link Predicate} and {@link #discardIfCurrent(Predicate)} to
     * revert back to the original {@link Predicate}. <p>
     *     If a dynamic predicate (as set by {@link #replaceCurrent(Predicate)} terminates the stream,
     *     this will automatically revert to the original {@link Predicate} as done by calling {@link #discardIfCurrent(Predicate)}.
     *
     * @param <Read> Type of objects tested by this {@link Predicate}.
     */
    final class TerminalPredicate<Read> implements Predicate<Read> {

        private static final AtomicReferenceFieldUpdater<TerminalPredicate, Predicate> currentUpdater =
                newUpdater(TerminalPredicate.class, Predicate.class, "current");

        private final Predicate<Read> original;
        private final boolean unsupported;
        private volatile Predicate<Read> current;

        /**
         * New instance.
         * @param original {@link Predicate}. This is the {@link Predicate} that will be set as <i>current</i> after {@link #discardIfCurrent(Predicate)} is called.
         */
        public TerminalPredicate(Predicate<Read> original) {
            this.original = requireNonNull(original);
            current = original;
            unsupported = false;
        }

        TerminalPredicate() {
            original = read1 -> false;
            current = original;
            unsupported = true;
        }

        /**
         * Sets <i>current</i> {@link Predicate} to {@code next}.
         *
         * @param next {@link Predicate} to set as <i>current</i>.
         */
        public void replaceCurrent(Predicate<Read> next) {
            checkUnsupported();
            current = requireNonNull(next);
        }

        /**
         * Discards the <i>current</i> {@link Predicate} (if same as passed {@code current}) and sets <i>current</i> as the original {@link Predicate}.
         *
         * @param current {@link Predicate} to discard if current.
         * @return {@code true} if the predicate was discarded.
         */
        public boolean discardIfCurrent(Predicate<Read> current) {
            return currentUpdater.compareAndSet(this, current, original);
        }

        @Override
        public boolean test(Read read) {
            final Predicate<Read> current = this.current;
            boolean terminated = current.test(read);
            if (terminated) {
                // Reset to original predicate when the dynamic predicate terminated.
                currentUpdater.compareAndSet(this, current, original);
            }
            return terminated;
        }

        private void checkUnsupported() {
            if (unsupported) {
                throw new UnsupportedOperationException("Dynamic predicates not supported.");
            }
        }
    }
}
