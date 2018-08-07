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
package io.servicetalk.http.api;

import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.transport.api.ExecutionContext;

import static io.servicetalk.http.api.BlockingUtils.blockingInvocation;

/**
 * A builder for {@link HttpConnection} objects.
 *
 * @param <ResolvedAddress> The type of resolved address that can be used for connecting.
 */
public interface HttpConnectionBuilder<ResolvedAddress> {

    /**
     * Create a new {@link HttpConnection}.
     *
     * @param executionContext {@link ExecutionContext} when building {@link HttpConnection}s.
     * @param resolvedAddress a resolved address to use when connecting
     * @return A single that will complete with the {@link HttpConnection}
     */
    Single<HttpConnection> build(ExecutionContext executionContext, ResolvedAddress resolvedAddress);

    /**
     * Create a new {@link HttpConnection}, using a default {@link ExecutionContext}.
     *
     * @param resolvedAddress a resolved address to use when connecting
     * @return A single that will complete with the {@link HttpConnection}
     * @see #build(ExecutionContext, Object)
     */
    Single<HttpConnection> build(ResolvedAddress resolvedAddress);

    /**
     * Create a new {@link AggregatedHttpConnection}.
     *
     * @param executionContext {@link ExecutionContext} when building {@link AggregatedHttpConnection}s.
     * @param resolvedAddress a resolved address to use when connecting
     * @return A single that will complete with the {@link AggregatedHttpConnection}
     */
    default Single<AggregatedHttpConnection> buildAggregated(ExecutionContext executionContext,
                                                             ResolvedAddress resolvedAddress) {
        return build(executionContext, resolvedAddress).map(HttpConnection::asAggregatedConnection);
    }

    /**
     * Create a new {@link AggregatedHttpConnection}, using a default {@link ExecutionContext}.
     *
     * @param resolvedAddress a resolved address to use when connecting
     * @return A single that will complete with the {@link AggregatedHttpConnection}
     * @see #buildAggregated(ExecutionContext, Object)
     */
    default Single<AggregatedHttpConnection> buildAggregated(ResolvedAddress resolvedAddress) {
        return build(resolvedAddress).map(HttpConnection::asAggregatedConnection);
    }

    /**
     * Create a new {@link BlockingHttpConnection} and waits till it is created.
     *
     * @param executionContext {@link ExecutionContext} when building {@link BlockingHttpConnection}s.
     * @param resolvedAddress a resolved address to use when connecting
     * @return {@link BlockingHttpConnection}
     * @throws Exception If the connection can not be created.
     */
    default BlockingHttpConnection buildBlocking(ExecutionContext executionContext,
                                                 ResolvedAddress resolvedAddress) throws Exception {
        return blockingInvocation(build(executionContext, resolvedAddress)).asBlockingConnection();
    }

    /**
     * Create a new {@link BlockingHttpConnection} and waits till it is created, using a default {@link
     * ExecutionContext}.
     *
     * @param resolvedAddress a resolved address to use when connecting
     * @return {@link BlockingHttpConnection}
     * @throws Exception If the connection can not be created.
     * @see #buildBlocking(ExecutionContext, Object)
     */
    default BlockingHttpConnection buildBlocking(ResolvedAddress resolvedAddress) throws Exception {
        return blockingInvocation(build(resolvedAddress)).asBlockingConnection();
    }

    /**
     * Create a new {@link BlockingAggregatedHttpConnection} and waits till it is created.
     *
     * @param executionContext {@link ExecutionContext} when building {@link BlockingAggregatedHttpConnection}s.
     * @param resolvedAddress a resolved address to use when connecting
     * @return {@link BlockingAggregatedHttpConnection}
     * @throws Exception If the connection can not be created.
     */
    default BlockingAggregatedHttpConnection buildBlockingAggregated(ExecutionContext executionContext,
                                                                     ResolvedAddress resolvedAddress) throws Exception {
        return blockingInvocation(build(executionContext, resolvedAddress)).asBlockingAggregatedConnection();
    }

    /**
     * Create a new {@link BlockingAggregatedHttpConnection} and waits till it is created, using a default
     * {@link ExecutionContext}.
     *
     * @param resolvedAddress a resolved address to use when connecting
     * @return {@link BlockingAggregatedHttpConnection}
     * @throws Exception If the connection can not be created.
     * @see #buildBlockingAggregated(ExecutionContext, Object)
     */
    default BlockingAggregatedHttpConnection buildBlockingAggregated(ResolvedAddress resolvedAddress) throws Exception {
        return blockingInvocation(build(resolvedAddress)).asBlockingAggregatedConnection();
    }

    /**
     * Convert this {@link HttpConnectionBuilder} to a {@link ConnectionFactory}.
     * <p>
     * This can be useful to take advantage of connection filters targeted at the {@link ConnectionFactory} API.
     *
     * @param executionContext {@link ExecutionContext} to use for the connections.
     * @return A {@link ConnectionFactory} that will use the {@link #build(ExecutionContext, Object)}
     * method to create new {@link HttpConnection} objects.
     */
    default ConnectionFactory<ResolvedAddress, HttpConnection> asConnectionFactory(ExecutionContext executionContext) {
        return new EmptyCloseConnectionFactory<>(ra -> build(executionContext, ra));
    }

    /**
     * Convert this {@link HttpConnectionBuilder} to a {@link ConnectionFactory}, using a default
     * {@link ExecutionContext}.
     * <p>
     * This can be useful to take advantage of connection filters targeted at the {@link ConnectionFactory} API.
     *
     * @return A {@link ConnectionFactory} that will use the {@link #build(Object)}
     * method to create new {@link HttpConnection} objects.
     * @see #asConnectionFactory(ExecutionContext)
     */
    default ConnectionFactory<ResolvedAddress, HttpConnection> asConnectionFactory() {
        return new EmptyCloseConnectionFactory<>(this::build);
    }
}
