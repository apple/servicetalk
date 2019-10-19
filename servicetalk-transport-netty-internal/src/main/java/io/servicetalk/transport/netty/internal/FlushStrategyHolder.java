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
import io.servicetalk.transport.netty.internal.NettyConnectionContext.FlushStrategyProvider;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

/**
 * A utility class to hold a {@link FlushStrategy} and allow it to be updated atomically using
 * {@link #updateFlushStrategy(NettyConnectionContext.FlushStrategyProvider)}.
 */
public final class FlushStrategyHolder {
    private static final AtomicReferenceFieldUpdater<FlushStrategyHolder, FlushStrategy> flushStrategyUpdater =
            newUpdater(FlushStrategyHolder.class, FlushStrategy.class, "flushStrategy");

    private final FlushStrategy originalFlushStrategy;
    private volatile FlushStrategy flushStrategy;

    /**
     * Creates new instance.
     *
     * @param flushStrategy Initial {@link FlushStrategy} to use.
     */
    public FlushStrategyHolder(final FlushStrategy flushStrategy) {
        // Wrap the strategy so that we can do reference equality to check if the strategy has been modified.
        originalFlushStrategy = new DelegatingFlushStrategy(flushStrategy);
        this.flushStrategy = originalFlushStrategy;
    }

    /**
     * Returns the current value of the enclosed {@link FlushStrategy}.
     *
     * @return Current value of the enclosed {@link FlushStrategy}.
     */
    public FlushStrategy currentStrategy() {
        return flushStrategy;
    }

    /**
     * Updates {@link FlushStrategy} enclosed in this {@link FlushStrategyHolder}.
     *
     * @param strategyProvider {@link NettyConnectionContext.FlushStrategyProvider} to provide a new
     * {@link FlushStrategy}.
     * {@link NettyConnectionContext.FlushStrategyProvider#computeFlushStrategy(FlushStrategy, boolean)}
     * <strong>MAY</strong> be invoked multiple times for a single call to this method and is expected to be idempotent.
     *
     * @return A {@link Cancellable} that will cancel this update.
     */
    public Cancellable updateFlushStrategy(final FlushStrategyProvider strategyProvider) {
        for (;;) {
            final FlushStrategy cStrategy = flushStrategy;
            FlushStrategy newStrategy = strategyProvider.computeFlushStrategy(cStrategy,
                    cStrategy == originalFlushStrategy);
            if (flushStrategyUpdater.compareAndSet(this, cStrategy, newStrategy)) {
                return () -> flushStrategyUpdater.getAndUpdate(FlushStrategyHolder.this,
                        // Only revert if the current strategy is what we had set, otherwise, some other code path has
                        // already modified the strategy.
                        fs -> fs == newStrategy ? originalFlushStrategy : fs);
            }
        }
    }
}
