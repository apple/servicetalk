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
package io.servicetalk.http.netty;

import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpConnectionFilterFactory;
import io.servicetalk.http.api.HttpExecutionStrategy;

import static io.servicetalk.concurrent.api.AsyncCloseables.emptyAsyncCloseable;

abstract class AbstractLBHttpConnectionFactory<ResolvedAddress>
        implements ConnectionFactory<ResolvedAddress, LoadBalancedStreamingHttpConnection> {
    private final HttpConnectionFilterFactory connectionFilterFunction;
    private final ListenableAsyncCloseable close = emptyAsyncCloseable();
    protected final HttpExecutionStrategy defaultStrategy;

    AbstractLBHttpConnectionFactory(final HttpConnectionFilterFactory connectionFilterFunction,
                                    final HttpExecutionStrategy defaultStrategy) {
        this.connectionFilterFunction = connectionFilterFunction;
        this.defaultStrategy = defaultStrategy;
    }

    abstract Single<LoadBalancedStreamingHttpConnection> newConnection(ResolvedAddress address,
                                                                       HttpConnectionFilterFactory connectionFilterFunction);

    @Override
    public final Single<LoadBalancedStreamingHttpConnection> newConnection(ResolvedAddress address) {
        return newConnection(address, connectionFilterFunction);
    }

    @Override
    public final Completable onClose() {
        return close.onClose();
    }

    @Override
    public final Completable closeAsync() {
        return close.closeAsync();
    }

    @Override
    public final Completable closeAsyncGracefully() {
        return close.closeAsyncGracefully();
    }
}
