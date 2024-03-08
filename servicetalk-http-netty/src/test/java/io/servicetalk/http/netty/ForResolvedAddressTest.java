/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpServerContext;
import io.servicetalk.transport.api.HostAndPort;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.net.InetSocketAddress;

import static io.servicetalk.dns.discovery.netty.DnsServiceDiscoverers.globalARecordsDnsServiceDiscoverer;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.netty.InternalServiceDiscoverers.mappingServiceDiscoverer;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static io.servicetalk.transport.netty.internal.BuilderUtils.toResolvedInetSocketAddress;
import static java.util.function.Function.identity;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SuppressWarnings("PMD.AvoidUsingHardCodedIP")
class ForResolvedAddressTest {

    @ParameterizedTest(name = "{displayName} [{index}]: protocol={0}")
    @EnumSource(HttpProtocol.class)
    void hostAndPort(HttpProtocol protocol) throws Exception {
        try (HttpServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .protocols(protocol.config)
                .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok());
             BlockingHttpClient client = HttpClients.forResolvedAddress(serverHostAndPort(serverContext))
                     .protocols(protocol.config)
                     .buildBlocking()) {
            HttpResponse response = client.request(client.get("/"));
            assertThat(response.status(), is(OK));
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}]: protocol={0}")
    @EnumSource(HttpProtocol.class)
    void inetSocketAddress(HttpProtocol protocol) throws Exception {
        try (HttpServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .protocols(protocol.config)
                .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok());
             BlockingHttpClient client = HttpClients.forResolvedAddress(
                     (InetSocketAddress) serverContext.listenAddress())
                     .protocols(protocol.config)
                     .buildBlocking()) {
            HttpResponse response = client.request(client.get("/"));
            assertThat(response.status(), is(OK));
        }
    }

    @Test
    void hostAndPortThrowIfSdChanges() {
        ServiceDiscoverer<HostAndPort, InetSocketAddress, ServiceDiscovererEvent<InetSocketAddress>> otherSd =
                globalARecordsDnsServiceDiscoverer();
        HostAndPort address = HostAndPort.of("127.0.0.1", 8080);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> HttpClients.forResolvedAddress(address).serviceDiscoverer(otherSd));
        assertThat(e.getMessage(), allOf(containsString(address.toString()), containsString(otherSd.toString())));
    }

    @Test
    void inetSocketAddressThrowIfSdChanges() {
        ServiceDiscoverer<InetSocketAddress, InetSocketAddress, ServiceDiscovererEvent<InetSocketAddress>> otherSd =
                mappingServiceDiscoverer(identity(), ForResolvedAddressTest.class.getSimpleName());
        InetSocketAddress address = toResolvedInetSocketAddress(HostAndPort.of("127.0.0.1", 8080));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> HttpClients.forResolvedAddress(address).serviceDiscoverer(otherSd));
        assertThat(e.getMessage(), allOf(containsString(address.toString()), containsString(otherSd.toString())));
    }
}
