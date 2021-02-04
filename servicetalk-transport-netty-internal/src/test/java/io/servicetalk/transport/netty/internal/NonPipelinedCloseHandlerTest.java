/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.transport.netty.internal.CloseHandler.CloseEvent;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.socket.SocketChannel;
import io.netty.util.concurrent.EventExecutor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;

import static io.servicetalk.transport.netty.internal.CloseHandler.CloseEvent.CHANNEL_CLOSED_INBOUND;
import static io.servicetalk.transport.netty.internal.CloseHandler.CloseEvent.CHANNEL_CLOSED_OUTBOUND;
import static io.servicetalk.transport.netty.internal.CloseHandler.CloseEvent.GRACEFUL_USER_CLOSING;
import static io.servicetalk.transport.netty.internal.CloseHandler.CloseEvent.PROTOCOL_CLOSING_INBOUND;
import static io.servicetalk.transport.netty.internal.CloseHandler.CloseEvent.PROTOCOL_CLOSING_OUTBOUND;
import static io.servicetalk.transport.netty.internal.NonPipelinedCloseHandlerTest.Events.CI;
import static io.servicetalk.transport.netty.internal.NonPipelinedCloseHandlerTest.Events.CO;
import static io.servicetalk.transport.netty.internal.NonPipelinedCloseHandlerTest.Events.FC;
import static io.servicetalk.transport.netty.internal.NonPipelinedCloseHandlerTest.Events.IB;
import static io.servicetalk.transport.netty.internal.NonPipelinedCloseHandlerTest.Events.IC;
import static io.servicetalk.transport.netty.internal.NonPipelinedCloseHandlerTest.Events.IE;
import static io.servicetalk.transport.netty.internal.NonPipelinedCloseHandlerTest.Events.IS;
import static io.servicetalk.transport.netty.internal.NonPipelinedCloseHandlerTest.Events.OB;
import static io.servicetalk.transport.netty.internal.NonPipelinedCloseHandlerTest.Events.OC;
import static io.servicetalk.transport.netty.internal.NonPipelinedCloseHandlerTest.Events.OE;
import static io.servicetalk.transport.netty.internal.NonPipelinedCloseHandlerTest.Events.OS;
import static io.servicetalk.transport.netty.internal.NonPipelinedCloseHandlerTest.Events.UC;
import static io.servicetalk.transport.netty.internal.NonPipelinedCloseHandlerTest.ExpectEvent.CCI;
import static io.servicetalk.transport.netty.internal.NonPipelinedCloseHandlerTest.ExpectEvent.CCO;
import static io.servicetalk.transport.netty.internal.NonPipelinedCloseHandlerTest.ExpectEvent.GUC;
import static io.servicetalk.transport.netty.internal.NonPipelinedCloseHandlerTest.ExpectEvent.NIL;
import static io.servicetalk.transport.netty.internal.NonPipelinedCloseHandlerTest.ExpectEvent.PCI;
import static io.servicetalk.transport.netty.internal.NonPipelinedCloseHandlerTest.ExpectEvent.PCO;
import static java.util.Arrays.asList;
import static java.util.Objects.hash;
import static java.util.stream.Collectors.toSet;
import static junit.framework.TestCase.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(Parameterized.class)
public class NonPipelinedCloseHandlerTest {
    private final ChannelHandlerContext ctx;
    private final Channel channel;
    private final CloseHandler h;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final List<Events> events;
    private final ExpectEvent expectEvent;
    @Nullable
    private CloseEvent observedEvent;

    protected enum Events {
        IB, IE, IC, // emit Input Begin/End/Closing
        OB, OE, OC, // emit Output Begin/End/Closing
        IS, OS,     // emit Input/Output Socket closed
        UC,         // emit User Closing
        FC,         // Full-Close
        CI, CO,     // emit Inbound/Outbound close (read cancellation, write subscriber termination)
    }

    protected enum ExpectEvent {
        NIL(null), // No event, not closed
        PCO(PROTOCOL_CLOSING_OUTBOUND),
        PCI(PROTOCOL_CLOSING_INBOUND),
        GUC(GRACEFUL_USER_CLOSING),
        CCO(CHANNEL_CLOSED_OUTBOUND),
        CCI(CHANNEL_CLOSED_INBOUND);

        @Nullable
        private final CloseEvent ce;

