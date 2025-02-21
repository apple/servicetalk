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

import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.utils.internal.ThrowableUtils.addSuppressed;
import static java.util.Objects.requireNonNull;

final class OnErrorResumePublisher<T> extends AbstractNoHandleSubscribePublisher<T> {
    private final Publisher<T> original;
    private final Predicate<? super Throwable> predicate;
    private final Function<? super Throwable, ? extends Publisher<? extends T>> nextFactory;

    OnErrorResumePublisher(Publisher<T> original, Predicate<? super Throwable> predicate,
                           Function<? super Throwable, ? extends Publisher<? extends T>> nextFactory) {
        this.original = original;
        this.predicate = requireNonNull(predicate);
        this.nextFactory = requireNonNull(nextFactory);
    }

    @Override
    void handleSubscribe(final Subscriber<? super T> subscriber,
                         final CapturedContext capturedContext, final AsyncContextProvider contextProvider) {
        original.delegateSubscribe(new ResumeSubscriber(subscriber, capturedContext, contextProvider),
                capturedContext, contextProvider);
    }

    private final class ResumeSubscriber implements Subscriber<T> {
        private final Subscriber<? super T> subscriber;
        private final CapturedContext capturedContext;
        private final AsyncContextProvider contextProvider;
        @Nullable
        private SequentialSubscription sequentialSubscription;
        private boolean resubscribed;

        ResumeSubscriber(Subscriber<? super T> subscriber, CapturedContext capturedContext,
                         AsyncContextProvider contextProvider) {
            this.subscriber = subscriber;
            this.capturedContext = capturedContext;
            this.contextProvider = contextProvider;
        }

        @Override
        public void onSubscribe(Subscription s) {
            if (sequentialSubscription == null) {
                sequentialSubscription = new SequentialSubscription(s);
                subscriber.onSubscribe(sequentialSubscription);
            } else {
                resubscribed = true;
                sequentialSubscription.switchTo(s);
            }
        }

        @Override
        public void onNext(T t) {
            assert sequentialSubscription != null;
            sequentialSubscription.itemReceived();
            subscriber.onNext(t);
        }

        @Override
        public void onError(Throwable throwable) {
            final Publisher<? extends T> next;
            try {
                next = !resubscribed && predicate.test(throwable) ? requireNonNull(nextFactory.apply(throwable)) : null;
            } catch (Throwable t) {
                subscriber.onError(addSuppressed(t, throwable));
                return;
            }

            if (next == null) {
                subscriber.onError(throwable);
            } else {
                final Subscriber<? super T> offloadedSubscriber =
                        contextProvider.wrapPublisherSubscriber(this, capturedContext);
                next.subscribeInternal(offloadedSubscriber);
            }
        }

        @Override
        public void onComplete() {
            subscriber.onComplete();
        }
    }
}
