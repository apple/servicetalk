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
package io.servicetalk.client.api;

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;

import static io.servicetalk.concurrent.api.Completable.failed;

/**
 * A connection managed by a {@link LoadBalancer}.
 *
 * @see ScoreSupplier
 * @see ReservableRequestConcurrencyController
 */
public interface LoadBalancedConnection extends ScoreSupplier, ReservableRequestConcurrencyController,
                                                ListenableAsyncCloseable {

    @Override
    default Result tryRequest() {
        throw new UnsupportedOperationException("LoadBalancedConnection#tryRequest() is not supported by " +
                getClass());
    }

    @Override
    default void requestFinished() {
        throw new UnsupportedOperationException("LoadBalancedConnection#requestFinished() is not supported by " +
                getClass());
    }

    @Override
    default boolean tryReserve() {
        throw new UnsupportedOperationException("LoadBalancedConnection#tryReserve() is not supported by " +
                getClass());
    }

    @Override
    default Completable releaseAsync() {
        return failed(new UnsupportedOperationException("LoadBalancedConnection#releaseAsync() is not supported by " +
                getClass()));
    }
}
