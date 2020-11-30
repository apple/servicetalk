/*
 * Copyright © 2018, 2020 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.api.single;

import io.servicetalk.concurrent.api.LegacyTestSingle;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.SingleTerminalSignalConsumer;
import io.servicetalk.concurrent.test.internal.TestSingleSubscriber;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.fail;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

abstract class AbstractWhenFinallyTest {
    @Rule
    public final ExpectedException thrown = ExpectedException.none();
    final TestSingleSubscriber<String> listener = new TestSingleSubscriber<>();

    @SuppressWarnings("unchecked")
    private final SingleTerminalSignalConsumer<String> doFinally = mock(SingleTerminalSignalConsumer.class);

    @Test
    public void testForCancel() {
        toSource(Single.<String>never().afterFinally(doFinally)).subscribe(listener);
        listener.awaitSubscription().cancel();
        verify(doFinally).cancel();
        verifyNoMoreInteractions(doFinally);
    }

    @Test
    public void testForCancelPostSuccess() {
        String result = "Hello";
        toSource(doFinally(Single.succeeded(result), doFinally)).subscribe(listener);
        listener.awaitSubscription().cancel();
        verify(doFinally).onSuccess(result);
        verifyNoMoreInteractions(doFinally);
    }

    @Test
    public void testForCancelPostError() {
        toSource(doFinally(Single.failed(DELIBERATE_EXCEPTION), doFinally)).subscribe(listener);
        listener.awaitSubscription().cancel();
        verify(doFinally).onError(DELIBERATE_EXCEPTION);
        verifyNoMoreInteractions(doFinally);
    }

    @Test
    public void testForSuccess() {
        String result = "Hello";
        toSource(doFinally(Single.succeeded(result), doFinally)).subscribe(listener);
        assertThat(listener.awaitOnSuccess(), is(result));
        listener.awaitSubscription().cancel();
        verify(doFinally).onSuccess(result);
        verifyNoMoreInteractions(doFinally);
    }

    @Test
    public void testForError() {
        toSource(doFinally(Single.failed(DELIBERATE_EXCEPTION), doFinally)).subscribe(listener);
        assertThat(listener.awaitOnError(), is(DELIBERATE_EXCEPTION));
        verify(doFinally).onError(DELIBERATE_EXCEPTION);
        verifyNoMoreInteractions(doFinally);
    }

    @Test
    public void testCallbackThrowsErrorOnCancel() {
        SingleTerminalSignalConsumer<String> mock = throwableMock(DELIBERATE_EXCEPTION);
        LegacyTestSingle<String> single = new LegacyTestSingle<>();
        try {
            toSource(doFinally(single, mock)).subscribe(listener);
            thrown.expect(is(sameInstance(DELIBERATE_EXCEPTION)));
            listener.awaitSubscription().cancel();
            fail();
        } finally {
            single.verifyCancelled();
            verify(mock).cancel();
            verifyNoMoreInteractions(mock);
        }
    }

    @Test
    public abstract void testCallbackThrowsErrorOnSuccess() throws InterruptedException;

    @Test
    public abstract void testCallbackThrowsErrorOnError() throws InterruptedException;

    protected abstract <T> Single<T> doFinally(Single<T> single, SingleTerminalSignalConsumer<T> signalConsumer);

    @SuppressWarnings("unchecked")
    protected SingleTerminalSignalConsumer<String> throwableMock(RuntimeException exception) {
        return mock(SingleTerminalSignalConsumer.class, delegatesTo(new SingleTerminalSignalConsumer<String>() {
            @Override
            public void onSuccess(@Nullable final String result) {
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
