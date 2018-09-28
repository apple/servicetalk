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
 * A builder for {@link StreamingHttpConnection} objects.
 *
 * @param <ResolvedAddress> The type of resolved address that can be used for connecting.
 */
public interface HttpConnectionBuilder<ResolvedAddress> {

    /**
     * Sets an {@link ExecutionContext} for all connections created from this {@link HttpClientBuilder}.
     *
     * @param context {@link ExecutionContext} to use.
     * @return {@code this}.
     */
    HttpConnectionBuilder<ResolvedAddress> executionContext(ExecutionContext context);

    /**
     * Create a new {@link StreamingHttpConnection}, using a default {@link ExecutionContext}.
     *
     * @param resolvedAddress a resolved address to use when connecting
     * @return A single that will complete with the {@link StreamingHttpConnection}
     */
    Single<StreamingHttpConnection> buildStreaming(ResolvedAddress resolvedAddress);

    /**
     * Create a new {@link HttpConnection}, using a default {@link ExecutionContext}.
     *
     * @param resolvedAddress a resolved address to use when connecting
     * @return A single that will complete with the {@link HttpConnection}
     */
    default Single<HttpConnection> build(ResolvedAddress resolvedAddress) {
        return buildStreaming(resolvedAddress).map(StreamingHttpConnection::asConnection);
    }

    /**
     * Create a new {@link BlockingStreamingHttpConnection} and waits till it is created, using a default {@link
     * ExecutionContext}.
     *
     * @param resolvedAddress a resolved address to use when connecting
     * @return {@link BlockingStreamingHttpConnection}
     * @throws Exception If the connection can not be created.
     */
    default BlockingStreamingHttpConnection buildBlockingStreaming(ResolvedAddress resolvedAddress) throws Exception {
        return blockingInvocation(buildStreaming(resolvedAddress)).asBlockingStreamingConnection();
    }

    /**
     * Create a new {@link BlockingHttpConnection} and waits till it is created, using a default
     * {@link ExecutionContext}.
     *
     * @param resolvedAddress a resolved address to use when connecting
     * @return {@link BlockingHttpConnection}
     * @throws Exception If the connection can not be created.
     */
    default BlockingHttpConnection buildBlocking(ResolvedAddress resolvedAddress) throws Exception {
        return blockingInvocation(buildStreaming(resolvedAddress)).asBlockingConnection();
    }

    /**
     * Convert this {@link HttpConnectionBuilder} to a {@link ConnectionFactory}, using a default
     * {@link ExecutionContext}.
     * <p>
     * This can be useful to take advantage of connection filters targeted at the {@link ConnectionFactory} API.
     *
     * @return A {@link ConnectionFactory} that will use the {@link #buildStreaming(Object)}
     * method to create new {@link StreamingHttpConnection} objects.
     */
    default ConnectionFactory<ResolvedAddress, StreamingHttpConnection> asConnectionFactory() {
        return new EmptyCloseConnectionFactory<>(this::buildStreaming);
    }
}
