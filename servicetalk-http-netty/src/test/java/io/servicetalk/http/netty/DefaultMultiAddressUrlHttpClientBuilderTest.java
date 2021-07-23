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
package io.servicetalk.http.netty;

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.buffer.netty.BufferAllocators;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.http.api.BlockingHttpRequester;
import io.servicetalk.http.api.BlockingStreamingHttpRequester;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpRequester;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.transport.api.ExecutionStrategy;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.ExecutionContextExtension.cached;
import static io.servicetalk.transport.netty.internal.ExecutionContextExtension.immediate;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class DefaultMultiAddressUrlHttpClientBuilderTest {

    @RegisterExtension
    static final ExecutionContextExtension CTX = immediate();

    @RegisterExtension
    static final ExecutionContextExtension INTERNAL_CLIENT_CTX = cached();

    @Test
    void buildWithDefaults() throws Exception {
        StreamingHttpRequester newRequester = HttpClients.forMultiAddressUrl()
                .ioExecutor(CTX.ioExecutor())
                .executor(CTX.executor())
                .executionStrategy(defaultStrategy())
                .buildStreaming();
        assertNotNull(newRequester);
        newRequester.closeAsync().toFuture().get();
    }

    @Test
    void buildAggregatedWithDefaults() throws Exception {
        HttpRequester newAggregatedRequester = HttpClients.forMultiAddressUrl()
                .ioExecutor(CTX.ioExecutor())
                .executor(CTX.executor())
                .executionStrategy(defaultStrategy())
                .build();
        assertNotNull(newAggregatedRequester);
        newAggregatedRequester.closeAsync().toFuture().get();
    }

    @Test
    void buildBlockingWithDefaults() throws Exception {
        BlockingStreamingHttpRequester newBlockingRequester = HttpClients.forMultiAddressUrl()
                .ioExecutor(CTX.ioExecutor())
                .executor(CTX.executor())
                .executionStrategy(defaultStrategy())
                .buildBlockingStreaming();
        assertNotNull(newBlockingRequester);
        newBlockingRequester.close();
    }

    @Test
    void buildBlockingAggregatedWithDefaults() throws Exception {
        BlockingHttpRequester newBlockingAggregatedRequester = HttpClients.forMultiAddressUrl()
                .ioExecutor(CTX.ioExecutor())
                .executor(CTX.executor())
                .executionStrategy(defaultStrategy())
                .buildBlocking();
        assertNotNull(newBlockingAggregatedRequester);
        newBlockingAggregatedRequester.close();
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildWithProvidedServiceDiscoverer() throws Exception {
        ServiceDiscoverer<HostAndPort, InetSocketAddress,
                ServiceDiscovererEvent<InetSocketAddress>> mockedServiceDiscoverer = mock(ServiceDiscoverer.class);
        StreamingHttpRequester newRequester = HttpClients.forMultiAddressUrl(mockedServiceDiscoverer)
                .ioExecutor(CTX.ioExecutor())
                .executionStrategy(defaultStrategy())
                .buildStreaming();
        newRequester.closeAsync().toFuture().get();
        verify(mockedServiceDiscoverer, never()).closeAsync();
    }

    @Test
    void internalClientsUseDifferentExecutionContextWhenConfigured() throws Exception {
        // Assert prerequisites first.
        // Use different strategies, as ExecutionContextExtension shares the same strategy.
        HttpExecutionStrategy internalExecutionStrategy = HttpExecutionStrategies.customStrategyBuilder()
                .offloadNone().build();
        HttpExecutionStrategy externalExecutionStrategy = HttpExecutionStrategies.customStrategyBuilder()
                .offloadAll().build();
        assertThat(internalExecutionStrategy, not(equalTo(externalExecutionStrategy)));

        BufferAllocator internalBufferAllocator = BufferAllocators.PREFER_DIRECT_ALLOCATOR;
        BufferAllocator externalBufferAllocator = BufferAllocators.PREFER_HEAP_ALLOCATOR;
        assertThat(internalBufferAllocator, not(equalTo(externalBufferAllocator)));

        assertThat(CTX.executor(), not(equalTo(INTERNAL_CLIENT_CTX.executor())));
        assertThat(CTX.ioExecutor(), not(equalTo(INTERNAL_CLIENT_CTX.ioExecutor())));

        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .executionStrategy(noOffloadsStrategy())
                .listenStreamingAndAwait((ctx, request, responseFactory) -> succeeded(responseFactory.ok()))) {
            AtomicReference<BufferAllocator> actualInternalBufferAllocator = new AtomicReference<>();
            AtomicReference<IoExecutor> actualInternalIoExecutor = new AtomicReference<>();
            AtomicReference<Executor> actualInternalExecutor = new AtomicReference<>();
            AtomicReference<ExecutionStrategy> actualInternalExecutionStrategy = new AtomicReference<>();

            final StreamingHttpClient streamingHttpClient = HttpClients.forMultiAddressUrl()
                    .initializer((scheme, address, builder) ->
                            builder.executionStrategy(internalExecutionStrategy)
                                    .executor(INTERNAL_CLIENT_CTX.executor())
                                    .ioExecutor(INTERNAL_CLIENT_CTX.ioExecutor())
                                    .bufferAllocator(internalBufferAllocator)
                                    .executionStrategy(internalExecutionStrategy)
                                    .appendClientFilter(client -> {
                                        HttpExecutionContext internalContext = client.executionContext();
                                        actualInternalBufferAllocator.set(internalContext.bufferAllocator());
                                        actualInternalExecutor.set(internalContext.executor());
                                        actualInternalIoExecutor.set(internalContext.ioExecutor());
                                        actualInternalExecutionStrategy.set(internalContext.executionStrategy());
                                        return new StreamingHttpClientFilter(client) {
                                        };
                                    }))
                    .executor(CTX.executor())
                    .ioExecutor(CTX.ioExecutor())
                    .executionStrategy(externalExecutionStrategy)
                    .bufferAllocator(externalBufferAllocator)
                    .buildStreaming();

            assertNotNull(streamingHttpClient);

            // Check external client
            final HttpExecutionContext executionContext = streamingHttpClient.executionContext();
            assertThat(executionContext.executor(), equalTo(CTX.executor()));
            assertThat(executionContext.executionStrategy(), equalTo(externalExecutionStrategy));
            assertThat(executionContext.ioExecutor(), equalTo(CTX.ioExecutor()));
            assertThat(executionContext.bufferAllocator(), equalTo(CTX.bufferAllocator()));

            // Make a request to trigger the filter execution that extracts the execution context.
            final HostAndPort address = HostAndPort.of((InetSocketAddress) serverContext.listenAddress());
            streamingHttpClient.reserveConnection(streamingHttpClient.get("http://" + address)).toFuture().get();

            // Check internal client
            assertThat(actualInternalBufferAllocator.get(), equalTo(internalBufferAllocator));
            assertThat(actualInternalExecutor.get(), equalTo(INTERNAL_CLIENT_CTX.executor()));
            assertThat(actualInternalIoExecutor.get(), equalTo(INTERNAL_CLIENT_CTX.ioExecutor()));
            assertThat(actualInternalExecutionStrategy.get(), equalTo(internalExecutionStrategy));

            streamingHttpClient.closeAsync().toFuture().get();
        }
    }
}
