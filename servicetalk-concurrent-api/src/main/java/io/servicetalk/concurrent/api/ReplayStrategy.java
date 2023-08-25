/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.PublisherSource.Subscriber;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Used to customize the strategy for the {@link Publisher} replay operator.
 * @param <T> The type of data.
 */
public interface ReplayStrategy<T> {
    /**
     * Get the minimum number of downstream subscribers before subscribing upstream.
     * @return the minimum number of downstream subscribers before subscribing upstream.
     */
    int minSubscribers();

    /**
     * Get a {@link Supplier} that provides the {@link ReplayAccumulator} on each upstream subscribe.
     * @return a {@link Supplier} that provides the {@link ReplayAccumulator} on each upstream subscribe.
     */
    Supplier<ReplayAccumulator<T>> accumulatorSupplier();

    /**
     * Determine if all the downstream subscribers cancel, should upstream be cancelled.
     * @return {@code true} if all the downstream subscribers cancel, should upstream be cancelled. {@code false}
     * will not cancel upstream if all downstream subscribers cancel.
     */
    boolean cancelUpstream();

    /**
     * Get a hint to limit the number of elements which will be queued for each {@link Subscriber} in order to
     * compensate for unequal demand and late subscribers.
     * @return a hint to limit the number of elements which will be queued for each {@link Subscriber} in order to
     * compensate for unequal demand and late subscribers.
     */
    int queueLimitHint();

    /**
     * Get a {@link Function} that is invoked when a terminal signal arrives from upstream and determines when state
     * is reset to allow for upstream resubscribe.
     * @return A {@link Function} that is invoked when a terminal signal arrives from upstream, and
     * returns a {@link Completable} whose termination resets the state of the returned {@link Publisher} and allows
     * for downstream resubscribing. The argument to this function is as follows:
     * <ul>
     *   <li>{@code null} if upstream terminates with {@link Subscriber#onComplete()}</li>
     *   <li>otherwise the {@link Throwable} from {@link Subscriber#onError(Throwable)}</li>
     * </ul>
     */
    Function<Throwable, Completable> terminalResubscribe();
}
