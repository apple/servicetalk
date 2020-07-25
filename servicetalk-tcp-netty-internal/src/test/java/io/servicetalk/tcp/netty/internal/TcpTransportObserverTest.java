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
import io.servicetalk.transport.netty.internal.NettyConnection;

import org.junit.Test;

import java.nio.channels.ClosedChannelException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.transport.netty.internal.MockitoUtils.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class TcpTransportObserverTest extends AbstractTransportObserverTest {

    @Test
    public void testConnectionObserverEvents() throws Exception {
        NettyConnection<Buffer, Buffer> connection = client.connectBlocking(CLIENT_CTX, serverAddress);
        verify(clientTransportObserver).onNewConnection();
        verify(serverTransportObserver, await()).onNewConnection();
        verify(clientConnectionObserver).established(any(ConnectionInfo.class));
        verify(serverConnectionObserver, await()).established(any(ConnectionInfo.class));

        Buffer content = connection.executionContext().bufferAllocator().fromAscii("Hello");
        connection.write(from(content.duplicate())).toFuture().get();
        verifyWriteObserver(clientNonMultiplexedObserver, clientWriteObserver, true);
        verify(clientConnectionObserver).onDataWrite(content.readableBytes());
        verify(clientConnectionObserver).onFlush();

        AtomicReference<Buffer> response = new AtomicReference<>();
        CountDownLatch responseLatch = new CountDownLatch(1);
        connection.read().whenOnNext(buffer -> {
            response.set(buffer);
            responseLatch.countDown();
        }).ignoreElements().subscribe(); // Keep reading in background thread to prevent connection from closing
        responseLatch.await();
        assertThat("Unexpected response.", response.get(), equalTo(content));
        verify(serverConnectionObserver).onDataRead(content.readableBytes());
        verifyReadObserver(serverNonMultiplexedObserver, serverReadObserver);
        verify(serverConnectionObserver).onDataWrite(content.readableBytes());
        verify(serverConnectionObserver).onFlush();
        verifyWriteObserver(serverNonMultiplexedObserver, serverWriteObserver, false);
        verify(clientConnectionObserver).onDataRead(content.readableBytes());
        verifyReadObserver(clientNonMultiplexedObserver, clientReadObserver);

        verify(clientConnectionObserver, never()).connectionClosed();
        verify(serverConnectionObserver, never()).connectionClosed();
        connection.closeAsync().toFuture().get();
        verify(clientConnectionObserver).connectionClosed();
        verify(serverConnectionObserver, await()).connectionClosed();

        verify(clientReadObserver, await()).readFailed(any(ClosedChannelException.class));
        verify(serverReadObserver, await()).readCancelled();
        // WriteStreamSubscriber.close0(...) cancels subscription and then terminates the subscriber:
        verify(serverWriteObserver, await()).writeFailed(any(ClosedChannelException.class));
        verify(serverWriteObserver).writeCancelled();

        verifyNoMoreInteractions(clientTransportObserver, clientConnectionObserver, clientSecurityHandshakeObserver,
                clientNonMultiplexedObserver, clientReadObserver, clientWriteObserver,
                serverTransportObserver, serverConnectionObserver, serverSecurityHandshakeObserver,
                serverNonMultiplexedObserver, serverReadObserver, serverWriteObserver);
    }
}
