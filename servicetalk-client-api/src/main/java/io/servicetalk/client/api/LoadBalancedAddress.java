/*
 * Copyright © 2019, 2021-2022 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.client.api;

import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.context.api.ContextMap;

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Single.failed;

/**
 * An address managed by a {@link LoadBalancer}.
 * <p>
 * Note: {@link ScoreSupplier} contract provides this address' score, where 0.0 is the lowest score and 1.0 is the
 * highest. {@link LoadBalancer}s prefer addresses with a higher score.
 * Note: Weight provides a way to influence the selection of the connection with external factors.
 * With a range of 0.1 to 1.0 influencing the connection score for selectivity.
 * @param <C> type of {@link LoadBalancedConnection}.
 */
public interface LoadBalancedAddress<C extends LoadBalancedConnection>
        extends ListenableAsyncCloseable, ScoreSupplier {

    /**
     * Creates and asynchronously returns a connection for this address.
     *
     * @return {@link Single} that emits the created {@link LoadBalancedConnection}.
     * @deprecated Implement and use {@link #newConnection(ContextMap)}.
     */
    @Deprecated
    default Single<C> newConnection() { // FIXME: 0.43 - remove deprecated method
        return failed(new UnsupportedOperationException(
                "LoadBalancer#selectConnection(Predicate) is not implemented by " + getClass()));
    }

    /**
     * Creates and asynchronously returns a connection for this address.
     * @param context A {@link ContextMap context} of the caller (e.g. request context) or {@code null} if no context
     * provided.
     * @return {@link Single} that emits the created {@link LoadBalancedConnection}.
     */
    default Single<C> newConnection(@Nullable ContextMap context) {
        return newConnection(); // FIXME: 0.43 - remove default impl
    }

    /**
     * Enables addresses scoring to be influenced by a weight factor.
     * Sets the weight of a resource and returns the previous one.
     *
     * @param weight The new weight.
     * @return the previous associated weight.
     */
    default float weight(float weight) {
        return 1f;
    }
}
