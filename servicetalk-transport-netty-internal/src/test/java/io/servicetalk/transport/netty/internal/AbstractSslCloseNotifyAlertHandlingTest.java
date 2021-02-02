/*
 * Copyright © 2018-2020 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.transport.netty.internal;

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.logging.api.LogLevel;
import io.servicetalk.transport.api.ConnectionInfo.Protocol;
import io.servicetalk.transport.netty.internal.NoopTransportObserver.NoopConnectionObserver;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.ssl.SslCloseCompletionEvent;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.test.Verifiers.stepVerifier;
import static io.servicetalk.transport.netty.internal.CloseHandler.forPipelinedRequestResponse;
import static io.servicetalk.transport.netty.internal.FlushStrategies.defaultFlushStrategy;
import static io.servicetalk.transport.netty.internal.NoopExecutionStrategy.NOOP_STRATEGY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;

abstract class AbstractSslCloseNotifyAlertHandlingTest {

    private static final WireLoggingInitializer WIRE_LOGGING_INITIALIZER =
            new WireLoggingInitializer("servicetalk-tests-wire-logger", LogLevel.TRACE, () -> true);

    protected static final String BEGIN = "MSG_BEGIN";
    protected static final String END = "MSG_END";

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    protected final EmbeddedDuplexChannel channel;
    protected final DefaultNettyConnection<String, String> conn;

    AbstractSslCloseNotifyAlertHandlingTest(boolean isClient) throws Exception {
        channel = new EmbeddedDuplexChannel(false);
        final CloseHandler closeHandler = forPipelinedRequestResponse(isClient, channel.config());
        conn = DefaultNettyConnection.<String, String>initChannel(channel, DEFAULT_ALLOCATOR, immediate(),
                END::equals, closeHandler, defaultFlushStrategy(), null,
                WIRE_LOGGING_INITIALIZER.andThen(ch -> ch.pipeline().addLast(new ChannelDuplexHandler() {
                    @Override
                    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
                        if (BEGIN.equals(msg)) {
                            closeHandler.protocolPayloadBeginInbound(ctx);
                        }
                        ctx.fireChannelRead(msg);
                        if (END.equals(msg)) {
                            closeHandler.protocolPayloadEndInbound(ctx);
                        }
                    }

                    @Override
                    public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise) {
                        if (BEGIN.equals(msg)) {
                            closeHandler.protocolPayloadBeginOutbound(ctx);
                        }
                        if (END.equals(msg)) {
                            closeHandler.protocolPayloadEndOutbound(ctx, promise);
                        }
                        ctx.write(msg, promise);
                    }
                })), NOOP_STRATEGY, mock(Protocol.class), NoopConnectionObserver.INSTANCE, isClient)
                .toFuture().get();
    }

    @After
    public void tearDown() throws Exception {
        try {
            // Make sure the connection and channel are closed after each test:
            assertThat("Underlying Channel is not closed", channel.isOpen(), is(false));
            assertThat("Unexpected inbound messages", channel.inboundMessages(), hasSize(0));
            assertThat("Unexpected outbound messages", channel.outboundMessages(), hasSize(0));
            stepVerifier(conn.onClose()).expectComplete().verify();
        } finally {
            // In case of test errors, do the clean up:
            try {
                conn.closeAsync().toFuture().get();
            } finally {
                channel.finishAndReleaseAll();
                channel.close().syncUninterruptibly();
            }
        }
    }

    @Test
    public void neverUsedIdleConnection() {
        closeNotifyAndVerifyClosing();
    }

    protected final void closeNotifyAndVerifyClosing() {
        channel.pipeline().fireUserEventTriggered(SslCloseCompletionEvent.SUCCESS);
        stepVerifier(conn.onClosing()).expectComplete().verify();
    }

    protected final void writeMsg(PublisherSource.Processor<String, String> writeSource, String msg) {
        writeSource.onNext(msg);
        assertThat("Unexpected outbound message", channel.readOutbound(), is(msg));
    }
}
