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

import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.SubscriberUtils.newExceptionForInvalidRequestN;

/**
 * {@link Publisher} created from a {@link Completable}.
 * @param <T> Type of item emitted by the {@link Publisher}.
 */
final class CompletableToPublisher<T> extends AbstractNoHandleSubscribePublisher<T> {
    private final Completable original;

    CompletableToPublisher(Completable original, Executor executor) {
        super(executor);
        this.original = original;
    }

    @Override
    void handleSubscribe(final Subscriber<? super T> subscriber, final SignalOffloader signalOffloader,
                         final AsyncContextMap contextMap, final AsyncContextProvider contextProvider) {
        subscriber.onSubscribe(new ConversionSubscriber<>(this, subscriber, signalOffloader, contextMap,
                contextProvider));
    }

    private static final class ConversionSubscriber<T> implements CompletableSource.Subscriber, Subscription {
        private final SequentialCancellable sequentialCancellable;
        private final Subscriber<? super T> subscriber;
        private final SignalOffloader signalOffloader;
        private final AsyncContextMap contextMap;
        private final AsyncContextProvider contextProvider;
        private final CompletableToPublisher<T> parent;
        private boolean subscribedToParent;

        private ConversionSubscriber(CompletableToPublisher<T> parent, Subscriber<? super T> subscriber,
                                     SignalOffloader signalOffloader, AsyncContextMap contextMap,
                                     AsyncContextProvider contextProvider) {
            this.parent = parent;
            this.subscriber = subscriber;
            this.signalOffloader = signalOffloader;
            this.contextMap = contextMap;
            this.contextProvider = contextProvider;
            sequentialCancellable = new SequentialCancellable();
        }

        @Override
        public void onSubscribe(Cancellable cancellable) {
            sequentialCancellable.setNextCancellable(cancellable);
        }

        @Override
        public void onComplete() {
            subscriber.onComplete();
        }

        @Override
        public void onError(Throwable t) {
            subscriber.onError(t);
        }

        @Override
        public void request(long n) {
            if (!subscribedToParent) {
                subscribedToParent = true;
                if (isRequestNValid(n)) {
                    // Since this is converting a Completable to a Publisher, we should try to use the same
                    // SignalOffloader for subscribing to the original Completable to avoid thread hop. Since, it is the
                    // same source, just viewed as a Publisher, there is no additional risk of deadlock.
                    //
                    // parent is a Completable but we always drive the Cancellable from this Subscription.
                    // So, even though we are using the subscribe method that does not offload Cancellable, we do not
                    // need to explicitly add the offload here.
                    parent.original.subscribeWithOffloaderAndContext(this, signalOffloader, contextMap,
                            contextProvider);
                } else {
                    subscriber.onError(newExceptionForInvalidRequestN(n));
                }
            }
        }

        @Override
        public void cancel() {
            sequentialCancellable.cancel();
        }
    }
}
