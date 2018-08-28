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

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class FlushTest extends AbstractFlushTest {

    @Rule
    public final MockedSubscriberRule<String> subscriber = new MockedSubscriberRule<>();
    @Rule
    public final PublisherRule<String> source = new PublisherRule<>();

    @Before
    public void setUp() {
        @SuppressWarnings("unchecked")
        FlushStrategyHolder<String> holder = (FlushStrategyHolder<String>) mock(FlushStrategyHolder.class);
        when(holder.getSource()).thenReturn(source.getPublisher());
        when(holder.getFlushSignals()).thenReturn(new FlushStrategyHolder.FlushSignals());
        subscriber.subscribe(super.setup(holder));
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

    @Override
    void verifyWriteAndFlushAfter(final String... items) {
        super.verifyWriteAndFlushAfter(items);
        subscriber.verifyItems(items);
    }
}
