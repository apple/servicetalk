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
package io.servicetalk.client.api;

import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Single;

import java.net.SocketAddress;
import java.util.function.Function;

import static java.util.function.Function.identity;

/**
 * Given multiple {@link SocketAddress}es select the most desired {@link SocketAddress} to use. This is typically used
 * to determine which connection to issue a request to.
 */
public interface LoadBalancer<C extends ListenableAsyncCloseable> extends ListenableAsyncCloseable {

    /**
     * Select the most appropriate connection for a request. Returned connection may be used concurrently for other requests.
     *
     * @param selector A {@link Function} that evaluates a connection for selection.
     *                 This selector should return {@code null} if the connection <strong>MUST</strong> not be selected.
     *                 This selector is guaranteed to be called for any connection that is returned from this method.
     * @return a {@link Single} that completes with the most appropriate connection to use.
     * @param <CC> Type of connection returned.
     */
    <CC extends C> Single<CC> selectConnection(Function<C, CC> selector);

    /**
     * A connection selector with no custom selector as {@link #selectConnection(Function)}.
     *
     * @return a {@link Single} that completes with the most appropriate connection to use.
     */
    default Single<C> selectConnection() {
        return selectConnection(identity());
    }
}
