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
import io.servicetalk.transport.api.SecurityConfigurator.SslProvider;
import io.servicetalk.transport.netty.internal.NettyConnection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.transport.api.SecurityConfigurator.SslProvider.JDK;
import static io.servicetalk.transport.api.SecurityConfigurator.SslProvider.OPENSSL;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@RunWith(Parameterized.class)
public class SecureTcpTransportObserverTest extends AbstractTransportObserverTest {

    private final CountDownLatch serverConnectionClosed = new CountDownLatch(1);

    private final SslProvider clientProvider;
    private final SslProvider serverProvider;

    public SecureTcpTransportObserverTest(SslProvider clientProvider, SslProvider serverProvider) {
        this.clientProvider = clientProvider;
        this.serverProvider = serverProvider;

        connectionAcceptor(ctx -> {
            ctx.onClose().whenFinally(serverConnectionClosed::countDown).subscribe();
            return completed();
        });
    }

    @Parameters(name = "clientProvider={0}, serverProvider={1}")
    public static Collection<Object[]> data() {
        return asList(
                new Object[] {JDK, JDK},
                new Object[] {JDK, OPENSSL},
                new Object[] {OPENSSL, JDK},
                new Object[] {OPENSSL, OPENSSL}
        );
    }

    @Test
    public void testConnectionObserverEvents() throws Exception {
        NettyConnection<Buffer, Buffer> connection = client.connectBlocking(CLIENT_CTX, serverAddress);
        verify(clientTransportObserver).onNewConnection();
        verify(serverTransportObserver, await()).onNewConnection();

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
        config.secure(defaultClientSecurityConfig(clientProvider));
        return config;
    }

    @Override
    TcpServerConfig getTcpServerConfig() {
        final TcpServerConfig config = super.getTcpServerConfig();
        config.secure(defaultServerSecurityConfig(serverProvider));
        return config;
    }
}
