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
import io.servicetalk.concurrent.CompletableSource;

final class CompletableToSingle<T> extends AbstractNoHandleSubscribeSingle<T> {
    private final Completable original;

    CompletableToSingle(Completable original) {
        this.original = original;
    }

    @Override
    protected void handleSubscribe(Subscriber<? super T> subscriber,
                                   AsyncContextMap contextMap, AsyncContextProvider contextProvider) {
        // We are not modifying the Cancellable between sources, so we do not need to take care of offloading between
        // the sources (in this operator). If the Cancellable is configured to be offloaded, it will be done when the
        // resulting Completable is subscribed. Since, it is the same source, just viewed as a Single, there is no
        // additional risk of deadlock.
        original.delegateSubscribe(new CompletableSource.Subscriber() {
            @Override
            public void onSubscribe(Cancellable cancellable) {
                subscriber.onSubscribe(cancellable);
            }

            @Override
            public void onComplete() {
                subscriber.onSuccess(null);
            }

            @Override
            public void onError(Throwable t) {
                subscriber.onError(t);
            }
        }, contextMap, contextProvider);
    }
}
