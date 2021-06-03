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

import java.util.function.Function;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * {@link Single} as returned by {@link Single#flatMapCompletable(Function)}.
 */
final class SingleFlatMapCompletable<T> extends AbstractNoHandleSubscribeCompletable {
    private final Single<T> original;
    private final Function<? super T, ? extends Completable> nextFactory;

    SingleFlatMapCompletable(Single<T> original, Function<? super T, ? extends Completable> nextFactory,
                             Executor executor) {
        this.original = requireNonNull(original);
        this.nextFactory = requireNonNull(nextFactory);
    }

    @Override
    void handleSubscribe(final Subscriber subscriber, final SignalOffloader signalOffloader,
                         final AsyncContextMap contextMap, final AsyncContextProvider contextProvider) {
        original.delegateSubscribe(new SubscriberImpl<>(subscriber, nextFactory, signalOffloader,
                        contextMap, contextProvider), signalOffloader, contextMap, contextProvider);
    }

    private static final class SubscriberImpl<T> implements SingleSource.Subscriber<T>, Subscriber {
        private final Subscriber subscriber;
        private final Function<T, ? extends Completable> nextFactory;
        private final SignalOffloader signalOffloader;
        private final AsyncContextMap contextMap;
        private final AsyncContextProvider contextProvider;
        @Nullable
        private SequentialCancellable sequentialCancellable;

        SubscriberImpl(Subscriber subscriber, Function<T, ? extends Completable> nextFactory,
                       final SignalOffloader signalOffloader, final AsyncContextMap contextMap,
                       final AsyncContextProvider contextProvider) {
            this.subscriber = subscriber;
            this.nextFactory = nextFactory;
            this.signalOffloader = signalOffloader;
            this.contextMap = contextMap;
            this.contextProvider = contextProvider;
        }

        @Override
        public void onSubscribe(Cancellable cancellable) {
            if (sequentialCancellable == null) {
                sequentialCancellable = new SequentialCancellable(cancellable);
                subscriber.onSubscribe(sequentialCancellable);
            } else {
                sequentialCancellable.nextCancellable(cancellable);
            }
        }

        @Override
        public void onComplete() {
            subscriber.onComplete();
        }

        @Override
        public void onSuccess(@Nullable T result) {
            final Completable next;
            try {
                next = requireNonNull(nextFactory.apply(result));
            } catch (Throwable cause) {
                subscriber.onError(cause);
                return;
            }
            // We need to preserve the threading semantics for the original subscriber. Since here we are subscribing to
            // a new source which may have different threading semantics, we explicitly offload signals going down to
            // the original subscriber. If we do not do this and next source does not support blocking operations,
            // whereas original subscriber does, we will violate threading assumptions.
            //
            // The static AsyncContext should be the same as the original contextMap at this point because we are
            // being notified in the Subscriber path, but we make sure that it is restored after the asynchronous
            // boundary and use an isolated copy to subscribe to the new source.
            next.subscribeInternal(signalOffloader.offloadSubscriber(
                    contextProvider.wrapCompletableSubscriber(this, contextMap)));
        }

        @Override
        public void onError(Throwable t) {
            subscriber.onError(t);
        }
    }
}
