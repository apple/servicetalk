/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.internal.SequentialCancellable;
import io.servicetalk.concurrent.internal.SignalOffloader;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static io.servicetalk.concurrent.api.OnSubscribeIgnoringSubscriberForOffloading.offloadWithDummyOnSubscribe;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.SubscriberUtils.newExceptionForInvalidRequestN;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

/**
 * {@link Publisher} created from a {@link Completable}.
 * @param <T> Type of item emitted by the {@link Publisher}.
 */
final class CompletableToPublisher<T> extends AbstractNoHandleSubscribePublisher<T> {
    private final Completable original;

    CompletableToPublisher(Completable original) {
        this.original = original;
    }

    @Override
    Executor executor() {
        return original.executor();
    }

    @Override
    void handleSubscribe(final Subscriber<? super T> subscriber, final SignalOffloader signalOffloader,
                         final AsyncContextMap contextMap, final AsyncContextProvider contextProvider) {
        original.delegateSubscribe(new ConversionSubscriber<>(subscriber, signalOffloader, contextMap, contextProvider),
                signalOffloader, contextMap, contextProvider);
    }

    private static final class ConversionSubscriber<T> extends SequentialCancellable
            implements CompletableSource.Subscriber, Subscription {
        @SuppressWarnings("rawtypes")
        private static final AtomicIntegerFieldUpdater<ConversionSubscriber> terminatedUpdater =
                newUpdater(ConversionSubscriber.class, "terminated");
        private final Subscriber<? super T> subscriber;
        private final SignalOffloader signalOffloader;
        private final AsyncContextMap contextMap;
        private final AsyncContextProvider contextProvider;

        private volatile int terminated;

        private ConversionSubscriber(Subscriber<? super T> subscriber, final SignalOffloader signalOffloader,
                                     final AsyncContextMap contextMap, final AsyncContextProvider contextProvider) {
            this.subscriber = subscriber;
            this.signalOffloader = signalOffloader;
            this.contextMap = contextMap;
            this.contextProvider = contextProvider;
        }

        @Override
        public void onSubscribe(Cancellable cancellable) {
            nextCancellable(cancellable);
            subscriber.onSubscribe(this);
        }

        @Override
        public void onComplete() {
            if (terminatedUpdater.compareAndSet(this, 0, 1)) {
                subscriber.onComplete();
            }
        }

        @Override
        public void onError(Throwable t) {
            if (terminatedUpdater.compareAndSet(this, 0, 1)) {
                subscriber.onError(t);
            }
        }

        @Override
        public void request(long n) {
            if (!isRequestNValid(n) && terminatedUpdater.compareAndSet(this, 0, 1)) {
                // We have not offloaded the Subscriber as we generally emit to the Subscriber from the Completable
                // Subscriber methods which is correctly offloaded. This is the only case where we invoke the
                // Subscriber directly, hence we explicitly offload.
                Subscriber<? super T> offloaded = offloadWithDummyOnSubscribe(subscriber, signalOffloader,
                        contextMap, contextProvider);
                try {
                    // offloadSubscriber before cancellation so that signalOffloader does not exit on seeing a cancel.
                    cancel();
                } catch (Throwable t) {
                    offloaded.onError(t);
                    return;
                }
                offloaded.onError(newExceptionForInvalidRequestN(n));
            }
        }
    }
}
