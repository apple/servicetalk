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
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.internal.SequentialCancellable;
import io.servicetalk.concurrent.internal.SignalOffloader;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.OnSubscribeIgnoringSubscriberForOffloading.offloadSubscriber;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.SubscriberUtils.newExceptionForInvalidRequestN;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

/**
 * {@link Publisher} created from a {@link Single}.
 * @param <T> Type of item emitted by the {@link Publisher}.
 */
final class SingleToPublisher<T> extends AbstractNoHandleSubscribePublisher<T> {
    private final Single<T> original;

    SingleToPublisher(Single<T> original, Executor executor) {
        super(executor);
        this.original = original;
    }

    @Override
    void handleSubscribe(final Subscriber<? super T> subscriber, final SignalOffloader signalOffloader,
                         final AsyncContextMap contextMap, final AsyncContextProvider contextProvider) {
        original.delegateSubscribe(new ConversionSubscriber<>(subscriber, signalOffloader), signalOffloader,
                contextMap, contextProvider);
    }

    private static final class ConversionSubscriber<T> extends SequentialCancellable
            implements Subscription, SingleSource.Subscriber<T> {
        private static final int STATE_IDLE = 0;
        private static final int STATE_REQUESTED = 1;
        private static final int STATE_AWAITING_REQUESTED = 2;
        private static final int STATE_TERMINATED = 3;
        @SuppressWarnings("rawtypes")
        private static final AtomicIntegerFieldUpdater<ConversionSubscriber> stateUpdater =
                newUpdater(ConversionSubscriber.class, "state");
        private final Subscriber<? super T> subscriber;
        private final SignalOffloader signalOffloader;

        @Nullable
        private T result;
        private volatile int state;

        ConversionSubscriber(Subscriber<? super T> subscriber, final SignalOffloader signalOffloader) {
            this.subscriber = subscriber;
            this.signalOffloader = signalOffloader;
        }

        @Override
        public void onSubscribe(Cancellable cancellable) {
            nextCancellable(cancellable);
            subscriber.onSubscribe(this);
        }

        @Override
        public void onSuccess(@Nullable T result) {
            this.result = result;
            for (;;) {
                int cState = state;
                if (cState == STATE_REQUESTED &&
                        stateUpdater.compareAndSet(this, STATE_REQUESTED, STATE_TERMINATED)) {
                    terminateSuccessfully(result, subscriber);
                    return;
                } else if (cState == STATE_IDLE &&
                        stateUpdater.compareAndSet(this, STATE_IDLE, STATE_AWAITING_REQUESTED)) {
                    return;
                } else if (cState == STATE_AWAITING_REQUESTED || cState == STATE_TERMINATED) {
                    return;
                }
            }
        }

        @Override
        public void onError(Throwable t) {
            if (stateUpdater.getAndSet(this, STATE_TERMINATED) != STATE_TERMINATED) {
                subscriber.onError(t);
            }
        }

        @Override
        public void request(long n) {
            if (isRequestNValid(n)) {
                for (;;) {
                    int cState = state;
                    if (cState == STATE_AWAITING_REQUESTED &&
                            stateUpdater.compareAndSet(this, STATE_AWAITING_REQUESTED, STATE_TERMINATED)) {
                        // We have not offloaded the Subscriber as we generally emit to the Subscriber from the
                        // Single Subscriber methods which is correctly offloaded. This is the case where we invoke the
                        // Subscriber directly, hence we explicitly offload.
                        terminateSuccessfully(result, offloadSubscriber(signalOffloader, subscriber));
                        return;
                    } else if (cState == STATE_IDLE &&
                            stateUpdater.compareAndSet(this, STATE_IDLE, STATE_REQUESTED)) {
                        return;
                    } else if (cState == STATE_TERMINATED || cState == STATE_REQUESTED) {
                        return;
                    }
                }
            } else {
                if (stateUpdater.getAndSet(this, STATE_TERMINATED) != STATE_TERMINATED) {
                    Subscriber<? super T> offloaded = offloadSubscriber(signalOffloader, this.subscriber);
                    try {
                        // offloadSubscriber before cancellation so that signalOffloader does not exit on seeing a
                        // cancel.
                        cancel();
                    } catch (Throwable t) {
                        offloaded.onError(t);
                        return;
                    }
                    offloaded.onError(newExceptionForInvalidRequestN(n));
                }
            }
        }

        private void terminateSuccessfully(@Nullable final T result, Subscriber<? super T> subscriber) {
            try {
                subscriber.onNext(result);
            } catch (Throwable cause) {
                subscriber.onError(cause);
                return;
            }
            subscriber.onComplete();
        }
    }
}
