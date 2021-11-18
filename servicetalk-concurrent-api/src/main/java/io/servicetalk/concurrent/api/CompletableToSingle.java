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
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.internal.SignalOffloader;
import io.servicetalk.context.api.ContextMap;

final class CompletableToSingle<T> extends AbstractNoHandleSubscribeSingle<T> {
    private final Completable parent;

    CompletableToSingle(Completable parent, Executor executor) {
        super(executor);
        this.parent = parent;
    }

    @Override
    protected void handleSubscribe(Subscriber<? super T> subscriber, SignalOffloader offloader,
                                   ContextMap contextMap, AsyncContextProvider contextProvider) {
        // We are not modifying the Cancellable between sources, so we do not need to take care of offloading between
        // the sources (in this operator). If the Cancellable is configured to be offloaded, it will be done when the
        // resulting Completable is subscribed.
        parent.delegateSubscribe(new CompletableSource.Subscriber() {
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
        },
                // Since this is converting a Completable to a Single, we should try to use the same SignalOffloader for
                // subscribing to the original Completable to avoid thread hop. Since, it is the same source, just
                // viewed as a Single, there is no additional risk of deadlock.
                offloader, contextMap, contextProvider);
    }
}
