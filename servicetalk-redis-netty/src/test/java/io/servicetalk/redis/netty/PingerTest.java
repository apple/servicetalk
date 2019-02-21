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
package io.servicetalk.redis.netty;

import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.redis.api.PubSubRedisConnection;
import io.servicetalk.redis.api.PubSubRedisMessage;
import io.servicetalk.redis.api.RedisConnection;
import io.servicetalk.redis.api.RedisData;
import io.servicetalk.redis.api.RedisProtocolSupport;
import io.servicetalk.redis.api.TransactedRedisCommander;
import io.servicetalk.tcp.netty.internal.ReadOnlyTcpClientConfig;
import io.servicetalk.tcp.netty.internal.TcpClientChannelInitializer;
import io.servicetalk.tcp.netty.internal.TcpClientConfig;
import io.servicetalk.tcp.netty.internal.TcpConnector;
import io.servicetalk.transport.api.DefaultExecutionContext;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.netty.internal.ChannelInitializer;
import io.servicetalk.transport.netty.internal.DefaultNettyConnection;
import io.servicetalk.transport.netty.internal.NettyConnection;
import io.servicetalk.transport.netty.internal.NettyConnection.TerminalPredicate;
import io.servicetalk.transport.netty.internal.NettyIoExecutor;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.BlockingTestUtils.awaitIndefinitely;
import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.DISCARD;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.EXEC;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.MULTI;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.PING;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.SUBSCRIBE;
import static io.servicetalk.redis.netty.InternalSubscribedRedisConnection.newSubscribedConnection;
import static io.servicetalk.redis.netty.PipelinedRedisConnection.newPipelinedConnection;
import static io.servicetalk.transport.netty.NettyIoExecutors.createIoExecutor;
import static io.servicetalk.transport.netty.internal.CloseHandler.UNSUPPORTED_PROTOCOL_CLOSE_HANDLER;
import static io.servicetalk.transport.netty.internal.NettyIoExecutors.toNettyIoExecutor;
import static io.servicetalk.transport.netty.internal.RandomDataUtils.randomCharSequenceOfByteLength;
import static java.lang.Long.MAX_VALUE;
import static java.net.InetAddress.getLoopbackAddress;
import static java.time.Duration.ofSeconds;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assume.assumeThat;

public class PingerTest {

    public static final Duration PING_PERIOD = ofSeconds(1);
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout(1, TimeUnit.MINUTES);

    @Nullable
    private static NettyIoExecutor nettyIoExecutor;
    @Nullable
    private static ReadOnlyRedisClientConfig config;
    @Nullable
    private static InetSocketAddress serverAddress;

    @BeforeClass
    public static void setUp() {
        final String tmpRedisPort = System.getenv("REDIS_PORT");
        assumeThat(tmpRedisPort, not(isEmptyOrNullString()));
        int redisPort = Integer.parseInt(tmpRedisPort);
        String redisHost = System.getenv().getOrDefault("REDIS_HOST", getLoopbackAddress().getHostName());
        serverAddress = new InetSocketAddress(redisHost, redisPort);

        nettyIoExecutor = toNettyIoExecutor(createIoExecutor());
        config = new RedisClientConfig(new TcpClientConfig(false)).setPingPeriod(PING_PERIOD)
                .setMaxPipelinedRequests(10).asReadOnly();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        if (nettyIoExecutor != null) {
            awaitIndefinitely(nettyIoExecutor.closeAsync());
        }
    }

    @Test
    public void testPingWithMulti() throws Exception {
        assert config != null;

        BlockingQueue<Object> commandsWritten = new LinkedBlockingQueue<>();
        RedisConnection connection = awaitIndefinitely(connect(commandsWritten)
                .map(conn -> newPipelinedConnection(conn, conn.executionContext(), config)));
        assert connection != null;

        Object command = commandsWritten.take();
        assertThat("Unexpected command written.", command, is(PING));
        TransactedRedisCommander commander = awaitIndefinitely(connection.asCommander().multi());
        assert commander != null;

        while (true) {
            Object nextCommand = commandsWritten.take();
            if (nextCommand == MULTI) {
                // We may have got some PINGS so await till MULTI is written
                break;
            }
        }

        awaitPingDurations(2); // Await for a few ping durations to verify no pings were sent.

        assertThat("Unexpected command written.", commandsWritten, hasSize(0)); // No command must be written post MULTI
        awaitIndefinitely(commander.exec());
        Object nextCommand = commandsWritten.take();
        assertThat("Unexpected command written.", nextCommand, is(EXEC));

        awaitPingDurations(2); // Await for a few ping durations to verify pings were sent.
        nextCommand = commandsWritten.take();
        assertThat("Unexpected command written.", nextCommand, is(PING));

        awaitIndefinitely(connection.closeAsync());
    }

