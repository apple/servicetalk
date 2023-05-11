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

import io.servicetalk.concurrent.internal.FlowControlUtils;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.function.ObjLongConsumer;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static java.util.Objects.requireNonNull;

final class ValidateDemandPublisher<T> extends AbstractSynchronousPublisherOperator<T, T> {
    private final ObjLongConsumer<T> onNextConsumer;
    private final LongBinaryConsumer requestConsumer;

    ValidateDemandPublisher(final Publisher<T> original,
                            final ObjLongConsumer<T> onNextConsumer,
                            final LongBinaryConsumer requestConsumer) {
        super(original);
        this.onNextConsumer = requireNonNull(onNextConsumer);
        this.requestConsumer = requireNonNull(requestConsumer);
    }

    @Override
    public Subscriber<? super T> apply(final Subscriber<? super T> subscriber) {
        return new ValidateDemandSubscriber<>(this, subscriber);
    }

    private static final class ValidateDemandSubscriber<T> implements Subscriber<T> {
        @SuppressWarnings("rawtypes")
        private static final AtomicLongFieldUpdater<ValidateDemandSubscriber> demandUpdater =
                AtomicLongFieldUpdater.newUpdater(ValidateDemandSubscriber.class, "demand");
        private final Subscriber<? super T> subscriber;
        private final ValidateDemandPublisher<T> parent;
        private volatile long demand;

        private ValidateDemandSubscriber(ValidateDemandPublisher<T> parent,
                                         final Subscriber<? super T> subscriber) {
            this.parent = parent;
            this.subscriber = requireNonNull(subscriber);
        }

        @Override
        public void onSubscribe(final Subscription subscription) {
            subscriber.onSubscribe(new Subscription() {
                @Override
                public void request(final long n) {
                    try {
                        if (isRequestNValid(n)) {
                            final long currDemand = demandUpdater.accumulateAndGet(ValidateDemandSubscriber.this, n,
                                    FlowControlUtils::addWithOverflowProtection);
                            parent.requestConsumer.accept(n, currDemand);
                        }
                    } finally {
                        subscription.request(n);
                    }
                }

                @Override
                public void cancel() {
                    subscription.cancel();
                }
            });
        }

        @Override
        public void onNext(@Nullable final T t) {
            final long currDemand = demandUpdater.decrementAndGet(this);
            parent.onNextConsumer.accept(t, currDemand);
            if (currDemand < 0) {
                throw new IllegalStateException("Received onNext signal='" + t + "' with no demand");
            }
            subscriber.onNext(t);
        }

        @Override
        public void onError(final Throwable t) {
            subscriber.onError(t);
        }

        @Override
        public void onComplete() {
            subscriber.onComplete();
        }
    }
}
