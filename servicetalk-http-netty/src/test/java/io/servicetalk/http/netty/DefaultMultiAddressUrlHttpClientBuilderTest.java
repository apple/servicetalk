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

import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.http.api.BlockingHttpRequester;
import io.servicetalk.http.api.BlockingStreamingHttpRequester;
import io.servicetalk.http.api.HttpRequester;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.net.InetSocketAddress;

import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.transport.netty.internal.ExecutionContextExtension.immediate;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class DefaultMultiAddressUrlHttpClientBuilderTest {

    @RegisterExtension
    static final ExecutionContextExtension CTX = immediate();

    @Test
    void buildWithDefaults() throws Exception {
        StreamingHttpRequester newRequester = HttpClients.forMultiAddressUrl()
                .ioExecutor(CTX.ioExecutor())
                .executionStrategy(defaultStrategy(CTX.executor()))
                .buildStreaming();
        assertNotNull(newRequester);
        newRequester.closeAsync().toFuture().get();
    }

    @Test
    void buildAggregatedWithDefaults() throws Exception {
        HttpRequester newAggregatedRequester = HttpClients.forMultiAddressUrl()
                .ioExecutor(CTX.ioExecutor())
                .executionStrategy(defaultStrategy(CTX.executor()))
                .build();
        assertNotNull(newAggregatedRequester);
        newAggregatedRequester.closeAsync().toFuture().get();
    }

    @Test
    void buildBlockingWithDefaults() throws Exception {
        BlockingStreamingHttpRequester newBlockingRequester = HttpClients.forMultiAddressUrl()
                .ioExecutor(CTX.ioExecutor())
                .executionStrategy(defaultStrategy(CTX.executor()))
                .buildBlockingStreaming();
        assertNotNull(newBlockingRequester);
        newBlockingRequester.close();
    }

    @Test
    void buildBlockingAggregatedWithDefaults() throws Exception {
        BlockingHttpRequester newBlockingAggregatedRequester = HttpClients.forMultiAddressUrl()
                .ioExecutor(CTX.ioExecutor())
                .executionStrategy(defaultStrategy(CTX.executor()))
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
                .executionStrategy(defaultStrategy(CTX.executor()))
                .buildStreaming();
        newRequester.closeAsync().toFuture().get();
        verify(mockedServiceDiscoverer, never()).closeAsync();
    }
}
