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
 * {@link Single} as returned by {@link Single#recoverWith(Function)}.
 */
final class ResumeSingle<T> extends AbstractNoHandleSubscribeSingle<T> {

    private final Single<T> original;
    private final Function<? super Throwable, ? extends Single<? extends T>> nextFactory;

    /**
     * New instance.
     *
     * @param original Source.
     * @param nextFactory For creating the next {@link Single}.
     */
    ResumeSingle(Single<T> original, Function<? super Throwable, ? extends Single<? extends T>> nextFactory,
                 Executor executor) {
        super(executor);
        this.original = original;
        this.nextFactory = requireNonNull(nextFactory);
    }

    @Override
    void handleSubscribe(final Subscriber<? super T> subscriber, final SignalOffloader signalOffloader,
                         final AsyncContextMap contextMap, final AsyncContextProvider contextProvider) {
        original.delegateSubscribe(new ResumeSubscriber<>(subscriber, nextFactory, signalOffloader,
                contextMap, contextProvider), signalOffloader, contextMap, contextProvider);
    }

    private static final class ResumeSubscriber<T> implements Subscriber<T> {
        private final Subscriber<? super T> subscriber;
        private final SignalOffloader signalOffloader;
        private final AsyncContextMap contextMap;
        private final AsyncContextProvider contextProvider;
        @Nullable
        private SequentialCancellable sequentialCancellable;
        @Nullable
        private Function<? super Throwable, ? extends Single<? extends T>> nextFactory;

        ResumeSubscriber(Subscriber<? super T> subscriber,
                         Function<? super Throwable, ? extends Single<? extends T>> nextFactory,
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
            if (sequentialCancellable == null) {
                sequentialCancellable = new SequentialCancellable(cancellable);
                subscriber.onSubscribe(sequentialCancellable);
            } else {
                // Only a single re-subscribe is allowed.
                nextFactory = null;
                sequentialCancellable.nextCancellable(cancellable);
            }
        }

        @Override
        public void onSuccess(@Nullable T result) {
            subscriber.onSuccess(result);
        }

        @Override
        public void onError(Throwable throwable) {
            if (nextFactory == null) {
                subscriber.onError(throwable);
                return;
            }

            Single<? extends T> next;
            try {
                next = requireNonNull(nextFactory.apply(throwable));
            } catch (Throwable t) {
                t.addSuppressed(throwable);
                subscriber.onError(t);
                return;
            }
            // We are subscribing to a new Single which will send signals to the original Subscriber. This means
            // that the threading semantics may differ with respect to the original Subscriber when we emit signals from
            // the new Single. This is the reason we use the original offloader now to offload signals which
            // originate from this new Single.
            final Subscriber<? super T> offloadedSubscriber = signalOffloader.offloadSubscriber(
                    contextProvider.wrapSingleSubscriber(this, contextMap));
            next.subscribeInternal(offloadedSubscriber);
        }
    }
}
