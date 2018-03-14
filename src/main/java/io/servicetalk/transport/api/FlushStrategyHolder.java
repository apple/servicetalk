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
package io.servicetalk.transport.api;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Publisher;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import javax.annotation.Nullable;

/**
 * A source that provides a {@link Publisher} to write on any connection and also an associated strategy for flushing.
 *
 * @param <T> Type of elements emitted by the {@link Publisher} contained in this holder.
 */
public interface FlushStrategyHolder<T> {

    /**
     * Stream of items to write.
     *
     * @return {@link Publisher} of items to write.
     */
    Publisher<T> getSource();

    /**
     * A source of signals that will flush all pending writes.
     *
     * @return {@link FlushSignals} to trigger flushes.
     */
    FlushSignals getFlushSignals();

    /**
     * Creates a {@link FlushStrategyHolder} for the passed {@code source} and {@code flushSignals}.
     *
     * @param source that needs to flushed using {@code flushSignals}.
     * @param flushSignals A {@link Publisher} that emits an item whenever the writes are to be flushed.
     * @param <T> Type of items emitted by {@code source}.
     * @return A new instance of {@link FlushStrategyHolder}.
     */
    static <T> FlushStrategyHolder<T> from(Publisher<T> source, FlushSignals flushSignals) {
        return new FlushStrategyHolder<T>() {
            @Override
            public Publisher<T> getSource() {
                return source;
            }

            @Override
            public FlushSignals getFlushSignals() {
                return flushSignals;
            }
        };
    }

    /**
     * A source of events that triggers flush of pending writes on the connection.<p>
     *     All flushes received via {@link #signalFlush()} are ignored if there is no active listener registered via {@link #listen(Runnable)}.
     */
    final class FlushSignals {

        private static final AtomicReferenceFieldUpdater<FlushSignals, Runnable> listenerUpdater =
                AtomicReferenceFieldUpdater.newUpdater(FlushSignals.class, Runnable.class, "listener");

        @SuppressWarnings("unused") @Nullable private volatile Runnable listener;

        /**
         * Register a {@link Runnable} that will be invoked whenever a flush is signaled by {@link #signalFlush()}.<p>
         *     Only one {@link Runnable} is allowed to be registered at any time.
         *     {@link Runnable#run()} will be invoked concurrently if {@link #signalFlush()} is called concurrently.
         *
         * @param onFlush {@link Runnable} to invoke when a flush signal is received.
         *
         * @return {@link Cancellable} that is used to cancel this registration.
         * @throws IllegalStateException If there is already a listener registered.
         */
        public Cancellable listen(Runnable onFlush) {
            if (listenerUpdater.compareAndSet(this, null, onFlush)) {
                return () -> listenerUpdater.compareAndSet(this, onFlush, null);
            }
            throw new IllegalStateException("Only one listener allowed.");
        }

        /**
         * Send a flush signal to the active listener, if any, registered via {@link #listen(Runnable)} and not yet cancelled.
         *
         * @return {@code true} if the signal was sent i.e. there was an active listener.
         */
        public boolean signalFlush() {
            Runnable l = listener;
            if (l == null) {
                // Its ok to miss the signal if there is a race between subscribe and flush.
                return false;
            }
            l.run();
            return true;
        }
    }
}
