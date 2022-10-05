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
import io.servicetalk.transport.netty.internal.WriteStreamSubscriber.AbortedFirstWriteException;

import io.netty.channel.Channel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.nio.channels.ClosedChannelException;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.lang.Long.MAX_VALUE;
import static java.util.function.UnaryOperator.identity;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class WriteStreamSubscriberTest extends AbstractWriteTest {

    private final Subscription subscription = mock(Subscription.class);
    private final CloseHandler closeHandler = mock(CloseHandler.class);
    private WriteStreamSubscriber subscriber;

    void setUp(boolean isClient, boolean shouldWait) {
        subscriber = new WriteStreamSubscriber(channel, demandEstimator, completableSubscriber, closeHandler,
                NoopWriteObserver.INSTANCE, identity(), isClient, __ -> shouldWait);
        when(demandEstimator.estimateRequestN(anyLong())).thenReturn(1L);
        subscriber.onSubscribe(subscription);
    }

    @Test
    void testSingleItem() {
        setUp(false, false);
        WriteInfo info = writeAndFlush("Hello");
        subscriber.onComplete();
        verifyListenerSuccessful();
        verifyWriteSuccessful("Hello");
        verifyWrite(info);
        verifyNoInteractions(closeHandler);
    }

    @Test
    void testMultipleItem() {
        setUp(false, false);
        WriteInfo info1 = writeAndFlush("Hello1");
        WriteInfo info2 = writeAndFlush("Hello2");
        WriteInfo info3 = writeAndFlush("Hello3");
        subscriber.onComplete();
        verifyListenerSuccessful();
        verifyWriteSuccessful("Hello1", "Hello2", "Hello3");
        verifyWrite(info1, info2, info3);
        verifyNoInteractions(closeHandler);
    }

    @ParameterizedTest(name = "{displayName} [{index}] error={0}")
    @ValueSource(booleans = {false, true})
    void testMultipleItemThenOutboundClosed(boolean error) {
        setUp(false, false);
        verify(subscription).request(anyLong());
        WriteInfo info1 = write("Hello1");
        WriteInfo info2 = write("Hello2");
        WriteInfo info3 = write("Hello3");

        subscriber.channelOutboundClosed();
        verify(subscription).request(eq(MAX_VALUE));
        verify(completableSubscriber).onSubscribe(any());
        verifyNoMoreInteractions(completableSubscriber);

        verifyWriteSuccessful("Hello1", "Hello2", "Hello3");
        verifyWrite(info1, info2, info3);

        // Write after channelOutboundClosed should be discarded
        writeAndFlush("Hello4");
        assertThat("Unexpected message(s) written.", channel.outboundMessages(), is(empty()));

        verifyNoMoreInteractions(completableSubscriber);
        if (error) {
            subscriber.onError(DELIBERATE_EXCEPTION);
            verify(completableSubscriber).onError(DELIBERATE_EXCEPTION);
        } else {
            subscriber.onComplete();
            verify(completableSubscriber).onComplete();
        }
        verifyNoMoreInteractions(completableSubscriber, subscription, closeHandler);
    }

    @Test
    void testOnErrorNoWrite() throws InterruptedException {
        setUp(false, false);
        subscriber.onError(DELIBERATE_EXCEPTION);
        ArgumentCaptor<Throwable> exceptionCaptor = forClass(Throwable.class);
        verify(this.completableSubscriber).onError(exceptionCaptor.capture());
        assertThat(exceptionCaptor.getValue(), instanceOf(AbortedFirstWriteException.class));
        assertThat(exceptionCaptor.getValue().getCause(), is(DELIBERATE_EXCEPTION));
        assertChannelClose();
    }

    @Test
    void testOnCompleteNoWrite() {
        setUp(false, false);
        subscriber.onComplete();
        verify(this.completableSubscriber).onComplete();
        verifyNoInteractions(closeHandler);
    }

    @Test
    void testOnErrorPostWrite() throws InterruptedException {
        setUp(false, false);
        writeAndFlush("Hello");
        channel.flushOutbound();
        subscriber.onError(DELIBERATE_EXCEPTION);
        verify(this.completableSubscriber).onError(DELIBERATE_EXCEPTION);
        assertThat("Message not written.", channel.outboundMessages(), contains("Hello"));
        assertChannelClose();
    }

    @Test
    void testCancelBeforeOnSubscribe() {
        subscriber = new WriteStreamSubscriber(channel, demandEstimator, completableSubscriber, closeHandler,
                NoopWriteObserver.INSTANCE, identity(), false, __ -> false);
        when(demandEstimator.estimateRequestN(anyLong())).thenReturn(1L);
        subscriber.cancel();
        subscriber.onSubscribe(subscription);
        verify(subscription).cancel();
        verifyNoInteractions(closeHandler);
    }

    @Test
    void testCancelAfterOnSubscribe() {
        setUp(false, false);
        subscriber.cancel();
        verify(subscription).cancel();
        verifyNoInteractions(closeHandler);
    }

    @Test
    void testRequestMoreBeforeOnSubscribe() {
        subscriber = new WriteStreamSubscriber(channel, demandEstimator, completableSubscriber, closeHandler,
                NoopWriteObserver.INSTANCE, identity(), false, __ -> false);
        when(demandEstimator.estimateRequestN(anyLong())).thenReturn(1L);
        subscriber.channelWritable();
        subscriber.onSubscribe(subscription);
        WriteInfo info = writeAndFlush("Hello");
        subscriber.onComplete();
        verifyListenerSuccessful();
        verifyWriteSuccessful("Hello");
        verifyWrite(info);
        verifyNoInteractions(closeHandler);
    }

    @Test
    void writeFailureClosesChannel() throws Exception {
        setUp(false, false);
        failingWriteClosesChannel(failingWriteHandler::failNextWritePromise);
    }

    @Test
    void uncaughtWriteExceptionClosesChannel() throws Exception {
        setUp(false, false);
        failingWriteClosesChannel(failingWriteHandler::throwFromNextWrite);
    }

    @Test
    void onNextAfterChannelClose() {
        setUp(false, false);
        subscriber.channelClosed(DELIBERATE_EXCEPTION);
        verify(subscription).cancel();
        subscriber.onNext("Hello");
        verifyListenerFailed(null);
        assertThat("Unexpected message(s) written.", channel.outboundMessages(), is(empty()));
    }

    @Test
    void secondOnNextAfterChannelClose() {
        setUp(false, false);
        subscriber.onNext("Hello");
        verifyWriteSuccessful("Hello");
        subscriber.channelClosed(DELIBERATE_EXCEPTION);
        verify(subscription).cancel();

        subscriber.onNext("Hello2");
        verifyListenerFailed(DELIBERATE_EXCEPTION);
    }

    @Test
    void closeChannelDuringFirstWrite() {
        setUp(false, false);
        subscriber.onNext("Hello");
        channel.close();    // fails the first write with StacklessClosedChannelException
        subscriber.channelClosed(// simulate channelInactive event
                StacklessClosedChannelException.newInstance(WriteStreamSubscriberTest.class, "channel.close()"));
        verify(subscription).cancel();
        subscriber.onNext("Hello2");
        channel.runPendingTasks();
        channel.flush();
        assertThat("Unexpected message(s) written.", channel.outboundMessages(), is(empty()));

        verify(completableSubscriber).onSubscribe(any());
        verify(completableSubscriber).onError(any(ClosedChannelException.class));
        verifyNoMoreInteractions(completableSubscriber);
    }

    @Test
    void clientRequestsOne() {
        when(demandEstimator.estimateRequestN(anyLong())).thenReturn(10L);
        subscriber = new WriteStreamSubscriber(channel, demandEstimator, completableSubscriber, closeHandler,
                NoopWriteObserver.INSTANCE, identity(), true, __ -> false);
        subscriber.onSubscribe(subscription);
        verify(subscription).request(1L);
        verify(demandEstimator, never()).estimateRequestN(anyLong());
        WriteInfo info = writeAndFlush("Hello");
        verify(demandEstimator).onItemWrite(info.messsage(), info.writeCapacityBefore(), info.writeCapacityAfter());
        verify(demandEstimator).estimateRequestN(info.writeCapacityAfter());
        verify(subscription).request(10L);
        subscriber.onComplete();
        verifyListenerSuccessful();
        verifyWriteSuccessful("Hello");
        verifyNoInteractions(closeHandler);
    }

    @Test
    void channelClosedDoesNotCancelAfterOutboundEnd() throws Exception {
        setUp(false, false);
        verify(completableSubscriber).onSubscribe(any());
        WriteInfo info = writeAndFlush("Hello");
        verify(subscription, times(2)).request(anyLong());
        verifyNoMoreInteractions(completableSubscriber, subscription);

        subscriber.channelOutboundClosed();
        verify(subscription).request(MAX_VALUE);

        verifyWriteSuccessful("Hello");
        verifyWrite(info);
        verifyNoMoreInteractions(completableSubscriber, subscription);

        channel.finishAndReleaseAll();
        subscriber.channelClosed(DELIBERATE_EXCEPTION); // simulate channelInactive event from DefaultNettyConnection
        // channelClosed after channelOutboundClosed should not fail subscriber
        verifyNoMoreInteractions(completableSubscriber, closeHandler);

        subscriber.onComplete();    // signal completion after "channelInactive"
        verify(completableSubscriber).onComplete();
        verify(closeHandler).closeChannelOutbound(any(Channel.class));
        verifyNoMoreInteractions(completableSubscriber, subscription, closeHandler);
        assertChannelClose();
    }

    @Test
    void clientContinueWriting() {
        when(demandEstimator.estimateRequestN(anyLong())).thenReturn(10L);
        subscriber = new WriteStreamSubscriber(channel, demandEstimator, completableSubscriber, closeHandler,
                NoopWriteObserver.INSTANCE, identity(), true, __ -> true);
        subscriber.onSubscribe(subscription);

        verify(subscription).request(1L);
        writeAndFlush("Hello1");
        verifyNoMoreInteractions(subscription);

        subscriber.continueWriting();
        verify(subscription).request(10L);
        writeAndFlush("Hello2");

        verifyWriteSuccessful("Hello1", "Hello2");
        subscriber.onComplete();
        verifyListenerSuccessful();
        verifyNoMoreInteractions(subscription, closeHandler);
    }

    @Test
    void clientTerminateSource() {
        when(demandEstimator.estimateRequestN(anyLong())).thenReturn(10L);
        subscriber = new WriteStreamSubscriber(channel, demandEstimator, completableSubscriber, closeHandler,
                NoopWriteObserver.INSTANCE, identity(), true, __ -> true);
        subscriber.onSubscribe(subscription);

        verify(subscription).request(1L);
        writeAndFlush("Hello");
        verifyNoMoreInteractions(subscription);

        subscriber.terminateSource();
        verify(subscription).cancel();

        verifyListenerSuccessful();
        verifyWriteSuccessful("Hello");

        subscriber.onComplete();
        verifyNoMoreInteractions(subscription, closeHandler, completableSubscriber);
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

    private WriteInfo write(String msg) {
        return write(msg, false);
    }

    private WriteInfo writeAndFlush(String msg) {
        return write(msg, true);
    }

    private WriteInfo write(String msg, boolean shouldFlush) {
        long pre = channel.bytesBeforeUnwritable();
        subscriber.onNext(msg);
        long post = channel.bytesBeforeUnwritable();
        if (shouldFlush) {
            channel.flushOutbound();
        }
        return new WriteInfo(pre, post, msg);
    }

    private void assertChannelClose() throws InterruptedException {
        channel.closeFuture().sync();
        assertThat("Channel not closed on write failure.", channel.isActive(), is(false));
    }
}
