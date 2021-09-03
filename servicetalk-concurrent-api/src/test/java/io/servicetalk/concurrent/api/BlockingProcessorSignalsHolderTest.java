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

import java.util.concurrent.TimeoutException;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class BlockingProcessorSignalsHolderTest {
    private final DefaultBlockingProcessorSignalsHolder<Integer> buffer;
    @SuppressWarnings("unchecked")
    private final ProcessorSignalsConsumer<Integer> consumer = mock(ProcessorSignalsConsumer.class);

    BlockingProcessorSignalsHolderTest() {
        buffer = new DefaultBlockingProcessorSignalsHolder<>(1);
    }

    @Test
    void consumeItem() throws Exception {
        buffer.add(1);
        assertThat("Item not consumed.", buffer.consume(consumer), is(true));
        verify(consumer).consumeItem(1);
        verifyNoMoreInteractions(consumer);
    }

    @Test
    void consumeEmpty() {
        assertThrows(TimeoutException.class,
                () -> buffer.consume(consumer, 1, MILLISECONDS), "Unexpected consume when empty.");
        verifyNoInteractions(consumer);
    }

    @Test
    void consumeTerminal() throws Exception {
        buffer.terminate();
        assertThat("Item not consumed.", buffer.consume(consumer), is(true));
        verify(consumer).consumeTerminal();
        verifyNoMoreInteractions(consumer);
    }

    @Test
    void consumeTerminalError() throws Exception {
        buffer.terminate(DELIBERATE_EXCEPTION);
        assertThat("Item not consumed.", buffer.consume(consumer), is(true));
        verify(consumer).consumeTerminal(DELIBERATE_EXCEPTION);
        verifyNoMoreInteractions(consumer);
    }
}
