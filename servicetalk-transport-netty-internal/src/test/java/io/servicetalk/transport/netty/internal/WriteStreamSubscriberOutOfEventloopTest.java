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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.CompletableSource.Processor;
import io.servicetalk.transport.netty.internal.NoopTransportObserver.NoopWriteObserver;

import io.netty.channel.EventLoop;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;

import static io.servicetalk.concurrent.api.Processors.newCompletableProcessor;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.transport.netty.internal.CloseHandler.UNSUPPORTED_PROTOCOL_CLOSE_HANDLER;
import static java.util.function.UnaryOperator.identity;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;

class WriteStreamSubscriberOutOfEventloopTest extends AbstractOutOfEventloopTest {

    private WriteStreamSubscriber subscriber;

    @Override
    public void setup0() {
        CompletableSource.Subscriber completableSubscriber = mock(CompletableSource.Subscriber.class);
        WriteDemandEstimator demandEstimator = mock(WriteDemandEstimator.class);
        subscriber = new WriteStreamSubscriber(channel, demandEstimator, completableSubscriber,
                UNSUPPORTED_PROTOCOL_CLOSE_HANDLER, NoopWriteObserver.INSTANCE, identity(), false);
    }

    @Test
    void writeOnDifferntEventLoopThenWriteOnSameEventLoop() throws Exception {
        testWriteFromDifferentEventLoops(getDifferentEventloopThanChannel(), channel.eventLoop());
    }

    @Test
    void writeOnSameEventLoopThenWriteOnDifferntEventLoop() throws Exception {
        testWriteFromDifferentEventLoops(channel.eventLoop(), getDifferentEventloopThanChannel());
    }

    @Test
    void testWriteFromEventloop() throws Exception {
        channel.eventLoop().submit(() -> subscriber.onNext(1))
                .addListener(future -> {
                    subscriber.onNext(2);
                    subscriber.onComplete();
                });
        assertPendingFlush();
    }

    @Test
    void testWriteFromOutsideEventloop() throws Exception {
        subscriber.onNext(1);
        subscriber.onNext(2);
        subscriber.onComplete();
        ensureEnqueuedTaskAreRun(channel.eventLoop());
        assertPendingFlush();
    }

    @Test
    void testTerminalOrder() throws Exception {
        Processor subject = newCompletableProcessor();
        CompletableSource.Subscriber subscriber = new CompletableSource.Subscriber() {
            @Override
            public void onSubscribe(Cancellable cancellable) {
                // noop
            }

            @Override
            public void onComplete() {
                subject.onComplete();
            }

            @Override
            public void onError(Throwable t) {
                if (pendingFlush.contains(1)) {
                    subject.onError(t);
                } else {
                    subject.onError(
                            new IllegalStateException("The expected object wasn't written before termination!", t));
                }
            }
        };
        WriteDemandEstimator demandEstimator = mock(WriteDemandEstimator.class);
        this.subscriber = new WriteStreamSubscriber(channel, demandEstimator, subscriber,
                UNSUPPORTED_PROTOCOL_CLOSE_HANDLER, NoopWriteObserver.INSTANCE, identity(), false);

        this.subscriber.onNext(1);
        this.subscriber.onError(DELIBERATE_EXCEPTION);

        try {
            fromSource(subject).toFuture().get();
            fail();
        } catch (ExecutionException cause) {
            assertSame(cause.getCause(), DELIBERATE_EXCEPTION);
        }
    }

    private void testWriteFromDifferentEventLoops(EventLoop first, EventLoop second) throws InterruptedException {
        first.submit(() -> subscriber.onNext(1))
                .addListener(future -> second.submit(() -> {
                    subscriber.onNext(2);
                    subscriber.onComplete();
                }));
        assertPendingFlush();
    }

    private void assertPendingFlush() throws InterruptedException {
        assertThat("Unexpected items pending flush. Written items: " + written, pendingFlush.take(), is(1));
        assertThat("Unexpected items pending flush. Written items: " + written, pendingFlush.take(), is(2));
        assertThat("Unexpected extra items pending flush. Written items: " + written, pendingFlush, is(empty()));
    }
}
