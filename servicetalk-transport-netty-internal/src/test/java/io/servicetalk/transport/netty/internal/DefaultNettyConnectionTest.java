/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.LegacyMockedCompletableListenerRule;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestPublisherSubscriber;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.transport.api.ConnectionInfo.Protocol;
import io.servicetalk.transport.netty.internal.NoopTransportObserver.NoopConnectionObserver;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.mockito.ArgumentCaptor;

import java.nio.channels.ClosedChannelException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Executors.from;
import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Publisher.never;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static io.servicetalk.transport.netty.internal.CloseHandler.UNSUPPORTED_PROTOCOL_CLOSE_HANDLER;
import static io.servicetalk.transport.netty.internal.CloseHandler.forPipelinedRequestResponse;
import static io.servicetalk.transport.netty.internal.FlushStrategies.batchFlush;
import static io.servicetalk.transport.netty.internal.FlushStrategies.defaultFlushStrategy;
import static io.servicetalk.transport.netty.internal.FlushStrategies.flushOnEnd;
import static io.servicetalk.transport.netty.internal.OffloadAllExecutionStrategy.OFFLOAD_ALL_STRATEGY;
import static java.lang.Integer.MAX_VALUE;
import static java.nio.charset.Charset.defaultCharset;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
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
import static org.mockito.Mockito.when;

public class DefaultNettyConnectionTest {

    private static final String TRAILER_MSG = "Trailer";
    private static final Buffer TRAILER = DEFAULT_ALLOCATOR.fromAscii(TRAILER_MSG);
    private TestPublisher<Buffer> publisher;
    @Rule
    public final LegacyMockedCompletableListenerRule writeListener = new LegacyMockedCompletableListenerRule();
    @Rule
    public final LegacyMockedCompletableListenerRule secondWriteListener = new LegacyMockedCompletableListenerRule();
    @Rule
    public final LegacyMockedCompletableListenerRule closeListener = new LegacyMockedCompletableListenerRule();
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    private final TestPublisherSubscriber<Buffer> subscriber = new TestPublisherSubscriber<>();
    private BufferAllocator allocator;
    private EmbeddedChannel channel;
    private WriteDemandEstimator demandEstimator;
    private int requestNext = MAX_VALUE;
    private DefaultNettyConnection<Buffer, Buffer> conn;

    @Before
    public void setUp() throws Exception {
        setupWithCloseHandler(UNSUPPORTED_PROTOCOL_CLOSE_HANDLER);
    }

    private void setupWithCloseHandler(final CloseHandler closeHandler) throws Exception {
        setupWithCloseHandler(closeHandler, immediate());
    }

    private void setupWithCloseHandler(final CloseHandler closeHandler, Executor executor) throws Exception {
        allocator = DEFAULT_ALLOCATOR;
        channel = new EmbeddedChannel();
        demandEstimator = mock(WriteDemandEstimator.class);
        when(demandEstimator.estimateRequestN(anyLong())).then(invocation1 -> (long) requestNext);
        conn = DefaultNettyConnection.<Buffer, Buffer>initChannel(channel, allocator, executor,
                buffer -> {
                    if ("DELIBERATE_EXCEPTION".equals(buffer.toString(US_ASCII))) {
                        throw DELIBERATE_EXCEPTION;
                    }
                    return true;
                },
                closeHandler, defaultFlushStrategy(), null, trailerProtocolEndEventEmitter(), OFFLOAD_ALL_STRATEGY,
                mock(Protocol.class), NoopConnectionObserver.INSTANCE, true).toFuture().get();
        publisher = new TestPublisher<>();
    }

    private static ChannelInitializer trailerProtocolEndEventEmitter() {
        return ch -> ch.pipeline()
                .addLast(new ChannelOutboundHandlerAdapter() {
                    @Override
                    public void write(final ChannelHandlerContext ctx,
                                      final Object msg,
                                      final ChannelPromise promise) {
                        if (TRAILER.equals(msg)) {
                            ctx.pipeline().fireUserEventTriggered(CloseHandler.ProtocolPayloadEndEvent.OUTBOUND);
                        }
                        ctx.write(msg, promise);
                    }
                });
    }

    @Test
    public void testWritePublisher() {
        writeListener.listen(conn.write(from(newBuffer("Hello1"), newBuffer("Hello2"), TRAILER.duplicate())))
                .verifyCompletion();
        pollChannelAndVerifyWrites("Hello1", "Hello2", TRAILER_MSG);
    }

    @Test
    public void testConcurrentWritePubAndPub() {
        writeListener.listen(conn.write(Publisher.never())).verifyNoEmissions();
        secondWriteListener.listen(conn.write(Publisher.never())).verifyFailure(IllegalStateException.class);
    }

    @Test
    public void testSequentialPubAndPub() {
        testWritePublisher();
        writeListener.reset();
        testWritePublisher();
    }

