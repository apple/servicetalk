/*
 * Copyright © 2018, 2020 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.DefaultThreadFactory;
import io.servicetalk.concurrent.api.Executors;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.transport.netty.internal.CloseHandler.CloseEvent;
import io.servicetalk.transport.netty.internal.CloseHandler.DiscardFurtherInboundEvent;
import io.servicetalk.transport.netty.internal.CloseHandler.OutboundDataEndEvent;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoop;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.channel.socket.ChannelOutputShutdownEvent;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.SocketChannelConfig;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.EventExecutor;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.rules.TestWatcher;
import org.junit.rules.Timeout;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.InOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;

import static io.netty.channel.ChannelOption.ALLOW_HALF_CLOSURE;
import static io.netty.channel.ChannelOption.AUTO_CLOSE;
import static io.netty.channel.ChannelOption.AUTO_READ;
import static io.netty.util.ReferenceCountUtil.release;
import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.BuilderUtils.serverChannel;
import static io.servicetalk.transport.netty.internal.BuilderUtils.socketChannel;
import static io.servicetalk.transport.netty.internal.CloseHandler.CloseEvent.CHANNEL_CLOSED_INBOUND;
import static io.servicetalk.transport.netty.internal.CloseHandler.CloseEvent.CHANNEL_CLOSED_OUTBOUND;
import static io.servicetalk.transport.netty.internal.CloseHandler.CloseEvent.PROTOCOL_CLOSING_INBOUND;
import static io.servicetalk.transport.netty.internal.CloseHandler.CloseEvent.PROTOCOL_CLOSING_OUTBOUND;
import static io.servicetalk.transport.netty.internal.CloseHandler.CloseEvent.USER_CLOSING;
import static io.servicetalk.transport.netty.internal.CloseHandler.forPipelinedRequestResponse;
import static io.servicetalk.transport.netty.internal.EventLoopAwareNettyIoExecutors.toEventLoopAwareNettyIoExecutor;
import static io.servicetalk.transport.netty.internal.NettyIoExecutors.createIoExecutor;
import static io.servicetalk.transport.netty.internal.RequestResponseCloseHandlerTest.Scenarios.Events.CI;
import static io.servicetalk.transport.netty.internal.RequestResponseCloseHandlerTest.Scenarios.Events.FC;
import static io.servicetalk.transport.netty.internal.RequestResponseCloseHandlerTest.Scenarios.Events.IB;
import static io.servicetalk.transport.netty.internal.RequestResponseCloseHandlerTest.Scenarios.Events.IC;
import static io.servicetalk.transport.netty.internal.RequestResponseCloseHandlerTest.Scenarios.Events.ID;
import static io.servicetalk.transport.netty.internal.RequestResponseCloseHandlerTest.Scenarios.Events.IE;
import static io.servicetalk.transport.netty.internal.RequestResponseCloseHandlerTest.Scenarios.Events.IS;
import static io.servicetalk.transport.netty.internal.RequestResponseCloseHandlerTest.Scenarios.Events.OB;
import static io.servicetalk.transport.netty.internal.RequestResponseCloseHandlerTest.Scenarios.Events.OC;
import static io.servicetalk.transport.netty.internal.RequestResponseCloseHandlerTest.Scenarios.Events.OE;
import static io.servicetalk.transport.netty.internal.RequestResponseCloseHandlerTest.Scenarios.Events.OH;
import static io.servicetalk.transport.netty.internal.RequestResponseCloseHandlerTest.Scenarios.Events.OS;
import static io.servicetalk.transport.netty.internal.RequestResponseCloseHandlerTest.Scenarios.Events.SR;
import static io.servicetalk.transport.netty.internal.RequestResponseCloseHandlerTest.Scenarios.Events.UC;
import static io.servicetalk.transport.netty.internal.RequestResponseCloseHandlerTest.Scenarios.ExpectEvent.CCI;
import static io.servicetalk.transport.netty.internal.RequestResponseCloseHandlerTest.Scenarios.ExpectEvent.CCO;
import static io.servicetalk.transport.netty.internal.RequestResponseCloseHandlerTest.Scenarios.ExpectEvent.NIL;
import static io.servicetalk.transport.netty.internal.RequestResponseCloseHandlerTest.Scenarios.ExpectEvent.PCI;
import static io.servicetalk.transport.netty.internal.RequestResponseCloseHandlerTest.Scenarios.ExpectEvent.PCO;
import static io.servicetalk.transport.netty.internal.RequestResponseCloseHandlerTest.Scenarios.ExpectEvent.UCO;
import static io.servicetalk.transport.netty.internal.RequestResponseCloseHandlerTest.Scenarios.Mode.C;
import static io.servicetalk.transport.netty.internal.RequestResponseCloseHandlerTest.Scenarios.Mode.S;
import static java.lang.Integer.toHexString;
import static java.lang.Thread.NORM_PRIORITY;
import static java.util.Arrays.asList;
import static java.util.Objects.hash;
import static java.util.stream.Collectors.toSet;
import static junit.framework.TestCase.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(Enclosed.class)
public class RequestResponseCloseHandlerTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestResponseCloseHandlerTest.class);

    @RunWith(Parameterized.class)
    public static class Scenarios {

        @Rule
        public final TestWatcher pendingWatcher = new FailedPendingWatcher();

        private Mode mode;
        private List<Events> events;
        private ExpectEvent expectEvent;
        private String desc;
        private String location; // Inferred from parameters

        private ChannelHandlerContext ctx;
        private SocketChannel channel;
        private ChannelPipeline pipeline;
        private RequestResponseCloseHandler h;
        @Nullable
        private CloseEvent observedEvent;
        private AtomicBoolean closed = new AtomicBoolean();
        private AtomicBoolean inputShutdown = new AtomicBoolean();
        private AtomicBoolean outputShutdown = new AtomicBoolean();
        private SocketChannelConfig scc;

        public Scenarios(final Mode mode, final List<Events> events, final ExpectEvent expectEvent,
                         final String desc, final String location) {
            this.mode = mode;
            this.events = events;
            this.expectEvent = expectEvent;
            this.desc = desc;
            this.location = location;
        }

        protected enum Mode {
            C,  // Client
            S,  // Server
        }

        protected enum Events {
            IB, IE, IC, // emit Input Begin/End/Closing
            OB, OE, OC, // emit Output Begin/End/Closing
            IS, OS,     // emit Input/Output Socket closed
            SR,         // validate Socket TCP RST -> SO_LINGER=0
            UC,         // emit User Closing
            IH, OH, FC, // validate Input/Output Half-close, Full-Close
            ID,         // validate Input discarding
            CI, CO,     // emit Inbound/Outbound close (read cancellation, write subscriber termination)
        }

        protected enum ExpectEvent {
            NIL(null), // No event, not closed
            PCO(PROTOCOL_CLOSING_OUTBOUND),
            PCI(PROTOCOL_CLOSING_INBOUND),
            UCO(USER_CLOSING),
            CCO(CHANNEL_CLOSED_OUTBOUND),
            CCI(CHANNEL_CLOSED_INBOUND);

            @Nullable
            private final CloseEvent ce;

            ExpectEvent(@Nullable CloseEvent c) {
                this.ce = c;
            }
        }

        @Parameters(name = "{index}. {3} - {0} {1} = {2}")
        public static Collection<Object[]> data() { // If inserting lines here, adjust the `offset` variable below
            StackTraceElement se = Thread.currentThread().getStackTrace()[1];
            List<Object[]> params = asList(new Object[][]{
                    // {Mode, Events, ExpectedEvent, Desc} - IGNORE disables test-case, keep contiguous on single line
                    {C, e(OB, OE, IB, IE), NIL, "sequential, no close"},
                    {C, e(OB, OE, IB, OB, OE, IE, IB, IE), NIL, "pipelined, no close"},
                    {C, e(OB, OC, OE, IB, IE, FC), PCO, "sequential, closing outbound"},
                    {C, e(OB, OE, OB, OC, OE, IB, IE, IB, IE, FC), PCO, "pipelined, closing outbound"},
                    {C, e(OB, OE, OB, OC, IB, OE, IE, IB, IE, FC), PCO, "pipelined, full dup, closing outbound"},
                    {C, e(OB, IB, IC, OE, IE, FC), PCI, "full dup, closing inbound"},
                    {C, e(OB, OE, IB, OB, IC, IE, FC), PCI, "server closes 1st request, 2nd request discarded"},
                    {C, e(OB, IB, OE, IC, IE, FC), PCI, "pipelined, closing inbound"},
                    {C, e(OB, IB, IC, IE, OE, FC), PCI, "pipelined, full dup, closing inbound"},
                    {C, e(OB, OE, IB, IC, IE, FC), PCI, "sequential, closing inbound"},
                    {C, e(OB, UC, OE, IB, IE, FC), UCO, "sequential, user close"},
                    {C, e(OB, IB, OE, OB, UC, OE, IE, IB, IE, FC), UCO, "pipelined req graceful close"},
                    {C, e(OB, IB, UC, OE, IE, FC), UCO, "interleaved, user close"},
                    {C, e(OB, OE, IB, IE, UC, FC), UCO, "sequential, idle, user close"},
                    {C, e(OB, IB, OE, IE, UC, FC), UCO, "interleaved, idle, user close"},
                    {C, e(OB, IB, IE, OE, UC, FC), UCO, "interleaved full dup, idle, user close"},
                    {C, e(OB, IB, UC, IE, OE, FC), UCO, "interleaved full dup, user close"},
                    {C, e(OB, OE, IS, FC), CCI, "abrupt input close after complete write, resp abort"},
                    {C, e(IS, FC), CCI, "idle, inbound closed"},
                    {C, e(OB, IS, SR, FC), CCI, "req abort, inbound closed"},
                    {C, e(OB, IB, OE, OB, IC, IE, FC), PCI, "pipelined req abort after inbound close"},
                    {C, e(OB, IB, OE, IS, FC), CCI, "req complete, resp abort, inbound closed"},
                    {C, e(OB, IB, IE, IS, OE, FC), CCI, "continue write read completed, inbound closed"},
                    {C, e(OS, FC), CCO, "idle, outbound closed"},
                    {C, e(OB, OS, SR, FC), CCO, "req abort, outbound closed"},
                    {C, e(OB, OE, OB, IB, OS, SR, IE, FC), CCO, "new req abort, complete read, outbound closed"},
                    {C, e(OB, OE, OS, IB, IE, FC), CCO, "req complete, complete read, outbound closed"},
                    {C, e(OB, IB, IE, OS, SR, FC), CCO, "outbound closed while not reading"},
                    {C, e(OB, IB, OS, SR, IE, FC), CCO, "outbound closed while reading"},
                    {C, e(OB, OE, OB, OS, SR, IB, IE, FC), CCO, "outbound closed while not reading, 2 pending"},
                    {C, e(OB, OE, OB, OE, OB, OS, SR, IB, IE, IB, IE, FC), CCO, "outbound closed while not reading, >2 pending"},
                    {C, e(OB, OE, OB, OE, OB, IB, OS, SR, IE, IB, IE, FC), CCO, "outbound closed while reading, >2 pending"},
                    {C, e(OB, IB, IS, OE, FC), CCI, "inbound closed when reading, finish pending request"},
                    {S, e(IS, FC), CCI, "idle, inbound closed"},
                    {S, e(IB, OB, IE, IS, OE, FC), CCI, "continue resp, req completed, inbound closed"},
                    {S, e(IB, OB, OE, IS, SR, FC), CCI, "req aborted, resp completed, inbound closed"},
                    {S, e(IB, OB, IE, IB, IS, SR, OE, OB, OE, FC), CCI, "new req abort, complete responses, inbound closed"},
                    {S, e(OS, FC), CCO, "idle, outbound closed"},
                    {S, e(IB, OS, SR, FC), CCO, "req aborted, outbound closed"},
                    {S, e(IB, OB, OS, IE, FC), CCO, "continue req, outbound shutdown, no reset"},
                    {S, e(IB, OB, OS, IS, FC), CCO, "outbound shutdown, inbound shutdown, no reset"},
                    {S, e(IB, OB, OE, OS, IE, FC), CCO, "resp completed, complete req, outbound closed"},
                    {S, e(IB, OB, IE, IB, OS, SR, FC), CCO, "new req abort, resp abort, outbound closed"},
                    {S, e(IB, OB, OE, IE, IB, OS, SR, FC), CCO, "new req abort, complete resp, outbound closed"},
                    {S, e(IB, IE, OB, OE), NIL, "sequential, no close"},
                    {S, e(IB, IE, OB, IB, IE, OE, OB, OE), NIL, "pipelined, no close"},
                    {S, e(IB, IE, IB, OB, OC, ID, OE, OH, IS, FC), PCO, "pipelined, closing outbound"},
                    {S, e(IB, IE, IB, IE, OB, OC, ID, OE, OH, IS, FC), PCO, "pipelined, closing outbound, drop pending!"},
                    {S, e(IB, IE, OB, OC, ID, OE, OH, IS, FC), PCO, "sequential, closing outbound"},
                    {S, e(IB, OB, OC, IE, ID, OE, OH, IS, FC), PCO, "interleaved, closing outbound"},
                    {S, e(IB, OB, OC, OE, IE, ID, OH, IS, FC), PCO, "interleaved full dup, closing outbound"},
                    {S, e(IB, OB, OC, IE, ID, IS, OE, FC), PCO, "interleaved, input shutdowns, closing outbound"},
                    {S, e(IB, OB, IE, IB, IC, OE, OB, IE, ID, OE, FC), PCI, "pipelined, closing inbound, drain"},
                    {S, e(IB, IE, OB, IB, IC, IE, ID, OE, OB, OE, FC), PCI, "pipelined, closing inbound"},
                    {S, e(IB, IE, OB, IB, IE, UC, ID, OE, OB, OE, OH, IS, FC), UCO, "pipelined, user closing, drain"},
                    {S, e(IB, IC, OB, OE, IE, FC), PCI, "pipelined full dup, closing inbound"},
                    {S, e(IB, OB, IE, IB, IC, IE, ID, OE, OB, OE, FC), PCI, "pipelined, closing inbound"},
                    {S, e(IB, OB, IC, OE, IE, FC), PCI, "pipelined, full dup, closing inbound"},
                    {S, e(IB, IC, IE, ID, OB, OE, FC), PCI, "sequential, closing inbound"},
                    {S, e(UC, ID, OH, IS, FC), UCO, "recently open connection, idle, user close"},
                    {S, e(IB, UC, IE, ID, OB, OE, OH, IS, FC), UCO, "sequential, during req, user close"},
                    {S, e(IB, IE, UC, ID, OB, OE, OH, IS, FC), UCO, "sequential, user close"},
                    {S, e(IB, IE, UC, ID, IS, OB, OE, FC), UCO, "sequential, input shutdown before resp, user close"},
                    {S, e(IB, IE, UC, ID, OB, IS, OE, FC), UCO, "sequential, input shutdown after resp, user close"},
                    {S, e(IB, IE, OB, UC, ID, OE, OH, IS, FC), UCO, "sequential, during resp, user close"},
                    {S, e(IB, IE, OB, OE, UC, ID, OH, IS, FC), UCO, "sequential, idle, user close"},
                    {S, e(IB, IE, OB, OE, UC, ID, OH, CI, IS, FC), UCO, "sequential, idle, read cancelled, user close"},
                    {S, e(IB, UC, OB, IE, ID, OE, OH, IS, FC), UCO, "interleaved, before resp, user close"},
                    {S, e(IB, OB, UC, IE, ID, OE, OH, IS, FC), UCO, "interleaved, user close"},
                    {S, e(IB, OB, IE, UC, ID, OE, OH, IS, FC), UCO, "interleaved, after req, user close"},
                    {S, e(IB, OB, IE, OE, UC, ID, OH, IS, FC), UCO, "interleaved, idle, user close"},
                    {S, e(IB, UC, OB, OE, IE, ID, OH, IS, FC), UCO, "interleaved full dup, before resp, user close"},
                    {S, e(IB, OB, UC, OE, IE, ID, OH, IS, FC), UCO, "interleaved full dup, user close"},
                    {S, e(IB, OB, UC, OE, IE, ID, OH, CI, IS, FC), UCO, "interleaved full dup, read cancelled, user close"},
                    {S, e(IB, OB, OE, UC, IE, ID, OH, IS, FC), UCO, "interleaved full dup, after resp, user close"},
                    {S, e(IB, OB, OE, IE, UC, ID, OH, IS, FC), UCO, "interleaved full dup, idle, user close"},
                    {S, e(IB, IE, OB, OE, IS, FC), CCI, "sequential, idle, inbound closed"},
                    {S, e(IB, OB, IS, SR, OE, FC), CCI, "inbound closed while reading no pipeline"},
                    {S, e(IB, IS, SR, OB, OE, FC), CCI, "inbound closed while reading delay close until response"},
                    {S, e(IB, IE, IB, IS, SR, OB, OE, OB, OE, FC), CCI, "inbound closed while not writing pipelined, 2 pending"},
                    {S, e(IB, IE, IB, OB, IS, SR, OE, OB, OE, FC), CCI, "inbound closed while writing pipelined, 1 pending"},
                    {S, e(IB, IE, IB, IE, IB, IS, SR, OB, OE, OB, OE, OB, OE, FC), CCI, "inbound closed while not writing pipelined, >2 pending"},
                    {S, e(IB, IE, IB, IE, IB, OB, IS, SR, OE, OB, OE, OB, OE, FC), CCI, "inbound closed while writing pipelined, >2 pending"},
                    {S, e(IB, IE, IS, OB, OS, FC), CCI, "Input closed after read, outbound closed while writing"},
                    {S, e(IB, IE, IS, OS, FC), CCI, "Input closed after read, outbound closed while writing"},
            });
            String fileName = se.getFileName();
            int offset = se.getLineNumber() + 3; // Lines between `se` and first parameter
            for (int i = 0; i < params.size(); i++) { // Appends param location as last entry
                Object[] o = params.get(i);
                params.set(i, new Object[]{o[0], o[1], o[2], o[3], fileName + ":" + (offset + i)});
            }
            Set<Integer> uniques = params.stream().map(objs -> hash(objs[0], objs[1], objs[2])).collect(toSet());
            assertEquals("Duplicate test scenario?", uniques.size(), params.size());
            return params;
        }

        // Helps debug failed tests, by printing the internal state
        private class FailedPendingWatcher extends TestWatcher {
            @Override
            protected void failed(final Throwable e, final Description description) {
                LOGGER.error("Test Failed – Pending state: {}", toHexString(h.state() << 24 | h.pending()), e);
            }
        }

        /**
         * Simulates netty channel behavior, behavior verified by {@link ChannelBehavior} below.
         */
        @Before
        public void setup() {
            // A description prefixed with "IGNORE " will not fail the suite!
            assumeThat(desc, desc, not(startsWith("IGNORE ")));
            ctx = mock(ChannelHandlerContext.class);
            channel = mock(SocketChannel.class, "");
            when(ctx.channel()).thenReturn(channel);

            // Asserts
            EventExecutor exec = mock(EventExecutor.class);
            when(ctx.executor()).thenReturn(exec);
            when(exec.inEventLoop()).thenReturn(true);
            scc = mock(SocketChannelConfig.class);
            when(channel.config()).thenReturn(scc);
            EventLoop loop = mock(EventLoop.class);
            when(channel.eventLoop()).thenReturn(loop);
            when(loop.inEventLoop()).thenReturn(true);
            when(scc.isAllowHalfClosure()).thenReturn(true);
            pipeline = mock(ChannelPipeline.class);
            when(channel.pipeline()).thenReturn(pipeline);

            when(channel.isOutputShutdown()).then(__ -> outputShutdown.get());
            when(channel.isInputShutdown()).then(__ -> inputShutdown.get());
            when(channel.isOpen()).then(__ -> !closed.get());
            ChannelFuture future = mock(ChannelFuture.class);
            when(channel.shutdownInput()).then(__ -> {
                inputShutdown.set(true);
                LOGGER.debug("channel.shutdownInput()");
                h.channelClosedInbound(ctx); // ChannelInputShutdownReadComplete observed from transport
                return future;
            });
            when(channel.shutdownOutput()).then(__ -> {
                outputShutdown.set(true);
                LOGGER.debug("channel.shutdownOutput()");
                h.channelClosedOutbound(ctx); // ChannelOutputShutdownEvent observed from transport
                return future;
            });
            when(channel.close()).then(__ -> {
                closed.set(true);
                LOGGER.debug("channel.close()");
                return future;
            });
            when(scc.setSoLinger(0)).then(__ -> {
                LOGGER.debug("channel.config().setSoLinger(0)");
                if (inputShutdown.get() && outputShutdown.get()) {
                    fail("mock => setsockopt() failed - output already shutdown!");
                }
                return scc;
            });
            h = (RequestResponseCloseHandler) spy(mode == C ? forPipelinedRequestResponse(true, channel.config()) :
                    forPipelinedRequestResponse(false, channel.config()));
            h.registerEventHandler(channel, e -> {
                if (observedEvent == null) {
                    LOGGER.debug("Emitted: {}", e);
                    observedEvent = e;
                }
            });
        }

        private void assertCanRead() {
            assertTrue("Channel Closed (read) - testcase invalid or bug?", !closed.get() && !inputShutdown.get());
        }

        private void assertCanWrite() {
            assertTrue("Channel Closed (write) - testcase invalid or bug?", !closed.get() && !outputShutdown.get());
        }

        @Test
        public void simulate() {
            LOGGER.debug("Test.Params: ({})", location); // Intellij jump to parameter format, don't change!
            LOGGER.debug("[{}] {} - {} = {}", desc, mode, events, expectEvent);
            InOrder order = inOrder(h, channel, pipeline, scc);
            verify(h).registerEventHandler(eq(channel), any());
            for (Events event : events) {
                LOGGER.debug("{}", event);
                switch (event) {
                    case IB:
                        assertCanRead();
                        h.protocolPayloadBeginInbound(ctx);
                        order.verify(h).protocolPayloadBeginInbound(ctx);
                        break;
                    case IE:
                        assertCanRead();
                        h.protocolPayloadEndInbound(ctx);
                        order.verify(h).protocolPayloadEndInbound(ctx);
                        break;
                    case IC:
                        h.protocolClosingInbound(ctx);
                        order.verify(h).protocolClosingInbound(ctx);
                        break;
                    case OB:
                        assertCanWrite();
                        h.protocolPayloadBeginOutbound(ctx);
                        order.verify(h).protocolPayloadBeginOutbound(ctx);
                        break;
                    case OE:
                        assertCanWrite();
                        h.protocolPayloadEndOutboundSuccess(ctx);
                        order.verify(h).protocolPayloadEndOutboundSuccess(ctx);
                        break;
                    case OC:
                        h.protocolClosingOutbound(ctx);
                        order.verify(h).protocolClosingOutbound(ctx);
                        break;
                    case IS:
                        inputShutdown.set(true);
                        h.channelClosedInbound(ctx);
                        order.verify(h).channelClosedInbound(ctx);
                        break;
                    case OS:
                        outputShutdown.set(true);
                        h.channelClosedOutbound(ctx);
                        order.verify(h).channelClosedOutbound(ctx);
                        break;
                    case SR:
                        order.verify(scc).setSoLinger(0);
                        break;
                    case UC:
                        h.userClosing(channel);
                        order.verify(h).userClosing(channel);
                        break;
                    case IH:
                        order.verify(channel).shutdownInput();
                        break;
                    case OH:
                        order.verify(channel).shutdownOutput();
                        break;
                    case FC:
                        order.verify(channel).close();
                        break;
                    case ID:
                        order.verify(pipeline).fireUserEventTriggered(DiscardFurtherInboundEvent.INSTANCE);
                        break;
                    case CI:
                        h.closeChannelInbound(channel);
                        order.verify(h).closeChannelInbound(channel);
                        break;
                    case CO:
                        h.closeChannelOutbound(channel);
                        order.verify(h).closeChannelOutbound(channel);
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown: " + event);
                }
            }
            assertThat("Expected CloseEvent", observedEvent, equalTo(expectEvent.ce));
            for (Events c : Events.values()) {
                if (!events.contains(c)) {
                    switch (c) {
                        case IB:
                            verify(h, never()).protocolPayloadBeginInbound(ctx);
                            break;
                        case IE:
                            verify(h, never()).protocolPayloadEndInbound(ctx);
                            break;
                        case IC:
                            verify(h, never()).protocolClosingInbound(ctx);
                            break;
                        case OB:
                            verify(h, never()).protocolPayloadBeginOutbound(ctx);
                            break;
                        case OE:
                            verify(h, never()).protocolPayloadEndOutboundSuccess(ctx);
                            break;
                        case OC:
                            verify(h, never()).protocolClosingOutbound(ctx);
                            break;
                        case IS:
                        case OS:
                            // These may be called implicitly, skip verify
                            break;
                        case SR:
                            verify(scc, never()).setSoLinger(0);
                            break;
                        case UC:
                            verify(h, never()).userClosing(channel);
                            break;
                        case FC:
                            verify(channel, never()).close();
                            break;
                        case IH:
                            verify(channel, never()).shutdownInput();
                            break;
                        case OH:
                            verify(channel, never()).shutdownOutput();
                            break;
                        case ID:
                            verify(pipeline, never()).fireUserEventTriggered(DiscardFurtherInboundEvent.INSTANCE);
                            break;
                        case CI:
                            verify(h, never()).closeChannelInbound(channel);
                            break;
                        case CO:
                            verify(h, never()).closeChannelOutbound(channel);
                            break;
                        default:
                            throw new IllegalArgumentException("Unknown: " + c);
                    }
                }
            }
        }

        private static List<Events> e(Events... args) {
            return asList(args);
        }
    }

    public static class RequestResponseUserEventTest {

        @Rule
        public final Timeout timeout = new ServiceTalkTestTimeout();

        @Test
        public void clientOutboundDataEndEventEmitsUserEventAlways() {
            AtomicBoolean ab = new AtomicBoolean(false);
            final EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter() {
                @Override
                public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) {
                    if (evt == OutboundDataEndEvent.INSTANCE) {
                        ab.set(true);
                    }
                    ctx.fireUserEventTriggered(evt);
                }
            });
            final RequestResponseCloseHandler ch = new RequestResponseCloseHandler(true);
            channel.eventLoop().execute(() -> ch.protocolPayloadEndOutbound(channel.pipeline().firstContext()));
            channel.close().syncUninterruptibly();
            assertThat("OutboundDataEndEvent not fired", ab.get(), is(true));
        }

        @Test
        public void serverOutboundDataEndEventDoesntEmitUntilClosing() {
            AtomicBoolean ab = new AtomicBoolean(false);
            final EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter() {
                @Override
                public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) {
                    if (evt == OutboundDataEndEvent.INSTANCE) {
                        ab.set(true);
                    }
                    ctx.fireUserEventTriggered(evt);
                }
            });
            final RequestResponseCloseHandler ch = new RequestResponseCloseHandler(false);
            channel.eventLoop().execute(() -> ch.protocolPayloadEndOutbound(channel.pipeline().firstContext()));
            channel.close().syncUninterruptibly();
            assertThat("OutboundDataEndEvent should not fire", ab.get(), is(false));
        }

        @Test
        public void serverOutboundDataEndEventDoesntEmitUntilClosingAndIdle() throws Exception {
            AtomicBoolean ab = new AtomicBoolean(false);
            final EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter() {
                @Override
                public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) {
                    if (evt == OutboundDataEndEvent.INSTANCE) {
                        ab.set(true);
                    }
                    ctx.fireUserEventTriggered(evt);
                }
            });
            final ChannelHandlerContext ctx = channel.pipeline().firstContext();
            final RequestResponseCloseHandler ch = new RequestResponseCloseHandler(false);
            // Request #1
            channel.eventLoop().execute(() -> ch.protocolPayloadBeginInbound(ctx));
            channel.eventLoop().execute(() -> ch.protocolPayloadEndInbound(ctx));
            // Request #2
            channel.eventLoop().execute(() -> ch.protocolPayloadBeginInbound(ctx));
            channel.eventLoop().execute(() -> ch.protocolPayloadEndInbound(ctx));
            channel.eventLoop().execute(() -> ch.userClosing(channel));
            // Response #1
            channel.eventLoop().execute(() -> ch.protocolPayloadBeginOutbound(ctx));
            channel.eventLoop().execute(() -> ch.protocolPayloadEndOutbound(ctx));
            channel.runPendingTasks();
            assertThat("OutboundDataEndEvent should not fire", ab.get(), is(false));
            // Response #2
            channel.eventLoop().execute(() -> ch.protocolPayloadBeginOutbound(ctx));
            channel.eventLoop().execute(() -> ch.protocolPayloadEndOutbound(ctx));
            channel.close().syncUninterruptibly();
            assertThat("OutboundDataEndEvent not fired", ab.get(), is(true));
        }

        @Test
        public void serverOutboundDataEndEventEmitsUserEventWhenClosing() {
            AtomicBoolean ab = new AtomicBoolean(false);
            final EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter() {
                @Override
                public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) {
                    if (evt == OutboundDataEndEvent.INSTANCE) {
                        ab.set(true);
                    }
                    ctx.fireUserEventTriggered(evt);
                }
            });
            final RequestResponseCloseHandler ch = new RequestResponseCloseHandler(false);
            channel.eventLoop().execute(() -> ch.userClosing(channel));
            channel.eventLoop().execute(() -> ch.protocolPayloadEndOutbound(channel.pipeline().firstContext()));
            channel.close().syncUninterruptibly();
            assertThat("OutboundDataEndEvent not fired", ab.get(), is(true));
        }
    }

    // Sanity checks to validate assumptions in above mock behavior
    public static class ChannelBehavior {

        @Rule
        public final Timeout timeout = new ServiceTalkTestTimeout();

        @ClassRule
        public static final ExecutionContextRule C_CTX = new ExecutionContextRule(() -> DEFAULT_ALLOCATOR,
                () -> createIoExecutor(
                        new DefaultThreadFactory("client-thread", true, NORM_PRIORITY)), Executors::immediate);
        @ClassRule
        public static final ExecutionContextRule S_CTX = new ExecutionContextRule(() -> DEFAULT_ALLOCATOR,
                () -> createIoExecutor(
                        new DefaultThreadFactory("server-thread", true, NORM_PRIORITY)), Executors::immediate);

        private SocketChannel cChannel;
        private volatile SocketChannel sChannel;
        private ServerSocketChannel ssChannel;

        private final CountDownLatch connectedLatch = new CountDownLatch(1);
        private final CountDownLatch clientInputShutdownLatch = new CountDownLatch(1);
        private final CountDownLatch clientInputShutdownReadCompleteLatch = new CountDownLatch(1);
        private final CountDownLatch clientOutputShutdownLatch = new CountDownLatch(1);
        private final CountDownLatch serverInputShutdownLatch = new CountDownLatch(1);
        private final CountDownLatch serverInputShutdownReadCompleteLatch = new CountDownLatch(1);
        private final CountDownLatch serverOutputShutdownLatch = new CountDownLatch(1);

        @Before
        @SuppressWarnings("unchecked")
        public void setup() throws InterruptedException {
            ssChannel = startServer();
            cChannel = connectClient(ssChannel.localAddress());
            connectedLatch.await();
        }

        @After
        public void dispose() {
            cChannel.close().syncUninterruptibly();
            sChannel.close().syncUninterruptibly();
            ssChannel.close().syncUninterruptibly();
        }

        // Based on TcpServerInitializer
        private ServerSocketChannel startServer() {
            EventLoopAwareNettyIoExecutor eventLoopAwareNettyIoExecutor =
                    toEventLoopAwareNettyIoExecutor(S_CTX.ioExecutor());
            EventLoop loop = eventLoopAwareNettyIoExecutor.eventLoopGroup().next();

            ServerBootstrap bs = new ServerBootstrap();
            bs.group(loop);
            bs.channel(serverChannel(loop, InetSocketAddress.class));
            bs.childHandler(new ChannelInitializer() {
                @Override
                protected void initChannel(final Channel ch) {
                    sChannel = (SocketChannel) ch;
                    ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) {
                            LOGGER.debug("Server Evt: {}", evt.getClass().getSimpleName());
                            if (evt == ChannelInputShutdownEvent.INSTANCE) {
                                serverInputShutdownLatch.countDown();
                            } else if (evt == ChannelInputShutdownReadComplete.INSTANCE) {
                                serverInputShutdownReadCompleteLatch.countDown();
                            } else if (evt == ChannelOutputShutdownEvent.INSTANCE) {
                                serverOutputShutdownLatch.countDown();
                            }
                            release(evt);
                        }
                    });
                    ch.eventLoop().execute(connectedLatch::countDown);
                }
            });

            bs.childOption(AUTO_READ, true);
            bs.childOption(ALLOW_HALF_CLOSURE, true);
            bs.childOption(AUTO_CLOSE, false);

            return (ServerSocketChannel) bs.bind(localAddress(0))
                    .syncUninterruptibly().channel();
        }

        // Based on TcpConnector
        private SocketChannel connectClient(InetSocketAddress address) {
            EventLoopAwareNettyIoExecutor eventLoopAwareNettyIoExecutor =
                    toEventLoopAwareNettyIoExecutor(C_CTX.ioExecutor());
            EventLoop loop = eventLoopAwareNettyIoExecutor.eventLoopGroup().next();

            Bootstrap bs = new Bootstrap();
            bs.group(loop);
            bs.channel(socketChannel(loop, InetSocketAddress.class));
            bs.handler(new ChannelInitializer() {
                @Override
                protected void initChannel(final Channel ch) {
                    ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) {
                            LOGGER.debug("Client Evt: {}", evt.getClass().getSimpleName());
                            if (evt == ChannelInputShutdownEvent.INSTANCE) {
                                clientInputShutdownLatch.countDown();
                            } else if (evt == ChannelInputShutdownReadComplete.INSTANCE) {
                                clientInputShutdownReadCompleteLatch.countDown();
                            } else if (evt == ChannelOutputShutdownEvent.INSTANCE) {
                                clientOutputShutdownLatch.countDown();
                            }
                            release(evt);
                        }
                    });
                }
            });

            bs.option(AUTO_READ, true);
            bs.option(ALLOW_HALF_CLOSURE, true);
            bs.option(AUTO_CLOSE, false);

            return (SocketChannel) bs.connect(address).syncUninterruptibly().channel();
        }

        @Test
        public void clientCloseEmitsNoShutdownEventsOnClient() {
            cChannel.close().syncUninterruptibly();
            assertThat(clientOutputShutdownLatch.getCount(), equalTo(1L));
            assertThat(clientInputShutdownLatch.getCount(), equalTo(1L));
            assertThat(clientInputShutdownReadCompleteLatch.getCount(), equalTo(1L));
            assertThat(cChannel.isInputShutdown(), is(true));
            assertThat(cChannel.isOutputShutdown(), is(true));
            assertThat(cChannel.isOpen(), is(false));
        }

        @Test
        public void clientCloseEmitsServerInputShutdownImmediatelyAndOutputAfterWriting() throws Exception {
            cChannel.close().syncUninterruptibly();
            serverInputShutdownReadCompleteLatch.await();
            serverInputShutdownLatch.await();
            assertThat(sChannel.isInputShutdown(), is(true));
            assertThat(sChannel.isOutputShutdown(), is(false));
            assertThat(sChannel.isOpen(), is(true));
            writeUntilFailure(sChannel);
            serverOutputShutdownLatch.await();
            assertThat(sChannel.isOutputShutdown(), is(true));
            assertThat(sChannel.isOpen(), is(true));
        }

        @Test
        public void clientShutdownOutputEmitsClientOutputShutdownAndServerInputShutdown() throws Exception {
            cChannel.shutdownOutput().syncUninterruptibly();
            clientOutputShutdownLatch.await();
            serverInputShutdownReadCompleteLatch.await();
            serverInputShutdownLatch.await();
            assertThat(cChannel.isInputShutdown(), is(false));
            assertThat(cChannel.isOutputShutdown(), is(true));
            assertThat(sChannel.isInputShutdown(), is(true));
            assertThat(sChannel.isOutputShutdown(), is(false));
            assertThat(serverOutputShutdownLatch.getCount(), equalTo(1L));
            assertThat(sChannel.isOpen(), is(true));
        }

        @Test
        public void serverShutdownInputEmitsServerInputShutdownReadCompleteOnly() throws Exception {
            assumeThat("Windows doesn't emit ChannelInputShutdownReadComplete. Investigation Required.", sChannel,
                    is(Matchers.not(instanceOf(NioSocketChannel.class))));
            sChannel.shutdownInput().syncUninterruptibly();
            serverInputShutdownReadCompleteLatch.await();
            assertThat(serverInputShutdownLatch.getCount(), is(1L));
            assertThat(sChannel.isInputShutdown(), is(true));
            assertThat(sChannel.isOutputShutdown(), is(false));
            assertThat(sChannel.isOpen(), is(true));
            assertThat(clientOutputShutdownLatch.getCount(), is(1L));
            assertThat(cChannel.isInputShutdown(), is(false));
            assertThat(cChannel.isOutputShutdown(), is(false));
        }

        @Test
        public void serverCloseEmitsNoShutdownEventsOnServer() {
            sChannel.close().syncUninterruptibly();
            assertThat(serverOutputShutdownLatch.getCount(), equalTo(1L));
            assertThat(serverInputShutdownLatch.getCount(), equalTo(1L));
            assertThat(serverInputShutdownReadCompleteLatch.getCount(), equalTo(1L));
            assertThat(sChannel.isInputShutdown(), is(true));
            assertThat(sChannel.isOutputShutdown(), is(true));
            assertThat(sChannel.isOpen(), is(false));
        }

        @Test
        public void serverCloseEmitsClientInputShutdownImmediatelyAndOutputAfterWriting() throws Exception {
            sChannel.close().syncUninterruptibly();
            clientInputShutdownReadCompleteLatch.await();
            clientInputShutdownLatch.await();
            assertThat(cChannel.isInputShutdown(), is(true));
            assertThat(cChannel.isOutputShutdown(), is(false));
            assertThat(cChannel.isOpen(), is(true));
            writeUntilFailure(cChannel);
            clientOutputShutdownLatch.await();
            assertThat(cChannel.isOutputShutdown(), is(true));
            assertThat(cChannel.isOpen(), is(true));
        }

        private void writeUntilFailure(Channel channel) throws InterruptedException {
            channel.writeAndFlush(channel.alloc().buffer(1).writeZero(1)).syncUninterruptibly(); // triggers RST
            for (;;) {
                try {
                    // observes error
                    channel.writeAndFlush(channel.alloc().buffer(1).writeZero(1)).syncUninterruptibly();
                } catch (Exception ignored) {
                    break;
                }
                // macOS has been observed to write a TCP window probe after getting the first RST, and the peer may
                // send multiple RSTs before the write attempt fails locally. So we back off a bit to wait for failure.
                Thread.sleep(100);
            }
        }

        // When making the posix setsockopt call, there's no difference in the way netty handles Darwin vs Linux return
        // values, but Darwin tracks the socket state and returns EINVAL (accurate according to posix).
        // In the case of Linux we'll assume it's more lenient and ignore the missing error.
        // http://pubs.opengroup.org/onlinepubs/9699919799/functions/setsockopt.html
        private void expectToFailIfNotOnLinux(Runnable call) {
            // TODO(scott) Windows doesn't propagate the exception. Some times an unhandled exception in pipeline.
            if (cChannel instanceof EpollSocketChannel || (!KQueue.isAvailable() && !Epoll.isAvailable())) {
                call.run();
            } else {
                try {
                    call.run();
                    fail("Should fail");
                } catch (ChannelException e) {
                    // Expected
                }
            }
        }

        @Test
        public void socketOptionsSucceedWhenInputShutdown() throws InterruptedException {
            sChannel.shutdownOutput();
            clientInputShutdownLatch.await();
            cChannel.config().setSoLinger(0);
        }

        @Test
        public void socketOptionsSucceedWhenOutputShutdown() throws InterruptedException {
            cChannel.shutdownOutput();
            clientOutputShutdownLatch.await();
            cChannel.config().setSoLinger(0);
        }

        @Test
        public void socketOptionsFailWhenInAndOutputShutdown() throws InterruptedException {
            sChannel.shutdownOutput();
            cChannel.shutdownOutput();
            clientInputShutdownLatch.await();
            clientOutputShutdownLatch.await();
            expectToFailIfNotOnLinux(() -> cChannel.config().setSoLinger(0));
        }

        @Test
        public void socketOptionsSucceedWhenServerCloses() throws InterruptedException {
            sChannel.close();
            clientInputShutdownLatch.await();
            cChannel.config().setSoLinger(0);
        }

        @Test
        public void socketOptionsFailWhenServerClosesAndOutputShutdown() throws InterruptedException {
            cChannel.shutdownOutput();
            sChannel.close();
            clientInputShutdownLatch.await();
            clientOutputShutdownLatch.await();
            expectToFailIfNotOnLinux(() -> cChannel.config().setSoLinger(0));
        }

        @Test
        public void socketOptionsFailWhenServerRstCloses() throws InterruptedException {
            sChannel.config().setSoLinger(0);
            sChannel.close();
            clientInputShutdownLatch.await();
            expectToFailIfNotOnLinux(() -> cChannel.config().setSoLinger(0));
        }

        @Test
        public void socketOptionsFailWhenServerRstClosesAndOutputShutdown() throws InterruptedException {
            cChannel.shutdownOutput();
            sChannel.config().setSoLinger(0);
            sChannel.close();
            clientInputShutdownLatch.await();
            clientOutputShutdownLatch.await();
            expectToFailIfNotOnLinux(() -> cChannel.config().setSoLinger(0));
        }
    }
}
