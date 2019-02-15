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
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Executor;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.Executors.from;
import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.concurrent.api.Publisher.never;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.transport.netty.internal.CloseHandler.UNSUPPORTED_PROTOCOL_CLOSE_HANDLER;
import static io.servicetalk.transport.netty.internal.CloseHandler.forPipelinedRequestResponse;
import static io.servicetalk.transport.netty.internal.FlushStrategies.batchFlush;
import static io.servicetalk.transport.netty.internal.FlushStrategies.defaultFlushStrategy;
import static io.servicetalk.transport.netty.internal.FlushStrategies.flushOnEnd;
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

public class DefaultNettyConnectionTest {

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
    private NettyConnection.RequestNSupplier requestNSupplier;
    private int requestNext = MAX_VALUE;
    private DefaultNettyConnection<Buffer, Buffer> conn;
    private NettyConnection.TerminalPredicate<Buffer> terminalPredicate;
    private TestPublisher<Buffer> readPublisher;

    @Before
    public void setUp() {
        setupWithCloseHandler(UNSUPPORTED_PROTOCOL_CLOSE_HANDLER);
    }

    private void setupWithCloseHandler(final CloseHandler closeHandler) {
        setupWithCloseHandler(closeHandler, immediate());
    }

    private void setupWithCloseHandler(final CloseHandler closeHandler, Executor executor) {
        ExecutionContext executionContext = mock(ExecutionContext.class);
        when(executionContext.executor()).thenReturn(executor);
        allocator = DEFAULT_ALLOCATOR;
        channel = new EmbeddedChannel();
        context = mock(ConnectionContext.class);
        when(context.onClose()).thenReturn(new NettyFutureCompletable(channel::closeFuture).publishOn(executor));
        when(context.closeAsync()).thenReturn(new NettyFutureCompletable(channel::close));
        when(context.executionContext()).thenReturn(executionContext);
        requestNSupplier = mock(NettyConnection.RequestNSupplier.class);
        when(requestNSupplier.requestNFor(anyLong())).then(invocation1 -> (long) requestNext);
        terminalPredicate = new NettyConnection.TerminalPredicate<>(o -> false);
        readPublisher = new TestPublisher<Buffer>().sendOnSubscribe();
        conn = new DefaultNettyConnection<>(channel, context, readPublisher, terminalPredicate, closeHandler,
                defaultFlushStrategy());
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
        final NettyPipelinedConnection<Buffer, Buffer> c = new DefaultNettyPipelinedConnection<>(conn, 2);
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
        secondWriteListener.listen(conn.writeAndFlush(newBuffer("Hello")))
                .verifyFailure(IllegalStateException.class);
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
        secondWriteListener.listen(conn.writeAndFlush(newBuffer("Hello")))
                .verifyFailure(IllegalStateException.class);
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
        writeListener.listen(conn.writeAndFlush(Single.error(DELIBERATE_EXCEPTION)))
                .verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testPublisherWithPredictor() {
        requestNext = 1;
        writeListener.listen(conn.write(publisher, () -> requestNSupplier));
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
    public void testUpdateFlushStrategy() {
        writeListener.listen(conn.write(just(newBuffer("Hello"))));
        writeListener.verifyCompletion();
        pollChannelAndVerifyWrites("Hello"); // Flush on each (default)

        writeListener.reset();
        Cancellable c = conn.updateFlushStrategy(old -> batchFlush(2, never()));
        writeListener.listen(conn.write(publisher));
        publisher.sendItems(newBuffer("Hello1"));
        pollChannelAndVerifyWrites(); // No flush
        publisher.sendItems(newBuffer("Hello2"));
        pollChannelAndVerifyWrites("Hello1", "Hello2"); // Batch flush of 2
        publisher.onComplete();
        writeListener.verifyCompletion();

        c.cancel();

        writeListener.reset();
        writeListener.listen(conn.write(just(newBuffer("Hello3"))));
        writeListener.verifyCompletion();
        pollChannelAndVerifyWrites("Hello3"); // Reverted to flush on each
    }

    @Test
    public void testRead() {
        subscriberRule.subscribe(conn.read());
        readPublisher.onComplete();
        subscriberRule.verifySuccess();
    }

    @Test
    public void testCloseAsync() {
        conn.updateFlushStrategy(fs -> flushOnEnd());
        writeListener.listen(conn.write(publisher));
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
        CloseHandler closeHandler = forPipelinedRequestResponse(true);
        setupWithCloseHandler(closeHandler);
        closeListener.listen(conn.onClosing());
        conn.closeAsyncGracefully().toFuture().get();
        closeListener.verifyCompletion();
    }

    @Test
    public void testOnClosingWithHardClose() throws Exception {
        CloseHandler closeHandler = forPipelinedRequestResponse(true);
        setupWithCloseHandler(closeHandler);
        closeListener.listen(conn.onClosing());
        conn.closeAsync().toFuture().get();
        closeListener.verifyCompletion();
    }

    @Test
    public void testOnClosingWithoutUserInitiatedClose() throws Exception {
        CloseHandler closeHandler = forPipelinedRequestResponse(true);
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
        verify(requestNSupplier, times(1 + items.length + channelWritabilityChangedCount))
                .requestNFor(anyLong());
    }

    private void changeWritability(boolean writable) {
        ChannelOutboundBuffer cob = channel.unsafe().outboundBuffer();
        cob.setUserDefinedWritability(15, writable);
        channel.runPendingTasks();
    }

    @Test
    public void testErrorEnrichmentWithCloseHandlerOnWriteError() {
        CloseHandler closeHandler = forPipelinedRequestResponse(true);
        setupWithCloseHandler(closeHandler);
        closeHandler.channelClosedOutbound(channel.pipeline().firstContext());
        assertThat(channel.isActive(), is(false));
        writeListener.listen(conn.write(publisher));

        ArgumentCaptor<Throwable> exCaptor = ArgumentCaptor.forClass(Throwable.class);
        subscriberRule.subscribe(conn.read());
        writeListener.verifyFailure(exCaptor); // ClosedChannelException was translated

        // Exception should be of type CloseEventObservedException
        assertThat(exCaptor.getValue(), instanceOf(ClosedChannelException.class));
        assertThat(exCaptor.getValue().getCause(), instanceOf(ClosedChannelException.class));
        assertThat(exCaptor.getValue().getMessage(), equalTo(
                "CHANNEL_CLOSED_OUTBOUND(The transport backing this connection has been shutdown (write)) " +
                        "[id: 0xembedded, L:embedded ! R:embedded]"));
    }

    @Test
    public void testErrorEnrichmentWithCloseHandlerOnReadError() {
        CloseHandler closeHandler = forPipelinedRequestResponse(true);
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
        assertThat(exCaptor.getValue().getMessage(), equalTo(
                "CHANNEL_CLOSED_INBOUND(The transport backing this connection has been shutdown (read)) " +
                        "[id: 0xembedded, L:embedded ! R:embedded]"));
    }

    @Test
    public void testNoErrorEnrichmentWithoutCloseHandlerOnError() {
        channel.close().syncUninterruptibly();
        writeListener.listen(conn.write(publisher));

        ArgumentCaptor<Throwable> exCaptor = ArgumentCaptor.forClass(Throwable.class);
        writeListener.verifyFailure(exCaptor);

        // Exception should NOT be of type CloseEventObservedException
        assertThat(exCaptor.getValue(), instanceOf(ClosedChannelException.class));
        assertThat(exCaptor.getValue().getCause(), nullValue());
        assertThat(exCaptor.getValue().getStackTrace()[0].getClassName(), equalTo(DefaultNettyConnection.class.getName()));
    }

    @Test
    public void testConnectionDoesNotHoldAThread() {
        AtomicInteger taskSubmitted = new AtomicInteger();
        ExecutorService executor = java.util.concurrent.Executors.newCachedThreadPool();
        try {
            setupWithCloseHandler(forPipelinedRequestResponse(true), from(task -> {
                taskSubmitted.incrementAndGet();
                executor.submit(task);
            }));
            assertThat("Unexpected tasks submitted.", taskSubmitted.get(), is(0));
        } finally {
            executor.shutdownNow();
        }
    }
}
