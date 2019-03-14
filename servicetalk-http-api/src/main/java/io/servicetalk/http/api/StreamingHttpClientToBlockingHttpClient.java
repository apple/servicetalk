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

import io.servicetalk.transport.api.ExecutionContext;

import static io.servicetalk.http.api.BlockingUtils.blockingInvocation;
import static io.servicetalk.http.api.HttpExecutionStrategies.OFFLOAD_NONE_STRATEGY;
import static java.util.Objects.requireNonNull;

final class StreamingHttpClientToBlockingHttpClient extends BlockingHttpClient {
    private final StreamingHttpClient client;

    private StreamingHttpClientToBlockingHttpClient(final StreamingHttpClient client,
                                                    final HttpExecutionStrategy strategy) {
        super(new StreamingHttpRequestResponseFactoryToHttpRequestResponseFactory(client.reqRespFactory), strategy);
        this.client = requireNonNull(client);
    }

    @Override
    public ReservedBlockingHttpConnection reserveConnection(final HttpExecutionStrategy strategy,
                                                            final HttpRequestMetaData metaData) throws Exception {
        return blockingInvocation(client.reserveConnection(strategy, metaData)
                .map(c -> new ReservedBlockingHttpConnection(c, executionStrategy())));
    }

    @Override
    public HttpResponse request(final HttpExecutionStrategy strategy, final HttpRequest request) throws Exception {
        return BlockingUtils.request(client, strategy, request);
    }

    @Override
    public ExecutionContext executionContext() {
        return client.executionContext();
    }

    @Override
    public void close() throws Exception {
        blockingInvocation(client.closeAsync());
    }

    static BlockingHttpClient transform(StreamingHttpClient client) {
        final HttpExecutionStrategy defaultStrategy =
                client.filterChain.effectiveExecutionStrategy(OFFLOAD_NONE_STRATEGY);
        return new StreamingHttpClientToBlockingHttpClient(client, defaultStrategy);
    }

    @Override
    public StreamingHttpClient asStreamingClient() {
        return client;
    }
}
