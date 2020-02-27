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
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.ServiceTalkSocketOptions;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketOption;
import java.net.StandardSocketOptions;

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
    private final ServerContext serverContext;
    private final BlockingHttpClient client;

    public ConnectionContextSocketOptionTest(Protocol protocol) throws Exception {
        this.protocol = protocol;
        serverContext = HttpServers.forAddress(localAddress(0))
                .protocols(protocol.config)
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
        client = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                .protocols(protocol.config)
                .buildBlocking();
    }

    @Parameters(name = "protocol={0}")
    public static Object[] data() {
        return Protocol.values();
    }

    @After
    public void tearDown() throws Exception {
        try {
            client.close();
        } finally {
            serverContext.close();
        }
    }

    @Test
    public void tcpStandardSocketOptionIsNotNull() throws Exception {
        try (BlockingHttpConnection connection = client.reserveConnection(client.get("/"))) {
            SocketOption<Boolean> socketOption = socketOption("TCP_NODELAY", Boolean.class);
            assertThat(connection.connectionContext().socketOption(socketOption), is(notNullValue()));
            HttpResponse response = connection.request(connection.get("/")
                    .setHeader(SOCKET_OPTION_NAME, socketOption.name())
                    .setHeader(SOCKET_OPTION_TYPE, socketOption.type().getName()));
            assertThat(response.status(), is(OK));
            CharSequence value = response.headers().get(SOCKET_OPTION_VALUE);
            assertThat(value, is(notNullValue()));
            // HTTP_2 stream channel does not have TCP_NODELAY option
            assertThat(value.toString(), protocol == Protocol.HTTP_2 ? equalTo("null") : not(equalTo("null")));
        }
    }

    @Test
    public void udpStandardSocketOptionIsNull() throws Exception {
        try (BlockingHttpConnection connection = client.reserveConnection(client.get("/"))) {
            SocketOption<Boolean> socketOption = socketOption("IP_MULTICAST_LOOP", Boolean.class);
            assertThat(connection.connectionContext().socketOption(socketOption), is(nullValue()));
            HttpResponse response = connection.request(connection.get("/")
                    .setHeader(SOCKET_OPTION_NAME, socketOption.name())
                    .setHeader(SOCKET_OPTION_TYPE, socketOption.type().getName()));
            assertThat(response.status(), is(OK));
            CharSequence value = response.headers().get(SOCKET_OPTION_VALUE);
            assertThat(value, is(notNullValue()));
            assertThat(value.toString(), equalTo("null"));
        }
    }

    @Test
    public void stConnectionTimeoutSocketOption() throws Exception {
        try (BlockingHttpConnection connection = client.reserveConnection(client.get("/"))) {
            SocketOption<Boolean> socketOption = socketOption("CONNECT_TIMEOUT", Integer.class);
            assertThat(connection.connectionContext().socketOption(socketOption), is(notNullValue()));
            HttpResponse response = connection.request(connection.get("/")
                    .setHeader(SOCKET_OPTION_NAME, socketOption.name())
                    .setHeader(SOCKET_OPTION_TYPE, socketOption.type().getName()));
            assertThat(response.status(), is(OK));
            CharSequence value = response.headers().get(SOCKET_OPTION_VALUE);
            assertThat(value, is(notNullValue()));
            assertThat(value.toString(), not(equalTo("null")));
        }
    }

    @Test
    public void stWriteBufferThresholdSocketOption() throws Exception {
        try (BlockingHttpConnection connection = client.reserveConnection(client.get("/"))) {
            SocketOption<Boolean> socketOption = socketOption("WRITE_BUFFER_THRESHOLD", Integer.class);
            assertThat(connection.connectionContext().socketOption(socketOption), is(notNullValue()));
            HttpResponse response = connection.request(connection.get("/")
                    .setHeader(SOCKET_OPTION_NAME, socketOption.name())
                    .setHeader(SOCKET_OPTION_TYPE, socketOption.type().getName()));
            assertThat(response.status(), is(OK));
            CharSequence value = response.headers().get(SOCKET_OPTION_VALUE);
            assertThat(value, is(notNullValue()));
            assertThat(value.toString(), not(equalTo("null")));
        }
    }

    // TODO: add stIdleTimeoutSocketOption test

    @Test
    public void stIdleTimeoutSocketOptionIsNull() throws Exception {
        try (BlockingHttpConnection connection = client.reserveConnection(client.get("/"))) {
            SocketOption<Boolean> socketOption = socketOption("IDLE_TIMEOUT", Long.class);
            assertThat(connection.connectionContext().socketOption(socketOption), is(nullValue()));
            HttpResponse response = connection.request(connection.get("/")
                    .setHeader(SOCKET_OPTION_NAME, socketOption.name())
                    .setHeader(SOCKET_OPTION_TYPE, socketOption.type().getName()));
            assertThat(response.status(), is(OK));
            CharSequence value = response.headers().get(SOCKET_OPTION_VALUE);
            assertThat(value, is(notNullValue()));
            assertThat(value.toString(), equalTo("null"));
        }
    }

    @Test
    public void customSupportedSocketOption() throws Exception {
        try (BlockingHttpConnection connection = client.reserveConnection(client.get("/"))) {
            SocketOption<Boolean> socketOption = socketOption("AUTO_READ", Boolean.class);
            assertThat(connection.connectionContext().socketOption(socketOption), is(protocol.autoRead));
            HttpResponse response = connection.request(connection.get("/")
                    .setHeader(SOCKET_OPTION_NAME, socketOption.name())
                    .setHeader(SOCKET_OPTION_TYPE, socketOption.type().getName()));
            assertThat(response.status(), is(OK));
            CharSequence value = response.headers().get(SOCKET_OPTION_VALUE);
            assertThat(value, is(notNullValue()));
            assertThat(value.toString(), equalTo(valueOf(protocol.autoRead)));
        }
    }

    @Test
    public void unsupportedSocketOptionIsNull() throws Exception {
        try (BlockingHttpConnection connection = client.reserveConnection(client.get("/"))) {
            SocketOption<Boolean> socketOption = socketOption("UNSUPPORTED", Boolean.class);
            assertThat(connection.connectionContext().socketOption(socketOption), is(nullValue()));
            HttpResponse response = connection.request(connection.get("/")
                    .setHeader(SOCKET_OPTION_NAME, socketOption.name())
                    .setHeader(SOCKET_OPTION_TYPE, socketOption.type().getName()));
            assertThat(response.status(), is(OK));
            CharSequence value = response.headers().get(SOCKET_OPTION_VALUE);
            assertThat(value, is(notNullValue()));
            assertThat(value.toString(), equalTo("null"));
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> SocketOption<T> socketOption(String name, Class<?> type) {
        switch (name) {
            case "TCP_NODELAY":
                return (SocketOption<T>) StandardSocketOptions.TCP_NODELAY;
            case "IP_MULTICAST_LOOP":
                return (SocketOption<T>) StandardSocketOptions.IP_MULTICAST_LOOP;
            case "CONNECT_TIMEOUT":
                return (SocketOption<T>) ServiceTalkSocketOptions.CONNECT_TIMEOUT;
            case "WRITE_BUFFER_THRESHOLD":
                return (SocketOption<T>) ServiceTalkSocketOptions.WRITE_BUFFER_THRESHOLD;
            case "IDLE_TIMEOUT":
                return (SocketOption<T>) ServiceTalkSocketOptions.IDLE_TIMEOUT;
            case "AUTO_READ":
                return new SocketOption<T>() {
                    @Override
                    public String name() {
                        return "AUTO_READ";
                    }

                    @Override
                    public Class<T> type() {
                        return (Class<T>) type;
                    }
                };
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
    }
}
