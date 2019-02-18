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
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * {@link Completable} as returned by {@link Completable#onErrorResume(Function)}.
 */
final class ResumeCompletable extends AbstractNoHandleSubscribeCompletable {
    private final Completable original;
    private final Function<? super Throwable, Completable> nextFactory;

    ResumeCompletable(Completable original, Function<? super Throwable, Completable> nextFactory,
                      Executor executor) {
        super(executor);
        this.original = original;
        this.nextFactory = requireNonNull(nextFactory);
    }

    @Override
    void handleSubscribe(final Subscriber subscriber, final SignalOffloader signalOffloader,
                         final AsyncContextMap contextMap, final AsyncContextProvider contextProvider) {
        original.subscribeWithOffloaderAndContext(new ResumeSubscriber(subscriber, nextFactory, signalOffloader,
                        contextMap, contextProvider), signalOffloader, contextMap, contextProvider);
    }

    private static final class ResumeSubscriber implements Subscriber {
        private final Subscriber subscriber;
        @Nullable
        private volatile Function<? super Throwable, Completable> nextFactory;
        private final SignalOffloader signalOffloader;
        private final AsyncContextMap contextMap;
        private final AsyncContextProvider contextProvider;
        @Nullable
        private volatile SequentialCancellable sequentialCancellable;

        ResumeSubscriber(Subscriber subscriber, Function<? super Throwable, Completable> nextFactory,
                         SignalOffloader signalOffloader, AsyncContextMap contextMap,
                         AsyncContextProvider contextProvider) {
            this.subscriber = subscriber;
            this.nextFactory = nextFactory;
            this.signalOffloader = signalOffloader;
            this.contextMap = contextMap;
            this.contextProvider = contextProvider;
        }

        @Override
        public void onSubscribe(Cancellable cancellable) {
            SequentialCancellable sequentialCancellable = this.sequentialCancellable;
            if (sequentialCancellable == null) {
                this.sequentialCancellable = sequentialCancellable = new SequentialCancellable(cancellable);
                subscriber.onSubscribe(sequentialCancellable);
            } else {
                // Only a single re-subscribe is allowed.
                nextFactory = null;
                sequentialCancellable.nextCancellable(cancellable);
            }
        }

        @Override
        public void onComplete() {
            subscriber.onComplete();
        }

        @Override
        public void onError(Throwable throwable) {
            final Function<? super Throwable, Completable> nextFactory = this.nextFactory;
            if (nextFactory == null) {
                subscriber.onError(throwable);
                return;
            }

            final Completable next;
            try {
                next = requireNonNull(nextFactory.apply(throwable));
            } catch (Throwable t) {
                t.addSuppressed(throwable);
                subscriber.onError(t);
                return;
            }
            // We are subscribing to a new Completable which will send signals to the original Subscriber. This means
            // that the threading semantics may differ with respect to the original Subscriber when we emit signals from
            // the new Completable. This is the reason we use the original offloader now to offload signals which
            // originate from this new Completable.
            final Subscriber offloadedSubscriber = signalOffloader.offloadSubscriber(
                    contextProvider.wrap(this, contextMap));
            next.subscribe(offloadedSubscriber);
        }
    }
}
