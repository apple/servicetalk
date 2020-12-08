/*
 * Copyright © 2018-2020 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.internal.DelayedCancellable;
import io.servicetalk.concurrent.internal.SignalOffloader;

/**
 * A {@link Completable} created from a {@link Publisher}.
 *
 * @param <T> Item type emitted from the original {@link Publisher}.
 */
abstract class AbstractPubToCompletable<T> extends AbstractNoHandleSubscribeCompletable {
    private final Publisher<T> source;

    /**
     * New instance.
     *
     * @param source {@link Publisher} from which this {@link Completable} is created.
     */
    AbstractPubToCompletable(Publisher<T> source) {
        super(source.executor());
        this.source = source;
    }

    abstract PublisherSource.Subscriber<T> newSubscriber(Subscriber original);

    @Override
    final void handleSubscribe(final Subscriber subscriber, final SignalOffloader signalOffloader,
                         final AsyncContextMap contextMap, final AsyncContextProvider contextProvider) {
        // We are now subscribing to the original Publisher chain for the first time, re-using the SignalOffloader.
        // Using the special subscribe() method means it will not offload the Subscription (done in the public
        // subscribe() method). So, we use the SignalOffloader to offload subscription if required.
        PublisherSource.Subscriber<? super T> offloadedSubscription = signalOffloader.offloadSubscription(
                contextProvider.wrapSubscription(newSubscriber(subscriber), contextMap));
        // Since this is converting a Publisher to a Completable, we should try to use the same SignalOffloader for
        // subscribing to the original Publisher to avoid thread hop. Since, it is the same source, just viewed as a
        // Completable, there is no additional risk of deadlock.
        source.delegateSubscribe(offloadedSubscription, signalOffloader, contextMap, contextProvider);
    }

    abstract static class AbstractPubToCompletableSubscriber<T> extends DelayedCancellable
            implements PublisherSource.Subscriber<T> {
        private final Subscriber subscriber;

        AbstractPubToCompletableSubscriber(final Subscriber subscriber) {
            this.subscriber = subscriber;
        }

        @Override
        public final void onSubscribe(final PublisherSource.Subscription s) {
            subscriber.onSubscribe(this);
            s.request(Long.MAX_VALUE);
            delayedCancellable(s);
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
