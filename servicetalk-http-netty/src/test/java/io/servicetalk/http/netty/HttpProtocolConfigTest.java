/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.HttpProtocolConfig;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.transport.api.HostAndPort;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static io.servicetalk.http.netty.HttpProtocolConfigs.h1Default;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2Default;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpProtocolConfigTest {

    private static final HttpProtocolConfig UNKNOWN_CONFIG = new HttpProtocolConfig() {

        @Override
        public String alpnId() {
            return "unknown";
        }

        @Override
        public HttpHeadersFactory headersFactory() {
            return DefaultHttpHeadersFactory.INSTANCE;
        }
    };

    @Test
    void clientDoesNotSupportH2cUpgrade() {
        SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder =
                HttpClients.forSingleAddress("localhost", 8080)
                        .protocols(h2Default(), h1Default());

        Exception e = assertThrows(IllegalStateException.class, () -> builder.build());
        assertEquals("Cleartext HTTP/1.1 -> HTTP/2 (h2c) upgrade is not supported", e.getMessage());
    }

    @Test
    void serverDoesNotSupportH2cUpgrade() {
        HttpServerBuilder builder = HttpServers.forAddress(localAddress(0))
                .protocols(h2Default(), h1Default());

        Exception e = assertThrows(IllegalStateException.class, () ->
            builder.listenBlocking((ctx, request, responseFactory) -> responseFactory.noContent()));
        assertEquals("Cleartext HTTP/1.1 -> HTTP/2 (h2c) upgrade is not supported", e.getMessage());
    }

    @Test
    void clientWithNullProtocolConfig() {
        SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder =
                HttpClients.forSingleAddress("localhost", 8080);

        assertThrows(NullPointerException.class, () -> builder.protocols((HttpProtocolConfig) null));
    }

    @Test
    void serverWithNullProtocolConfig() {
        HttpServerBuilder builder = HttpServers.forAddress(localAddress(0));

        assertThrows(NullPointerException.class, () -> builder.protocols(null));
    }

    @Test
    void clientWithEmptyProtocolConfig() {
        SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder =
                HttpClients.forSingleAddress("localhost", 8080);

        Exception e = assertThrows(IllegalArgumentException.class, () ->
                builder.protocols(new HttpProtocolConfig[0]));
        assertEquals("No protocols specified", e.getMessage());
    }

    @Test
    void serverWithEmptyProtocolConfig() {
        HttpServerBuilder builder = HttpServers.forAddress(localAddress(0));

        Exception e = assertThrows(IllegalArgumentException.class, () ->
            builder.protocols(new HttpProtocolConfig[0]));
        assertEquals("No protocols specified", e.getMessage());
    }

    @Test
    void clientWithUnsupportedProtocolConfig() {
        SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder =
                HttpClients.forSingleAddress("localhost", 8080);

        Exception e = assertThrows(IllegalArgumentException.class, () ->
            builder.protocols(UNKNOWN_CONFIG));
        assertThat(e.getMessage(), startsWith("Unsupported HttpProtocolConfig"));
    }

    @Test
    void serverWithUnsupportedProtocolConfig() {
        HttpServerBuilder builder = HttpServers.forAddress(localAddress(0));

        Exception e = assertThrows(IllegalArgumentException.class, () ->
            builder.protocols(UNKNOWN_CONFIG));
        assertThat(e.getMessage(), startsWith("Unsupported HttpProtocolConfig"));
    }

    @Test
    void clientWithDuplicatedH1ProtocolConfig() {
        SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder =
                HttpClients.forSingleAddress("localhost", 8080);

        Exception e = assertThrows(IllegalArgumentException.class, () ->
            builder.protocols(h1Default(), h1Default()));
        assertThat(e.getMessage(), startsWith("Duplicated configuration"));
    }

    @Test
    void clientWithDuplicatedH2ProtocolConfig() {
        SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder =
                HttpClients.forSingleAddress("localhost", 8080);

        Exception e = assertThrows(IllegalArgumentException.class, () ->
            builder.protocols(h2Default(), h2Default()));
        assertThat(e.getMessage(), startsWith("Duplicated configuration"));
    }

    @Test
    void serverWithDuplicatedH1ProtocolConfig() {
        HttpServerBuilder builder = HttpServers.forAddress(localAddress(0));

        Exception e = assertThrows(IllegalArgumentException.class, () ->
            builder.protocols(h1Default(), h1Default()));
        assertThat(e.getMessage(), startsWith("Duplicated configuration"));
    }

    @Test
    void serverWithDuplicatedH2ProtocolConfig() {
        HttpServerBuilder builder = HttpServers.forAddress(localAddress(0));

        Exception e = assertThrows(IllegalArgumentException.class, () ->
            builder.protocols(h2Default(), h2Default()));
        assertThat(e.getMessage(), startsWith("Duplicated configuration"));
    }
}
