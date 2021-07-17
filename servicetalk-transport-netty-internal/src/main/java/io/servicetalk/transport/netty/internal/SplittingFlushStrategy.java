/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.internal.SequentialCancellable;
import io.servicetalk.transport.netty.internal.NettyConnectionContext.FlushStrategyProvider;
import io.servicetalk.transport.netty.internal.SplittingFlushStrategy.FlushBoundaryProvider.FlushBoundary;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import javax.annotation.Nullable;

import static io.servicetalk.transport.netty.internal.SplittingFlushStrategy.FlushBoundaryProvider.FlushBoundary.End;
import static io.servicetalk.transport.netty.internal.SplittingFlushStrategy.FlushBoundaryProvider.FlushBoundary.InProgress;
import static io.servicetalk.transport.netty.internal.SplittingFlushStrategy.FlushBoundaryProvider.FlushBoundary.Start;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

/**
 * A {@link FlushStrategy} that splits writes into logical write boundaries and manages flush state across those logical
 * write boundaries. Actual flush logic is delegated to an externally provided (and updatable) {@link FlushStrategy}.
 */
public final class SplittingFlushStrategy implements FlushStrategy {
    private static final AtomicReferenceFieldUpdater<SplittingFlushStrategy, SplittingWriteEventsListener>
            listenerUpdater = newUpdater(SplittingFlushStrategy.class, SplittingWriteEventsListener.class,
            "listener");
    private final FlushBoundaryProvider flushBoundaryProvider;
    private final FlushStrategyHolder flushStrategyHolder;

    @Nullable
    private volatile SplittingWriteEventsListener listener;
    /**
     * Create a new instance.
     *
     * @param delegate {@link FlushStrategy} to use for flushing each delineated write boundary.
     * @param boundaryProvider {@link FlushBoundaryProvider} to provide {@link FlushBoundary} to delineate writes.
     */
    public SplittingFlushStrategy(final FlushStrategy delegate, final FlushBoundaryProvider boundaryProvider) {
        this.flushBoundaryProvider = requireNonNull(boundaryProvider);
        flushStrategyHolder = new FlushStrategyHolder(delegate);
    }

    @Override
    public WriteEventsListener apply(final FlushSender sender) {
        SplittingWriteEventsListener cListener = listener;
        if (cListener != null) {
            return cListener;
        }
        SplittingWriteEventsListener l = listenerUpdater.updateAndGet(this,
                existing -> existing != null ? existing :
                        new SplittingWriteEventsListener(sender, flushBoundaryProvider, flushStrategyHolder));
        assert l != null;
        return l;
    }

    @Override
    public boolean shouldFlushOnUnwritable() {
        return flushStrategyHolder.currentStrategy().shouldFlushOnUnwritable();
    }

    /**
     * Updates the {@link FlushStrategy} that is used for flushing each delineated write boundary.
     *
     * @param strategyProvider {@link FlushStrategyProvider} to provide the {@link FlushStrategy} to use.
     *
     * @return A {@link Cancellable} that will cancel this update.
     */
    public Cancellable updateFlushStrategy(FlushStrategyProvider strategyProvider) {
        return flushStrategyHolder.updateFlushStrategy(strategyProvider);
    }

    /**
     * Updates the {@link FlushStrategy} that is used for flushing each delineated write boundary. This update will
     * automatically be cancelled after it has been applied to {@code boundariesTillCancel} delineated write boundaries.
     *
     * @param strategyProvider {@link FlushStrategyProvider} to provide the {@link FlushStrategy} to use.
     * @param boundariesTillCancel Number of delineated write boundaries to apply this flush on.
     */
    public void updateFlushStrategy(FlushStrategyProvider strategyProvider, int boundariesTillCancel) {
        CountingFlushStrategyProvider countingProvider =
                new CountingFlushStrategyProvider(strategyProvider, boundariesTillCancel);
        countingProvider.nextCancellable(flushStrategyHolder.updateFlushStrategy(countingProvider));
    }

    /**
     * A provider of {@link FlushBoundary} for each written item.
     */
    @FunctionalInterface
    public interface FlushBoundaryProvider {

        /**
         * An enumeration for boundary of flushes on which this {@link SplittingFlushStrategy} splits writes.
         */
        enum FlushBoundary {
            Start,
            InProgress,
            End
        }

        /**
         * Detect the {@link FlushBoundary} for the passed {@code itemWritten}.
         *
         * @param itemWritten Item written which determines the {@link FlushBoundary}.
         * @return {@link FlushBoundary} representing the passed  {@code itemWritten}.
         */
        FlushBoundary detectBoundary(@Nullable Object itemWritten);
    }

