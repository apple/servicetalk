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

import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.HttpProtocolConfig;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.transport.api.HostAndPort;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.Timeout;

import java.net.InetSocketAddress;

import static io.servicetalk.http.netty.HttpProtocolConfigs.h1Default;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2Default;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.rules.ExpectedException.none;

public class HttpProtocolConfigTest {

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

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    @Rule
    public final ExpectedException expectedException = none();

    @Test
    public void clientDoesNotSupportH2cUpgrade() {
        SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder =
                HttpClients.forSingleAddress("localhost", 8080)
                        .protocols(h2Default(), h1Default());

        expectedException.expect(IllegalStateException.class);
        expectedException.expectMessage("Cleartext HTTP/1.1 -> HTTP/2 (h2c) upgrade is not supported");
        builder.build();
    }

    @Test
    public void serverDoesNotSupportH2cUpgrade() {
        HttpServerBuilder builder = HttpServers.forAddress(localAddress(0))
                .protocols(h2Default(), h1Default());

        expectedException.expect(IllegalStateException.class);
        expectedException.expectMessage("Cleartext HTTP/1.1 -> HTTP/2 (h2c) upgrade is not supported");
        builder.listenBlocking((ctx, request, responseFactory) -> responseFactory.noContent());
    }

    @Test
    public void clientWithNullProtocolConfig() {
        SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder =
                HttpClients.forSingleAddress("localhost", 8080);

        expectedException.expect(NullPointerException.class);
        builder.protocols(null);
    }

    @Test
    public void serverWithNullProtocolConfig() {
        HttpServerBuilder builder = HttpServers.forAddress(localAddress(0));

        expectedException.expect(NullPointerException.class);
        builder.protocols(null);
    }

    @Test
    public void clientWithEmptyProtocolConfig() {
        SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder =
                HttpClients.forSingleAddress("localhost", 8080);

        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("No protocols specified");
        builder.protocols(new HttpProtocolConfig[0]);
    }

    @Test
    public void serverWithEmptyProtocolConfig() {
        HttpServerBuilder builder = HttpServers.forAddress(localAddress(0));

        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("No protocols specified");
        builder.protocols(new HttpProtocolConfig[0]);
    }

    @Test
    public void clientWithUnsupportedProtocolConfig() {
        SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder =
                HttpClients.forSingleAddress("localhost", 8080);

        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage(startsWith("Unsupported HttpProtocolConfig"));
        builder.protocols(UNKNOWN_CONFIG);
    }

    @Test
    public void serverWithUnsupportedProtocolConfig() {
        HttpServerBuilder builder = HttpServers.forAddress(localAddress(0));

        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage(startsWith("Unsupported HttpProtocolConfig"));
        builder.protocols(UNKNOWN_CONFIG);
    }

    @Test
    public void clientWithDuplicatedH1ProtocolConfig() {
        SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder =
                HttpClients.forSingleAddress("localhost", 8080);

        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage(startsWith("Duplicated configuration"));
        builder.protocols(h1Default(), h1Default());
    }

    @Test
    public void clientWithDuplicatedH2ProtocolConfig() {
        SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder =
                HttpClients.forSingleAddress("localhost", 8080);

        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage(startsWith("Duplicated configuration"));
        builder.protocols(h2Default(), h2Default());
    }

    @Test
    public void serverWithDuplicatedH1ProtocolConfig() {
        HttpServerBuilder builder = HttpServers.forAddress(localAddress(0));

        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage(startsWith("Duplicated configuration"));
        builder.protocols(h1Default(), h1Default());
    }

    @Test
    public void serverWithDuplicatedH2ProtocolConfig() {
        HttpServerBuilder builder = HttpServers.forAddress(localAddress(0));

        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage(startsWith("Duplicated configuration"));
        builder.protocols(h2Default(), h2Default());
    }
}
