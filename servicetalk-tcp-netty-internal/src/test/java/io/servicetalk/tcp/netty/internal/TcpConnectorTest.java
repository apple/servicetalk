/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.client.api.RetryableConnectException;
import io.servicetalk.transport.netty.internal.NettyConnection;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.nio.channels.ClosedChannelException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static java.net.InetSocketAddress.createUnresolved;
import static java.nio.charset.Charset.defaultCharset;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.core.Is.is;

public final class TcpConnectorTest extends AbstractTcpServerTest {
    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    @Test
    public void testConnect() throws Exception {
        client.connectBlocking(CLIENT_CTX, serverPort);
    }

    @Test
    public void testWriteAndRead() throws Exception {
        testWriteAndRead(client.connectBlocking(CLIENT_CTX, serverPort));
    }

    private static void testWriteAndRead(NettyConnection<Buffer, Buffer> connection)
            throws ExecutionException, InterruptedException {
        awaitIndefinitely(connection.writeAndFlush(
                connection.executionContext().bufferAllocator().fromAscii("Hello")));
        String response = awaitIndefinitely(connection.read().first().map(buffer -> buffer.toString(defaultCharset())));
        assertThat("Unexpected response.", response, is("Hello"));
        awaitIndefinitely(connection.onClose());
    }

    @Test
    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    public void testUnresolvedAddress() throws Exception {
        testWriteAndRead(client.connectBlocking(CLIENT_CTX, createUnresolved("127.0.0.1", serverPort)));
    }

    @Test
    public void testConnectToUnknownPort() throws Exception {
        thrown.expectCause(anyOf(instanceOf(RetryableConnectException.class),
                instanceOf(ClosedChannelException.class)));
        awaitIndefinitely(serverContext.closeAsync());
        // Closing the server to increase probability of finding a port on which no one is listening.
        client.connectBlocking(CLIENT_CTX, serverPort);
    }

    @Test
    public void testConnectWithFD() throws Exception {
        testWriteAndRead(client.connectWithFdBlocking(CLIENT_CTX, serverContext.listenAddress()));
    }

    @Test
    public void testRegisteredAndActiveEventsFired() throws Exception {
        final CountDownLatch registeredLatch = new CountDownLatch(1);
        final CountDownLatch activeLatch = new CountDownLatch(1);

        TcpConnector<Buffer, Buffer> connector = new TcpConnector<>(new ReadOnlyTcpClientConfig(true),
                (channel, context) -> {
                    channel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelRegistered(ChannelHandlerContext ctx) {
                            registeredLatch.countDown();
                            ctx.fireChannelRegistered();
                        }

                        @Override
                        public void channelActive(ChannelHandlerContext ctx) {
                            activeLatch.countDown();
                            ctx.fireChannelActive();
                        }
                    });
                    return context;
                }, () -> v -> true);
        NettyConnection<Buffer, Buffer> connection = awaitIndefinitely(connector.connect(CLIENT_CTX,
                serverContext.listenAddress()));
        assert connection != null;
        awaitIndefinitely(connection.closeAsync());

        registeredLatch.await();
        activeLatch.await();
    }
}
