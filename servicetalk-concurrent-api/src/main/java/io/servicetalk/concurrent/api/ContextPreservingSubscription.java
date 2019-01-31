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

import org.reactivestreams.Subscription;

import static io.servicetalk.concurrent.api.DefaultAsyncContextProvider.INSTANCE;
import static java.util.Objects.requireNonNull;

final class ContextPreservingSubscription implements Subscription {
    private final AsyncContextMap saved;
    private final Subscription subscription;

    private ContextPreservingSubscription(Subscription subscription, AsyncContextMap current) {
        this.subscription = requireNonNull(subscription);
        this.saved = requireNonNull(current);
    }

    static Subscription wrap(Subscription subscription, AsyncContextMap current) {
        return subscription instanceof ContextPreservingSubscription &&
                ((ContextPreservingSubscription) subscription).saved == current ? subscription :
                new ContextPreservingSubscription(subscription, current);
    }

    @Override
    public void request(long l) {
        AsyncContextMap prev = INSTANCE.contextMap();
        try {
            INSTANCE.contextMap(saved);
            subscription.request(l);
        } finally {
            INSTANCE.contextMap(prev);
        }
    }

    @Override
    public void cancel() {
        AsyncContextMap prev = INSTANCE.contextMap();
        try {
            INSTANCE.contextMap(saved);
            subscription.cancel();
        } finally {
            INSTANCE.contextMap(prev);
        }
    }
}
