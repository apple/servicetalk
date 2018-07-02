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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.internal.SequentialCancellable;

import java.util.function.Function;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * {@link Single} as returned by {@link Single#flatMapCompletable(Function)}.
 */
final class SingleFlatMapCompletable<T> extends AbstractNoHandleSubscribeCompletable {
    private final Single<T> first;
    private final Function<T, Completable> nextFactory;

    SingleFlatMapCompletable(Single<T> first, Function<T, Completable> nextFactory, Executor executor) {
        super(executor);
        this.first = requireNonNull(first);
        this.nextFactory = requireNonNull(nextFactory);
    }

    @Override
    void handleSubscribe(final Subscriber subscriber, final SignalOffloader signalOffloader) {
        first.subscribe(new SubscriberImpl<>(subscriber, nextFactory, signalOffloader), signalOffloader);
    }

    private static final class SubscriberImpl<T> implements Single.Subscriber<T>, Subscriber {
        private final Subscriber subscriber;
        private final Function<T, Completable> nextFactory;
        private final SignalOffloader signalOffloader;
        @Nullable
        private volatile SequentialCancellable sequentialCancellable;

        SubscriberImpl(Subscriber subscriber, Function<T, Completable> nextFactory,
                       final SignalOffloader signalOffloader) {
            this.subscriber = subscriber;
            this.nextFactory = nextFactory;
            this.signalOffloader = signalOffloader;
        }

        @Override
        public void onSubscribe(Cancellable cancellable) {
            SequentialCancellable sequentialCancellable = this.sequentialCancellable;
            if (sequentialCancellable == null) {
                this.sequentialCancellable = sequentialCancellable = new SequentialCancellable(cancellable);
                subscriber.onSubscribe(sequentialCancellable);
            } else {
                sequentialCancellable.setNextCancellable(cancellable);
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
            next.subscribe(signalOffloader.offloadSubscriber((Subscriber) this));
        }

        @Override
        public void onError(Throwable t) {
            subscriber.onError(t);
        }
    }
}
