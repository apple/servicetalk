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

import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A mocking setup for {@link FlushStrategy} that provides ways to verify interactions with {@link WriteEventsListener}
 * and also provides access to {@link FlushSender}.
 */
public final class MockFlushStrategy implements FlushStrategy {

    private final FlushStrategy mock;
    private final WriteEventsListener writeListener;
    private final InOrder writeVerifier;

    /**
     * New instance.
     */
    public MockFlushStrategy() {
        mock = mock(FlushStrategy.class);
        writeListener = mock(WriteEventsListener.class);
        when(mock.apply(any())).thenReturn(writeListener);
        writeVerifier = inOrder(writeListener);
    }

    /**
     * Verifies that this {@link FlushStrategy} was applied.
     *
     * @return {@link FlushSender} provided to {@link #apply(FlushSender)}.
     */
    public FlushSender verifyApplied() {
        ArgumentCaptor<FlushSender> captor = forClass(FlushSender.class);
        verify(mock).apply(captor.capture());
        return captor.getValue();
    }

    /**
     * Verifies whether {@link WriteEventsListener#writeStarted()} was called for the {@link WriteEventsListener}
     * returned from {@link #apply(FlushSender)}.
     */
    public void verifyWriteStarted() {
        writeVerifier.verify(writeListener).writeStarted();
    }

    /**
     * Verifies whether {@link WriteEventsListener#itemWritten()} was called for the {@link WriteEventsListener}
     * returned from {@link #apply(FlushSender)}.
     * @param count Number of times {@link WriteEventsListener#itemWritten()} is expected to be called.
     */
    public void verifyItemWritten(int count) {
        writeVerifier.verify(writeListener, times(count)).itemWritten();
    }

    /**
     * Verifies whether {@link WriteEventsListener#writeTerminated()} was called for the {@link WriteEventsListener}
     * returned from {@link #apply(FlushSender)}.
     */
    public void verifyWriteTerminated() {
        writeVerifier.verify(writeListener).writeTerminated();
    }

    /**
     * Verifies whether {@link WriteEventsListener#writeCancelled()} was called for the {@link WriteEventsListener}
     * returned from {@link #apply(FlushSender)}.
     */
    public void verifyWriteCancelled() {
        writeVerifier.verify(writeListener).writeCancelled();
    }

    /**
     * Verifies there were no more interactions with the {@link WriteEventsListener} returned from
     * {@link #apply(FlushSender)}.
     */
    public void verifyNoMoreInteractions() {
        Mockito.verifyNoMoreInteractions(mock);
        writeVerifier.verifyNoMoreInteractions();
    }

    @Override
    public WriteEventsListener apply(final FlushSender sender) {
        return mock.apply(sender);
    }
}
