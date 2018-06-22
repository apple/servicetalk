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

import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * As returned by {@link Completable#andThen(Single)}.
 *
 * @param <T> Type of result of this {@link Single}.
 */
final class CompletableAndThenSingle<T> extends AbstractNoHandleSubscribeSingle<T> {
    private final Completable original;
    private final Single<T> next;

    CompletableAndThenSingle(Completable original, Single<T> next, Executor executor) {
        super(executor);
        this.original = requireNonNull(original);
        this.next = requireNonNull(next);
    }

    @Override
    protected void handleSubscribe(Subscriber<? super T> subscriber, SignalOffloader offloader) {
        // Since we use the same Subscriber for two sources we always need to offload it. We do not subscribe to the
        // next source using the same offloader so we have the following cases:
        //
        //  (1) Original Completable was not using an Executor but the next Single uses an Executor.
        //  (2) Original Completable uses an Executor but the next Single does not.
        //  (3) None of the sources use an Executor.
        //  (4) Both the sources use an Executor.
        //
        // SignalOffloader passed here is created from the Executor of the original Completable.
        // While subscribing to the next Single, we do not pass any SignalOffloader so whatever is chosen for that
        // Single will be used.
        //
        // The only interesting case is (2) above where for the first Subscriber we are running on an Executor thread
        // but for the second we are not which changes the threading model such that blocking code could run on the
        // eventloop. Important thing to note is that once the next Single is subscribed we never touch the Cancellable
        // of the original Completable. So, we do not need to do anything special there.
        // In order to cover for this case ((2) above) we always offload the passed Subscriber here.
        Subscriber<? super T> offloadSubscriber = offloader.offloadSubscriber(subscriber);
        original.subscribe(new AndThenSubscriber<>(offloadSubscriber, next), offloader);
    }

    private static final class AndThenSubscriber<T> implements Subscriber<T>, Completable.Subscriber {
        private final Subscriber<? super T> target;
        private final Single<T> next;
        @Nullable
        private volatile SequentialCancellable sequentialCancellable;

        AndThenSubscriber(Subscriber<? super T> target, Single<T> next) {
            this.target = target;
            this.next = next;
        }

        @Override
        public void onComplete() {
            // Do not use the same SignalOffloader as used for original as that may cause deadlock.
            // Using a regular subscribe helps us to inherit the threading model for this next source. However, since
            // we always offload the original Subscriber (in handleSubscribe above) we are assured that this Subscriber
            // is not called unexpectedly on an eventloop if this source does not use an Executor.
            next.subscribe(this);
        }

        @Override
        public void onSubscribe(Cancellable cancellable) {
            SequentialCancellable sequentialCancellable = this.sequentialCancellable;
            if (sequentialCancellable == null) {
                this.sequentialCancellable = sequentialCancellable = new SequentialCancellable(cancellable);
                target.onSubscribe(sequentialCancellable);
            } else {
                sequentialCancellable.setNextCancellable(cancellable);
            }
        }

        @Override
        public void onSuccess(@Nullable T result) {
            target.onSuccess(result);
        }

        @Override
        public void onError(Throwable t) {
            target.onError(t);
        }
    }
}
