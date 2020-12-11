/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.api.test;

import io.servicetalk.concurrent.PublisherSource;

import java.util.function.Consumer;

import static io.servicetalk.concurrent.PublisherSource.Subscriber;
import static io.servicetalk.concurrent.PublisherSource.Subscription;

/**
 * Provides the ability to express expectations for the first step in a {@link Subscriber Subscriber}'s lifecycle.
 * @param <T> The type of {@link Subscriber}.
 */
public interface PublisherFirstStep<T> extends PublisherStep<T> {
    /**
     * Declare an expectation that {@link PublisherSource.Subscriber#onSubscribe(Subscription)} is the next signal.
     * @return An object which allows for subsequent expectations to be defined.
     */
    PublisherStep<T> expectSubscription();

    /**
     * Declare an expectation that can be asserted when the {@link PublisherSource.Subscriber#onSubscribe(Subscription)}
     * method is called.
     * @param consumer Consumes the {@link Subscription} from
     * {@link PublisherSource.Subscriber#onSubscribe(Subscription)}.
     * @return An object which allows for subsequent expectations to be defined.
     */
    PublisherStep<T> expectSubscriptionConsumed(Consumer<? super Subscription> consumer);
}
