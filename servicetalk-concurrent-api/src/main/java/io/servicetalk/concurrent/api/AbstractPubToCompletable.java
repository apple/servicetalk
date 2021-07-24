/*
 * Copyright © 2018-2021 Apple Inc. and the ServiceTalk project authors
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
        this.source = source;
    }

    abstract PublisherSource.Subscriber<T> newSubscriber(Subscriber original);

    @Override
    final void handleSubscribe(final Subscriber subscriber,
                               final AsyncContextMap contextMap, final AsyncContextProvider contextProvider) {
        // We are now subscribing to the original Publisher chain for the first time, wrap Subscription to preserve the
        // context.
        source.delegateSubscribe(contextProvider.wrapSubscription(newSubscriber(subscriber), contextMap),
                contextMap, contextProvider);
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
