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
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.transport.api.FlushStrategyHolder.FlushSignals;

import io.netty.channel.EventLoop;
import org.junit.Rule;
import org.junit.Test;

import static io.servicetalk.transport.netty.internal.Flush.composeFlushes;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

public class FlushOutsideEventloopTest extends AbstractOutOfEventloopTest {

    @Rule
    public final MockedSubscriberRule<Integer> subscriber = new MockedSubscriberRule<>();

    private TestPublisher<Integer> src;
    private FlushSignals signals;

    @Override
    public void setup0() {
        src = new TestPublisher<Integer>().sendOnSubscribe();
        signals = new FlushSignals();
        Publisher<Integer> composedFlush = composeFlushes(channel, src, signals).doBeforeNext(integer -> channel.write(integer));
        subscriber.subscribe(composedFlush).request(Long.MAX_VALUE);
    }

    @Test
    public void testWriteAndFlushOutsideEventloop() throws Exception {
        EventLoop executor = getDifferentEventloopThanChannel();
        executor.submit(() -> src.sendItems(1)).get();
        signals.signalFlush();
        ensureEnqueuedTaskAreRun(executor);
        ensureEnqueuedTaskAreRun(channel.eventLoop());
        assertWritten(1);
    }

    @Test
    public void testWriteFromOutsideEventloopThenWriteAndFlushOnEventloop() throws Exception {
        EventLoop executor = getDifferentEventloopThanChannel();
        executor.submit(() -> src.sendItems(1)).get();
        channel.eventLoop().submit(() -> {
            src.sendItems(2);
            signals.signalFlush();
        }).get();
        ensureEnqueuedTaskAreRun(executor);
        assertWritten(1, 2);
    }

    @Test
    public void testWriteInEventloopThenWriteAndFlushOutsideEventloop() throws Exception {
        EventLoop executor = getDifferentEventloopThanChannel();
        channel.eventLoop().submit(() -> src.sendItems(1)).get();
        executor.submit(() -> {
            src.sendItems(2);
            signals.signalFlush();
        }).get();
        ensureEnqueuedTaskAreRun(executor);
        assertWritten(1, 2);
    }

    @Test
    public void testWriteFromEventloopAndFlushOutsideEventloop() throws Exception {
        EventLoop executor = getDifferentEventloopThanChannel();
        channel.eventLoop().submit(() -> src.sendItems(1)).get();
        executor.submit(() -> {
            signals.signalFlush();
        }).get();
        ensureEnqueuedTaskAreRun(executor);
        assertWritten(1);
    }

    private void assertWritten(Integer... values) throws InterruptedException {
        for (Integer v: values) {
            assertThat("Unexpected items written. Items pending flush: " + pendingFlush, written.take(), is(v));
        }
        assertThat("Unexpected extra items written. Items pending flush: " + pendingFlush, written, is(empty()));
    }
}
