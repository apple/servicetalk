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
package io.servicetalk.tcp.netty.internal;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.transport.api.ConnectionInfo;
import io.servicetalk.transport.api.SslProvider;
import io.servicetalk.transport.netty.internal.NettyConnection;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.transport.api.SslProvider.JDK;
import static io.servicetalk.transport.api.SslProvider.OPENSSL;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class SecureTcpTransportObserverTest extends AbstractTransportObserverTest {

    private final CountDownLatch serverConnectionClosed = new CountDownLatch(1);

    private SslProvider clientProvider;
    private SslProvider serverProvider;

    private void setUp(SslProvider clientProvider, SslProvider serverProvider) throws Exception {
        this.clientProvider = clientProvider;
        this.serverProvider = serverProvider;

        connectionAcceptor(ctx -> {
            ctx.onClose().whenFinally(serverConnectionClosed::countDown).subscribe();
            return completed();
        });
        setUp();
    }

    static Stream<Arguments> data() {
        return Stream.of(
                Arguments.of(JDK, JDK),
                Arguments.of(JDK, OPENSSL),
                Arguments.of(OPENSSL, JDK),
                Arguments.of(OPENSSL, OPENSSL)
        );
    }

    @ParameterizedTest(name = "clientProvider={0}, serverProvider={1}")
    @MethodSource("data")
    void testConnectionObserverEvents(SslProvider clientProvider, SslProvider serverProvider) throws Exception {
        setUp(clientProvider, serverProvider);
        NettyConnection<Buffer, Buffer> connection = client.connectBlocking(CLIENT_CTX, serverAddress);
        verify(clientTransportObserver).onNewConnection();
        verify(serverTransportObserver, await()).onNewConnection();

        verify(clientConnectionObserver).connectionEstablished(any(ConnectionInfo.class));
        verify(serverConnectionObserver, await()).connectionEstablished(any(ConnectionInfo.class));

        // handshake starts
        verify(clientConnectionObserver).onSecurityHandshake();
        verify(serverConnectionObserver).onSecurityHandshake();
        // handshake progress
        verify(clientConnectionObserver, atLeastOnce()).onDataRead(anyInt());
        verify(clientConnectionObserver, atLeastOnce()).onDataWrite(anyInt());
        verify(clientConnectionObserver, atLeastOnce()).onFlush();
        verify(serverConnectionObserver, atLeastOnce()).onDataRead(anyInt());
        verify(serverConnectionObserver, atLeastOnce()).onDataWrite(anyInt());
        verify(serverConnectionObserver, atLeastOnce()).onFlush();
        // handshake completes
        verify(clientSecurityHandshakeObserver).handshakeComplete(any());
        verify(serverSecurityHandshakeObserver).handshakeComplete(any());

        Buffer content = connection.executionContext().bufferAllocator().fromAscii("Hello");
        connection.write(from(content.duplicate())).toFuture().get();
        verify(clientConnectionObserver, atLeastOnce()).onDataWrite(anyInt());
        verify(clientConnectionObserver, atLeastOnce()).onFlush();

        AtomicReference<Buffer> response = new AtomicReference<>();
        CountDownLatch responseLatch = new CountDownLatch(1);
        connection.read().whenOnNext(buffer -> {
            response.set(buffer);
            responseLatch.countDown();
        }).ignoreElements().subscribe(); // Keep reading in background thread to prevent connection from closing
        responseLatch.await();
        assertThat("Unexpected response.", response.get(), equalTo(content));
        verify(serverConnectionObserver, atLeastOnce()).onDataRead(anyInt());
        verify(serverConnectionObserver, atLeastOnce()).onDataWrite(anyInt());
        verify(serverConnectionObserver, atLeastOnce()).onFlush();
        verify(clientConnectionObserver, atLeastOnce()).onDataRead(anyInt());

        verify(clientConnectionObserver, never()).connectionClosed();
        verify(serverConnectionObserver, never()).connectionClosed();
        connection.closeAsync().toFuture().get();
        // client sends close_notify
        verify(clientConnectionObserver, atLeastOnce()).onDataWrite(anyInt());
        verify(clientConnectionObserver, atLeastOnce()).onFlush();
        verify(clientConnectionObserver).connectionClosed();

        serverConnectionClosed.await();
        // server receives close_notify and replies the same
        verify(serverConnectionObserver, atLeastOnce()).onDataRead(anyInt());
        verify(serverConnectionObserver, atLeastOnce()).onDataWrite(anyInt());
        verify(serverConnectionObserver, atLeastOnce()).onFlush();
        verify(serverConnectionObserver).connectionClosed();

        verifyNoMoreInteractions(clientTransportObserver, clientConnectionObserver, clientSecurityHandshakeObserver,
                serverTransportObserver, serverConnectionObserver, serverSecurityHandshakeObserver);
    }

    @Override
    TcpClientConfig getTcpClientConfig() {
        final TcpClientConfig config = super.getTcpClientConfig();
        config.sslConfig(defaultClientSslConfig(clientProvider));
        return config;
    }

    @Override
    TcpServerConfig getTcpServerConfig() {
        final TcpServerConfig config = super.getTcpServerConfig();
        config.sslConfig(defaultServerSslConfig(serverProvider));
        return config;
    }
}
