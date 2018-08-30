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

import io.servicetalk.client.api.DefaultServiceDiscovererEvent;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.transport.api.HostAndPort;

import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HttpClientBuilderTest extends AbstractEchoServerBasedHttpRequesterTest {

    @Test
    public void httpClientWithStaticLoadBalancing() throws ExecutionException, InterruptedException {

        DefaultServiceDiscovererEvent<InetSocketAddress> sdEvent = new DefaultServiceDiscovererEvent<>(
                (InetSocketAddress) serverContext.getListenAddress(), true);

        sendRequestAndValidate(Publisher.just(sdEvent));
    }

    @Test
    public void httpClientWithDynamicLoadBalancing() throws ExecutionException, InterruptedException {

        TestPublisher<ServiceDiscoverer.Event<InetSocketAddress>> sdPub = new TestPublisher<>();
        sdPub.sendOnSubscribe();

        DefaultServiceDiscovererEvent<InetSocketAddress> sdEvent = new DefaultServiceDiscovererEvent<>(
                (InetSocketAddress) serverContext.getListenAddress(), true);

        // Simulate delayed discovery
        CTX.getExecutor().schedule(() -> {
            sdPub.sendItems(sdEvent);
            sdPub.onComplete();
        }, 300, MILLISECONDS);

        sendRequestAndValidate(sdPub);
    }

    private void sendRequestAndValidate(final Publisher<ServiceDiscoverer.Event<InetSocketAddress>> sdPub)
            throws ExecutionException, InterruptedException {
        ServiceDiscoverer<HostAndPort, InetSocketAddress> disco = mock(ServiceDiscoverer.class);
        when(disco.discover(any())).thenReturn(sdPub);
        int port = ((InetSocketAddress) serverContext.getListenAddress()).getPort();
        StreamingHttpClient requester = HttpClients.forSingleAddress("localhost", port)
                .setServiceDiscoverer(disco)
                .buildStreaming(CTX);
        makeRequestValidateResponseAndClose(requester);
    }
}
