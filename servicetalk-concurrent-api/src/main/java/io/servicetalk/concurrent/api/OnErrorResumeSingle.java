/*
 * Copyright © 2018-2019, 2021 Apple Inc. and the ServiceTalk project authors
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
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

final class OnErrorResumeSingle<T> extends AbstractNoHandleSubscribeSingle<T> {
    private final Single<T> original;
    private final Predicate<? super Throwable> predicate;
    private final Function<? super Throwable, ? extends Single<? extends T>> nextFactory;

    OnErrorResumeSingle(Single<T> original, Predicate<? super Throwable> predicate,
                        Function<? super Throwable, ? extends Single<? extends T>> nextFactory) {
        this.original = original;
        this.predicate = requireNonNull(predicate);
        this.nextFactory = requireNonNull(nextFactory);
    }

    @Override
    void handleSubscribe(final Subscriber<? super T> subscriber,
                         final AsyncContextMap contextMap, final AsyncContextProvider contextProvider) {
        original.delegateSubscribe(new ResumeSubscriber(subscriber, contextMap, contextProvider),
                contextMap, contextProvider);
    }

    private final class ResumeSubscriber implements Subscriber<T> {
        private final Subscriber<? super T> subscriber;
        private final AsyncContextMap contextMap;
        private final AsyncContextProvider contextProvider;
        @Nullable
        private SequentialCancellable sequentialCancellable;
        private boolean resubscribed;

        ResumeSubscriber(Subscriber<? super T> subscriber, AsyncContextMap contextMap,
                         AsyncContextProvider contextProvider) {
            this.subscriber = subscriber;
            this.contextMap = contextMap;
            this.contextProvider = contextProvider;
        }

        @Override
        public void onSubscribe(Cancellable cancellable) {
            if (sequentialCancellable == null) {
                sequentialCancellable = new SequentialCancellable(cancellable);
                subscriber.onSubscribe(sequentialCancellable);
            } else {
                resubscribed = true;
                sequentialCancellable.nextCancellable(cancellable);
            }
        }

        @Override
        public void onSuccess(@Nullable T result) {
            subscriber.onSuccess(result);
        }

        @Override
        public void onError(Throwable throwable) {
            final Single<? extends T> next;
            try {
                next = !resubscribed && predicate.test(throwable) ? requireNonNull(nextFactory.apply(throwable)) : null;
            } catch (Throwable t) {
                t.addSuppressed(throwable);
                subscriber.onError(t);
                return;
            }

            if (next == null) {
                subscriber.onError(throwable);
            } else {
                next.subscribeInternal(contextProvider.wrapSingleSubscriber(this, contextMap));
            }
        }
    }
}
