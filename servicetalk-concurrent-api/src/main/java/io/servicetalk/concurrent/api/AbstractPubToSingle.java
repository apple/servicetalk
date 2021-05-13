/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.PublisherSource.Subscription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.SubscriberApiUtils.unwrapNullUnchecked;
import static io.servicetalk.concurrent.internal.SubscriberUtils.checkDuplicateSubscription;

abstract class AbstractPubToSingle<T> extends AbstractNoHandleSubscribeSingle<T> {
    private final Publisher<T> source;

    AbstractPubToSingle(Publisher<T> source) {
        this.source = source;
    }

    @Override
    final void handleSubscribe(final Subscriber<? super T> subscriber,
                               final AsyncContextMap contextMap, final AsyncContextProvider contextProvider) {
        // We are now subscribing to the original Publisher chain for the first time.
        PublisherSource.Subscriber<? super T> wrappedSubscription =
                contextProvider.wrapSubscription(newSubscriber(subscriber), contextMap);
        source.delegateSubscribe(wrappedSubscription, contextMap, contextProvider);
    }

    abstract PublisherSource.Subscriber<T> newSubscriber(Subscriber<? super T> original);

    abstract static class AbstractPubToSingleSubscriber<T> implements PublisherSource.Subscriber<T> {
        private static final Logger LOGGER = LoggerFactory.getLogger(AbstractPubToSingleSubscriber.class);
        private static final byte STATE_WAITING_FOR_SUBSCRIBE = 0;
        /**
         * We have called {@link PublisherSource.Subscriber#onSubscribe(Subscription)}.
         */
        private static final byte STATE_SENT_ON_SUBSCRIBE = 1;
        /**
         * We have called {@link PublisherSource.Subscriber#onSubscribe(Subscription)} and terminated.
         */
        private static final byte STATE_SENT_ON_SUBSCRIBE_AND_DONE = 2;

        private final Subscriber<? super T> subscriber;
        @Nullable
        Subscription subscription;
        /**
         * Can either be {@link #STATE_WAITING_FOR_SUBSCRIBE}, {@link #STATE_SENT_ON_SUBSCRIBE}, or
         * {@link #STATE_SENT_ON_SUBSCRIBE_AND_DONE}.
         */
        private byte state = STATE_WAITING_FOR_SUBSCRIBE;

        AbstractPubToSingleSubscriber(final Subscriber<? super T> subscriber) {
            this.subscriber = subscriber;
        }

        @Override
        public final void onSubscribe(Subscription s) {
            if (checkDuplicateSubscription(subscription, s)) {
                subscription = s;
                s.request(numberOfItemsToRequest());
                if (state == STATE_WAITING_FOR_SUBSCRIBE) {
                    state = STATE_SENT_ON_SUBSCRIBE;
                    subscriber.onSubscribe(s);
                }
            }
        }

        abstract int numberOfItemsToRequest();

        @Override
        public final void onError(Throwable t) {
            terminate(t);
        }

        @Override
        public final void onComplete() {
            if (state == STATE_SENT_ON_SUBSCRIBE_AND_DONE) {
                return;
            }
            terminate(terminalSignalForComplete());
        }

        abstract Object terminalSignalForComplete();

        void terminate(Object terminal) {
            if (state == STATE_SENT_ON_SUBSCRIBE_AND_DONE) {
                return;
            } else if (state == STATE_WAITING_FOR_SUBSCRIBE) {
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
                subscriber.onSuccess(unwrapNullUnchecked(terminal));
            }
        }
    }
}
