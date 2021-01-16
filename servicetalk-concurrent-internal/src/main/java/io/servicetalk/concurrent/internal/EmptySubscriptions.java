/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.internal;

import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;

import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.SubscriberUtils.newExceptionForInvalidRequestN;

/**
 * Utility methods for {@link Subscription}s which don't deliver any data.
 */
public final class EmptySubscriptions {
    /**
     * A {@link Subscription} with no associated {@link Subscriber} that will throw on invalid demand.
     */
    public static final Subscription EMPTY_SUBSCRIPTION = newEmptySubscription();
    /**
     * A {@link Subscription} with no associated {@link Subscriber} that will <strong>not</strong> throw on invalid
     * demand.
     */
    public static final Subscription EMPTY_SUBSCRIPTION_NO_THROW = newEmptySubscriptionNoThrow();

    private EmptySubscriptions() {
    }

    /**
     * Create an empty {@link Subscription} that will propagate an error to a {@link Subscriber} upon invalid demand.
     * @param subscriber The subscriber to propagate
     * @param <T> The type of {@link Subscriber}.
     * @return An empty {@link Subscription} that will propagate an error to a {@link Subscriber} upon invalid demand.
     */
    public static <T> Subscription newEmptySubscription(Subscriber<T> subscriber) {
        return new Subscription() {
            private boolean terminated;
            @Override
            public void request(final long n) {
                if (!terminated && !isRequestNValid(n)) {
                    terminated = true;
                    subscriber.onError(newExceptionForInvalidRequestN(n));
                }
            }

            @Override
            public void cancel() {
            }
        };
    }

    /**
     * Create an empty {@link Subscription} that will throw on invalid demand.
     * @return A {@link Subscription} that will throw on invalid demand.
     */
    public static Subscription newEmptySubscription() {
        return new Subscription() {
            @Override
            public void request(final long n) {
                if (!isRequestNValid(n)) {
                    throw newExceptionForInvalidRequestN(n);
                }
            }

            @Override
            public void cancel() {
            }
        };
    }

    /**
     * Create an empty {@link Subscription} that will <strong>not</strong> throw on invalid demand.
     * @return A {@link Subscription} that will will <strong>not</strong> throw on invalid demand.
     */
    private static Subscription newEmptySubscriptionNoThrow() {
        return new Subscription() {
            @Override
            public void request(final long n) {
            }

            @Override
            public void cancel() {
            }
        };
    }
}
