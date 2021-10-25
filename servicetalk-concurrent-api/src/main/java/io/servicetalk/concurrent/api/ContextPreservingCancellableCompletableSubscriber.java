/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.context.api.ContextMap;

import static io.servicetalk.concurrent.CompletableSource.Subscriber;
import static java.util.Objects.requireNonNull;

final class ContextPreservingCancellableCompletableSubscriber implements Subscriber {
    final ContextMap saved;
    final Subscriber subscriber;

    ContextPreservingCancellableCompletableSubscriber(Subscriber subscriber, ContextMap current) {
        this.subscriber = requireNonNull(subscriber);
        this.saved = requireNonNull(current);
    }

    @Override
    public void onSubscribe(final Cancellable cancellable) {
        subscriber.onSubscribe(ContextPreservingCancellable.wrap(cancellable, saved));
    }

    @Override
    public void onComplete() {
        subscriber.onComplete();
    }

    @Override
    public void onError(final Throwable t) {
        subscriber.onError(t);
    }

    @Override
    public String toString() {
        return ContextPreservingCancellableCompletableSubscriber.class.getSimpleName() + '(' + subscriber + ')';
    }
}
