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

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.SingleSource;

import java.util.function.Function;

/**
 * An operator contract for a {@link Publisher} to {@link Single} conversion.
 * Logically an operator sits between a {@link Publisher} and a {@link PublisherSource.Subscriber} and hence it has two
 * responsibilities:
 * <ul>
 *     <li>Subscribe to the {@link Publisher} on which this operator is applied.</li>
 *     <li>Accept a {@link SingleSource.Subscriber} which is converted to a {@link PublisherSource.Subscriber} that
 *     subscribes to this operator.</li>
 * </ul>
 *
 * So, an operator can be defined as a {@link Function} that takes a {@link SingleSource.Subscriber} and returns a
 * {@link PublisherSource.Subscriber}. The {@link SingleSource.Subscriber} that is passed to this {@link Function} is
 * the one that has subscribed to the {@link Single} operator. The {@link PublisherSource.Subscriber} that is returned
 * by this {@link Function} is the one that should be used to subscribe to the {@link Publisher} on which this operator
 * is applied.
 *
 * @param <T> Type of items emitted by the {@link Publisher} this operator is applied.
 * @param <R> Type of items emitted by this operator.
 */
public interface PublisherToSingleOperator<T, R> extends Function<SingleSource.Subscriber<? super R>,
        PublisherSource.Subscriber<? super T>> {
    /**
     * Implementation of this operator. See {@link PublisherToSingleOperator} for definition of an operator.
     *
     * @param subscriber {@link SingleSource.Subscriber} that subscribed to this operator.
     * @return {@link PublisherSource.Subscriber} that is used to subscribe to the {@link Publisher} that this operator
     * is applied to.
     */
    @Override
    PublisherSource.Subscriber<? super T> apply(SingleSource.Subscriber<? super R> subscriber);
}
