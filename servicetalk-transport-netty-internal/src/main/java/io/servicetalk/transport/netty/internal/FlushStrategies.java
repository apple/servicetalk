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
import io.servicetalk.transport.netty.internal.FlushStrategy.FlushSender;
import io.servicetalk.transport.netty.internal.FlushStrategy.WriteEventsListener;

import static io.servicetalk.transport.netty.internal.FlushOnEach.FLUSH_ON_EACH;
import static io.servicetalk.transport.netty.internal.FlushOnEnd.FLUSH_ON_END;
import static java.lang.Integer.MAX_VALUE;

/**
 * A factory for creating {@link FlushStrategy}.
 */
public final class FlushStrategies {

    private FlushStrategies() {
        // No instances.
    }

    /**
     * Creates a default {@link FlushStrategy}.
     *
     * @return Default {@link FlushStrategy}.
     */
    public static FlushStrategy defaultFlushStrategy() {
        return flushOnEach();
    }

    /**
     * Creates a {@link FlushStrategy} that will {@link FlushSender#flush() flush writes} on each call to
     * the returned {@link WriteEventsListener#itemWritten()} from {@link FlushStrategy#apply(FlushSender)}.
     *
     * @return A {@link FlushStrategy} that will {@link FlushSender#flush() flush writes} on each call to the returned
     * {@link WriteEventsListener#itemWritten()} from {@link FlushStrategy#apply(FlushSender)}.
     */
    public static FlushStrategy flushOnEach() {
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
    public static FlushStrategy batchFlush(int batchSize, Publisher<?> durationBoundaries) {
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
    public static FlushStrategy flushWith(Publisher<?> flushBoundaries) {
        return batchFlush(MAX_VALUE, flushBoundaries);
    }

    /**
     * Creates a {@link FlushStrategy} that will {@link FlushSender#flush() flush writes} when
     * {@link WriteEventsListener#writeTerminated()} is called on the {@link WriteEventsListener} returned from
     * {@link FlushStrategy#apply(FlushSender)}.
     *
     * @return A {@link FlushStrategy} that will {@link FlushSender#flush() flush writes} when either of
     * {@link WriteEventsListener#writeTerminated()} is called on the {@link WriteEventsListener} returned from
     * {@link FlushStrategy#apply(FlushSender)}.
     */
    public static FlushStrategy flushOnEnd() {
        return FLUSH_ON_END;
    }
}
