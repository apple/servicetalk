/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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

import org.mockito.Mockito;

import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Mocked {@link TerminalSignalConsumer} that helps to verify that only a single terminal method was invoked.
 */
public final class TerminalSignalConsumerMock implements TerminalSignalConsumer {

    private final Runnable runnable;
    private final TerminalSignalConsumer signalConsumer;

    /**
     * Creates a new instance.
     */
    public TerminalSignalConsumerMock() {
        runnable = Mockito.mock(Runnable.class);
        signalConsumer = Mockito.mock(TerminalSignalConsumer.class, delegatesTo(TerminalSignalConsumer.from(runnable)));
    }

    @Override
    public void onComplete() {
        signalConsumer.onComplete();
    }

    @Override
    public void onError(final Throwable throwable) {
        signalConsumer.onError(throwable);
    }

    @Override
    public void onCancel() {
        signalConsumer.onCancel();
    }

    /**
     * Verifies that only {@link TerminalSignalConsumer#onComplete()} was invoked and no other methods.
     */
    public void verifyOnComplete() {
        verify(signalConsumer).onComplete();
        verify(signalConsumer, never()).onError(any(Throwable.class));
        verify(signalConsumer, never()).onCancel();
        verify(runnable).run();
    }

    /**
     * Verifies that only {@link TerminalSignalConsumer#onError(Throwable)} was invoked and no other methods.
     */
    public void verifyOnError(final Throwable throwable) {
        verify(signalConsumer).onError(throwable);
        verify(signalConsumer, never()).onComplete();
        verify(signalConsumer, never()).onCancel();
        verify(runnable).run();
    }

    /**
     * Verifies that only {@link TerminalSignalConsumer#onCancel()} ()} was invoked and no other methods.
     */
    public void verifyOnCancel() {
        verify(signalConsumer).onCancel();
        verify(signalConsumer, never()).onComplete();
        verify(signalConsumer, never()).onError(any(Throwable.class));
        verify(runnable).run();
    }

    /**
     * Returns internal mock object of {@link TerminalSignalConsumer} that could be used for other verifications.
     *
     * @return internal mock object of {@link TerminalSignalConsumer}
     */
    public TerminalSignalConsumer mock() {
        return signalConsumer;
    }
}
