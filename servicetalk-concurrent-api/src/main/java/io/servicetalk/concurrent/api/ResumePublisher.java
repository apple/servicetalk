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

import io.servicetalk.concurrent.internal.SignalOffloader;

import java.util.function.Function;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * As returned by {@link Publisher#onErrorResume(Function)}.
 *
 * @param <T> Type of items emitted by this {@link Publisher}.
 */
final class ResumePublisher<T> extends AbstractNoHandleSubscribePublisher<T> {

    private final Publisher<T> original;
    private final Function<Throwable, ? extends Publisher<? extends T>> nextFactory;

    ResumePublisher(Publisher<T> original, Function<Throwable, ? extends Publisher<? extends T>> nextFactory,
                    Executor executor) {
        super(executor);
        this.original = original;
        this.nextFactory = requireNonNull(nextFactory);
    }

    @Override
    void handleSubscribe(final Subscriber<? super T> subscriber, final SignalOffloader signalOffloader,
                         final AsyncContextMap contextMap, final AsyncContextProvider contextProvider) {
        original.delegateSubscribe(
                new ResumeSubscriber<>(subscriber, nextFactory, signalOffloader, contextMap, contextProvider),
                signalOffloader, contextMap, contextProvider);
    }

    private static final class ResumeSubscriber<T> implements Subscriber<T> {
        private final Subscriber<? super T> subscriber;
        @Nullable
        private volatile Function<Throwable, ? extends Publisher<? extends T>> nextFactory;
        private final SignalOffloader signalOffloader;
        private final AsyncContextMap contextMap;
        private final AsyncContextProvider contextProvider;
        @Nullable
        private volatile SequentialSubscription sequentialSubscription;

        ResumeSubscriber(Subscriber<? super T> subscriber,
                         Function<Throwable, ? extends Publisher<? extends T>> nextFactory,
                         SignalOffloader signalOffloader, AsyncContextMap contextMap,
                         AsyncContextProvider contextProvider) {
            this.subscriber = subscriber;
            this.nextFactory = nextFactory;
            this.signalOffloader = signalOffloader;
            this.contextMap = contextMap;
            this.contextProvider = contextProvider;
        }

        @Override
        public void onSubscribe(Subscription s) {
            SequentialSubscription sequentialSubscription = this.sequentialSubscription;
            if (sequentialSubscription == null) {
                this.sequentialSubscription = sequentialSubscription = new SequentialSubscription(s);
                subscriber.onSubscribe(sequentialSubscription);
            } else {
                // Only a single re-subscribe is allowed.
                nextFactory = null;
                sequentialSubscription.switchTo(s);
            }
        }

        @Override
        public void onNext(T t) {
            SequentialSubscription sequentialSubscription = this.sequentialSubscription;
            assert sequentialSubscription != null : "Subscription can not be null in onNext.";
            sequentialSubscription.itemReceived();
            subscriber.onNext(t);
        }

        @Override
        public void onError(Throwable t) {
            final Function<Throwable, ? extends Publisher<? extends T>> nextFactory = this.nextFactory;
            if (nextFactory == null) {
                subscriber.onError(t);
                return;
            }

            final Publisher<? extends T> next;
            try {
                next = requireNonNull(nextFactory.apply(t));
            } catch (Throwable throwable) {
                throwable.addSuppressed(t);
                subscriber.onError(throwable);
                return;
            }

            // We are subscribing to a new Publisher which will send signals to the original Subscriber. This means
            // that the threading semantics may differ with respect to the original Subscriber when we emit signals from
            // the new Publisher. This is the reason we use the original offloader now to offload signals which
            // originate from this new Publisher.
            final Subscriber<? super T> offloadedSubscriber = signalOffloader.offloadSubscriber(
                    contextProvider.wrapPublisherSubscriber(this, contextMap));
            next.subscribeInternal(offloadedSubscriber);
        }

        @Override
        public void onComplete() {
            subscriber.onComplete();
        }
    }
}