    private static final class SplittingWriteEventsListener implements WriteEventsListener {
        private static final WriteEventsListener NOOP_LISTENER = new NoopWriteEventsListener() { };

        private final FlushSender flushSender;
        private final FlushBoundaryProvider flushBoundaryProvider;
        private final FlushStrategyHolder flushStrategyHolder;

        /**
         * This field should only be touched withing the context of the methods of {@link WriteEventsListener} and hence
         * does not need to provide additional visibility guarantees.
         */
        private WriteEventsListener delegate;
        @Nullable
        private FlushBoundary previousBoundary;

        SplittingWriteEventsListener(final FlushSender flushSender, final FlushBoundaryProvider flushBoundaryProvider,
                                     final FlushStrategyHolder flushStrategyHolder) {
            delegate = NOOP_LISTENER;
            this.flushSender = flushSender;
            this.flushBoundaryProvider = flushBoundaryProvider;
            this.flushStrategyHolder = flushStrategyHolder;
        }

        @Override
        public void writeStarted() {
            delegate.writeStarted();
        }

        @Override
        public void itemWritten(@Nullable final Object written) {
            FlushBoundary boundary = flushBoundaryProvider.detectBoundary(written);
            adjustForMissingBoundaries(boundary);
            previousBoundary = boundary;
            switch (boundary) {
                case Start:
                    delegate = flushStrategyHolder.currentStrategy().apply(flushSender);
                    delegate.writeStarted();
                    delegate.itemWritten(written);
                    break;
                case InProgress:
                    delegate.itemWritten(written);
                    break;
                case End:
                    delegate.itemWritten(written);
                    delegate.writeTerminated(); // New boundary started, terminate the old listener
                    delegate = NOOP_LISTENER;
                    break;
                default:
                    throw new IllegalArgumentException("Unknown flush boundary: " + boundary);
            }
        }

        private void adjustForMissingBoundaries(final FlushBoundary boundary) {
            if (boundary == Start && (previousBoundary == Start || previousBoundary == InProgress)) {
                // consecutive starts or missing end after InProgress, terminate previous listener for these
                // unexpected scenarios
                delegate.writeTerminated();
                delegate = NOOP_LISTENER;
                return;
            }
            if ((boundary == InProgress || boundary == End) && (previousBoundary == null || previousBoundary == End)) {
                // missing start or just consecutive ends
                delegate = flushStrategyHolder.currentStrategy().apply(flushSender);
                delegate.writeStarted();
            }
        }

        @Override
        public void writeTerminated() {
            delegate.writeTerminated();
        }

        @Override
        public void writeCancelled() {
            delegate.writeCancelled();
        }
    }

    private static final class CountingFlushStrategyProvider extends SequentialCancellable
            implements FlushStrategyProvider {
        private static final AtomicIntegerFieldUpdater<CountingFlushStrategyProvider> boundariesLeftUpdater =
                AtomicIntegerFieldUpdater.newUpdater(CountingFlushStrategyProvider.class, "boundariesLeft");

        private final FlushStrategyProvider strategyProvider;

        private volatile int boundariesLeft;

        CountingFlushStrategyProvider(final FlushStrategyProvider strategyProvider, final int boundariesTillCancel) {
            this.strategyProvider = strategyProvider;
            this.boundariesLeft = boundariesTillCancel;
        }

        @Override
        public FlushStrategy computeFlushStrategy(final FlushStrategy current, final boolean isCurrentOriginal) {
            FlushStrategy flushStrategy = strategyProvider.computeFlushStrategy(current, isCurrentOriginal);
            return sender -> {
                WriteEventsListener actual = flushStrategy.apply(sender);
                return new BoundaryCountingWriteListener(actual);
            };
        }

        private final class BoundaryCountingWriteListener implements WriteEventsListener {

            private final WriteEventsListener delegate;

            BoundaryCountingWriteListener(final WriteEventsListener delegate) {
                this.delegate = delegate;
            }

            @Override
            public void writeStarted() {
                delegate.writeStarted();
            }

            @Override
            public void itemWritten(@Nullable final Object written) {
                delegate.itemWritten(written);
            }

            @Override
            public void writeTerminated() {
                try {
                    delegate.writeTerminated();
                } finally {
                    if (boundariesLeftUpdater.decrementAndGet(CountingFlushStrategyProvider.this) == 0) {
                        CountingFlushStrategyProvider.this.cancel();
                    }
                }
            }

            @Override
            public void writeCancelled() {
                delegate.writeCancelled();
                // Since, this listener is used as part of splitting writes, if the top level write is cancelled, we do
                // not accept further interactions, hence the boundary counting is not required.
            }
        }
    }
}
