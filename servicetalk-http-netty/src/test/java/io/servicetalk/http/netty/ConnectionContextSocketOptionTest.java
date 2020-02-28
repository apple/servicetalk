/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.BlockingHttpConnection;
import io.servicetalk.http.api.HttpProtocolConfig;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.ServiceTalkSocketOptions;

import org.hamcrest.Matcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpSerializationProviders.textDeserializer;
import static io.servicetalk.http.api.HttpSerializationProviders.textSerializer;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h1Default;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2Default;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.lang.String.valueOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@RunWith(Parameterized.class)
public class ConnectionContextSocketOptionTest {

    private enum Protocol {

        HTTP_1_1(h1Default(), false),
        HTTP_2(h2Default(), true);

        final HttpProtocolConfig config;
        final boolean autoRead;

        Protocol(HttpProtocolConfig config, boolean autoRead) {
            this.config = config;
            this.autoRead = autoRead;
        }
    }

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private final Protocol protocol;

    public ConnectionContextSocketOptionTest(Protocol protocol) {
        this.protocol = protocol;
    }

    @Parameters(name = "protocol={0}")
    public static Object[] data() {
        return Protocol.values();
    }

    @Test
    public void tcpStandardSocketOptionIsNotNull() throws Exception {
        testSocketOption(StandardSocketOptions.TCP_NODELAY, is(notNullValue()),
                // HTTP_2 stream channel does not have TCP_NODELAY option
                protocol == Protocol.HTTP_2 ? equalTo("null") : not(equalTo("null")));
    }

    @Test
    public void udpStandardSocketOptionIsNull() throws Exception {
        testSocketOption(StandardSocketOptions.IP_MULTICAST_LOOP, is(nullValue()), equalTo("null"));
    }

    @Test
    public void stConnectionTimeoutSocketOption() throws Exception {
        testSocketOption(ServiceTalkSocketOptions.CONNECT_TIMEOUT, is(notNullValue()), not(equalTo("null")));
    }

    @Test
    public void stWriteBufferThresholdSocketOption() throws Exception {
        testSocketOption(ServiceTalkSocketOptions.WRITE_BUFFER_THRESHOLD, is(notNullValue()), not(equalTo("null")));
    }

    @Test
    public void stIdleTimeoutSocketOption() throws Exception {
        testSocketOption(ServiceTalkSocketOptions.IDLE_TIMEOUT, is(30000L), equalTo("30000"), 30000L);
    }

    @Test
    public void stIdleTimeoutSocketOptionIsNull() throws Exception {
        testSocketOption(ServiceTalkSocketOptions.IDLE_TIMEOUT, is(nullValue()), equalTo("null"), null);
    }

    @Test
    public void customSupportedSocketOption() throws Exception {
        testSocketOption(new CustomSocketOption<>("AUTO_READ", Boolean.class),
                is(protocol.autoRead), equalTo(valueOf(protocol.autoRead)));
    }

    @Test
    public void supportedSocketOptionWithIncorrectTypeIsNull() throws Exception {
        // Intentionally use Integer.class for AUTO_CLOSE option that requires Boolean.class type
        testSocketOption(new CustomSocketOption<>("AUTO_CLOSE", Integer.class), is(nullValue()), equalTo("null"));
    }

    @Test
    public void unsupportedSocketOptionIsNull() throws Exception {
        testSocketOption(new CustomSocketOption<>("UNSUPPORTED", Boolean.class), is(nullValue()), equalTo("null"));
    }

    private <T> void testSocketOption(SocketOption<T> socketOption, Matcher<Object> clientMatcher,
                                      Matcher<Object> serverMatcher) throws Exception {
        testSocketOption(socketOption, clientMatcher, serverMatcher, null);
    }

    private <T> void testSocketOption(SocketOption<T> socketOption, Matcher<Object> clientMatcher,
                                      Matcher<Object> serverMatcher, @Nullable Long idleTimeoutMs) throws Exception {
        try (ServerContext serverContext = startServer(idleTimeoutMs, socketOption);
             BlockingHttpClient client = newClient(serverContext, idleTimeoutMs);
             BlockingHttpConnection connection = client.reserveConnection(client.get("/"))) {

            assertThat(connection.connectionContext().socketOption(socketOption), clientMatcher);
            HttpResponse response = connection.request(connection.get("/"));
            assertThat(response.status(), is(OK));
            CharSequence value = response.payloadBody(textDeserializer());
            assertThat(value, is(notNullValue()));
            assertThat(value.toString(), serverMatcher);
        }
    }

    private <T> ServerContext startServer(@Nullable Long idleTimeoutMs, SocketOption<T> socketOption) throws Exception {
        final HttpServerBuilder builder = HttpServers.forAddress(localAddress(0))
                .protocols(protocol.config);
        if (idleTimeoutMs != null) {
            builder.socketOption(ServiceTalkSocketOptions.IDLE_TIMEOUT, idleTimeoutMs);
        }
        return builder.listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok()
                .payloadBody(valueOf(ctx.socketOption(socketOption)), textSerializer()));
    }

    private BlockingHttpClient newClient(ServerContext serverContext, @Nullable Long idleTimeoutMs) {
        SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder =
                HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                        .protocols(protocol.config);
        if (idleTimeoutMs != null) {
            builder.socketOption(ServiceTalkSocketOptions.IDLE_TIMEOUT, idleTimeoutMs);
        }
        return builder
                .buildBlocking();
    }

    private static final class CustomSocketOption<T> implements SocketOption<T> {
        private final String name;
        private final Class<T> type;

        CustomSocketOption(String name, Class<T> type) {
            this.name = name;
            this.type = type;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Class<T> type() {
            return type;
        }
    }
}
