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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.CompletableSource.Subscriber;

import java.util.function.Function;

/**
 * An operator contract for a {@link Completable}.
 * Logically an operator sits between a {@link Completable} and a {@link Subscriber} and hence it has two
 * responsibilities:
 *
 * <ul>
 *     <li>Subscribe to the {@link Completable} on which this operator is applied.</li>
 *     <li>Accept a {@link Subscriber} that subscribes to this operator.</li>
 * </ul>
 *
 * So, an operator can be defined as a {@link Function} that takes a {@link Subscriber} and returns a
 * {@link Subscriber}.
 * The {@link Subscriber} that is passed to this {@link Function} is the one that has subscribed to this operator.
 * The {@link Subscriber} that is returned by this {@link Function} is the one that should be used to subscribe to the
 * {@link Completable} on which this operator is applied.
 */
@FunctionalInterface
public interface CompletableOperator extends Function<Subscriber, Subscriber> {

    /**
     * Implementation of this operator. See {@link CompletableOperator} for definition of an operator.
     *
     * @param subscriber {@link Subscriber} that subscribed to this operator.
     * @return {@link Subscriber} that is used to subscribe to the {@link Completable} that this operator is applied to.
     */
    @Override
    Subscriber apply(Subscriber subscriber);
}