    @Test
    public void testWriteActiveWithPublisher() {
        writeListener.listen(conn.write(publisher));
        assertThat("Unexpected write active state.", conn.isWriteActive(), is(true));
        publisher.onNext(newBuffer("Hello"));
        publisher.onNext(TRAILER.duplicate());
        publisher.onComplete();
        writeListener.verifyCompletion();
        pollChannelAndVerifyWrites("Hello", TRAILER_MSG);
        assertThat("Unexpected write active state.", conn.isWriteActive(), is(false));
    }

    @Test
    public void testPublisherErrorFailsWrite() {
        writeListener.listen(conn.write(Publisher.failed(DELIBERATE_EXCEPTION)))
                .verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testPublisherWithPredictor() {
        requestNext = 1;
        writeListener.listen(conn.write(publisher, FlushStrategies::defaultFlushStrategy, () -> demandEstimator));
        requestNext = 0;
        Buffer hello1 = newBuffer("Hello1");
        Buffer hello2 = newBuffer("Hello2");
        publisher.onNext(hello1);
        changeWritability(false);
        pollChannelAndVerifyWrites("Hello1");
        requestNext = 1;
        changeWritability(true);
        publisher.onNext(hello2);
        publisher.onNext(TRAILER.duplicate());
        publisher.onComplete();
        pollChannelAndVerifyWrites("Hello2", TRAILER_MSG);
        verifyPredictorCalled(1, hello1, hello2, TRAILER);
        writeListener.verifyCompletion();
    }

    @Test
    public void testUpdateFlushStrategy() {
        writeListener.listen(conn.write(from(newBuffer("Hello"), TRAILER.duplicate())));
        writeListener.verifyCompletion();
        pollChannelAndVerifyWrites("Hello", TRAILER_MSG); // Flush on each (default)

        writeListener.reset();
        Cancellable c = conn.updateFlushStrategy((old, __) -> batchFlush(3, never()));
        writeListener.listen(conn.write(publisher));
        publisher.onNext(newBuffer("Hello1"));
        pollChannelAndVerifyWrites(); // No flush
        publisher.onNext(newBuffer("Hello2"));
        publisher.onNext(TRAILER.duplicate());
        pollChannelAndVerifyWrites("Hello1", "Hello2", TRAILER_MSG); // Batch flush of 2
        publisher.onComplete();
        writeListener.verifyCompletion();

        c.cancel();

        writeListener.reset();
        writeListener.listen(conn.write(from(newBuffer("Hello3"), TRAILER.duplicate())));
        writeListener.verifyCompletion();
        pollChannelAndVerifyWrites("Hello3", TRAILER_MSG); // Reverted to flush on each
    }

    @Test
    public void testRead() {
        toSource(conn.read()).subscribe(subscriber);
        Buffer expected = allocator.fromAscii("data");
        channel.writeInbound(expected.duplicate());
        assertThat(subscriber.takeItems(), hasSize(0));
        assertThat(subscriber.takeTerminal(), nullValue());
        subscriber.request(1);
        assertThat(subscriber.takeItems(), contains(expected));
        assertThat(subscriber.takeTerminal(), is(complete()));
    }

    @Test
    public void testCloseAsync() {
        conn.updateFlushStrategy((__, ___) -> flushOnEnd());
        writeListener.listen(conn.write(publisher));
        Buffer hello1 = newBuffer("Hello1");
        Buffer hello2 = newBuffer("Hello2");
        publisher.onNext(hello1);
        publisher.onNext(hello2);
        publisher.onNext(TRAILER.duplicate());
        closeListener.listen(conn.closeAsync());
        assertThat(channel.isOpen(), is(false));
        writeListener.verifyFailure(ClosedChannelException.class);
        pollChannelAndVerifyWrites();
    }

    @Test
    public void testOnClosingWithGracefulClose() throws Exception {
        CloseHandler closeHandler = forPipelinedRequestResponse(true, channel.config());
        setupWithCloseHandler(closeHandler);
        closeListener.listen(conn.onClosing());
        conn.closeAsyncGracefully().toFuture().get();
        closeListener.verifyCompletion();
    }

    @Test
    public void testOnClosingWithHardClose() throws Exception {
        CloseHandler closeHandler = forPipelinedRequestResponse(true, channel.config());
        setupWithCloseHandler(closeHandler);
        closeListener.listen(conn.onClosing());
        conn.closeAsync().toFuture().get();
        closeListener.verifyCompletion();
    }

    @Test
    public void testOnClosingWithoutUserInitiatedClose() throws Exception {
        CloseHandler closeHandler = forPipelinedRequestResponse(true, channel.config());
        setupWithCloseHandler(closeHandler);
        closeListener.listen(conn.onClosing());
        channel.close().get(); // Close and await closure.
        closeListener.verifyCompletion();
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
            verify(demandEstimator).onItemWrite(eq(item), anyLong(), anyLong());
        }
        final boolean hasTrailers = Arrays.asList(items).contains(TRAILER);
        verify(demandEstimator, times((hasTrailers ? 0 : 1) + items.length + channelWritabilityChangedCount))
                .estimateRequestN(anyLong());
    }

