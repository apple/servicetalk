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

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;
import io.servicetalk.transport.netty.internal.FlushStrategy.FlushSender;
import io.servicetalk.transport.netty.internal.NoopTransportObserver.NoopWriteObserver;

import io.netty.channel.EventLoop;
import org.junit.jupiter.api.Test;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.transport.netty.internal.Flush.composeFlushes;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

class FlushOutsideEventloopTest extends AbstractOutOfEventloopTest {

    private final TestPublisherSubscriber<Integer> subscriber = new TestPublisherSubscriber<>();
    private TestPublisher<Integer> src;
    private FlushSender flushSender;
    private MockFlushStrategy strategy;

    @Override
    public void setup0() {
        src = new TestPublisher<>();
        strategy = new MockFlushStrategy();
        Publisher<Integer> composedFlush = composeFlushes(channel, src, strategy, NoopWriteObserver.INSTANCE)
                .beforeOnNext(integer -> channel.write(integer));
        toSource(composedFlush).subscribe(subscriber);
        subscriber.awaitSubscription().request(Long.MAX_VALUE);
        flushSender = strategy.verifyApplied();
    }

    @Test
    void testWriteAndFlushOutsideEventloop() throws Exception {
        EventLoop executor = getDifferentEventloopThanChannel();
        executor.submit(() -> src.onNext(1)).get();
        flushSender.flush();
        ensureEnqueuedTaskAreRun(executor);
        ensureEnqueuedTaskAreRun(channel.eventLoop());
        assertWritten(1);
    }

    @Test
    void testWriteFromOutsideEventloopThenWriteAndFlushOnEventloop() throws Exception {
        EventLoop executor = getDifferentEventloopThanChannel();
        executor.submit(() -> src.onNext(1)).get();
        channel.eventLoop().submit(() -> {
            src.onNext(2);
            flushSender.flush();
        }).get();
        ensureEnqueuedTaskAreRun(executor);
        assertWritten(1, 2);
    }

    @Test
    void testWriteInEventloopThenWriteAndFlushOutsideEventloop() throws Exception {
        EventLoop executor = getDifferentEventloopThanChannel();
        channel.eventLoop().submit(() -> src.onNext(1)).get();
        executor.submit(() -> {
            src.onNext(2);
            flushSender.flush();
        }).get();
        ensureEnqueuedTaskAreRun(executor);
        assertWritten(1, 2);
    }

    @Test
    void testWriteFromEventloopAndFlushOutsideEventloop() throws Exception {
        EventLoop executor = getDifferentEventloopThanChannel();
        channel.eventLoop().submit(() -> src.onNext(1)).get();
        executor.submit(() -> flushSender.flush()).get();
        ensureEnqueuedTaskAreRun(executor);
        assertWritten(1);
    }

    private void assertWritten(Integer... values) throws InterruptedException {
        strategy.verifyWriteStarted();
        for (Integer v : values) {
            assertThat("Unexpected items written. Items pending flush: " + pendingFlush, written.take(), is(v));
        }
        strategy.verifyItemWritten(values.length);
        assertThat("Unexpected extra items written. Items pending flush: " + pendingFlush, written, is(empty()));
        src.onComplete();
        strategy.verifyWriteTerminated();
    }
}
