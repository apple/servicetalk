/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.DelegatingConnectionFactory;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.DefaultHttpLoadBalancerFactory;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.FilterableStreamingHttpLoadBalancedConnection;
import io.servicetalk.http.api.HttpServerContext;
import io.servicetalk.loadbalancer.ConnectionSelectorPolicies;
import io.servicetalk.loadbalancer.LoadBalancers;
import io.servicetalk.transport.api.TransportObserver;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.netty.BuilderUtils.newClientBuilder;
import static io.servicetalk.http.netty.BuilderUtils.newServerBuilder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class LoadBalancerConnectionSelectorTest {

    // H2 returns the connection to the pool on stream close rather than on response completion, so this must
    // stay well below the default 100 max concurrent streams or a single-connection case could open a second.
    private static final int REQUESTS = 5;

    @RegisterExtension
    static final ExecutionContextExtension SERVER_CTX =
            ExecutionContextExtension.cached("server-io", "server-executor")
                    .setClassLevel(true);
    @RegisterExtension
    static final ExecutionContextExtension CLIENT_CTX =
            ExecutionContextExtension.cached("client-io", "client-executor")
                    .setClassLevel(true);

    private static List<Arguments> arguments() {
        List<Arguments> arguments = new ArrayList<>();
        for (HttpProtocol protocol : HttpProtocol.values()) {
            // Requests are sequential, so a selector that accepts the existing connection never needs a second one.
            arguments.add(Arguments.of(protocol, 2, false, 1));
            // Forcing the core pool refuses to select until the pool has grown to the core size.
            arguments.add(Arguments.of(protocol, 2, true, 2));
            arguments.add(Arguments.of(protocol, 0, false, 1));
            // forceCorePool cannot hold back a core pool of 0, so this matches the un-forced case.
            arguments.add(Arguments.of(protocol, 0, true, 1));
        }
        return arguments;
    }

    @ParameterizedTest(name = "protocol={0} corePoolSize={1} forceCorePool={2}")
    @MethodSource("arguments")
    void corePoolServesRequests(HttpProtocol protocol, int corePoolSize, boolean forceCorePool,
                                int expectedConnections) throws Exception {
        AtomicInteger connectionsOpened = new AtomicInteger();
        try (HttpServerContext serverContext = newServerBuilder(SERVER_CTX, protocol)
                .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok());
             BlockingHttpClient client = newClientBuilder(serverContext, CLIENT_CTX, protocol)
                     .appendConnectionFactoryFilter(original -> new DelegatingConnectionFactory<InetSocketAddress,
                             FilterableStreamingHttpConnection>(original) {
                         @Override
                         public Single<FilterableStreamingHttpConnection> newConnection(InetSocketAddress address,
                                 @Nullable ContextMap context, @Nullable TransportObserver observer) {
                             connectionsOpened.incrementAndGet();
                             return delegate().newConnection(address, context, observer);
                         }
                     })
                     .loadBalancerFactory(new DefaultHttpLoadBalancerFactory<>(
                             LoadBalancers.<InetSocketAddress,
                                     FilterableStreamingHttpLoadBalancedConnection>builder(getClass().getSimpleName())
                                     .connectionSelectorPolicy(
                                             ConnectionSelectorPolicies.corePool(corePoolSize, forceCorePool))
                                     .build()))
                     .buildBlocking()) {
            // The pool starts empty, so the first selection has to fall through to opening a connection.
            for (int i = 0; i < REQUESTS; i++) {
                assertThat(client.request(client.get("/")).status(), is(OK));
            }
            assertThat(connectionsOpened.get(), is(expectedConnections));
        }
    }
}
