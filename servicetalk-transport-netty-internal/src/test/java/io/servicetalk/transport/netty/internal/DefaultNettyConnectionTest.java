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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.test.internal.TestCompletableSubscriber;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;
import io.servicetalk.transport.api.ConnectionInfo.Protocol;
import io.servicetalk.transport.netty.internal.NoopTransportObserver.NoopConnectionObserver;
import io.servicetalk.transport.netty.internal.WriteStreamSubscriber.AbortedFirstWriteException;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.channels.ClosedChannelException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Executors.from;
import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Publisher.never;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.transport.netty.internal.CloseHandler.UNSUPPORTED_PROTOCOL_CLOSE_HANDLER;
import static io.servicetalk.transport.netty.internal.CloseHandler.forPipelinedRequestResponse;
import static io.servicetalk.transport.netty.internal.FlushStrategies.batchFlush;
import static io.servicetalk.transport.netty.internal.FlushStrategies.defaultFlushStrategy;
import static io.servicetalk.transport.netty.internal.FlushStrategies.flushOnEnd;
import static io.servicetalk.transport.netty.internal.OffloadAllExecutionStrategy.OFFLOAD_ALL_STRATEGY;
import static java.lang.Integer.MAX_VALUE;
import static java.nio.charset.Charset.defaultCharset;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
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
import static org.mockito.Mockito.when;

class DefaultNettyConnectionTest {

    private static final String TRAILER_MSG = "Trailer";
    private static final Buffer TRAILER = DEFAULT_ALLOCATOR.fromAscii(TRAILER_MSG);
    private TestPublisher<Buffer> publisher;
    private TestCompletableSubscriber writeListener = new TestCompletableSubscriber();
    private final TestCompletableSubscriber secondWriteListener = new TestCompletableSubscriber();
    private final TestCompletableSubscriber closeListener = new TestCompletableSubscriber();
    private final TestPublisherSubscriber<Buffer> subscriber = new TestPublisherSubscriber<>();
    private BufferAllocator allocator;
    private EmbeddedDuplexChannel channel;
    private WriteDemandEstimator demandEstimator;
    private int requestNext = MAX_VALUE;
    private DefaultNettyConnection<Buffer, Buffer> conn;

    @BeforeEach
    public void setUp() throws Exception {
        setupWithCloseHandler(__ -> UNSUPPORTED_PROTOCOL_CLOSE_HANDLER);
    }

    private void setupWithCloseHandler(Function<EmbeddedChannel, CloseHandler> closeHandlerFactory) throws Exception {
        setupWithCloseHandler(closeHandlerFactory, immediate());
    }

