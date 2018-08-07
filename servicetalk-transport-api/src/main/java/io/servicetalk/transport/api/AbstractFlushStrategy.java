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
import io.servicetalk.transport.api.FlushStrategyHolder.FlushSignals;

import org.reactivestreams.Subscriber;

import static io.servicetalk.transport.api.FlushStrategyHolder.from;

abstract class AbstractFlushStrategy implements FlushStrategy {

    @Override
    public final <T> FlushStrategyHolder<T> apply(final Publisher<T> source) {
        FlushSignals signals = new FlushSignals();
        return from(source.liftSynchronous(subscriber -> newFlushSourceSubscriber(subscriber, signals)), signals);
    }

    /**
     * Returns a new {@link Subscriber} to use to apply this {@link FlushStrategy} to a {@link Publisher}.
     * @param original {@link Subscriber} for which this {@link FlushStrategy} is to be applied.
     * @param flushSignals {@link FlushSignals} to use to send signals for flush.
     * @param <T> Type of items emitted from the {@link Publisher} on which this {@link FlushStrategy} is applied.
     * @return {@link Subscriber} to use to subscribe to the {@link Publisher} on which this {@link FlushStrategy} is
     * applied.
     */
    abstract <T> Subscriber<? super T> newFlushSourceSubscriber(Subscriber<? super T> original,
                                                                FlushSignals flushSignals);
}