        ExpectEvent(@Nullable CloseEvent c) {
            this.ce = c;
        }
    }

    public NonPipelinedCloseHandlerTest(boolean client, final List<Events> events, final ExpectEvent expectEvent,
                                        final String desc, final String location) {
        h = new NonPipelinedCloseHandler(client);
        this.events = events;
        this.expectEvent = expectEvent;
        ctx = mock(ChannelHandlerContext.class);
        channel = mock(SocketChannel.class, "[id: 0xmocked, L:mocked - R:mocked]");
        when(ctx.channel()).thenReturn(channel);
        when(ctx.newPromise()).thenReturn(new DefaultChannelPromise(channel));
        when(channel.newPromise()).thenReturn(new DefaultChannelPromise(channel));

        EventExecutor exec = mock(EventExecutor.class);
        when(ctx.executor()).thenReturn(exec);
        when(exec.inEventLoop()).thenReturn(true);
        EventLoop loop = mock(EventLoop.class);
        when(channel.eventLoop()).thenReturn(loop);
        when(loop.inEventLoop()).thenReturn(true);
        ChannelPipeline pipeline = mock(ChannelPipeline.class);
        when(ctx.pipeline()).thenReturn(pipeline);
        when(channel.pipeline()).thenReturn(pipeline);
        ChannelFuture future = mock(ChannelFuture.class);
        when(channel.close()).then(__ -> {
            closed.set(true);
            return future;
        });
        h.registerEventHandler(channel, e -> {
            if (observedEvent == null) {
                observedEvent = e;
            }
        });
    }

