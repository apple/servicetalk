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

import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * As returned by {@link Completable#concat(Completable)}.
 */
final class CompletableConcatWithCompletable extends AbstractNoHandleSubscribeCompletable {
    private final Completable original;
    private final Completable next;

    CompletableConcatWithCompletable(Completable original, Completable next) {
        this.original = original;
        this.next = requireNonNull(next);
    }

    @Override
    Executor executor() {
        return original.executor();
    }

    @Override
    protected void handleSubscribe(Subscriber subscriber, SignalOffloader offloader, AsyncContextMap contextMap,
                                   AsyncContextProvider contextProvider) {
        // We have the following cases to consider w.r.t offloading signals:
        //
        //  (1) Original Completable was not using an Executor but the next Completable uses an Executor.
        //  (2) Original Completable uses an Executor but the next Completable does not.
        //  (3) None of the sources use an Executor.
        //  (4) Both the sources use an Executor.
        //
        // SignalOffloader passed here is created from the Executor of the original Completable.
        // While subscribing to the next Completable, we do not pass any SignalOffloader so whatever is chosen for that
        // Completable will be used.
        //
        // The only interesting case is (2) above where for the first Subscriber we are running on an Executor thread
        // but for the second we are not which changes the threading model such that blocking code could run on the
        // eventloop. Important thing to note is that once the next Completable is subscribed we never touch the
        // Cancellable of the original Completable. So, we do not need to do anything special there.
        // In order to cover for this case ((2) above) we always offload the passed Subscriber here.
        Subscriber offloadSubscriber = offloader.offloadSubscriber(
                contextProvider.wrapCompletableSubscriber(subscriber, contextMap));
        original.delegateSubscribe(new ConcatWithSubscriber(offloadSubscriber, next), offloader,
                contextMap, contextProvider);
    }

    private static final class ConcatWithSubscriber implements Subscriber {
        private final Subscriber target;
        private final Completable next;
        @Nullable
        private SequentialCancellable sequentialCancellable;
        private boolean nextSubscribed;

        ConcatWithSubscriber(Subscriber target, Completable next) {
            this.target = target;
            this.next = next;
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
            if (nextSubscribed) {
                target.onComplete();
            } else {
                nextSubscribed = true;
                // Do not use the same SignalOffloader as used for original as that may cause deadlock.
                // Using a regular subscribe helps us to inherit the threading model for this next source. However,
                // since we always offload the original Subscriber (in handleSubscribe above) we are assured that this
                // Subscriber is not called unexpectedly on an eventloop if this source does not use an Executor.
                //
                // This is an asynchronous boundary, and so we should recapture the AsyncContext instead of propagating
                // it.
                next.subscribeInternal(this);
            }
        }

        @Override
        public void onError(Throwable t) {
            target.onError(t);
        }
    }
}
