/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.internal.SequentialCancellable;
import io.servicetalk.concurrent.internal.SignalOffloader;
import io.servicetalk.context.api.ContextMap;

import javax.annotation.Nullable;

abstract class AbstractCompletableAndSingleConcatenated<T> extends AbstractNoHandleSubscribeSingle<T> {

    AbstractCompletableAndSingleConcatenated(final Executor executor) {
        super(executor);
    }

    @Override
    protected void handleSubscribe(final Subscriber<? super T> subscriber, final SignalOffloader offloader,
                                   final ContextMap contextMap, final AsyncContextProvider contextProvider) {
        // Since we use the same Subscriber for two sources we always need to offload it. We do not subscribe to the
        // next source using the same offloader so we have the following cases:
        //
        //  (1) Original Source was not using an Executor but the next Source uses an Executor.
        //  (2) Original Source uses an Executor but the next Source does not.
        //  (3) None of the sources use an Executor.
        //  (4) Both of the sources use an Executor.
        //
        // SignalOffloader passed here is created from the Executor of the original Source.
        // While subscribing to the next Source, we do not pass any SignalOffloader so whatever is chosen for that
        // Source will be used.
        //
        // The only interesting case is (2) above where for the first Subscriber we are running on an Executor thread
        // but for the second we are not which changes the threading model such that blocking code could run on the
        // eventloop. Important thing to note is that once the next Source is subscribed we never touch the original
        // Source. So, we do not need to do anything special there.
        // In order to cover for this case ((2) above) we always offload the passed Subscriber here.
        final Subscriber<? super T> offloadSubscriber = offloader.offloadSubscriber(
                contextProvider.wrapSingleSubscriber(subscriber, contextMap));
        delegateSubscribeToOriginal(offloadSubscriber, offloader, contextMap, contextProvider);
    }

    abstract void delegateSubscribeToOriginal(Subscriber<? super T> offloadSubscriber, SignalOffloader offloader,
                                              ContextMap contextMap, AsyncContextProvider contextProvider);

    abstract static class AbstractConcatWithSubscriber<T> implements Subscriber<T>, CompletableSource.Subscriber {

        private final Subscriber<? super T> target;
        @Nullable
        private SequentialCancellable sequentialCancellable;

        AbstractConcatWithSubscriber(final Subscriber<? super T> target) {
            this.target = target;
        }

        @Override
        public final void onSubscribe(final Cancellable cancellable) {
            if (sequentialCancellable == null) {
                sequentialCancellable = new SequentialCancellable(cancellable);
                target.onSubscribe(sequentialCancellable);
            } else {
                sequentialCancellable.nextCancellable(cancellable);
            }
        }

        final void subscribeToNext(final Completable next) {
            // Do not use the same SignalOffloader as used for original as that may cause deadlock.
            // Using a regular subscribe helps us to inherit the threading model for this next source. However, since
            // we always offload the original Subscriber (in handleSubscribe above) we are assured that this Subscriber
            // is not called unexpectedly on an eventloop if this source does not use an Executor.
            //
            // This is an asynchronous boundary, and so we should recapture the AsyncContext instead of propagating it.
            next.subscribeInternal(this);
        }

        final void subscribeToNext(final Single<T> next) {
            // Do not use the same SignalOffloader as used for original as that may cause deadlock.
            // Using a regular subscribe helps us to inherit the threading model for this next source. However, since
            // we always offload the original Subscriber (in handleSubscribe above) we are assured that this Subscriber
            // is not called unexpectedly on an eventloop if this source does not use an Executor.
            //
            // This is an asynchronous boundary, and so we should recapture the AsyncContext instead of propagating it.
            next.subscribeInternal(this);
        }

        final void sendSuccessToTarget(@Nullable final T result) {
            target.onSuccess(result);
        }

        @Override
        public final void onError(final Throwable t) {
            target.onError(t);
        }
    }
}
