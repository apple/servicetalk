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
import io.servicetalk.concurrent.internal.DelayedCancellable;
import io.servicetalk.concurrent.internal.SignalOffloader;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.SubscriberUtils.newExceptionForInvalidRequestN;

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
        Subscriber<? super T> offloadedSubscriber = signalOffloader.offloadSubscriber(subscriber);
        ConversionSubscriber<T> conversionSubscriber = new ConversionSubscriber<>(subscriber,
                offloadedSubscriber);
        // It is important that we call onSubscribe before subscribing, otherwise need more concurrency control between
        // the subscription and the subscriber.
        offloadedSubscriber.onSubscribe(conversionSubscriber);

        // Since this is converting a Single to a Publisher, we should try to use the same SignalOffloader
        // for subscribing to the original Single to avoid thread hop. Since, it is the same source, just
        // viewed as a Publisher, there is no additional risk of deadlock.
        //
        // parent is a Single but we always drive the Cancellable from this Subscription.
        // So, even though we are using the subscribe method that does not offload Cancellable, we do not
        // need to explicitly add the offload here.
        original.delegateSubscribe(conversionSubscriber, signalOffloader, contextMap, contextProvider);
    }

    private static final class ConversionSubscriber<T> implements Subscription, SingleSource.Subscriber<T> {
        private static final Object STATE_NULL = new Object();
        private static final Object STATE_REQUESTED = new Object();
        private static final Object STATE_TERMINATED = new Object();
        private static final AtomicReferenceFieldUpdater<ConversionSubscriber, Object> stateUpdater =
                AtomicReferenceFieldUpdater.newUpdater(ConversionSubscriber.class, Object.class, "state");
        private final DelayedCancellable delayedCancellable;
        private final Subscriber<? super T> subscriber;
        private final Subscriber<? super T> offloadedSubscriber;
        @Nullable
        private volatile Object state;

        private ConversionSubscriber(Subscriber<? super T> subscriber,
                                     Subscriber<? super T> offloadedSubscriber) {
            this.subscriber = subscriber;
            this.offloadedSubscriber = offloadedSubscriber;
            delayedCancellable = new DelayedCancellable();
        }

        @Override
        public void onSubscribe(Cancellable cancellable) {
            delayedCancellable.delayedCancellable(cancellable);
        }

        @Override
        public void onSuccess(@Nullable T result) {
            for (;;) {
                Object state = this.state;
                if (state == null) {
                    if (stateUpdater.compareAndSet(this, null, result == null ? STATE_NULL : result)) {
                        break;
                    }
                } else if (state == STATE_REQUESTED) {
                    if (stateUpdater.compareAndSet(this, STATE_REQUESTED, STATE_TERMINATED)) {
                        deliverOnNext(subscriber, result);
                        break;
                    }
                } else {
                    break;
                }
            }
        }

        @Override
        public void onError(Throwable t) {
            if (stateUpdater.getAndSet(this, STATE_TERMINATED) != STATE_TERMINATED) {
                subscriber.onError(t);
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public void request(long n) {
            if (isRequestNValid(n)) {
                for (;;) {
                    Object state = this.state;
                    if (state == null) {
                        if (stateUpdater.compareAndSet(this, null, STATE_REQUESTED)) {
                            break;
                        }
                    } else if (state == STATE_REQUESTED || state == STATE_TERMINATED) {
                        break;
                    } else if (stateUpdater.compareAndSet(this, state, STATE_TERMINATED)) {
                        deliverOnNext(offloadedSubscriber, state == STATE_NULL ? null : (T) state);
                        break;
                    }
                }
            } else {
                // We MUST propagate this to the Subscriber [1].
                // [1] https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.2/README.md#3.9
                // We don't need to protect against concurrency on the Subscriber.onSubscribe because we manually call
                // onSubscribe on before we actually subscribe.
                if (stateUpdater.getAndSet(this, STATE_TERMINATED) != STATE_TERMINATED) {
                    offloadedSubscriber.onError(newExceptionForInvalidRequestN(n));
                }
            }
        }

        @Override
        public void cancel() {
            state = STATE_TERMINATED; // make sure we dereference any onNext objects.
            delayedCancellable.cancel();
        }

        private void deliverOnNext(Subscriber<? super T> subscriber, @Nullable T state) {
            try {
                subscriber.onNext(state);
            } catch (Throwable cause) {
                subscriber.onError(cause);
                return;
            }
            subscriber.onComplete();
        }
    }
}
