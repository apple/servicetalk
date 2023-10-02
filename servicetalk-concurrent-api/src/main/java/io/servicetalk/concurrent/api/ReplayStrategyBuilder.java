/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.PublisherSource.Subscriber;

import java.util.function.Function;
import java.util.function.Supplier;

import static io.servicetalk.concurrent.api.Completable.never;
import static io.servicetalk.concurrent.api.MulticastPublisher.DEFAULT_MULTICAST_QUEUE_LIMIT;
import static java.util.Objects.requireNonNull;

/**
 * A builder of {@link ReplayStrategy}.
 * @param <T> The type of data for {@link ReplayStrategy}.
 */
public final class ReplayStrategyBuilder<T> {
    private int minSubscribers = 1;
    private final Supplier<ReplayAccumulator<T>> accumulatorSupplier;
    private boolean cancelUpstream;
    private int queueLimitHint = DEFAULT_MULTICAST_QUEUE_LIMIT;
    private Function<Throwable, Completable> terminalResubscribe = t -> never();

    /**
     * Create a new instance.
     * @param accumulatorSupplier provides the {@link ReplayAccumulator} to use on each subscribe to upstream.
     */
    public ReplayStrategyBuilder(Supplier<ReplayAccumulator<T>> accumulatorSupplier) {
        this.accumulatorSupplier = requireNonNull(accumulatorSupplier);
    }

    /**
     * Set the minimum number of downstream subscribers before subscribing upstream.
     * @param minSubscribers the minimum number of downstream subscribers before subscribing upstream.
     * @return {@code this}.
     */
    public ReplayStrategyBuilder<T> minSubscribers(int minSubscribers) {
        if (minSubscribers <= 0) {
            throw new IllegalArgumentException("minSubscribers: " + minSubscribers + " (expected >0)");
        }
        this.minSubscribers = minSubscribers;
        return this;
    }

    /**
     * Determine if all the downstream subscribers cancel, should upstream be cancelled.
     * @param cancelUpstream {@code true} if all the downstream subscribers cancel, should upstream be cancelled.
     * {@code false} will not cancel upstream if all downstream subscribers cancel.
     * @return {@code this}.
     */
    public ReplayStrategyBuilder<T> cancelUpstream(boolean cancelUpstream) {
        this.cancelUpstream = cancelUpstream;
        return this;
    }

    /**
     * Set a hint to limit the number of elements which will be queued for each {@link Subscriber} in order to
     * compensate for unequal demand and late subscribers.
     * @param queueLimitHint a hint to limit the number of elements which will be queued for each {@link Subscriber} in
     * order to compensate for unequal demand and late subscribers.
     * @return {@code this}.
     */
    public ReplayStrategyBuilder<T> queueLimitHint(int queueLimitHint) {
        if (queueLimitHint < 1) {
            throw new IllegalArgumentException("maxQueueSize: " + queueLimitHint + " (expected >1)");
        }
        this.queueLimitHint = queueLimitHint;
        return this;
    }

    /**
     * Set a {@link Function} that is invoked when a terminal signal arrives from upstream and determines when state
     * is reset to allow for upstream resubscribe.
     * @param terminalResubscribe A {@link Function} that is invoked when a terminal signal arrives from upstream, and
     * returns a {@link Completable} whose termination resets the state of the returned {@link Publisher} and allows
     * for downstream resubscribing. The argument to this function is as follows:
     * <ul>
     *   <li>{@code null} if upstream terminates with {@link Subscriber#onComplete()}</li>
     *   <li>otherwise the {@link Throwable} from {@link Subscriber#onError(Throwable)}</li>
     * </ul>
     * @return {@code this}.
     */
    public ReplayStrategyBuilder<T> terminalResubscribe(
            Function<Throwable, Completable> terminalResubscribe) {
        this.terminalResubscribe = requireNonNull(terminalResubscribe);
        return this;
    }

    /**
     * Build the {@link ReplayStrategy}.
     * @return the {@link ReplayStrategy}.
     */
    public ReplayStrategy<T> build() {
        return new DefaultReplayStrategy<>(minSubscribers, accumulatorSupplier, cancelUpstream, queueLimitHint,
                terminalResubscribe);
    }

    private static final class DefaultReplayStrategy<T> implements ReplayStrategy<T> {
        private final int minSubscribers;
        private final Supplier<ReplayAccumulator<T>> accumulatorSupplier;
        private final boolean cancelUpstream;
        private final int queueLimitHint;
        private final Function<Throwable, Completable> terminalResubscribe;

        private DefaultReplayStrategy(
                final int minSubscribers, final Supplier<ReplayAccumulator<T>> accumulatorSupplier,
                final boolean cancelUpstream, final int queueLimitHint,
                final Function<Throwable, Completable> terminalResubscribe) {
            this.minSubscribers = minSubscribers;
            this.accumulatorSupplier = accumulatorSupplier;
            this.cancelUpstream = cancelUpstream;
            this.queueLimitHint = queueLimitHint;
            this.terminalResubscribe = terminalResubscribe;
        }

        @Override
        public int minSubscribers() {
            return minSubscribers;
        }

        @Override
        public Supplier<ReplayAccumulator<T>> accumulatorSupplier() {
            return accumulatorSupplier;
        }

        @Override
        public boolean cancelUpstream() {
            return cancelUpstream;
        }

        @Override
        public int queueLimitHint() {
            return queueLimitHint;
        }

        @Override
        public Function<Throwable, Completable> terminalResubscribe() {
            return terminalResubscribe;
        }
    }
}
