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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.HttpResponseStatus.OK;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectionContextSocketOptionTest.class);

    private static final String SOCKET_OPTION_NAME = "socket-option-name";
    private static final String SOCKET_OPTION_TYPE = "socket-option-type";
    private static final String SOCKET_OPTION_VALUE = "socket-option-value";

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
        testSocketOption("TCP_NODELAY", Boolean.class, is(notNullValue()),
                // HTTP_2 stream channel does not have TCP_NODELAY option
                protocol == Protocol.HTTP_2 ? equalTo("null") : not(equalTo("null")));
    }

    @Test
    public void udpStandardSocketOptionIsNull() throws Exception {
        testSocketOption("IP_MULTICAST_LOOP", Boolean.class, is(nullValue()), equalTo("null"));
    }

    @Test
    public void stConnectionTimeoutSocketOption() throws Exception {
        testSocketOption("CONNECT_TIMEOUT", Integer.class, is(notNullValue()), not(equalTo("null")));
    }

    @Test
    public void stWriteBufferThresholdSocketOption() throws Exception {
        testSocketOption("WRITE_BUFFER_THRESHOLD", Integer.class, is(notNullValue()), not(equalTo("null")));
    }

    @Test
    public void stIdleTimeoutSocketOption() throws Exception {
        testSocketOption("IDLE_TIMEOUT", Long.class, is(30000L), equalTo("30000"), 30000L);
    }

    @Test
    public void stIdleTimeoutSocketOptionIsNull() throws Exception {
        testSocketOption("IDLE_TIMEOUT", Long.class, is(nullValue()), equalTo("null"));
    }

    @Test
    public void customSupportedSocketOption() throws Exception {
        testSocketOption("AUTO_READ", Boolean.class, is(protocol.autoRead), equalTo(valueOf(protocol.autoRead)));
    }

    @Test
    public void unsupportedSocketOptionIsNull() throws Exception {
        testSocketOption("UNSUPPORTED", Boolean.class, is(nullValue()), equalTo("null"));
    }

    private <T> void testSocketOption(String name, Class<T> type, Matcher<Object> clientMatcher,
                                      Matcher<Object> serverMatcher) throws Exception {
        testSocketOption(name, type, clientMatcher, serverMatcher, null);
    }

    private <T> void testSocketOption(String name, Class<T> type, Matcher<Object> clientMatcher,
                                      Matcher<Object> serverMatcher, @Nullable Long idleTimeoutMs) throws Exception {
        try (ServerContext serverContext = startServer(idleTimeoutMs);
             BlockingHttpClient client = newClient(serverContext, idleTimeoutMs);
             BlockingHttpConnection connection = client.reserveConnection(client.get("/"))) {

            SocketOption<T> socketOption = socketOption(name, type);
            assertThat(connection.connectionContext().socketOption(socketOption), clientMatcher);
            HttpResponse response = connection.request(connection.get("/")
                    .setHeader(SOCKET_OPTION_NAME, socketOption.name())
                    .setHeader(SOCKET_OPTION_TYPE, socketOption.type().getName()));
            assertThat(response.status(), is(OK));
            CharSequence value = response.headers().get(SOCKET_OPTION_VALUE);
            assertThat(value, is(notNullValue()));
            assertThat(value.toString(), serverMatcher);
        }
    }

    private ServerContext startServer(@Nullable Long idleTimeoutMs) throws Exception {
        final HttpServerBuilder builder = HttpServers.forAddress(localAddress(0))
                .protocols(protocol.config);
        if (idleTimeoutMs != null) {
            builder.socketOption(ServiceTalkSocketOptions.IDLE_TIMEOUT, idleTimeoutMs);
        }
        return builder
                .listenBlockingAndAwait((ctx, request, responseFactory) -> {
                    try {
                        String name = request.headers().get(SOCKET_OPTION_NAME).toString();
                        Class<?> type = Class.forName(request.headers().get(SOCKET_OPTION_TYPE).toString());
                        SocketOption<?> socketOption = socketOption(name, type);
                        return responseFactory.ok()
                                .setHeader(SOCKET_OPTION_VALUE, valueOf(ctx.socketOption(socketOption)));
                    } catch (Exception e) {
                        LOGGER.error("Unexpected exception during request processing", e);
                        return responseFactory.badRequest();
                    }
                });
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

    @SuppressWarnings("unchecked")
    private static <T> SocketOption<T> socketOption(String name, Class<?> type) {
        SocketOption<?> socketOption;
        switch (name) {
            case "TCP_NODELAY":
                socketOption = StandardSocketOptions.TCP_NODELAY;
                break;
            case "IP_MULTICAST_LOOP":
                socketOption = StandardSocketOptions.IP_MULTICAST_LOOP;
                break;
            case "CONNECT_TIMEOUT":
                socketOption = ServiceTalkSocketOptions.CONNECT_TIMEOUT;
                break;
            case "WRITE_BUFFER_THRESHOLD":
                socketOption = ServiceTalkSocketOptions.WRITE_BUFFER_THRESHOLD;
                break;
            case "IDLE_TIMEOUT":
                socketOption = ServiceTalkSocketOptions.IDLE_TIMEOUT;
                break;
            case "AUTO_READ":
                socketOption = new SocketOption<T>() {
                    @Override
                    public String name() {
                        return "AUTO_READ";
                    }

                    @Override
                    public Class<T> type() {
                        return (Class<T>) Boolean.class;
                    }
                };
                break;
            case "UNSUPPORTED":
                return new SocketOption<T>() {
                    @Override
                    public String name() {
                        return "UNSUPPORTED";
                    }

                    @Override
                    public Class<T> type() {
                        return (Class<T>) type;
                    }
                };
            default:
                throw new IllegalArgumentException("Unknown SocketOption name: " + name);
        }
        if (!socketOption.type().equals(type)) {
            throw new IllegalArgumentException("Incorrect type for SocketOption(" + name + "): " + type.getName() +
                    ", expected: " + socketOption.type().getName());
        }
        return (SocketOption<T>) socketOption;
    }
}
