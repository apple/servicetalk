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

import io.servicetalk.concurrent.api.MockedSubscriberRule;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.PublisherRule;
import io.servicetalk.transport.api.FlushStrategyHolder;

import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InOrder;

import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.transport.netty.internal.Flush.composeFlushes;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class FlushTest {

    @Rule
    public final MockedSubscriberRule<String> subscriber = new MockedSubscriberRule<>();
    @Rule
    public final PublisherRule<String> source = new PublisherRule<>();

    private FlushStrategyHolder.FlushSignals flushSignals;
    private Channel channel;
    private InOrder verifier;

    @Before
    public void setUp() {
        channel = mock(Channel.class);
        EventLoop eventLoop = mock(EventLoop.class);
        when(eventLoop.inEventLoop()).thenReturn(true);
        when(channel.eventLoop()).thenReturn(eventLoop);
        flushSignals = new FlushStrategyHolder.FlushSignals();
        Publisher<String> flushedStream = composeFlushes(channel, source.getPublisher(), flushSignals).doBeforeNext(s -> channel.write(s));
        subscriber.subscribe(flushedStream);
        verifier = inOrder(channel);
    }

    @Test
    public void testFlushOnEach() {
        writeAndFlush("Hello");

        verifyWriteAndFlushAfter("Hello");
        verifyNoMoreInteractions(channel);
    }

    @Test
    public void testBatchFlush() {
        writeAndFlush("Hello1", "Hello2", "Hello3");

        verifyWriteAndFlushAfter("Hello1", "Hello2", "Hello3");
        verifyNoMoreInteractions(channel);
    }

    @Test
    public void testMultipleBatchFlush() {
        writeAndFlush("Hello1", "Hello2", "Hello3");

        verifyWriteAndFlushAfter("Hello1", "Hello2", "Hello3");
        verifyNoMoreInteractions(channel);

        writeAndFlush("Hello4", "Hello5");

        verifyWriteAndFlushAfter("Hello4", "Hello5");
        verifyNoMoreInteractions(channel);
    }

    @Test
    public void testCancel() {
        subscriber.cancel();

        verify(channel).eventLoop();
        verifyZeroInteractions(channel);
        subscriber.verifyNoEmissions();
        source.verifyCancelled();
        verifyFlushSignalListenerRemoved();
    }

    @Test
    public void testSourceComplete() {
        source.complete();
        subscriber.verifySuccess();
        verifyFlushSignalListenerRemoved();
    }

    @Test
    public void testSourceEmitError() {
        source.fail();
        subscriber.verifyFailure(DELIBERATE_EXCEPTION);
        verifyFlushSignalListenerRemoved();
    }

    private void writeAndFlush(String... items) {
        if (items.length == 0) {
            return;
        }
        subscriber.request(items.length);
        source.sendItems(items);
        flushSignals.signalFlush();
    }

    private void verifyWriteAndFlushAfter(String... items) {
        for (String item : items) {
            verifier.verify(channel).write(item);
        }
        verifier.verify(channel).flush();
        verify(channel).eventLoop();
        subscriber.verifyItems(items);
    }

    private void verifyFlushSignalListenerRemoved() {
        flushSignals.listen(() -> { }).cancel(); // verifies the completableSubscriber was removed.
    }
}
