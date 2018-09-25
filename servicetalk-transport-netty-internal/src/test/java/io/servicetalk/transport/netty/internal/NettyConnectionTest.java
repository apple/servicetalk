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
package io.servicetalk.transport.netty.internal;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.api.MockedCompletableListenerRule;
import io.servicetalk.concurrent.api.MockedSubscriberRule;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.nio.channels.ClosedChannelException;
import java.util.concurrent.CompletableFuture;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.transport.netty.internal.CloseHandler.CloseEvent.CHANNEL_CLOSED_INBOUND;
import static io.servicetalk.transport.netty.internal.CloseHandler.CloseEvent.CHANNEL_CLOSED_OUTBOUND;
import static io.servicetalk.transport.netty.internal.CloseHandler.NOOP_CLOSE_HANDLER;
import static io.servicetalk.transport.netty.internal.FlushStrategy.defaultFlushStrategy;
import static io.servicetalk.transport.netty.internal.FlushStrategy.flushBeforeEnd;
import static io.servicetalk.transport.netty.internal.FlushStrategy.flushOnEach;
import static io.servicetalk.transport.netty.internal.ReadAwareFlushStrategyHolder.flushOnReadComplete;
import static java.lang.Integer.MAX_VALUE;
import static java.nio.charset.Charset.defaultCharset;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class NettyConnectionTest {

    private TestPublisher<Buffer> publisher;
    @Rule
    public final MockedCompletableListenerRule writeListener = new MockedCompletableListenerRule();
    @Rule
    public final MockedCompletableListenerRule secondWriteListener = new MockedCompletableListenerRule();
    @Rule
    public final MockedCompletableListenerRule closeListener = new MockedCompletableListenerRule();
    @Rule
    public final MockedSubscriberRule<Buffer> subscriberRule = new MockedSubscriberRule<>();

    private BufferAllocator allocator;
    private EmbeddedChannel channel;
    private ConnectionContext context;
    private Connection.RequestNSupplier requestNSupplier;
    private int requestNext = MAX_VALUE;
    private NettyConnection<Buffer, Buffer> conn;
    private Connection.TerminalPredicate<Buffer> terminalPredicate;
    private TestPublisher<Buffer> readPublisher;

    @Before
    public void setUp() {
        setupWithCloseHandler(NOOP_CLOSE_HANDLER);
    }

    private void setupWithCloseHandler(final CloseHandler closeHandler) {
        ExecutionContext executionContext = mock(ExecutionContext.class);
        when(executionContext.executor()).thenReturn(immediate());
        allocator = DEFAULT_ALLOCATOR;
        channel = new EmbeddedChannel();
        context = mock(ConnectionContext.class);
        when(context.onClose()).thenReturn(new NettyFutureCompletable(channel::closeFuture));
        when(context.closeAsync()).thenReturn(new NettyFutureCompletable(channel::close));
        when(context.executionContext()).thenReturn(executionContext);
        requestNSupplier = mock(Connection.RequestNSupplier.class);
        when(requestNSupplier.getRequestNFor(anyLong())).then(invocation1 -> (long) requestNext);
        terminalPredicate = new Connection.TerminalPredicate<>(o -> false);
        readPublisher = new TestPublisher<Buffer>().sendOnSubscribe();
        conn = new NettyConnection<>(channel, context, readPublisher, terminalPredicate, closeHandler);
        publisher = new TestPublisher<Buffer>().sendOnSubscribe();
    }

    @Test
    public void testWritePublisher() {
        writeListener.listen(conn.write(from(newBuffer("Hello1"), newBuffer("Hello2"))))
                .verifyCompletion();
        pollChannelAndVerifyWrites("Hello1", "Hello2");
    }

    @Test
    public void testWriteSingle() {
        writeListener.listen(conn.writeAndFlush(Single.success(newBuffer("Hello"))))
                .verifyCompletion();
        pollChannelAndVerifyWrites("Hello");
    }

    @Test
    public void testWriteItem() {
        writeListener.listen(conn.writeAndFlush(newBuffer("Hello"))).verifyCompletion();
        pollChannelAndVerifyWrites("Hello");
    }

    @Test
    public void testAsPipelinedConnection() {
        final PipelinedConnection<Buffer, Buffer> c = new DefaultPipelinedConnection<>(conn, 2);
        subscriberRule.subscribe(c.request(newBuffer("Hello"))).request(1);
        readPublisher.onComplete();
        subscriberRule.verifySuccess();
    }

    @Test
    public void testConcurrentWritePubAndPub() {
        writeListener.listen(conn.write(Publisher.never())).verifyNoEmissions();
        secondWriteListener.listen(conn.write(Publisher.never())).verifyFailure(IllegalStateException.class);
    }

    @Test
    public void testConcurrentWritePubAndSingle() {
        writeListener.listen(conn.write(Publisher.never())).verifyNoEmissions();
        secondWriteListener.listen(conn.writeAndFlush(Single.never())).verifyFailure(IllegalStateException.class);
    }

    @Test
    public void testConcurrentWritePubAndItem() {
        writeListener.listen(conn.write(Publisher.never())).verifyNoEmissions();
        secondWriteListener.listen(conn.writeAndFlush(newBuffer("Hello"))).verifyFailure(IllegalStateException.class);
    }

    @Test
    public void testConcurrentWriteSingleAndPub() {
        writeListener.listen(conn.writeAndFlush(Single.never())).verifyNoEmissions();
        secondWriteListener.listen(conn.write(Publisher.never())).verifyFailure(IllegalStateException.class);
    }

    @Test
    public void testConcurrentWriteSingleAndSingle() {
        writeListener.listen(conn.writeAndFlush(Single.never())).verifyNoEmissions();
        secondWriteListener.listen(conn.writeAndFlush(Single.never())).verifyFailure(IllegalStateException.class);
    }

    @Test
    public void testConcurrentWriteSingleAndItem() {
        writeListener.listen(conn.writeAndFlush(Single.never())).verifyNoEmissions();
        secondWriteListener.listen(conn.writeAndFlush(newBuffer("Hello"))).verifyFailure(IllegalStateException.class);
    }

    @Test
    public void testSequentialPubAndPub() {
        testWritePublisher();
        writeListener.reset();
        testWritePublisher();
    }

    @Test
    public void testSequentialPubAndSingle() {
        testWritePublisher();
        writeListener.reset();
        testWriteSingle();
    }

    @Test
    public void testSequentialPubAndItem() {
        testWritePublisher();
        writeListener.reset();
        testWriteItem();
    }

    @Test
    public void testSequentialSingleAndPub() {
        testWriteSingle();
        writeListener.reset();
        testWritePublisher();
    }

    @Test
    public void testSequentialSingleAndSingle() {
        testWriteSingle();
        writeListener.reset();
        testWriteSingle();
    }

    @Test
    public void testSequentialSingleAndItem() {
        testWriteSingle();
        writeListener.reset();
        testWriteItem();
    }

    @Test
    public void testSequentialItemAndPub() {
        testWriteItem();
        writeListener.reset();
        testWritePublisher();
    }

    @Test
    public void testSequentialItemAndSingle() {
        testWriteItem();
        writeListener.reset();
        testWriteSingle();
    }

    @Test
    public void testSequentialItemAndItem() {
        testWriteItem();
        writeListener.reset();
        testWriteItem();
    }

    @Test
    public void testWriteActiveWithSingle() throws Exception {
        CompletableFuture<Single.Subscriber<? super Buffer>> capturedListener = new CompletableFuture<>();
        Single<Buffer> toWrite = new Single<Buffer>() {
            @Override
            protected void handleSubscribe(Subscriber<? super Buffer> singleSubscriber) {
                singleSubscriber.onSubscribe(IGNORE_CANCEL);
                capturedListener.complete(singleSubscriber);
            }
        };
        writeListener.listen(conn.writeAndFlush(toWrite)).verifyNoEmissions();
        capturedListener.get(); // make sure subscribe() is called
        assertThat("Unexpected write active state.", conn.isWriteActive(), is(true));
        capturedListener.get().onSuccess(newBuffer("Hello"));
        writeListener.verifyCompletion();
        pollChannelAndVerifyWrites("Hello");
        assertThat("Unexpected write active state.", conn.isWriteActive(), is(false));
    }

    @Test
    public void testWriteActiveWithPublisher() {
        writeListener.listen(conn.write(publisher));
        assertThat("Unexpected write active state.", conn.isWriteActive(), is(true));
        publisher.sendItems(newBuffer("Hello")).onComplete();
        writeListener.verifyCompletion();
        pollChannelAndVerifyWrites("Hello");
        assertThat("Unexpected write active state.", conn.isWriteActive(), is(false));
    }

    @Test
    public void testPublisherErrorFailsWrite() {
        writeListener.listen(conn.write(Publisher.error(DELIBERATE_EXCEPTION)))
                .verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testSingleErrorFailsWrite() {
        writeListener.listen(conn.writeAndFlush(Single.error(DELIBERATE_EXCEPTION))).verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testPublisherWithPredictor() {
        requestNext = 1;
        writeListener.listen(conn.write(publisher, defaultFlushStrategy(), () -> requestNSupplier));
        requestNext = 0;
        Buffer hello1 = newBuffer("Hello1");
        Buffer hello2 = newBuffer("Hello2");
        publisher.sendItems(hello1);
        changeWritability(false);
        pollChannelAndVerifyWrites("Hello1");
        requestNext = 1;
        changeWritability(true);
        publisher.sendItems(hello2);
        publisher.onComplete();
        pollChannelAndVerifyWrites("Hello2");
        verifyPredictorCalled(1, hello1, hello2);
        writeListener.verifyCompletion();
    }

    @Test
    public void testFlushOnReadComplete() {
        writeListener.listen(conn.write(publisher, flushOnReadComplete(10)));
        channel.pipeline().fireChannelRead("ReadingMessage");
        publisher.sendItems(newBuffer("Hello"));
        pollChannelAndVerifyWrites();
        channel.pipeline().fireChannelReadComplete();
        pollChannelAndVerifyWrites("Hello");
        publisher.onComplete();
        writeListener.verifyCompletion();
    }

    @Test
    public void testRead() {
        subscriberRule.subscribe(conn.read());
        readPublisher.onComplete();
        subscriberRule.verifySuccess();
    }

    @Test
    public void testCloseAsync() {
        writeListener.listen(conn.write(publisher, flushBeforeEnd()));
        Buffer hello1 = newBuffer("Hello1");
        Buffer hello2 = newBuffer("Hello2");
        publisher.sendItems(hello1);
        publisher.sendItems(hello2);
        closeListener.listen(conn.closeAsync());
        assertThat(channel.isOpen(), is(false));
        writeListener.verifyFailure(ClosedChannelException.class);
        pollChannelAndVerifyWrites();
    }

    @Test
    public void testOnClosingWithGracefulClose() throws Exception {
        CloseHandler closeHandler = CloseHandler.forPipelinedRequestResponse(true);
        setupWithCloseHandler(closeHandler);
        closeListener.listen(conn.onClosing());
        awaitIndefinitely(conn.closeAsyncGracefully());
        closeListener.verifyCompletion();
    }

    @Test
    public void testOnClosingWithHardClose() throws Exception {
        CloseHandler closeHandler = CloseHandler.forPipelinedRequestResponse(true);
        setupWithCloseHandler(closeHandler);
        closeListener.listen(conn.onClosing());
        awaitIndefinitely(conn.closeAsync());
        closeListener.verifyCompletion();
    }

    @Test
    public void testOnClosingWithoutUserInitiatedClose() throws Exception {
        CloseHandler closeHandler = CloseHandler.forPipelinedRequestResponse(true);
        setupWithCloseHandler(closeHandler);
        closeListener.listen(conn.onClosing());
        channel.close().get(); // Close and await closure.
        closeListener.verifyCompletion();
    }

    @Test
    public void testContextDelegation() {
        conn.executionContext();
        verify(context).executionContext();
        verifyNoMoreInteractions(context);
        conn.localAddress();
        verify(context).localAddress();
        verifyNoMoreInteractions(context);
        conn.remoteAddress();
        verify(context).remoteAddress();
        verifyNoMoreInteractions(context);
        conn.sslSession();
        verify(context).sslSession();
        verifyNoMoreInteractions(context);
        conn.closeAsync().subscribe();
        verify(context).closeAsync();
        verifyNoMoreInteractions(context);
        conn.onClose();
        verify(context, times(1)).onClose();
        verifyNoMoreInteractions(context);
    }

    private Buffer newBuffer(String data) {
        return allocator.fromAscii(data);
    }

    private void pollChannelAndVerifyWrites(String... items) {
        assertThat("Unexpected items in outbound buffer.", channel.outboundMessages(), hasSize(items.length));
        for (String item : items) {
            Object written = channel.readOutbound();
            assertThat("Unexpected item found in outbound buffer.", written, instanceOf(Buffer.class));
            Buffer b = (Buffer) written;
            assertThat("Unexpected item found in outbound buffer.", b.toString(defaultCharset()), is(item));
        }
    }

    private void verifyPredictorCalled(int channelWritabilityChangedCount, Buffer... items) {
        for (Buffer item : items) {
            verify(requestNSupplier).onItemWrite(eq(item), anyLong(), anyLong());
        }
        verify(requestNSupplier, times(1 + items.length + channelWritabilityChangedCount)).getRequestNFor(anyLong());
    }

    private void changeWritability(boolean writable) {
        ChannelOutboundBuffer cob = channel.unsafe().outboundBuffer();
        cob.setUserDefinedWritability(15, writable);
        channel.runPendingTasks();
    }

    @Test
    public void testErrorEnrichmentWithCloseHandlerOnWriteError() {
        CloseHandler closeHandler = CloseHandler.forPipelinedRequestResponse(true);
        setupWithCloseHandler(closeHandler);
        closeHandler.channelClosedOutbound(channel.pipeline().firstContext());
        assertThat(channel.isActive(), is(false));
        writeListener.listen(conn.write(publisher, flushOnEach()));

        ArgumentCaptor<Throwable> exCaptor = ArgumentCaptor.forClass(Throwable.class);
        subscriberRule.subscribe(conn.read());
        writeListener.verifyFailure(exCaptor); // ClosedChannelException was translated

        // Exception should be of type CloseEventObservedException
        assertThat(exCaptor.getValue(), instanceOf(ClosedChannelException.class));
        assertThat(exCaptor.getValue().getCause(), instanceOf(ClosedChannelException.class));
        assertThat(exCaptor.getValue().getMessage(), equalTo(CHANNEL_CLOSED_OUTBOUND.name()));
    }

    @Test
    public void testErrorEnrichmentWithCloseHandlerOnReadError() {
        CloseHandler closeHandler = CloseHandler.forPipelinedRequestResponse(true);
        setupWithCloseHandler(closeHandler);
        closeHandler.channelClosedInbound(channel.pipeline().firstContext());
        assertThat(channel.isActive(), is(false));

        ArgumentCaptor<Throwable> exCaptor = ArgumentCaptor.forClass(Throwable.class);
        subscriberRule.subscribe(conn.read());
        readPublisher.onError(DELIBERATE_EXCEPTION);
        subscriberRule.verifyFailure(exCaptor); // DELIBERATE_EXCEPTION was translated

        // Exception should be of type CloseEventObservedException
        assertThat(exCaptor.getValue(), instanceOf(ClosedChannelException.class));
        assertThat(exCaptor.getValue().getCause(), equalTo(DELIBERATE_EXCEPTION));
        assertThat(exCaptor.getValue().getMessage(), equalTo(CHANNEL_CLOSED_INBOUND.name()));
    }

    @Test
    public void testNoErrorEnrichmentWithoutCloseHandlerOnError() {
        channel.close().syncUninterruptibly();
        writeListener.listen(conn.write(publisher, flushOnEach()));

        ArgumentCaptor<Throwable> exCaptor = ArgumentCaptor.forClass(Throwable.class);
        writeListener.verifyFailure(exCaptor);

        // Exception should NOT be of type CloseEventObservedException
        assertThat(exCaptor.getValue(), instanceOf(ClosedChannelException.class));
        assertThat(exCaptor.getValue().getCause(), nullValue());
        assertThat(exCaptor.getValue().getStackTrace()[0].getClassName(), equalTo(NettyConnection.class.getName()));
    }
}
