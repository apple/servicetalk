/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.transport.api.HostAndPort;

import org.junit.Test;
import org.mockito.InOrder;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nonnull;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.http.api.HttpExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HttpClientBuilderTest extends AbstractEchoServerBasedHttpRequesterTest {

    @Test
    public void httpClientWithStaticLoadBalancing() throws Exception {

        DefaultServiceDiscovererEvent<InetSocketAddress> sdEvent = new DefaultServiceDiscovererEvent<>(
                (InetSocketAddress) serverContext.listenAddress(), true);

        sendRequestAndValidate(Publisher.from(sdEvent));
    }

    @Test
    public void httpClientWithDynamicLoadBalancing() throws Exception {

        TestPublisher<ServiceDiscovererEvent<InetSocketAddress>> sdPub = new TestPublisher<>();

        DefaultServiceDiscovererEvent<InetSocketAddress> sdEvent = new DefaultServiceDiscovererEvent<>(
                (InetSocketAddress) serverContext.listenAddress(), true);

        // Simulate delayed discovery
        CTX.executor().schedule(() -> {
            sdPub.onNext(sdEvent);
            sdPub.onComplete();
        }, 300, MILLISECONDS);

        sendRequestAndValidate(sdPub);
    }

    @Test
    public void withConnectionFactoryFilter() throws Exception {
        ConnectionFactory<InetSocketAddress, ? extends StreamingHttpConnection> factory1 = newFilter();
        ConnectionFactory<InetSocketAddress, ? extends StreamingHttpConnection> factory2 = newFilter();
        StreamingHttpClient requester = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                .appendConnectionFactoryFilter(factoryFilter(factory1))
                .appendConnectionFactoryFilter(factoryFilter(factory2))
                .ioExecutor(CTX.ioExecutor())
                .executionStrategy(noOffloadsStrategy())
                .buildStreaming();
        makeRequestValidateResponseAndClose(requester);

        InOrder verifier = inOrder(factory1, factory2);
        verifier.verify(factory1).newConnection(any());
        verifier.verify(factory2).newConnection(any());
    }

    @Nonnull
    private static ConnectionFactoryFilter<InetSocketAddress, StreamingHttpConnection> factoryFilter(
            final ConnectionFactory<InetSocketAddress, ? extends StreamingHttpConnection> factory) {
        return orig -> {
            when(factory.newConnection(any()))
                    .thenAnswer(invocation -> orig.newConnection(invocation.getArgument(0)));
            return factory;
        };
    }

    @SuppressWarnings("unchecked")
    private static ConnectionFactory<InetSocketAddress, ? extends StreamingHttpConnection> newFilter() {
        ConnectionFactory<InetSocketAddress, ? extends StreamingHttpConnection> factory =
                mock(ConnectionFactory.class);
        when(factory.closeAsyncGracefully()).thenReturn(completed());
        when(factory.closeAsync()).thenReturn(completed());
        return factory;
    }

    private static void sendRequestAndValidate(final Publisher<ServiceDiscovererEvent<InetSocketAddress>> sdPub)
            throws ExecutionException, InterruptedException {
        @SuppressWarnings("unchecked")
        ServiceDiscoverer<HostAndPort, InetSocketAddress, ServiceDiscovererEvent<InetSocketAddress>> disco =
                mock(ServiceDiscoverer.class);
        when(disco.discover(any())).thenReturn(sdPub);

        StreamingHttpClient requester = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                .serviceDiscoverer(disco)
                .ioExecutor(CTX.ioExecutor())
                .executionStrategy(noOffloadsStrategy())
                .buildStreaming();
        makeRequestValidateResponseAndClose(requester);
    }
}
