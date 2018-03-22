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

import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.embedded.EmbeddedChannel;
import io.servicetalk.buffer.Buffer;
import io.servicetalk.buffer.BufferAllocator;
import io.servicetalk.concurrent.api.MockedCompletableListenerRule;
import io.servicetalk.concurrent.api.MockedSubscriberRule;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.transport.api.ConnectionContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT;
import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.api.Publisher.empty;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.transport.api.FlushStrategy.defaultFlushStrategy;
import static io.servicetalk.transport.netty.internal.ReadAwareFlushStrategyHolder.flushOnReadComplete;
import static java.lang.Integer.MAX_VALUE;
import static java.nio.charset.Charset.defaultCharset;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
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
    public final MockedSubscriberRule<Buffer> subscriberRule = new MockedSubscriberRule<>();

    private BufferAllocator allocator;
    private EmbeddedChannel channel;
    private ConnectionContext context;
    private Connection.RequestNSupplier requestNSupplier;
    private int requestNext = MAX_VALUE;
    private NettyConnection<Buffer, Buffer> conn;

    @Before
    public void setUp() {
        allocator = DEFAULT.getAllocator();
        channel = new EmbeddedChannel();
        context = mock(ConnectionContext.class);
        when(context.closeAsync()).thenReturn(new NettyFutureCompletable(channel::close));
        requestNSupplier = mock(Connection.RequestNSupplier.class);
        when(requestNSupplier.getRequestNFor(anyLong())).then(invocation1 -> (long) requestNext);
        conn = new NettyConnection<>(channel, context, empty(), new Connection.TerminalPredicate<>(o -> false));
        publisher = new TestPublisher<Buffer>().sendOnSubscribe();
    }

    @Test
    public void testWritePublisher() {
        writeListener.listen(conn.write(from(newBuffer("Hello1"), newBuffer("Hello2")), defaultFlushStrategy()))
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
        subscriberRule.subscribe(c.request(newBuffer("Hello"))).request(1).verifySuccess();
    }

    @Test
    public void testConcurrentWritePubAndPub() {
        writeListener.listen(conn.write(Publisher.never(), defaultFlushStrategy())).verifyNoEmissions();
        secondWriteListener.listen(conn.write(Publisher.never(), defaultFlushStrategy())).verifyFailure(IllegalStateException.class);
    }

    @Test
    public void testConcurrentWritePubAndSingle() {
        writeListener.listen(conn.write(Publisher.never(), defaultFlushStrategy())).verifyNoEmissions();
        secondWriteListener.listen(conn.writeAndFlush(Single.never())).verifyFailure(IllegalStateException.class);
    }

    @Test
    public void testConcurrentWritePubAndItem() {
        writeListener.listen(conn.write(Publisher.never(), defaultFlushStrategy())).verifyNoEmissions();
        secondWriteListener.listen(conn.writeAndFlush(newBuffer("Hello"))).verifyFailure(IllegalStateException.class);
    }

    @Test
    public void testConcurrentWriteSingleAndPub() {
        writeListener.listen(conn.writeAndFlush(Single.never())).verifyNoEmissions();
        secondWriteListener.listen(conn.write(Publisher.never(), defaultFlushStrategy())).verifyFailure(IllegalStateException.class);
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
        writeListener.listen(conn.write(publisher, defaultFlushStrategy()));
        assertThat("Unexpected write active state.", conn.isWriteActive(), is(true));
        publisher.sendItems(newBuffer("Hello")).onComplete();
        writeListener.verifyCompletion();
        pollChannelAndVerifyWrites("Hello");
        assertThat("Unexpected write active state.", conn.isWriteActive(), is(false));
    }

    @Test
    public void testPublisherErrorFailsWrite() {
        writeListener.listen(conn.write(Publisher.error(DELIBERATE_EXCEPTION), defaultFlushStrategy())).verifyFailure(DELIBERATE_EXCEPTION);
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
        subscriberRule.subscribe(conn.read()).verifySuccess();
    }

    @Test
    public void testContextDelegation() {
        conn.getAllocator();
        verify(context).getAllocator();
        verifyNoMoreInteractions(context);
        conn.getLocalAddress();
        verify(context).getLocalAddress();
        verifyNoMoreInteractions(context);
        conn.getRemoteAddress();
        verify(context).getRemoteAddress();
        verifyNoMoreInteractions(context);
        conn.getSslSession();
        verify(context).getSslSession();
        verifyNoMoreInteractions(context);
        conn.closeAsync();
        verify(context).closeAsync();
        verifyNoMoreInteractions(context);
        conn.onClose();
        verify(context).onClose();
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
}