    @Test
    public void testPingWithSubscribe() throws Exception {
        assert config != null;

        BlockingQueue<Object> commandsWritten = new LinkedBlockingQueue<>();
        RedisConnection connection = awaitIndefinitely(connect(commandsWritten)
                .map(conn -> newSubscribedConnection(conn, conn.executionContext(), config)));
        assert connection != null;

        awaitPingDurations(2); // Await for a few ping durations to verify no pings were sent before subscribe.
        assertThat("Unexpected command written.", commandsWritten, hasSize(0));

        toSource(connection.asCommander().subscribe(randomCharSequenceOfByteLength(32))
                .flatMapPublisher(PubSubRedisConnection::getMessages))
                .subscribe(new Subscriber<PubSubRedisMessage>() {
                    @Override
                    public void onSubscribe(Subscription s) {
                        s.request(MAX_VALUE);
                    }

                    @Override
                    public void onNext(PubSubRedisMessage pubSubRedisMessage) {
                    }

                    @Override
                    public void onError(Throwable t) {
                        commandsWritten.offer(t); // Just add the error so that we can bubble it up as a failure.
                    }

                    @Override
                    public void onComplete() {
                    }
                });

        while (true) {
            Object nextCommand = commandsWritten.take();
            if (nextCommand == SUBSCRIBE) {
                // We may have got some PINGS so await till SUBSCRIBE is written
                break;
            }
        }

        awaitPingDurations(2); // Await for a few ping durations to verify pings were sent.
        Object nextCommand = commandsWritten.take();
        assertThat("Unexpected command written.", nextCommand, is(PING));

        awaitIndefinitely(connection.closeAsync());
    }

    private static void awaitPingDurations(int durationCount) throws InterruptedException {
        Thread.sleep(PING_PERIOD.toMillis() * durationCount);
    }

    private static Single<? extends NettyConnection<RedisData, ByteBuf>> connect(Queue<Object> commandsWritten) {
        assert config != null;
        assert nettyIoExecutor != null;
        assert serverAddress != null;

        final ReadOnlyTcpClientConfig roTcpConfig = config.getTcpClientConfig();
        final ChannelInitializer initializer = new TcpClientChannelInitializer(roTcpConfig)
                .andThen(new RedisClientChannelInitializer())
                .andThen((channel, context) -> {
                    channel.pipeline().addFirst(new ChannelOutboundHandlerAdapter() {
                        @Override
                        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                            if (msg instanceof ByteBuf) {
                                String cmd = ((ByteBuf) msg).toString(StandardCharsets.UTF_8);
                                RedisProtocolSupport.Command command = null;
                                if (cmd.contains("PING")) {
                                    command = PING;
                                } else if (cmd.contains("MULTI")) {
                                    command = MULTI;
                                } else if (cmd.contains("EXEC")) {
                                    command = EXEC;
                                } else if (cmd.contains("DISCARD")) {
                                    command = DISCARD;
                                } else if (cmd.contains("SUBSCRIBE")) {
                                    command = SUBSCRIBE;
                                }
                                // Ignore unknown commands.

                                if (command != null && !commandsWritten.offer(command)) {
                                    throw new IllegalStateException("Queue rejected command: " + command);
                                }
                            }
                            super.write(ctx, msg, promise);
                        }
                    });
                    return context;
                });

        ExecutionContext executionContext =
                new DefaultExecutionContext(DEFAULT_ALLOCATOR, nettyIoExecutor, immediate());
        return TcpConnector.connect(null, serverAddress, roTcpConfig, executionContext).flatMap(channel ->
                DefaultNettyConnection.initChannel(channel,
                        executionContext.bufferAllocator(), executionContext.executor(),
                        new TerminalPredicate<>(o -> false), UNSUPPORTED_PROTOCOL_CLOSE_HANDLER,
                        config.getTcpClientConfig().getFlushStrategy(), initializer
                ));
    }
}
