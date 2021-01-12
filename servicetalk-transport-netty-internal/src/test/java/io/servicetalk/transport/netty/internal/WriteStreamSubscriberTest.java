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

import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.transport.netty.internal.NoopTransportObserver.NoopWriteObserver;

import org.junit.Before;
import org.junit.Test;

import java.nio.channels.ClosedChannelException;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.transport.netty.internal.CloseHandler.UNSUPPORTED_PROTOCOL_CLOSE_HANDLER;
import static java.util.function.UnaryOperator.identity;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class WriteStreamSubscriberTest extends AbstractWriteTest {

    private WriteStreamSubscriber subscriber;
    private Subscription subscription;
    private CloseHandler closeHandler;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        closeHandler = mock(CloseHandler.class);
        subscriber = new WriteStreamSubscriber(channel, demandEstimator, completableSubscriber, closeHandler,
                NoopWriteObserver.INSTANCE, identity());
        subscription = mock(Subscription.class);
        when(demandEstimator.estimateRequestN(anyLong())).thenReturn(1L);
        subscriber.onSubscribe(subscription);
    }

    @Test
    public void testSingleItem() {
        WriteInfo info = writeAndFlush("Hello");
        subscriber.onComplete();
        verifyListenerSuccessful();
        verifyWriteSuccessful("Hello");
        verifyWrite(info);
        verifyZeroInteractions(closeHandler);
    }

    @Test
    public void testMultipleItem() {
        WriteInfo info1 = writeAndFlush("Hello1");
        WriteInfo info2 = writeAndFlush("Hello2");
        WriteInfo info3 = writeAndFlush("Hello3");
        subscriber.onComplete();
        verifyListenerSuccessful();
        verifyWriteSuccessful("Hello1", "Hello2", "Hello3");
        verifyWrite(info1, info2, info3);
        verifyZeroInteractions(closeHandler);
    }

    @Test
    public void testOnErrorNoWrite() throws InterruptedException {
        subscriber.onError(DELIBERATE_EXCEPTION);
        verify(this.completableSubscriber).onError(DELIBERATE_EXCEPTION);
        assertChannelClose();
    }

    @Test
    public void testOnCompleteNoWrite() {
        subscriber.onComplete();
        verify(this.completableSubscriber).onComplete();
        verifyZeroInteractions(closeHandler);
    }

    @Test
    public void testOnErrorPostWrite() throws InterruptedException {
        writeAndFlush("Hello");
        channel.flushOutbound();
        subscriber.onError(DELIBERATE_EXCEPTION);
        verify(this.completableSubscriber).onError(DELIBERATE_EXCEPTION);
        assertThat("Message not written.", channel.outboundMessages(), contains("Hello"));
        assertChannelClose();
    }

    @Test
    public void testCancelBeforeOnSubscribe() {
        subscriber = new WriteStreamSubscriber(channel, demandEstimator, completableSubscriber,
                UNSUPPORTED_PROTOCOL_CLOSE_HANDLER, NoopWriteObserver.INSTANCE, identity());
        subscription = mock(Subscription.class);
        subscriber.cancel();
        subscriber.onSubscribe(subscription);
        verify(subscription).cancel();
        verifyZeroInteractions(closeHandler);
    }

    @Test
    public void testCancelAfterOnSubscribe() {
        subscriber.cancel();
        verify(subscription).cancel();
        verifyZeroInteractions(closeHandler);
    }

    @Test
    public void testRequestMoreBeforeOnSubscribe() {
        reset(completableSubscriber);
        subscriber = new WriteStreamSubscriber(channel, demandEstimator, completableSubscriber,
                UNSUPPORTED_PROTOCOL_CLOSE_HANDLER, NoopWriteObserver.INSTANCE, identity());
        subscriber.channelWritable();
        subscription = mock(Subscription.class);
        subscriber.onSubscribe(subscription);
        WriteInfo info = writeAndFlush("Hello");
        subscriber.onComplete();
        verifyListenerSuccessful();
        verifyWriteSuccessful("Hello");
        verifyWrite(info);
        verifyZeroInteractions(closeHandler);
    }

    @Test
    public void writeFailureClosesChannel() throws Exception {
        failingWriteClosesChannel(() -> failingWriteHandler.failNextWritePromise());
    }

    @Test
    public void uncaughtWriteExceptionClosesChannel() throws Exception {
        failingWriteClosesChannel(() -> failingWriteHandler.throwFromNextWrite());
    }

    @Test
    public void onNextAfterChannelClose() {
        subscriber.channelClosed(new ClosedChannelException());
        subscriber.onNext("Hello");
        channel.runPendingTasks();
        assertThat("Unexpected message(s) written.", channel.outboundMessages(), is(empty()));
    }

    private void failingWriteClosesChannel(Runnable enableWriteFailure) throws InterruptedException {
        WriteInfo info1 = writeAndFlush("Hello1");
        verify(completableSubscriber).onSubscribe(any());
        verifyWriteSuccessful("Hello1");
        verifyWrite(info1);

        enableWriteFailure.run();
        subscriber.onNext("Hello2");
        verify(completableSubscriber).onError(DELIBERATE_EXCEPTION);
        assertChannelClose();
    }

    private void verifyWrite(WriteInfo... infos) {
        for (WriteInfo info : infos) {
            verify(demandEstimator).onItemWrite(info.messsage(), info.writeCapacityBefore(),
                    info.writeCapacityAfter());
        }
        verify(subscription, times(infos.length + 1)).request(1);
    }

    private WriteInfo writeAndFlush(String msg) {
        long pre = channel.bytesBeforeUnwritable();
        subscriber.onNext(msg);
        long post = channel.bytesBeforeUnwritable();
        channel.flushOutbound();
        return new WriteInfo(pre, post, msg);
    }

    private void assertChannelClose() throws InterruptedException {
        channel.closeFuture().sync();
        assertThat("Channel not closed on write failure.", channel.isActive(), is(false));
    }
}
