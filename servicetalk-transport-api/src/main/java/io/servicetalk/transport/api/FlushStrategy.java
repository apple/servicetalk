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

import io.servicetalk.concurrent.api.Publisher;

import static io.servicetalk.transport.api.FlushBeforeEnd.FLUSH_BEFORE_END;
import static io.servicetalk.transport.api.FlushOnEach.FLUSH_ON_EACH;

/**
 * A strategy to decorate a {@link Publisher} with flush boundaries.
 */
@FunctionalInterface
public interface FlushStrategy {

    /**
     * Applies this flush strategy to the passed {@link Publisher}.<p>
     *<b>This strategy will apply only on the {@link Publisher} returned by {@link FlushStrategyHolder#getSource()} and not on the passed {@code source} here.</b>
     *<p>
     * <em>Generally a {@link FlushStrategy} will generate a {@link FlushStrategyHolder} for each distinct write operation.
     * If the {@link FlushStrategyHolder} returned by this method shares state between multiple calls the flush behavior maybe unexpected.</em>
     *
     *
     * @param <T> The type of data being flushed.
     * @param source {@link Publisher} to apply this strategy on.
     * @return A {@link FlushStrategyHolder} that contains flush signals and the decorated {@link Publisher} to emit the signals.
     */
    <T> FlushStrategyHolder<T> apply(Publisher<T> source);

    /**
     * Creates a default {@link FlushStrategy}.<p>
     *
     * @return Default {@link FlushStrategy}.
     */
    static FlushStrategy defaultFlushStrategy() {
        return flushOnEach();
    }

    /**
     * Creates a new {@link FlushStrategy} that flushes the connection on each item emitted from a {@link Publisher} that this strategy is applied.<p>
     *
     * @return {@link FlushStrategy}.
     */
    static FlushStrategy flushOnEach() {
        return FLUSH_ON_EACH;
    }

    /**
     * Flush writes in a batch of {@code batchSize} or on expiration of a batch duration i.e. when an item is emitted from {@code durationBoundaries}.<p>
     *
     * @param batchSize          Number of items in each batch which needs flushing.
     * @param durationBoundaries Time durations
     * @return {@link FlushStrategy}
     */
    static FlushStrategy batchFlush(int batchSize, Publisher<Long> durationBoundaries) {
        return new BatchFlush(durationBoundaries, batchSize);
    }

    /**
     * Creates a new {@link FlushStrategy} that flushes the connection upon completion or error of the {@link Publisher} that this strategy is applied.<p>
     *
     * @return {@link FlushStrategy}.
     */
    static FlushStrategy flushBeforeEnd() {
        return FLUSH_BEFORE_END;
    }
}
