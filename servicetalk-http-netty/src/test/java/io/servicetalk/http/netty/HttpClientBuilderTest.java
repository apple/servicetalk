/*
 * Copyright © 2018-2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.client.api.ConnectionFactoryFilter;
import io.servicetalk.client.api.DefaultServiceDiscovererEvent;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.transport.api.HostAndPort;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.concurrent.ExecutionException;

import static io.servicetalk.client.api.ServiceDiscovererEvent.Status.AVAILABLE;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadNone;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HttpClientBuilderTest extends AbstractEchoServerBasedHttpRequesterTest {

    @Test
    void httpClientWithStaticLoadBalancing() throws Exception {

        DefaultServiceDiscovererEvent<InetSocketAddress> sdEvent = new DefaultServiceDiscovererEvent<>(
                (InetSocketAddress) serverContext.listenAddress(), AVAILABLE);

        sendRequestAndValidate(Publisher.from(sdEvent));
    }

    @Test
    void httpClientWithDynamicLoadBalancing() throws Exception {

        TestPublisher<ServiceDiscovererEvent<InetSocketAddress>> sdPub = new TestPublisher<>();

        DefaultServiceDiscovererEvent<InetSocketAddress> sdEvent = new DefaultServiceDiscovererEvent<>(
                (InetSocketAddress) serverContext.listenAddress(), AVAILABLE);

        // Simulate delayed discovery
        CTX.executor().schedule(() -> {
            sdPub.onNext(sdEvent);
            sdPub.onComplete();
        }, 300, MILLISECONDS);

        sendRequestAndValidate(sdPub);
    }

    @Test
    void withConnectionFactoryFilter() throws Exception {
        ConnectionFactory<InetSocketAddress, FilterableStreamingHttpConnection> factory1 = newFilter();
        ConnectionFactory<InetSocketAddress, FilterableStreamingHttpConnection> factory2 = newFilter();
        StreamingHttpClient requester = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                .appendConnectionFactoryFilter(factoryFilter(factory1))
                .appendConnectionFactoryFilter(factoryFilter(factory2))
                .ioExecutor(CTX.ioExecutor())
                .executionStrategy(offloadNone())
                .buildStreaming();
        makeRequestValidateResponseAndClose(requester);

        InOrder verifier = inOrder(factory1, factory2);
        verifier.verify(factory1).newConnection(any(), any());
        verifier.verify(factory2).newConnection(any(), any());
    }

    private static ConnectionFactoryFilter<InetSocketAddress, FilterableStreamingHttpConnection> factoryFilter(
            final ConnectionFactory<InetSocketAddress, FilterableStreamingHttpConnection> factory) {
        return ConnectionFactoryFilter.withStrategy(orig -> {
            when(factory.newConnection(any(), any()))
                    .thenAnswer(invocation -> orig.newConnection(invocation.getArgument(0),
                            invocation.getArgument(1)));
            return factory;
        }, HttpExecutionStrategies.offloadNone());
    }

    @SuppressWarnings("unchecked")
    private static ConnectionFactory<InetSocketAddress, FilterableStreamingHttpConnection> newFilter() {
        ConnectionFactory<InetSocketAddress, FilterableStreamingHttpConnection> factory =
                mock(ConnectionFactory.class);
        when(factory.closeAsyncGracefully()).thenReturn(completed());
        when(factory.closeAsync()).thenReturn(completed());
        return factory;
    }

    private void sendRequestAndValidate(final Publisher<ServiceDiscovererEvent<InetSocketAddress>> sdPub)
            throws ExecutionException, InterruptedException {
        @SuppressWarnings("unchecked")
        ServiceDiscoverer<HostAndPort, InetSocketAddress, ServiceDiscovererEvent<InetSocketAddress>> disco =
                mock(ServiceDiscoverer.class);
        when(disco.discover(any())).thenReturn(sdPub.map(Collections::singletonList));

        StreamingHttpClient requester = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                .serviceDiscoverer(disco)
                .ioExecutor(CTX.ioExecutor())
                .executionStrategy(offloadNone())
                .buildStreaming();
        makeRequestValidateResponseAndClose(requester);
    }
}
