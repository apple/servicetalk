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
import io.servicetalk.concurrent.SingleSource;

import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

final class SingleToCompletable<T> extends AbstractNoHandleSubscribeCompletable {
    private final Single<T> original;

    SingleToCompletable(Single<T> original) {
        this.original = requireNonNull(original);
    }

    @Override
    void handleSubscribe(final Subscriber subscriber,
                         final CapturedContext capturedContext, final AsyncContextProvider contextProvider) {
        // We are not modifying the Cancellable between sources, so we do not need to take care of offloading between
        // the sources (in this operator). If the Cancellable is configured to be offloaded, it will be done when the
        // resulting Completable is subscribed. Since, it is the same source, just viewed as a Completable, there is no
        // additional risk of deadlock.
        original.delegateSubscribe(new SingleSource.Subscriber<T>() {
            @Override
            public void onSubscribe(Cancellable cancellable) {
                subscriber.onSubscribe(cancellable);
            }

            @Override
            public void onSuccess(@Nullable T result) {
                subscriber.onComplete();
            }

            @Override
            public void onError(Throwable t) {
                subscriber.onError(t);
            }
        }, capturedContext, contextProvider);
    }
}
