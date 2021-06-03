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
import io.servicetalk.concurrent.internal.SequentialCancellable;
import io.servicetalk.concurrent.internal.SignalOffloader;

import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

final class OnErrorResumeCompletable extends AbstractNoHandleSubscribeCompletable {
    private final Completable original;
    private final Predicate<? super Throwable> predicate;
    private final Function<? super Throwable, ? extends Completable> nextFactory;

    OnErrorResumeCompletable(Completable original, Predicate<? super Throwable> predicate,
                             Function<? super Throwable, ? extends Completable> nextFactory) {
        this.original = original;
        this.predicate = requireNonNull(predicate);
        this.nextFactory = requireNonNull(nextFactory);
    }

    @Override
    Executor executor() {
        return original.executor();
    }

    @Override
    void handleSubscribe(final Subscriber subscriber, final SignalOffloader signalOffloader,
                         final AsyncContextMap contextMap, final AsyncContextProvider contextProvider) {
        original.delegateSubscribe(new ResumeSubscriber(subscriber, signalOffloader, contextMap, contextProvider),
                signalOffloader, contextMap, contextProvider);
    }

    private final class ResumeSubscriber implements Subscriber {
        private final Subscriber subscriber;
        private final SignalOffloader signalOffloader;
        private final AsyncContextMap contextMap;
        private final AsyncContextProvider contextProvider;
        @Nullable
        private SequentialCancellable sequentialCancellable;
        private boolean resubscribed;

        ResumeSubscriber(Subscriber subscriber, SignalOffloader signalOffloader, AsyncContextMap contextMap,
                         AsyncContextProvider contextProvider) {
            this.subscriber = subscriber;
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
                resubscribed = true;
                sequentialCancellable.nextCancellable(cancellable);
            }
        }

        @Override
        public void onComplete() {
            subscriber.onComplete();
        }

        @Override
        public void onError(Throwable throwable) {
            final Completable next;
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
                // We are subscribing to a new Completable which will send signals to the original Subscriber. This
                // means that the threading semantics may differ with respect to the original Subscriber when we emit
                // signals from the new Completable. This is the reason we use the original offloader now to offload
                // signals which originate from this new Completable.
                final Subscriber offloadedSubscriber = signalOffloader.offloadSubscriber(
                        contextProvider.wrapCompletableSubscriber(this, contextMap));
                next.subscribeInternal(offloadedSubscriber);
            }
        }
    }
}