    private void changeWritability(boolean writable) {
        ChannelOutboundBuffer cob = channel.unsafe().outboundBuffer();
        cob.setUserDefinedWritability(15, writable);
        channel.runPendingTasks();
    }

    @Test
    public void testErrorEnrichmentWithCloseHandlerOnWriteError() throws Exception {
        CloseHandler closeHandler = forPipelinedRequestResponse(true, channel.config());
        setupWithCloseHandler(closeHandler);
        closeHandler.channelClosedOutbound(channel.pipeline().firstContext());
        assertThat(channel.isActive(), is(false));
        writeListener.listen(conn.write(publisher));

        ArgumentCaptor<Throwable> exCaptor = ArgumentCaptor.forClass(Throwable.class);
        toSource(conn.read()).subscribe(subscriber);
        writeListener.verifyFailure(exCaptor); // ClosedChannelException was translated

        exCaptor.getValue().printStackTrace();
        // Exception should be of type CloseEventObservedException
        assertThat(exCaptor.getValue(), instanceOf(RetryableClosureException.class));
        assertThat(exCaptor.getValue().getCause(), instanceOf(ClosedChannelException.class));
        assertThat(exCaptor.getValue().getCause().getMessage(), equalTo(
                "CHANNEL_CLOSED_OUTBOUND(The transport backing this connection has been shutdown (write)) " +
                        "[id: 0xembedded, L:embedded ! R:embedded]"));
    }

    @Test
    public void testTerminalPredicateThrowTerminatesReadPublisher() throws Exception {
        CloseHandler closeHandler = forPipelinedRequestResponse(true, channel.config());
        setupWithCloseHandler(closeHandler);

        toSource(conn.read()).subscribe(subscriber);
        subscriber.request(1);
        channel.writeInbound(allocator.fromAscii("DELIBERATE_EXCEPTION"));
        closeHandler.channelClosedInbound(channel.pipeline().firstContext());

        // TODO(scott): EmbeddedChannel doesn't support half closure so we need an alternative approach to fully
        // test half closure.
        assertThat(subscriber.takeError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testNoErrorEnrichmentWithoutCloseHandlerOnError() {
        channel.close().syncUninterruptibly();
        writeListener.listen(conn.write(publisher));

        ArgumentCaptor<Throwable> exCaptor = ArgumentCaptor.forClass(Throwable.class);
        writeListener.verifyFailure(exCaptor);

        // Exception should NOT be of type CloseEventObservedException
        assertThat(exCaptor.getValue(), instanceOf(StacklessClosedChannelException.class));
        assertThat(exCaptor.getValue().getCause(), nullValue());
        assertThat(exCaptor.getValue().getStackTrace()[0].getClassName(),
                equalTo(DefaultNettyConnection.class.getName()));
    }

    @Test
    public void testConnectionDoesNotHoldAThread() throws Exception {
        AtomicInteger taskSubmitted = new AtomicInteger();
        ExecutorService executor = java.util.concurrent.Executors.newCachedThreadPool();
        try {
            setupWithCloseHandler(forPipelinedRequestResponse(true, channel.config()), from(task -> {
                taskSubmitted.incrementAndGet();
                executor.submit(task);
            }));
            assertThat("Unexpected tasks submitted.", taskSubmitted.get(), is(0));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void testExceptionWithNoSubscriberIsQueued() throws Exception {
        channel.pipeline().fireExceptionCaught(DELIBERATE_EXCEPTION);
        toSource(conn.read()).subscribe(subscriber);
        assertThat(subscriber.takeError(), is(DELIBERATE_EXCEPTION));
        conn.onClose().toFuture().get();
    }

    @Test
    public void testChannelInactiveWithNoSubscriberIsQueued() throws Exception {
        channel.close().get();
        toSource(conn.read()).subscribe(subscriber);
        assertThat(subscriber.takeError(), instanceOf(ClosedChannelException.class));
        conn.onClose().toFuture().get();
    }

    @Test
    public void testChannelCloseBeforeWriteComplete() {
        writeListener.listen(conn.write(publisher));
        Buffer hello1 = newBuffer("Hello1");
        publisher.onNext(hello1);
        publisher.onNext(TRAILER.duplicate());
        pollChannelAndVerifyWrites("Hello1", TRAILER_MSG);

        channel.pipeline().fireChannelInactive();
        channel.close();
        publisher.onComplete();

        writeListener.verifyCompletion();
    }

    @Test
    public void testChannelCloseAfterWriteComplete() {
        writeListener.listen(conn.write(publisher));
        Buffer hello1 = newBuffer("Hello1");
        publisher.onNext(hello1);
        publisher.onNext(TRAILER.duplicate());

        pollChannelAndVerifyWrites("Hello1", TRAILER_MSG);

        channel.pipeline().fireChannelInactive();
        publisher.onComplete();

        channel.close();
        writeListener.verifyCompletion();
    }
}
