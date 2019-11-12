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

import io.servicetalk.concurrent.api.AsyncCloseable;
import io.servicetalk.concurrent.api.BiIntFunction;
import io.servicetalk.concurrent.api.Completable;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Completable.failed;

/**
 * A provider for {@link AutomaticRetryStrategy}.
 */
public interface AutoRetryStrategyProvider {

    /**
     * An {@link AutoRetryStrategyProvider} that disables automatic retries;
     */
    AutoRetryStrategyProvider DISABLE_AUTO_RETRIES = __ -> (___, cause) -> failed(cause);

    /**
     * Create a new {@link AutomaticRetryStrategy} instance using the passed {@link LoadBalancer}.
     *
     * @param loadBalancer {@link LoadBalancer} to use.
     * @return New {@link AutomaticRetryStrategy} instance.
     */
    AutomaticRetryStrategy forLoadbalancer(LoadBalancer<?> loadBalancer);

    /**
     * A strategy to use for automatic retries.
     */
    @FunctionalInterface
    interface AutomaticRetryStrategy extends AsyncCloseable, BiIntFunction<Throwable, Completable> {

        @Override
        default Completable closeAsync() {
            return completed();
        }
    }
}
