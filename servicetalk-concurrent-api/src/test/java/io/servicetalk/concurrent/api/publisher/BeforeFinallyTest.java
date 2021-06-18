/*
 * Copyright © 2018-2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.api.publisher;

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.TerminalSignalConsumer;
import io.servicetalk.concurrent.internal.DeliberateException;

import org.junit.jupiter.api.Test;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class BeforeFinallyTest extends AbstractWhenFinallyTest {
    @Override
    protected <T> PublisherSource<T> doFinally(Publisher<T> publisher, TerminalSignalConsumer signalConsumer) {
        return toSource(publisher.beforeFinally(signalConsumer));
    }

    @Override
    @Test
    void testCallbackThrowsErrorOnComplete() {
        TerminalSignalConsumer mock = throwableMock(DELIBERATE_EXCEPTION);
        doFinally(publisher, mock).subscribe(subscriber);
        assertFalse(subscription.isCancelled());
        publisher.onComplete();
        Throwable receivedError = subscriber.awaitOnError();
        assertThat(receivedError, is(notNullValue()));
        assertThat(receivedError, sameInstance(DELIBERATE_EXCEPTION));
        verify(mock).onComplete();
        verifyNoMoreInteractions(mock);
        assertFalse(subscription.isCancelled());
    }

    @Override
    @Test
    void testCallbackThrowsErrorOnError() {
        DeliberateException exception = new DeliberateException();
        TerminalSignalConsumer mock = throwableMock(exception);
        doFinally(publisher, mock).subscribe(subscriber);
        publisher.onError(DELIBERATE_EXCEPTION);
        Throwable receivedError = subscriber.awaitOnError();
        assertThat(receivedError, is(notNullValue()));
        assertThat(receivedError, sameInstance(exception));
        assertThat(receivedError.getSuppressed()[0], sameInstance(DELIBERATE_EXCEPTION));
        verify(mock).onError(DELIBERATE_EXCEPTION);
        verifyNoMoreInteractions(mock);
        assertFalse(subscription.isCancelled());
    }
}
