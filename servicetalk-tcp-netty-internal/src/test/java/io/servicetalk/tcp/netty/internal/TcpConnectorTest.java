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
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.transport.api.ConnectionInfo.Protocol;
import io.servicetalk.transport.netty.internal.DefaultNettyConnection;
import io.servicetalk.transport.netty.internal.NettyConnection;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

import static io.servicetalk.transport.netty.internal.CloseHandler.UNSUPPORTED_PROTOCOL_CLOSE_HANDLER;
import static io.servicetalk.transport.netty.internal.FlushStrategies.defaultFlushStrategy;
import static java.nio.charset.Charset.defaultCharset;
import static java.util.Collections.emptyList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.mock;

public final class TcpConnectorTest extends AbstractTcpServerTest {
    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    @Test
    public void testConnect() throws Exception {
        client.connectBlocking(CLIENT_CTX, serverAddress);
    }

    @Test
    public void testWriteAndRead() throws Exception {
        testWriteAndRead(client.connectBlocking(CLIENT_CTX, serverAddress));
    }

    private static void testWriteAndRead(NettyConnection<Buffer, Buffer> connection)
            throws ExecutionException, InterruptedException {
        connection.write(
                Publisher.from(connection.executionContext().bufferAllocator().fromAscii("Hello"))).toFuture().get();
        String response = connection.read().firstOrElse(() -> null).map(buffer -> buffer.toString(defaultCharset()))
                .toFuture().get();
        assertThat("Unexpected response.", response, is("Hello"));
        connection.onClose().toFuture().get();
    }

    @Test
    public void testResolvedAddress() throws Exception {
        testWriteAndRead(client.connectBlocking(CLIENT_CTX,
                new InetSocketAddress(serverAddress.getHostString(), serverAddress.getPort())));
    }

    @Test
    public void testConnectToUnknownPort() throws Exception {
        thrown.expectCause(anyOf(instanceOf(RetryableConnectException.class),
                instanceOf(ClosedChannelException.class)));
        serverContext.closeAsync().toFuture().get();
        // Closing the server to increase probability of finding a port on which no one is listening.
        client.connectBlocking(CLIENT_CTX, serverAddress);
    }

    @Test
    public void testConnectWithFD() throws Exception {
        testWriteAndRead(client.connectWithFdBlocking(CLIENT_CTX, serverContext.listenAddress()));
    }

    @Test
    public void testRegisteredAndActiveEventsFired() throws Exception {
        final CountDownLatch registeredLatch = new CountDownLatch(1);
        final CountDownLatch activeLatch = new CountDownLatch(1);

        NettyConnection<Buffer, Buffer> connection = TcpConnector.<NettyConnection<Buffer, Buffer>>connect(null,
                serverContext.listenAddress(), new TcpClientConfig().asReadOnly(emptyList()), false,
                CLIENT_CTX, channel -> DefaultNettyConnection.initChannel(channel,
                        CLIENT_CTX.bufferAllocator(), CLIENT_CTX.executor(), o -> true,
                        UNSUPPORTED_PROTOCOL_CLOSE_HANDLER, defaultFlushStrategy(), null, channel2 -> {
                            channel2.pipeline().addLast(new ChannelInboundHandlerAdapter() {
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
                        }, CLIENT_CTX.executionStrategy(), mock(Protocol.class))).toFuture().get();
        connection.closeAsync().toFuture().get();

        registeredLatch.await();
        activeLatch.await();
    }
}
