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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.internal.SignalOffloader;

import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.NoSuchElementException;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.internal.SubscriberUtils.checkDuplicateSubscription;

/**
 * A single created from a {@link Publisher}.
 *
 * @param <T> Type of items emitted by this {@link Single}.
 */
final class PubToSingle<T> extends AbstractNoHandleSubscribeSingle<T> {
    private final Publisher<T> source;

    /**
     * New instance.
     *
     * @param source {@link Publisher} for this {@link Single}.
     */
    PubToSingle(Publisher<T> source) {
        super(source.getExecutor());
        this.source = source;
    }

    @Override
    void handleSubscribe(final Subscriber<? super T> subscriber, final SignalOffloader signalOffloader,
                         final AsyncContextMap contextMap, final AsyncContextProvider contextProvider) {
        // We are now subscribing to the original Publisher chain for the first time, re-using the SignalOffloader.
        // Using the special subscribe() method means it will not offload the Subscription (done in the public
        // subscribe() method). So, we use the SignalOffloader to offload subscription if required.
        org.reactivestreams.Subscriber<? super T> offloadedSubscription = signalOffloader.offloadSubscription(
                contextProvider.wrapSubscription(new PubToSingleSubscriber<>(subscriber), contextMap));
        // Since this is converting a Publisher to a Single, we should try to use the same SignalOffloader for
        // subscribing to the original Publisher to avoid thread hop. Since, it is the same source, just viewed as a
        // Single, there is no additional risk of deadlock.
        source.subscribeWithOffloaderAndContext(offloadedSubscription, signalOffloader, contextMap, contextProvider);
    }

    private static final class PubToSingleSubscriber<T> implements org.reactivestreams.Subscriber<T> {

        private static final Logger LOGGER = LoggerFactory.getLogger(PubToSingleSubscriber.class);

        private static final byte STATE_SENT_ON_SUBSCRIBE = 1;
        private static final byte STATE_SENT_ON_SUBSCRIBE_AND_DONE = 2;

        private final Subscriber<? super T> subscriber;
        @Nullable
        private Subscription subscription;
        private byte state;

        PubToSingleSubscriber(final Subscriber<? super T> subscriber) {
            this.subscriber = subscriber;
        }

        @Override
        public void onSubscribe(Subscription s) {
            if (checkDuplicateSubscription(subscription, s)) {
                subscription = s;
                s.request(1);
                if (state != STATE_SENT_ON_SUBSCRIBE_AND_DONE) {
                    state = STATE_SENT_ON_SUBSCRIBE;
                    subscriber.onSubscribe(s::cancel);
                }
            }
        }

        @Override
        public void onNext(T t) {
            terminate(t);
        }

        @Override
        public void onError(Throwable t) {
            terminate(t);
        }

        @Override
        public void onComplete() {
            if (isDone()) {
                // Avoid creating a new exception if we are already done.
                return;
            }
            terminate(new NoSuchElementException());
        }

        private void terminate(Object terminal) {
            if (isDone()) {
                return;
            }
            if (state == 0) {
                state = STATE_SENT_ON_SUBSCRIBE_AND_DONE;
                try {
                    subscriber.onSubscribe(IGNORE_CANCEL);
                } catch (Throwable t) {
                    if (terminal instanceof Throwable) {
                        ((Throwable) terminal).addSuppressed(t);
                    } else {
                        LOGGER.warn("Unexpected exception from onSubscribe from subscriber {}. Discarding result {}.",
                                subscriber, terminal, t);
                        terminal = t;
                    }
                }
            } else {
                state = STATE_SENT_ON_SUBSCRIBE_AND_DONE;
            }

            if (terminal instanceof Throwable) {
                subscriber.onError((Throwable) terminal);
            } else {
                assert subscription != null : "Subscription can not be null.";
                subscription.cancel();

                @SuppressWarnings("unchecked")
                final T t = (T) terminal;
                subscriber.onSuccess(t);
            }
        }

        private boolean isDone() {
            return state == STATE_SENT_ON_SUBSCRIBE_AND_DONE;
        }
    }
}