    @Parameterized.Parameters(name = "{index}. {3} - {0} {1} = {2}")
    public static Collection<Object[]> data() {
        StackTraceElement se = Thread.currentThread().getStackTrace()[1];
        List<Object[]> params = asList(new Object[][] {
                {true, e(OB, OE, IB, IE), NIL, "sequential, no close"},
                {true, e(OB, IB, OE, IE), NIL, "interleaved, no close"},
                {true, e(OB, CI, OE, FC), NIL, "inbound close while writing"},
                {true, e(OB, UC, OE, FC), GUC, "user close while writing"},
                {true, e(OB, IC, OE, FC), PCI, "protocol inbound close while writing"},
                {true, e(OB, IS, OE, FC), CCI, "channel inbound close while writing"},
                {true, e(OB, IS, OS, FC), CCI, "channel inbound & outbound close while writing"},
                {true, e(OB, CO, FC), NIL, "outbound close while writing"},
                {true, e(OB, OC, FC), PCO, "protocol outbound close while writing"},
                {true, e(OB, OS, FC), CCO, "channel outbound close while writing"},
                {true, e(OB, IB, CI, OE, FC), NIL, "inbound close while writing & reading"},
                {true, e(OB, IB, UC, OE, IE, FC), GUC, "user close while writing & reading"},
                {true, e(OB, IB, IC, OE, FC), PCI, "protocol inbound close while writing & reading"},
                {true, e(OB, IB, IS, OE, FC), CCI, "channel inbound close while writing & reading"},
                {true, e(OB, IB, IS, OS, FC), CCI, "channel inbound & outbound close while writing & reading"},
                {true, e(OB, IB, IC, OC, FC), PCI, "protocol inbound & outbound close while writing & reading"},
                {true, e(OB, IB, CO, IE, FC), NIL, "outbound close while writing & reading"},
                {true, e(OB, IB, OC, IE, FC), PCO, "protocol outbound close while writing & reading"},
                {true, e(OB, IB, OS, IE, FC), CCO, "channel outbound close while writing & reading"},
                {true, e(OB, IB, OS, IS, FC), CCO, "channel outbound & inbound close while writing & reading"},
                {true, e(OB, IB, OC, IC, FC), PCO, "protocol outbound & inbound close while writing & reading"},
                {true, e(OB, OE, IB, IE, CO, FC), NIL, "outbound close while idle"},
                {true, e(OB, OE, IB, IE, OS, FC), CCO, "channel outbound close while idle"},
                {true, e(OB, OE, IB, IE, CI, FC), NIL, "inbound close while idle"},
                {true, e(OB, OE, IB, IE, IS, FC), CCI, "channel inbound close while idle"},
                {true, e(OB, OE, IB, IE, UC, FC), GUC, "user close while idle"},
                {true, e(OB, OE, IB, IE, IC), PCI, "protocol inbound close while idle"},
                {true, e(OB, OE, IB, IE, OC), PCO, "protocol outbound close while idle"},
                {false, e(IB, IE, OB, OE), NIL, "sequential, no close"},
                {false, e(IB, OB, IE, OE), NIL, "interleaved, no close"},
                {false, e(IB, CO, IE, FC), NIL, "outbound close while reading"},
                {false, e(IB, UC, IE, FC), GUC, "user close while reading"},
                {false, e(IB, OC, IE, FC), PCO, "protocol outbound close while reading"},
                {false, e(IB, OS, IE, FC), CCO, "channel outbound close while reading"},
                {false, e(IB, OS, IS, FC), CCO, "channel outbound & inbound close while reading"},
                {false, e(IB, CI, FC), NIL, "inbound close while reading"},
                {false, e(IB, IC, FC), PCI, "protocol inbound close while reading"},
                {false, e(IB, IS, FC), CCI, "channel inbound close while reading"},
                {false, e(IB, OB, CO, IE, FC), NIL, "outbound close while reading & writing"},
                {false, e(IB, OB, UC, IE, OE, FC), GUC, "user close while reading & writing"},
                {false, e(IB, OB, OC, IE, FC), PCO, "protocol outbound close while reading & writing"},
                {false, e(IB, OB, OS, IE, FC), CCO, "channel outbound close while reading & writing"},
                {false, e(IB, OB, OS, IS, FC), CCO, "channel outbound & inbound close while reading & writing"},
                {false, e(IB, OB, OC, IC, FC), PCO, "protocol outbound & inbound close while reading & writing"},
                {false, e(IB, OB, CI, OE, FC), NIL, "inbound close while reading & writing"},
                {false, e(IB, OB, IC, OE, FC), PCI, "protocol inbound close while reading & writing"},
                {false, e(IB, OB, IS, OE, FC), CCI, "channel inbound close while reading & writing"},
                {false, e(IB, OB, IS, OS, FC), CCI, "channel inbound & outbound close while reading & writing"},
                {false, e(IB, OB, IC, OC, FC), PCI, "protocol inbound & outbound close while reading & writing"},
                {false, e(IB, IE, OB, OE, CO, FC), NIL, "outbound close while idle"},
                {false, e(IB, IE, OB, OE, OS, FC), CCO, "channel outbound close while idle"},
                {false, e(IB, IE, OB, OE, CI, FC), NIL, "inbound close while idle"},
                {false, e(IB, IE, OB, OE, IS, FC), CCI, "channel inbound close while idle"},
                {false, e(IB, IE, OB, OE, UC), GUC, "user close while idle"},
                {false, e(IB, IE, OB, OE, IC), PCI, "protocol inbound close while idle"},
                {false, e(IB, IE, OB, OE, OC), PCO, "protocol outbound close while idle"},
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

    @Test
    public void simulate() {
        for (Events event : events) {
            switch (event) {
                case IB:
                    assertNotClosed();
                    h.protocolPayloadBeginInbound(ctx);
                    break;
                case IE:
                    assertNotClosed();
                    h.protocolPayloadEndInbound(ctx);
                    break;
                case IC:
                    h.protocolClosingInbound(ctx);
                    break;
                case OB:
                    assertNotClosed();
                    h.protocolPayloadBeginOutbound(ctx);
                    break;
                case OE:
                    assertNotClosed();
                    ChannelPromise promise = ctx.newPromise();
                    promise.trySuccess();
                    h.protocolPayloadEndOutbound(ctx, promise);
                    break;
                case OC:
                    h.protocolClosingOutbound(ctx);
                    break;
                case IS:
                    h.channelClosedInbound(ctx);
                    break;
                case OS:
                    h.channelClosedOutbound(ctx);
                    break;
                case UC:
                    h.gracefulUserClosing(channel);
                    break;
                case FC:
                    verify(channel).close();
                    break;
                case CI:
                    h.closeChannelInbound(channel);
                    break;
                case CO:
                    h.closeChannelOutbound(channel);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown: " + event);
            }
        }
        assertThat("Expected CloseEvent", observedEvent, equalTo(expectEvent.ce));
    }

    private void assertNotClosed() {
        assertFalse("Channel Closed - testcase invalid or bug?", closed.get());
    }

    private static List<Events> e(Events... args) {
        return asList(args);
    }
}
