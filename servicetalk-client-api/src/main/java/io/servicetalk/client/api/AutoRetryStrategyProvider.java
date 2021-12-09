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
import io.servicetalk.concurrent.api.BiIntPredicate;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Completable.failed;

/**
 * A provider for {@link AutoRetryStrategy}.
 * @deprecated The capabilities of the auto-retry have been introduced under a new universal retrying filter,
 * available from {@code io.servicetalk.http.netty.RetryingHttpRequesterFilter}.
 */
@FunctionalInterface
@Deprecated
public interface AutoRetryStrategyProvider {

    /**
     * An {@link AutoRetryStrategyProvider} that disables automatic retries;
     */
    AutoRetryStrategyProvider DISABLE_AUTO_RETRIES = (lbEventStream, sdErrorStream) -> (___, cause) -> failed(cause);

    /**
     * Creates a new {@link AutoRetryStrategy} instance.
     *
     * @param lbEventStream a stream of events from {@link LoadBalancer#eventStream() LoadBalancer}
     * @param sdStatus a {@link Completable} that will terminate with an error when the corresponding
     * {@link ServiceDiscoverer#discover(Object)} emits an error
     * @return New {@link AutoRetryStrategy} instance.
     */
    AutoRetryStrategy newStrategy(Publisher<Object> lbEventStream, Completable sdStatus);

    /**
     * A strategy to use for automatic retries.  Automatic retries are done by
     * the clients automatically when allowed by the passed {@link AutoRetryStrategyProvider}. These retries are not a
     * substitute for user level retries which are designed to infer retry decisions based on request/error information.
     * Typically such user level retries are done using protocol level filter but can also be done differently per
     * request (eg: by using {@link Single#retry(BiIntPredicate)}).
     */
    @FunctionalInterface
    interface AutoRetryStrategy extends AsyncCloseable, BiIntFunction<Throwable, Completable> {

        @Override
        default Completable closeAsync() {
            return completed();
        }
    }
}
