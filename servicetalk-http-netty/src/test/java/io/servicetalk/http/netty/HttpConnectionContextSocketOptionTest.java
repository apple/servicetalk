/*
 * Copyright © 2020-2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.BlockingHttpConnection;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.ServiceTalkSocketOptions;

import org.hamcrest.Matcher;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.lang.String.valueOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpConnectionContextSocketOptionTest {

    @ParameterizedTest(name = "protocol={0}")
    @EnumSource(HttpProtocol.class)
    void tcpStandardSocketOptionIsNotNull(HttpProtocol protocol) throws Exception {
        testSocketOption(StandardSocketOptions.TCP_NODELAY, is(notNullValue()), not(equalTo("null")), protocol);
    }

    @ParameterizedTest(name = "protocol={0}")
    @EnumSource(HttpProtocol.class)
    void udpStandardSocketOptionIsNull(HttpProtocol protocol) throws Exception {
        testSocketOption(StandardSocketOptions.IP_MULTICAST_LOOP, is(nullValue()), equalTo("null"), protocol);
    }

    @ParameterizedTest(name = "protocol={0}")
    @EnumSource(HttpProtocol.class)
    void stConnectionTimeoutSocketOption(HttpProtocol protocol) throws Exception {
        testSocketOption(ServiceTalkSocketOptions.CONNECT_TIMEOUT, is(notNullValue()), not(equalTo("null")), protocol);
    }

    @ParameterizedTest(name = "protocol={0}")
    @EnumSource(HttpProtocol.class)
    void stWriteBufferThresholdSocketOption(HttpProtocol protocol) throws Exception {
        testSocketOption(ServiceTalkSocketOptions.WRITE_BUFFER_THRESHOLD, is(notNullValue()),
                not(equalTo("null")), protocol);
    }

    @ParameterizedTest(name = "protocol={0}")
    @EnumSource(HttpProtocol.class)
    void stIdleTimeoutSocketOption(HttpProtocol protocol) throws Exception {
        testSocketOption(ServiceTalkSocketOptions.IDLE_TIMEOUT, is(30000L), equalTo("30000"), 30000L, protocol);
    }

    @ParameterizedTest(name = "protocol={0}")
    @EnumSource(HttpProtocol.class)
    void stIdleTimeoutSocketOptionIsNull(HttpProtocol protocol) throws Exception {
        testSocketOption(ServiceTalkSocketOptions.IDLE_TIMEOUT, is(nullValue()), equalTo("null"), null, protocol);
    }

    private <T> void testSocketOption(SocketOption<T> socketOption, Matcher<Object> clientMatcher,
                                      Matcher<Object> serverMatcher, HttpProtocol protocol) throws Exception {
        testSocketOption(socketOption, clientMatcher, serverMatcher, null, protocol);
    }

    private <T> void testSocketOption(SocketOption<T> socketOption, Matcher<Object> clientMatcher,
                                      Matcher<Object> serverMatcher, @Nullable Long idleTimeoutMs,
                                      HttpProtocol protocol)
        throws Exception {
        try (ServerContext serverContext = startServer(idleTimeoutMs, socketOption, protocol);
             BlockingHttpClient client = newClient(serverContext, idleTimeoutMs, protocol);
             BlockingHttpConnection connection = client.reserveConnection(client.get("/"))) {

            assertThat("Client-side connection SocketOption does not match expected value",
                    connection.connectionContext().socketOption(socketOption), clientMatcher);
            assertThat("Server-side connection SocketOption does not match expected value",
                    connection.request(connection.get("/")).payloadBody(textSerializerUtf8()), serverMatcher);
        }
    }

    private <T> ServerContext startServer(@Nullable Long idleTimeoutMs, SocketOption<T> socketOption,
                                          HttpProtocol protocol) throws Exception {
        final HttpServerBuilder builder = HttpServers.forAddress(localAddress(0))
                .protocols(protocol.config);
        if (idleTimeoutMs != null) {
            builder.socketOption(ServiceTalkSocketOptions.IDLE_TIMEOUT, idleTimeoutMs);
        }
        return builder.listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok()
                .payloadBody(valueOf(ctx.socketOption(socketOption)), textSerializerUtf8()));
    }

    private BlockingHttpClient newClient(ServerContext serverContext, @Nullable Long idleTimeoutMs,
                                         HttpProtocol protocol) {
        SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder =
                HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                        .protocols(protocol.config);
        if (idleTimeoutMs != null) {
            builder.socketOption(ServiceTalkSocketOptions.IDLE_TIMEOUT, idleTimeoutMs);
        }
        return builder.buildBlocking();
    }

    @ParameterizedTest(name = "protocol={0}")
    @EnumSource(HttpProtocol.class)
    void unsupportedSocketOptionThrows(HttpProtocol protocol) throws Exception {
        final SocketOption<Boolean> unsupported = new CustomSocketOption<>("UNSUPPORTED", Boolean.class);
        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .protocols(protocol.config)
                .listenBlockingAndAwait((ctx, request, responseFactory) -> {
                    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                            () -> ctx.socketOption(unsupported));
                    return responseFactory.ok().payloadBody(ex.getMessage(), textSerializerUtf8());
                });
             BlockingHttpClient client = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                     .protocols(protocol.config).buildBlocking();
             BlockingHttpConnection connection = client.reserveConnection(client.get("/"))) {

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> connection.connectionContext().socketOption(unsupported));
            assertThat(ex.getMessage(), endsWith("not supported"));
            assertThat(ex.getMessage(),
                    equalTo(connection.request(connection.get("/")).payloadBody(textSerializerUtf8())));
        }
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
