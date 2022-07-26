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
package io.servicetalk.client.api;

import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.context.api.ContextMap;

import java.net.SocketAddress;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Single.failed;

/**
 * Given multiple {@link SocketAddress}es select the most desired {@link SocketAddress} to use. This is typically used
 * to determine which connection to issue a request to.
 *
 * @param <C> The type of connection.
 */
public interface LoadBalancer<C extends LoadBalancedConnection> extends ListenableAsyncCloseable {

    /**
     * Select the most appropriate connection for a request. Returned connection may be used concurrently for other
     * requests.
     *
     * @param selector A {@link Function} that evaluates a connection for selection.
     *                 This selector should return {@code null} if the connection <strong>MUST</strong> not be selected.
     *                 This selector is guaranteed to be called for any connection that is returned from this method.
     * @return a {@link Single} that completes with the most appropriate connection to use. A
     * {@link Single#failed(Throwable) failed Single} with {@link NoAvailableHostException} can be returned if no
     * connection can be selected at this time or with {@link ConnectionRejectedException} if a newly created connection
     * was rejected by the {@code selector} or this load balancer.
     * @deprecated Use {@link #selectConnection(Predicate, ContextMap)}.
     */
    @Deprecated
    default Single<C> selectConnection(Predicate<C> selector) { // FIXME: 0.43 - remove deprecated method
        return failed(new UnsupportedOperationException(
                "LoadBalancer#selectConnection(Predicate) is not supported by " + getClass()));
    }

    /**
     * Select the most appropriate connection for a request. Returned connection may be used concurrently for other
     * requests.
     *
     * @param selector A {@link Function} that evaluates a connection for selection. This selector should return
     * {@code null} if the connection <strong>MUST</strong> not be selected. This selector is guaranteed to be called
     * for any connection that is returned from this method.
     * @param context A {@link ContextMap context} of the caller (e.g. request context) or {@code null} if no context
     * provided.
     * @return a {@link Single} that completes with the most appropriate connection to use. A
     * {@link Single#failed(Throwable) failed Single} with {@link NoAvailableHostException} can be returned if no
     * connection can be selected at this time or with {@link ConnectionRejectedException} if a newly created connection
     * was rejected by the {@code selector} or this load balancer.
     */
    default Single<C> selectConnection(Predicate<C> selector, @Nullable ContextMap context) {
        return selectConnection(selector);  // FIXME: 0.43 - remove default impl
    }

    /**
     * A {@link Publisher} of events provided by this {@link LoadBalancer}. This maybe used to broadcast internal state
     * of this {@link LoadBalancer} to provide hints/visibility for external usage.
     * @return A {@link Publisher} of events provided by this {@link LoadBalancer}.
     */
    Publisher<Object> eventStream();
}
