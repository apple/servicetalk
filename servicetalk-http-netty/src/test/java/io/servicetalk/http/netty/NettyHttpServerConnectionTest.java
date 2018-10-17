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
package io.servicetalk.http.netty;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.PublisherRule;
import io.servicetalk.concurrent.api.TestSingle;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.DefaultStreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.netty.internal.ExecutionContextRule;
import io.servicetalk.transport.netty.internal.FlushStrategy;
import io.servicetalk.transport.netty.internal.MockFlushStrategy;
import io.servicetalk.transport.netty.internal.NettyConnection.TerminalPredicate;
import io.servicetalk.transport.netty.internal.NettyConnectionContext;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.LinkedBlockingQueue;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.http.api.HttpRequestMethods.GET;
import static io.servicetalk.http.netty.DefaultHttpServiceContext.newInstance;
import static io.servicetalk.http.netty.NettyHttpServerConnection.newConnection;
import static io.servicetalk.transport.netty.internal.CloseHandler.UNSUPPORTED_PROTOCOL_CLOSE_HANDLER;
import static io.servicetalk.transport.netty.internal.ExecutionContextRule.immediate;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NettyHttpServerConnectionTest {

    private static final Object FLUSH = new Object();

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final PublisherRule<Object> connReadPublisherRule = new PublisherRule<>();
    @Rule
    public final PublisherRule<Buffer> responsePublisherRule = new PublisherRule<>();
    @Rule
    public final ExecutionContextRule contextRule = immediate();

    private WriteTracker tracker;
    private TestSingle<StreamingHttpResponse> responseSingle;
    private MockFlushStrategy originalStrategy;
    private MockFlushStrategy customStrategy;
    private StreamingHttpService httpService;
    private static StreamingHttpRequestResponseFactory requestResponseFactory =
            new DefaultStreamingHttpRequestResponseFactory(DEFAULT_ALLOCATOR, DefaultHttpHeadersFactory.INSTANCE);

    @Before
    public void setUp() {
        tracker = new WriteTracker();
        EmbeddedChannel channel = new EmbeddedChannel(tracker);
        ConnectionContext ctx = mock(ConnectionContext.class);
        when(ctx.executionContext()).thenReturn(contextRule);
        DefaultHttpServiceContext serviceContext = newInstance(ctx, DefaultHttpHeadersFactory.INSTANCE);

        httpService = mock(StreamingHttpService.class);
        responseSingle = new TestSingle<>();
        when(httpService.handle(any(), any(), any())).thenReturn(responseSingle);
        originalStrategy = new MockFlushStrategy();
        customStrategy = new MockFlushStrategy();
        final NettyHttpServerConnection conn = newConnection(channel, connReadPublisherRule.getPublisher(),
                new TerminalPredicate<>(obj -> obj instanceof HttpHeaders), UNSUPPORTED_PROTOCOL_CLOSE_HANDLER,
                serviceContext, httpService, originalStrategy, DefaultHttpHeadersFactory.INSTANCE);
        conn.process().subscribe();
    }

    @Test
    public void updateFlushStrategy() throws Exception {
        originalStrategy.verifyApplied();
        originalStrategy.reset();
        NettyConnectionContext ctx = invokeService();
        Cancellable c = ctx.updateFlushStrategy(current -> customStrategy);

        sendResponse();
        originalStrategy.verifyNotApplied();
        FlushStrategy.FlushSender flushSender = customStrategy.verifyApplied();
        customStrategy.verifyWriteStarted();
        tracker.assertAWrite(instanceOf(HttpResponseMetaData.class));
        customStrategy.verifyItemWritten(1);
        customStrategy.verifyNoMoreInteractions();

        flushSender.flush();
        tracker.assertAFlush();

        c.cancel();
        originalStrategy.verifyApplied();
        customStrategy.verifyWriteTerminated();
        customStrategy.verifyNoMoreInteractions();

        responsePublisherRule.complete();
        tracker.assertAWrite(instanceOf(HttpHeaders.class));
        originalStrategy.verifyApplied().flush();
        tracker.assertAFlush();
        originalStrategy.verifyWriteStarted();
        originalStrategy.verifyItemWritten(1);
        originalStrategy.verifyWriteTerminated();
        customStrategy.verifyNoMoreInteractions();
    }

    private NettyConnectionContext invokeService() {
        ArgumentCaptor<NettyHttpServerConnection> ctxCaptor = forClass(NettyHttpServerConnection.class);
        HttpRequestMetaData meta = requestResponseFactory.newRequest(GET, "/");
        connReadPublisherRule.sendItems(meta).complete();
        verify(httpService).handle(ctxCaptor.capture(), any(), any());
        return ctxCaptor.getValue();
    }

    private void sendResponse() {
        responseSingle.onSuccess(requestResponseFactory.ok().payloadBody(responsePublisherRule.getPublisher()));
    }

    private static final class WriteTracker extends ChannelOutboundHandlerAdapter {
        // Since we use embedded channel, we do not need to do anything special to ensure visibility of the contents of
        // this list.
        private final LinkedBlockingQueue<Object> writeEvents = new LinkedBlockingQueue<>();

        @Override
        public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise)
                throws Exception {
            writeEvents.add(msg);
            super.write(ctx, msg, promise);
        }

        @Override
        public void flush(final ChannelHandlerContext ctx) throws Exception {
            writeEvents.add(FLUSH);
            super.flush(ctx);
        }

        void assertAWrite(final Matcher<Object> writtenItemMatcher) throws Exception {
            assertWrittenItem(writtenItemMatcher);
        }

        void assertAFlush() throws Exception {
            assertThat("Flush not found.", writeEvents.take(), equalTo(FLUSH));
        }

        private void assertWrittenItem(final Matcher<Object> writtenItemMatcher) throws Exception {
            Object take = writeEvents.take();
            assertThat("Unexpected object written.", take, writtenItemMatcher);
        }
    }
}
