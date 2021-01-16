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

import static io.servicetalk.concurrent.internal.EmptySubscriptions.newEmptySubscription;
import static io.servicetalk.concurrent.internal.SubscriberUtils.handleExceptionFromOnSubscribe;

final class NeverPublisher<T> extends AbstractSynchronousPublisher<T> {
    @SuppressWarnings("rawtypes")
    private static final NeverPublisher NEVER_PUBLISHER = new NeverPublisher();

    private NeverPublisher() {
    }

    @Override
    void doSubscribe(Subscriber<? super T> subscriber) {
        try {
            subscriber.onSubscribe(newEmptySubscription(subscriber));
        } catch (Throwable t) {
            handleExceptionFromOnSubscribe(subscriber, t);
        }
    }

    @SuppressWarnings("unchecked")
    static <T> Publisher<T> neverPublisher() {
        return (Publisher<T>) NEVER_PUBLISHER;
    }
}
