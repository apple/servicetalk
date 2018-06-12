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
import io.servicetalk.client.api.LoadBalancerFactory;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpConnection;
import io.servicetalk.loadbalancer.RoundRobinLoadBalancer;

import org.junit.Test;

import java.net.SocketAddress;
import java.util.concurrent.ExecutionException;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class DefaultHttpClientBuilderTest extends AbstractEchoServerBasedHttpRequesterTest {

    @Test
    public void httpClientWithStaticLoadBalancing() throws ExecutionException, InterruptedException {

        DefaultServiceDiscovererEvent<SocketAddress> sdEvent = new DefaultServiceDiscovererEvent<>(
                serverContext.getListenAddress(), true);

        sendRequestAndValidate(Publisher.just(sdEvent));
    }

    @Test
    public void httpClientWithDynamicLoadBalancing() throws ExecutionException, InterruptedException {

        TestPublisher<ServiceDiscoverer.Event<SocketAddress>> sdPub = new TestPublisher<>();
        sdPub.sendOnSubscribe();

        DefaultServiceDiscovererEvent<SocketAddress> sdEvent = new DefaultServiceDiscovererEvent<>(
                serverContext.getListenAddress(), true);

        // Simulate delayed discovery
        CTX.getExecutor().schedule(() -> {
            sdPub.sendItems(sdEvent);
            sdPub.onComplete();
        }, 300, MILLISECONDS);

        sendRequestAndValidate(sdPub);
    }

    private void sendRequestAndValidate(final Publisher<ServiceDiscoverer.Event<SocketAddress>> sdPub) throws ExecutionException, InterruptedException {
        LoadBalancerFactory<SocketAddress, HttpConnection> rrLbf = RoundRobinLoadBalancer.newRoundRobinFactory();
        HttpClient requester = new DefaultHttpClientBuilder<>(rrLbf).build(CTX, sdPub);
        makeRequestValidateResponseAndClose(requester);
    }
}