    private void setupWithCloseHandler(Function<EmbeddedChannel, CloseHandler> closeHandlerFactory,
                                       Executor executor) throws Exception {
        allocator = DEFAULT_ALLOCATOR;
        channel = new EmbeddedDuplexChannel(false);
        demandEstimator = mock(WriteDemandEstimator.class);
        when(demandEstimator.estimateRequestN(anyLong())).then(invocation1 -> (long) requestNext);
        conn = DefaultNettyConnection.<Buffer, Buffer>initChannel(channel, allocator, executor,
                buffer -> {
                    if ("DELIBERATE_EXCEPTION".equals(buffer.toString(US_ASCII))) {
                        throw DELIBERATE_EXCEPTION;
                    }
                    return true;
                },
                closeHandlerFactory.apply(channel), defaultFlushStrategy(), null, trailerProtocolEndEventEmitter(),
                OFFLOAD_ALL_STRATEGY, mock(Protocol.class), NoopConnectionObserver.INSTANCE, true).toFuture().get();
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
                            ctx.pipeline().fireUserEventTriggered(CloseHandler.OutboundDataEndEvent.INSTANCE);
                        }
                        ctx.write(msg, promise);
                    }
                });
    }

    @Test
    void testWritePublisher() {
        toSource(conn.write(from(newBuffer("Hello1"), newBuffer("Hello2"), TRAILER.duplicate())))
                .subscribe(writeListener);
        writeListener.awaitOnComplete();
        pollChannelAndVerifyWrites("Hello1", "Hello2", TRAILER_MSG);
    }

    @Test
    void testConcurrentWritePubAndPub() {
        toSource(conn.write(Publisher.never())).subscribe(writeListener);
        assertThat(writeListener.pollTerminal(10, MILLISECONDS), is(nullValue()));
        toSource(conn.write(Publisher.never())).subscribe(secondWriteListener);
        assertThat(secondWriteListener.awaitOnError(), instanceOf(IllegalStateException.class));
    }

    @Test
    void testSequentialPubAndPub() {
        testWritePublisher();
        writeListener = new TestCompletableSubscriber();
        testWritePublisher();
    }

    @Test
    void testWriteActiveWithPublisher() {
        toSource(conn.write(publisher)).subscribe(writeListener);
        assertThat("Unexpected write active state.", conn.isWriteActive(), is(true));
        publisher.onNext(newBuffer("Hello"));
        publisher.onNext(TRAILER.duplicate());
        publisher.onComplete();
        writeListener.awaitOnComplete();
        pollChannelAndVerifyWrites("Hello", TRAILER_MSG);
        assertThat("Unexpected write active state.", conn.isWriteActive(), is(false));
    }

    @Test
    void testPublisherErrorFailsWrite() {
        toSource(conn.write(Publisher.failed(DELIBERATE_EXCEPTION))).subscribe(writeListener);
        final Throwable error = writeListener.awaitOnError();
        assertThat(error, instanceOf(AbortedFirstWriteException.class));
        assertThat(error.getCause(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void testPublisherWithPredictor() {
        requestNext = 1;
        toSource(conn.write(publisher, FlushStrategies::defaultFlushStrategy, () -> demandEstimator))
                .subscribe(writeListener);
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
        writeListener.awaitOnComplete();
    }

    @Test
    void testUpdateFlushStrategy() {
        toSource(conn.write(from(newBuffer("Hello"), TRAILER.duplicate()))).subscribe(writeListener);
        writeListener.awaitOnComplete();
        pollChannelAndVerifyWrites("Hello", TRAILER_MSG); // Flush on each (default)

        writeListener = new TestCompletableSubscriber();
        Cancellable c = conn.updateFlushStrategy((old, __) -> batchFlush(3, never()));
        toSource(conn.write(publisher)).subscribe(writeListener);
        publisher.onNext(newBuffer("Hello1"));
        pollChannelAndVerifyWrites(); // No flush
        publisher.onNext(newBuffer("Hello2"));
        publisher.onNext(TRAILER.duplicate());
        pollChannelAndVerifyWrites("Hello1", "Hello2", TRAILER_MSG); // Batch flush of 2
        publisher.onComplete();
        writeListener.awaitOnComplete();

        c.cancel();

        writeListener = new TestCompletableSubscriber();
        toSource(conn.write(from(newBuffer("Hello3"), TRAILER.duplicate()))).subscribe(writeListener);
        writeListener.awaitOnComplete();
        pollChannelAndVerifyWrites("Hello3", TRAILER_MSG); // Reverted to flush on each
    }

    @Test
    void testRead() {
        toSource(conn.read()).subscribe(subscriber);
        Buffer expected = allocator.fromAscii("data");
        channel.writeInbound(expected.duplicate());
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));
        subscriber.awaitSubscription().request(1);
        assertThat(subscriber.takeOnNext(), is(expected));
        subscriber.awaitOnComplete();
    }

    @Test
    void testCloseAsync() {
        conn.updateFlushStrategy((__, ___) -> flushOnEnd());
        toSource(conn.write(publisher)).subscribe(writeListener);
        Buffer hello1 = newBuffer("Hello1");
        Buffer hello2 = newBuffer("Hello2");
        publisher.onNext(hello1);
        publisher.onNext(hello2);
        publisher.onNext(TRAILER.duplicate());
        toSource(conn.closeAsync()).subscribe(closeListener);
        assertThat(channel.isOpen(), is(false));
        assertThat(writeListener.awaitOnError(), instanceOf(ClosedChannelException.class));
        pollChannelAndVerifyWrites();
    }

    @Test
    void testOnClosingWithGracefulClose() throws Exception {
        setupWithCloseHandler(ch -> forPipelinedRequestResponse(true, ch.config()));
        toSource(conn.onClosing()).subscribe(closeListener);
        conn.closeAsyncGracefully().toFuture().get();
        closeListener.awaitOnComplete();
    }

    @Test
    void testOnClosingWithHardClose() throws Exception {
        setupWithCloseHandler(ch -> forPipelinedRequestResponse(true, ch.config()));
        toSource(conn.onClosing()).subscribe(closeListener);
        conn.closeAsync().toFuture().get();
        closeListener.awaitOnComplete();
    }

    @Test
    void testOnClosingWithoutUserInitiatedClose() throws Exception {
        setupWithCloseHandler(ch -> forPipelinedRequestResponse(true, ch.config()));
        toSource(conn.onClosing()).subscribe(closeListener);
        channel.close().get(); // Close and await closure.
        closeListener.awaitOnComplete();
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
        verify(demandEstimator,
                // -1 because the first request(1) does not invoke WriteDemandEstimator for the client-side
                times((hasTrailers ? 0 : 1) + items.length + channelWritabilityChangedCount - 1))
                .estimateRequestN(anyLong());
    }

    private void changeWritability(boolean writable) {
        ChannelOutboundBuffer cob = channel.unsafe().outboundBuffer();
        cob.setUserDefinedWritability(15, writable);
        channel.runPendingTasks();
    }

    @Test
    void testErrorEnrichmentWithCloseHandlerOnWriteError() throws Exception {
        setupWithCloseHandler(ch -> forPipelinedRequestResponse(true, ch.config()));
        channel.shutdownOutput().sync();
        assertThat(channel.isActive(), is(false));
        toSource(conn.write(publisher)).subscribe(writeListener);

        toSource(conn.read()).subscribe(subscriber);
        Throwable cause = writeListener.awaitOnError(); // ClosedChannelException was translated
        // Exception should be of type CloseEventObservedException
        assertThat(cause, instanceOf(RetryableClosedChannelException.class));
        assertThat(cause.getCause(), instanceOf(ClosedChannelException.class));
        assertThat(cause.getCause().getMessage(), equalTo(
                "CHANNEL_CLOSED_OUTBOUND(The transport backing this connection has been shutdown (write)) " +
                        "[id: 0xembedded, L:embedded ! R:embedded]"));
        assertThat(channel.isOpen(), is(false));
    }

    @Test
    void testTerminalPredicateThrowTerminatesReadPublisher() throws Exception {
        setupWithCloseHandler(ch -> forPipelinedRequestResponse(true, ch.config()));
        toSource(conn.read()).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        channel.writeInbound(allocator.fromAscii("DELIBERATE_EXCEPTION"));
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
        assertThat(channel.isOpen(), is(false));
    }

    @Test
    void testNoErrorEnrichmentWithoutCloseHandlerOnError() {
        channel.close().syncUninterruptibly();
        toSource(conn.write(publisher)).subscribe(writeListener);

        Throwable error = writeListener.awaitOnError();
        assertThat(error, instanceOf(RetryableClosedChannelException.class));
        Throwable cause = error.getCause();
        // Error cause should NOT be of type CloseEventObservedException
        assertThat(cause, instanceOf(StacklessClosedChannelException.class));
        assertThat(cause.getCause(), nullValue());
        assertThat(cause.getStackTrace()[0].getClassName(), equalTo(DefaultNettyConnection.class.getName()));
    }

    @Test
    void testConnectionDoesNotHoldAThread() throws Exception {
        AtomicInteger taskSubmitted = new AtomicInteger();
        ExecutorService executor = java.util.concurrent.Executors.newCachedThreadPool();
        try {
            setupWithCloseHandler(ch -> forPipelinedRequestResponse(true, ch.config()), from(task -> {
                taskSubmitted.incrementAndGet();
                executor.submit(task);
            }));
            assertThat("Unexpected tasks submitted.", taskSubmitted.get(), is(0));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void testExceptionWithNoSubscriberIsQueued() throws Exception {
        channel.pipeline().fireExceptionCaught(DELIBERATE_EXCEPTION);
        toSource(conn.read()).subscribe(subscriber);
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
        conn.onClose().toFuture().get();
    }

    @Test
    void testChannelInactiveWithNoSubscriberIsQueued() throws Exception {
        channel.close().get();
        toSource(conn.read()).subscribe(subscriber);
        assertThat(subscriber.awaitOnError(), instanceOf(ClosedChannelException.class));
        conn.onClose().toFuture().get();
    }

    @Test
    void testChannelCloseBeforeWriteComplete() {
        toSource(conn.write(publisher)).subscribe(writeListener);
        Buffer hello1 = newBuffer("Hello1");
        publisher.onNext(hello1);
        publisher.onNext(TRAILER.duplicate());
        pollChannelAndVerifyWrites("Hello1", TRAILER_MSG);

        channel.pipeline().fireChannelInactive();
        channel.close();
        publisher.onComplete();

        writeListener.awaitOnComplete();
    }

    @Test
    void testChannelCloseAfterWriteComplete() {
        toSource(conn.write(publisher)).subscribe(writeListener);
        Buffer hello1 = newBuffer("Hello1");
        publisher.onNext(hello1);
        publisher.onNext(TRAILER.duplicate());

        pollChannelAndVerifyWrites("Hello1", TRAILER_MSG);

        channel.pipeline().fireChannelInactive();
        publisher.onComplete();

        channel.close();
        writeListener.awaitOnComplete();
    }
}
