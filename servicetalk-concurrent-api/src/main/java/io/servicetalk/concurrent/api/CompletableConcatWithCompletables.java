/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.context.api.ContextMap;

import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * As returned by {@link Completable#concat(Completable)}.
 */
final class CompletableConcatWithCompletables extends AbstractNoHandleSubscribeCompletable {
    private static final int MAX_STACK_DEPTH = 8;
    private final Completable original;
    private final Completable[] nexts;

    CompletableConcatWithCompletables(Executor executor, Completable original, Completable... nexts) {
        super(executor);
        this.original = original;
        this.nexts = requireNonNull(nexts);
    }

    @Override
    protected void handleSubscribe(Subscriber subscriber, SignalOffloader offloader, ContextMap contextMap,
                                   AsyncContextProvider contextProvider) {
        Subscriber offloadSubscriber = offloader.offloadSubscriber(
                contextProvider.wrapCompletableSubscriber(subscriber, contextMap));
        original.delegateSubscribe(new ConcatWithSubscriber(offloadSubscriber, nexts), offloader,
                contextMap, contextProvider);
    }

    private static final class ConcatWithSubscriber implements Subscriber {
        private final Subscriber target;
        private final Completable[] nexts;
        @Nullable
        private SequentialCancellable sequentialCancellable;
        private int nextIndex;
        private int onCompleteDepth;

        ConcatWithSubscriber(Subscriber target, Completable... nexts) {
            this.target = target;
            this.nexts = nexts;
        }

        @Override
        public void onSubscribe(Cancellable cancellable) {
            if (sequentialCancellable == null) {
                sequentialCancellable = new SequentialCancellable(cancellable);
                target.onSubscribe(sequentialCancellable);
            } else {
                sequentialCancellable.nextCancellable(cancellable);
            }
        }

        @Override
        public void onComplete() {
            if (++onCompleteDepth < MAX_STACK_DEPTH) {
                do {
                    if (nextIndex == nexts.length) {
                        target.onComplete();
                    } else {
                        // Asynchronous boundary we should recapture the AsyncContext instead of propagating it.
                        nexts[nextIndex++].subscribeInternal(this);
                    }
                } while (onCompleteDepth-- >= MAX_STACK_DEPTH);
            }
        }

        @Override
        public void onError(Throwable t) {
            target.onError(t);
        }
    }
}
