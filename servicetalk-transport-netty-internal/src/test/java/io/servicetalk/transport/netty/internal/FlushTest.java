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

import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestSubscription;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;
import io.servicetalk.transport.netty.internal.FlushStrategy.FlushSender;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

class FlushTest extends AbstractFlushTest {

    private final TestPublisher<String> source = new TestPublisher<>();
    private final TestPublisherSubscriber<String> subscriber = new TestPublisherSubscriber<>();
    private FlushSender flushSender;
    private MockFlushStrategy strategy;

    @BeforeEach
    public void setUp() {
        strategy = new MockFlushStrategy();
        toSource(super.setup(source, strategy)).subscribe(subscriber);
        flushSender = strategy.verifyApplied();
    }

    @Test
    void testFlushOnEach() {
        writeAndFlush("Hello");

        verifyWriteAndFlushAfter("Hello");
        verifyNoMoreInteractions(channel);
    }

    @Test
    void testBatchFlush() {
        writeAndFlush("Hello1", "Hello2", "Hello3");

        verifyWriteAndFlushAfter("Hello1", "Hello2", "Hello3");
        verifyNoMoreInteractions(channel);
    }

    @Test
    void testMultipleBatchFlush() {
        writeAndFlush("Hello1", "Hello2", "Hello3");

        verifyWriteAndFlushAfter("Hello1", "Hello2", "Hello3");
        verifyNoMoreInteractions(channel);

        writeAndFlush("Hello4", "Hello5");

        verifyWriteAndFlushAfter("Hello4", "Hello5");
        verifyNoMoreInteractions(channel);
    }

    @Test
    void testCancel() {
        final TestSubscription subscription = new TestSubscription();
        source.onSubscribe(subscription);
        subscriber.awaitSubscription().cancel();

        verify(channel).eventLoop();
        verifyZeroInteractions(channel);
        assertThat(subscriber.pollOnNext(10, MILLISECONDS), is(nullValue()));
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), is(nullValue()));

        assertTrue(subscription.isCancelled());
        strategy.verifyWriteCancelled();
    }

    @Test
    void testSourceComplete() {
        source.onComplete();
        subscriber.awaitOnComplete();
        strategy.verifyWriteTerminated();
    }

    @Test
    void testSourceEmitError() {
        source.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
        strategy.verifyWriteTerminated();
    }

    private void writeAndFlush(String... items) {
        if (items.length == 0) {
            return;
        }
        subscriber.awaitSubscription().request(items.length);
        source.onNext(items);
        flushSender.flush();
    }

    @Override
    void verifyWriteAndFlushAfter(final String... items) {
        super.verifyWriteAndFlushAfter(items);
        assertThat(subscriber.takeOnNext(items.length), contains(items));
        strategy.verifyItemWritten(items.length);
    }
}
