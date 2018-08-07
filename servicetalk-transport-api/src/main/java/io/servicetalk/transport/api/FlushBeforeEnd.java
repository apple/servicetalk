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

import io.servicetalk.transport.api.FlushStrategyHolder.FlushSignals;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import static java.util.Objects.requireNonNull;

final class FlushBeforeEnd extends AbstractFlushStrategy {

    static final FlushBeforeEnd FLUSH_BEFORE_END = new FlushBeforeEnd();

    private FlushBeforeEnd() {
        // No instances.
    }

    @Override
    <T> Subscriber<? super T> newFlushSourceSubscriber(final Subscriber<? super T> original,
                                                       final FlushSignals flushSignals) {
        return new FlushBeforeEndSubscriber<>(original, flushSignals);
    }

    private static final class FlushBeforeEndSubscriber<T> implements Subscriber<T> {

        private final Subscriber<T> original;
        private final FlushSignals signals;

        FlushBeforeEndSubscriber(Subscriber<T> original, FlushSignals signals) {
            this.original = requireNonNull(original);
            this.signals = requireNonNull(signals);
        }

        @Override
        public void onSubscribe(Subscription s) {
            original.onSubscribe(s);
        }

        @Override
        public void onNext(T t) {
            original.onNext(t);
        }

        @Override
        public void onError(Throwable t) {
            signals.signalFlush();
            original.onError(t);
        }

        @Override
        public void onComplete() {
            signals.signalFlush();
            original.onComplete();
        }
    }
}
