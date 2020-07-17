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
import io.servicetalk.transport.netty.internal.NettyConnection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static io.servicetalk.concurrent.api.Publisher.from;
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
public class TcpTransportObserverTest extends AbstractTransportObserverTest {

    public TcpTransportObserverTest(boolean secure) {
        super(secure);
    }

    @Parameters(name = "secure={0}")
    public static Collection<Boolean> data() {
        return asList(false, true);
    }

    @Test
    public void testConnectionObserverEvents() throws Exception {
        NettyConnection<Buffer, Buffer> connection = client.connectBlocking(CLIENT_CTX, serverAddress);
        verify(clientTransportObserver).onNewConnection();
        serverConnectionReceived.await();
        verify(serverTransportObserver).onNewConnection();

        if (secure) {
            verify(clientConnectionObserver).onSecurityHandshake();
            verify(serverConnectionObserver).onSecurityHandshake();

            verify(clientConnectionObserver, atLeastOnce()).onDataRead(anyInt());
            verify(clientConnectionObserver, atLeastOnce()).onDataWrite(anyInt());
            verify(clientConnectionObserver, atLeastOnce()).onFlush();
            verify(serverConnectionObserver, atLeastOnce()).onDataRead(anyInt());
            verify(serverConnectionObserver, atLeastOnce()).onDataWrite(anyInt());
            verify(serverConnectionObserver, atLeastOnce()).onFlush();

            verify(clientSecurityHandshakeObserver).handshakeComplete(any());
            verify(serverSecurityHandshakeObserver).handshakeComplete(any());
        }

        Buffer content = connection.executionContext().bufferAllocator().fromAscii("Hello");
        connection.write(from(content.duplicate())).toFuture().get();
        if (secure) {
            verify(clientConnectionObserver, atLeastOnce()).onDataWrite(anyInt());
            verify(clientConnectionObserver, atLeastOnce()).onFlush();
        } else {
            verify(clientConnectionObserver).onDataWrite(content.readableBytes());
            verify(clientConnectionObserver).onFlush();
        }

        AtomicReference<Buffer> response = new AtomicReference<>();
        CountDownLatch responseLatch = new CountDownLatch(1);
        connection.read().whenOnNext(buffer -> {
            response.set(buffer);
            responseLatch.countDown();
        }).ignoreElements().subscribe(); // Keep reading in background thread to prevent connection from closing
        responseLatch.await();
        assertThat("Unexpected response.", response.get(), equalTo(content));
        if (secure) {
            verify(serverConnectionObserver, atLeastOnce()).onDataRead(anyInt());
            verify(serverConnectionObserver, atLeastOnce()).onDataWrite(anyInt());
            verify(serverConnectionObserver, atLeastOnce()).onFlush();
            verify(clientConnectionObserver, atLeastOnce()).onDataRead(anyInt());
        } else {
            verify(serverConnectionObserver).onDataRead(content.readableBytes());
            verify(serverConnectionObserver).onDataWrite(content.readableBytes());
            verify(serverConnectionObserver).onFlush();
            verify(clientConnectionObserver).onDataRead(content.readableBytes());
        }

        verify(clientConnectionObserver, never()).connectionClosed();
        verify(serverConnectionObserver, never()).connectionClosed();
        connection.closeAsync().toFuture().get();
        if (secure) {
            // client sends close_notify
            verify(clientConnectionObserver, atLeastOnce()).onDataWrite(anyInt());
            verify(clientConnectionObserver, atLeastOnce()).onFlush();
        }
        verify(clientConnectionObserver).connectionClosed();
        serverConnectionClosed.await();
        if (secure) {
            // server receives close_notify and replies the same
            verify(serverConnectionObserver, atLeastOnce()).onDataRead(anyInt());
            verify(serverConnectionObserver, atLeastOnce()).onDataWrite(anyInt());
            verify(serverConnectionObserver, atLeastOnce()).onFlush();
        }
        verify(serverConnectionObserver).connectionClosed();
        verifyNoMoreInteractions(clientTransportObserver, clientConnectionObserver, clientSecurityHandshakeObserver,
                serverTransportObserver, serverConnectionObserver, serverSecurityHandshakeObserver);
    }
}
