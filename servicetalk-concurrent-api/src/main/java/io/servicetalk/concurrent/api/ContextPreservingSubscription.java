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

import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.context.api.ContextMap;

import static java.util.Objects.requireNonNull;

final class ContextPreservingSubscription implements Subscription {
    // TODO: remove after 0.42.55
    private final ContextMap saved;
    private final CapturedContext capturedContext;
    private final Subscription subscription;

    private ContextPreservingSubscription(Subscription subscription, CapturedContext capturedContext) {
        this.subscription = requireNonNull(subscription);
        this.capturedContext = requireNonNull(capturedContext);
        this.saved = capturedContext.captured();
    }

    static Subscription wrap(Subscription subscription, CapturedContext current) {
        return subscription instanceof ContextPreservingSubscription &&
                ((ContextPreservingSubscription) subscription).capturedContext == current ? subscription :
                new ContextPreservingSubscription(subscription, current);
    }

    @Override
    public void request(long l) {
        try (Scope ignored = capturedContext.restoreContext()) {
            subscription.request(l);
        }
    }

    @Override
    public void cancel() {
        try (Scope ignored = capturedContext.restoreContext()) {
            subscription.cancel();
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + '(' + subscription + ')';
    }
}
