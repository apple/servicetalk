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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.servicetalk.buffer.Buffer;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.transport.api.IoExecutorGroup;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.NettyIoExecutors;
import io.servicetalk.transport.netty.internal.Connection;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.Timeout;

import java.net.ConnectException;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.transport.api.FlushStrategy.defaultFlushStrategy;
import static java.net.InetSocketAddress.createUnresolved;
import static java.nio.charset.Charset.defaultCharset;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.core.Is.is;

public final class TcpConnectorTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    private static IoExecutorGroup group;

    private int serverPort;
    private ServerContext serverContext;
    private TcpClient client;

    @BeforeClass
    public static void beforeClass() {
        group = NettyIoExecutors.createGroup();
    }

    @AfterClass
    public static void afterClass() {
        group.closeAsync();
    }

    @Before
    public void setUp() throws Exception {
        TcpServer server = new TcpServer(group);
        serverContext = server.start(0, conn -> conn.write(conn.read(), defaultFlushStrategy()));
        serverPort = TcpServer.getServerPort(serverContext);
        client = new TcpClient(group);
    }

    @After
    public void tearDown() throws Exception {
        awaitIndefinitely(serverContext.closeAsync());
    }

    @Test
    public void testConnect() throws Exception {
        client.connectBlocking(serverPort);
    }

    @Test
    public void testWriteAndRead() throws Exception {
        testWriteAndRead(client.connectBlocking(serverPort));
    }

    private static void testWriteAndRead(Connection<Buffer, Buffer> connection) throws ExecutionException, InterruptedException {
        awaitIndefinitely(connection.writeAndFlush(connection.getAllocator().fromAscii("Hello")));
        String response = awaitIndefinitely(connection.read().first().map(buffer -> buffer.toString(defaultCharset())));
        assertThat("Unexpected response.", response, is("Hello"));
        awaitIndefinitely(connection.onClose());
    }

    @Test
    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    public void testUnresolvedAddress() throws Exception {
        testWriteAndRead(client.connectBlocking(createUnresolved("127.0.0.1", serverPort)));
    }

    @Test
    public void testConnectToUnknownPort() throws Exception {
        thrown.expectCause(anyOf(instanceOf(ConnectException.class), instanceOf(ClosedChannelException.class)));
        awaitIndefinitely(serverContext.closeAsync());
        // Closing the server to increase probability of finding a port on which no one is listening.
        client.connectBlocking(serverPort);
    }

    @Test
    public void testConnectWithFD() throws Exception {
        testWriteAndRead(client.connectWithFdBlocking(serverContext.getListenAddress()));
    }

    @Test
    public void testRegisteredAndActiveEventsFired() throws Exception {
        final CountDownLatch registeredLatch = new CountDownLatch(1);
        final CountDownLatch activeLatch = new CountDownLatch(1);

        TcpConnector<Buffer, Buffer> connector = new TcpConnector<>(new ReadOnlyTcpClientConfig(true), (channel, context) -> {
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
        Connection<Buffer, Buffer> connection = awaitIndefinitely(connector.connect(group, serverContext.getListenAddress()));
        assert connection != null;
        awaitIndefinitely(connection.closeAsync());

        registeredLatch.await();
        activeLatch.await();
    }
}
