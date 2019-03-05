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
import io.servicetalk.concurrent.SingleSource.Subscriber;
import io.servicetalk.concurrent.internal.DelayedCancellable;

import java.util.function.Function;

/**
 * Calls {@link Subscriber#onSubscribe(Cancellable)} automatically, sending a delegating {@link Cancellable}.
 * Returns a {@link Subscriber} which, upon receiving {@link Subscriber#onSubscribe(Cancellable)}, uses the received
 * {@link Cancellable} to delegate to.
 *
 * @param <T> Type of the result of the {@code Subscriber}.
 */
public final class AutoOnSubscribeSingleSubscriberFunction<T>
        implements Function<Subscriber<? super T>, Subscriber<? super T>> {

    @Override
    public Subscriber<? super T> apply(final Subscriber<? super T> subscriber) {
        final DelayedCancellable subscription = new DelayedCancellable();
        subscriber.onSubscribe(subscription);
        return new DelegatingSingleSubscriber<T>(subscriber) {
            @Override
            public void onSubscribe(final Cancellable s) {
                subscription.delayedCancellable(s);
            }
        };
    }
}
