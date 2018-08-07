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
import io.servicetalk.concurrent.internal.SignalOffloader;

import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

final class CompletableToSingle<T> extends AbstractNoHandleSubscribeSingle<T> {
    private final Supplier<T> valueSupplier;
    private final Completable parent;

    CompletableToSingle(Completable parent, Supplier<T> valueSupplier, Executor executor) {
        super(executor);
        this.valueSupplier = requireNonNull(valueSupplier);
        this.parent = parent;
    }

    @Override
    protected void handleSubscribe(Subscriber<? super T> subscriber, SignalOffloader offloader) {
        // Since this is converting a Completable to a Single, we should try to use the same SignalOffloader for
        // subscribing to the original Completable to avoid thread hop. Since, it is the same source, just viewed as a
        // Single, there is no additional risk of deadlock.
        parent.subscribe(new Completable.Subscriber() {
            @Override
            public void onSubscribe(Cancellable cancellable) {
                subscriber.onSubscribe(cancellable);
            }

            @Override
            public void onComplete() {
                final T next;
                try {
                    next = valueSupplier.get();
                } catch (Throwable cause) {
                    subscriber.onError(cause);
                    return;
                }
                subscriber.onSuccess(next);
            }

            @Override
            public void onError(Throwable t) {
                subscriber.onError(t);
            }
        }, offloader);
    }
}
