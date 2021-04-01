/*
 * Copyright © 2020-2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.api;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

public class PublisherProcessorSignalsHolderTest {
    private final List<Object> pastBufferSizeSignals;
    private final AbstractPublisherProcessorSignalsHolder<Integer, Queue<Object>> buffer;
    @SuppressWarnings("unchecked")
    private final ProcessorSignalsConsumer<Integer> consumer = mock(ProcessorSignalsConsumer.class);

    public PublisherProcessorSignalsHolderTest() {
        pastBufferSizeSignals = new ArrayList<>();
        buffer = new AbstractPublisherProcessorSignalsHolder<Integer, Queue<Object>>(1, new ConcurrentLinkedQueue<>()) {
            @Override
            void offerPastBufferSize(final Object signal, final Queue<Object> queue) {
                pastBufferSizeSignals.add(signal);
            }
        };
    }

    @Test
    public void bufferOverflow() {
        buffer.add(1);
        assertThat("Unexpected items overflow.", pastBufferSizeSignals, hasSize(0));
        buffer.add(2); // overflow
        assertThat("Unexpected items overflow.", pastBufferSizeSignals, hasSize(1));
        assertThat("Unexpected items overflow.", pastBufferSizeSignals.get(0), is(2));
    }

    @Test
    public void consumeItem() {
        buffer.add(1);
        assertThat("Item not consumed.", buffer.tryConsume(consumer), is(true));
        verify(consumer).consumeItem(1);
        verifyNoMoreInteractions(consumer);
    }

    @Test
    public void addAfterOverflowAndConsume() {
        buffer.add(1);
        assertThat("Unexpected items overflow.", pastBufferSizeSignals, hasSize(0));
        buffer.add(2); // overflow
        assertThat("Unexpected items overflow.", pastBufferSizeSignals, hasSize(1));
        assertThat("Unexpected items overflow.", pastBufferSizeSignals.get(0), is(2));
        assertThat("Item not consumed.", buffer.tryConsume(consumer), is(true));
        verify(consumer).consumeItem(1);
        verifyNoMoreInteractions(consumer);

        pastBufferSizeSignals.clear();
        buffer.add(3);
        assertThat("Unexpected items overflow.", pastBufferSizeSignals, hasSize(0));
        assertThat("Item not consumed.", buffer.tryConsume(consumer), is(true));
        verify(consumer).consumeItem(3);
        verifyNoMoreInteractions(consumer);
    }

    @Test
    public void tryConsumeEmpty() {
        assertThat("Item consumed when empty.", buffer.tryConsume(consumer), is(false));
        verifyZeroInteractions(consumer);
    }

    @Test
    public void consumeTerminal() {
        buffer.add(1);
        buffer.terminate();
        assertThat("Item not consumed.", buffer.tryConsume(consumer), is(true));
        verify(consumer).consumeItem(1);
        buffer.tryConsume(consumer);
        verify(consumer).consumeTerminal();
        verifyNoMoreInteractions(consumer);
    }

    @Test
    public void consumeTerminalError() {
        buffer.add(1);
        buffer.terminate(DELIBERATE_EXCEPTION);
        assertThat("Item not consumed.", buffer.tryConsume(consumer), is(true));
        verify(consumer).consumeItem(1);
        buffer.tryConsume(consumer);
        verify(consumer).consumeTerminal(DELIBERATE_EXCEPTION);
        verifyNoMoreInteractions(consumer);
    }
}
