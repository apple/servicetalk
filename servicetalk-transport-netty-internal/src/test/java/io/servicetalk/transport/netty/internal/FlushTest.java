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
import io.servicetalk.concurrent.api.PublisherRule;
import io.servicetalk.transport.netty.internal.FlushStrategy.FlushSender;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

public class FlushTest extends AbstractFlushTest {

    @Rule
    public final MockedSubscriberRule<String> subscriber = new MockedSubscriberRule<>();
    @Rule
    public final PublisherRule<String> source = new PublisherRule<>();
    private FlushSender flushSender;
    private MockFlushStrategy strategy;

    @Before
    public void setUp() {
        strategy = new MockFlushStrategy();
        subscriber.subscribe(super.setup(source.getPublisher(), strategy));
        flushSender = strategy.verifyApplied();
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
        strategy.verifyWriteCancelled();
    }

    @Test
    public void testSourceComplete() {
        source.complete();
        subscriber.verifySuccess();
        strategy.verifyWriteTerminated();
    }

    @Test
    public void testSourceEmitError() {
        source.fail();
        subscriber.verifyFailure(DELIBERATE_EXCEPTION);
        strategy.verifyWriteTerminated();
    }

    private void writeAndFlush(String... items) {
        if (items.length == 0) {
            return;
        }
        subscriber.request(items.length);
        source.sendItems(items);
        flushSender.flush();
    }

    @Override
    void verifyWriteAndFlushAfter(final String... items) {
        super.verifyWriteAndFlushAfter(items);
        subscriber.verifyItems(items);
        strategy.verifyItemWritten(items.length);
    }
}
