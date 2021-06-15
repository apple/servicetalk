/*
 * Copyright © 2018, 2020, 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.api.completable;

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.LegacyTestCompletable;
import io.servicetalk.concurrent.api.TerminalSignalConsumer;
import io.servicetalk.concurrent.internal.DeliberateException;
import io.servicetalk.concurrent.test.internal.TestCompletableSubscriber;

import org.junit.jupiter.api.Test;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

abstract class AbstractWhenFinallyTest {
    final TestCompletableSubscriber listener = new TestCompletableSubscriber();
    private final TerminalSignalConsumer doFinally = mock(TerminalSignalConsumer.class);

    @Test
    void testForCancel() {
        toSource(doFinally(Completable.never(), doFinally)).subscribe(listener);
        listener.awaitSubscription().cancel();
        verify(doFinally).cancel();
        verifyNoMoreInteractions(doFinally);
    }

    @Test
    void testForCancelPostSuccess() {
        toSource(doFinally(Completable.completed(), doFinally)).subscribe(listener);
        listener.awaitSubscription().cancel();
        verify(doFinally).onComplete();
        verifyNoMoreInteractions(doFinally);
    }

    @Test
    void testForCancelPostError() {
        toSource(doFinally(Completable.failed(DELIBERATE_EXCEPTION), doFinally)).subscribe(listener);
        listener.awaitSubscription().cancel();
        verify(doFinally).onError(DELIBERATE_EXCEPTION);
        verifyNoMoreInteractions(doFinally);
    }

    @Test
    void testForSuccess() {
        toSource(doFinally(Completable.completed(), doFinally)).subscribe(listener);
        listener.awaitOnComplete();
        listener.awaitSubscription().cancel();
        verify(doFinally).onComplete();
        verifyNoMoreInteractions(doFinally);
    }

    @Test
    void testForError() {
        toSource(doFinally(Completable.failed(DELIBERATE_EXCEPTION), doFinally)).subscribe(listener);
        assertThat(listener.awaitOnError(), is(DELIBERATE_EXCEPTION));
        verify(doFinally).onError(DELIBERATE_EXCEPTION);
        verifyNoMoreInteractions(doFinally);
    }

    @Test
    void testCallbackThrowsErrorWhenCancel() {
        TerminalSignalConsumer mock = throwableMock(DELIBERATE_EXCEPTION);
        LegacyTestCompletable completable = new LegacyTestCompletable();
        try {
            toSource(doFinally(completable, mock)).subscribe(listener);
            Exception e = assertThrows(DeliberateException.class, () -> listener.awaitSubscription().cancel());
            assertThat(e, is(sameInstance(DELIBERATE_EXCEPTION)));
        } finally {
            completable.verifyCancelled();
            verify(mock).cancel();
            verifyNoMoreInteractions(mock);
        }
    }

    @Test
    abstract void testCallbackThrowsErrorOnComplete();

    @Test
    abstract void testCallbackThrowsErrorOnError();

    protected abstract Completable doFinally(Completable completable, TerminalSignalConsumer doFinally);

    protected TerminalSignalConsumer throwableMock(RuntimeException exception) {
        return mock(TerminalSignalConsumer.class, delegatesTo(new TerminalSignalConsumer() {
            @Override
            public void onComplete() {
                throw exception;
            }

            @Override
            public void onError(final Throwable throwable) {
                throw exception;
            }

            @Override
            public void cancel() {
                throw exception;
            }
        }));
    }
}
