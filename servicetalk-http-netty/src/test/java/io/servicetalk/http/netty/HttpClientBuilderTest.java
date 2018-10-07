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
import io.servicetalk.client.api.DefaultServiceDiscovererEvent;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.transport.api.HostAndPort;

import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;

import static io.servicetalk.concurrent.api.Completable.completed;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class HttpClientBuilderTest extends AbstractEchoServerBasedHttpRequesterTest {

    @Test
    public void httpClientWithStaticLoadBalancing() throws Exception {

        DefaultServiceDiscovererEvent<InetSocketAddress> sdEvent = new DefaultServiceDiscovererEvent<>(
                (InetSocketAddress) serverContext.listenAddress(), true);

        sendRequestAndValidate(Publisher.just(sdEvent));
    }

    @Test
    public void httpClientWithDynamicLoadBalancing() throws Exception {

        TestPublisher<ServiceDiscovererEvent<InetSocketAddress>> sdPub = new TestPublisher<>();
        sdPub.sendOnSubscribe();

        DefaultServiceDiscovererEvent<InetSocketAddress> sdEvent = new DefaultServiceDiscovererEvent<>(
                (InetSocketAddress) serverContext.listenAddress(), true);

        // Simulate delayed discovery
        CTX.executor().schedule(() -> {
            sdPub.sendItems(sdEvent);
            sdPub.onComplete();
        }, 300, MILLISECONDS);

        sendRequestAndValidate(sdPub);
    }

    @Test
    public void withConnectionFactoryFilter() throws Exception {
        int port = ((InetSocketAddress) serverContext.listenAddress()).getPort();
        @SuppressWarnings("unchecked")
        ConnectionFactory<InetSocketAddress, ? extends StreamingHttpConnection> factory =
                (ConnectionFactory<InetSocketAddress, ? extends StreamingHttpConnection>) mock(ConnectionFactory.class);
        when(factory.closeAsyncGracefully()).thenReturn(completed());
        when(factory.closeAsync()).thenReturn(completed());
        StreamingHttpClient requester = HttpClients.forSingleAddress("localhost", port)
                .appendConnectionFactoryFilter(orig -> {
                    when(factory.newConnection(any()))
                            .thenAnswer(invocation -> orig.newConnection(invocation.getArgument(0)));
                    return factory;
                })
                .executionContext(CTX)
                .buildStreaming();
        makeRequestValidateResponseAndClose(requester);
        verify(factory).newConnection(any());
    }

    private void sendRequestAndValidate(final Publisher<ServiceDiscovererEvent<InetSocketAddress>> sdPub)
            throws ExecutionException, InterruptedException {
        @SuppressWarnings("unchecked")
        ServiceDiscoverer<HostAndPort, InetSocketAddress, ServiceDiscovererEvent<InetSocketAddress>> disco =
                mock(ServiceDiscoverer.class);
        when(disco.discover(any())).thenReturn(sdPub);
        int port = ((InetSocketAddress) serverContext.listenAddress()).getPort();
        StreamingHttpClient requester = HttpClients.forSingleAddress("localhost", port)
                .serviceDiscoverer(disco)
                .executionContext(CTX)
                .buildStreaming();
        makeRequestValidateResponseAndClose(requester);
    }
}
