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

import io.servicetalk.concurrent.api.LegacyMockedSingleListenerRule;
import io.servicetalk.concurrent.api.LegacyTestSingle;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TerminalSignalConsumer;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.fail;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public abstract class AbstractWhenFinallyTest {

    @Rule
    public final LegacyMockedSingleListenerRule<String> listener = new LegacyMockedSingleListenerRule<>();

    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    private final Runnable runnable = mock(Runnable.class);
    private final TerminalSignalConsumer doFinally = mock(TerminalSignalConsumer.class,
            delegatesTo(TerminalSignalConsumer.from(runnable)));

    @Test
    public void testForCancel() {
        listener.listen(Single.<String>never().afterFinally(doFinally));
        listener.cancel();
        verify(doFinally).onCancel();
        verifyNoMoreInteractions(doFinally);
        verify(runnable).run();
    }

    @Test
    public void testForCancelPostSuccess() {
        listener.listen(doFinally(Single.succeeded("Hello"), doFinally));
        listener.cancel();
        verify(doFinally).onComplete();
        verifyNoMoreInteractions(doFinally);
        verify(runnable).run();
    }

    @Test
    public void testForCancelPostError() {
        listener.listen(doFinally(Single.failed(DELIBERATE_EXCEPTION), doFinally));
        listener.cancel();
        verify(doFinally).onError(DELIBERATE_EXCEPTION);
        verifyNoMoreInteractions(doFinally);
        verify(runnable).run();
    }

    @Test
    public void testForSuccess() {
        listener.listen(doFinally(Single.succeeded("Hello"), doFinally));
        listener.verifySuccess("Hello").cancel();
        verify(doFinally).onComplete();
        verifyNoMoreInteractions(doFinally);
        verify(runnable).run();
    }

    @Test
    public void testForError() {
        listener.listen(doFinally(Single.failed(DELIBERATE_EXCEPTION), doFinally));
        listener.verifyFailure(DELIBERATE_EXCEPTION);
        verify(doFinally).onError(DELIBERATE_EXCEPTION);
        verifyNoMoreInteractions(doFinally);
        verify(runnable).run();
    }

    @Test
    public void testCallbackThrowsErrorOnCancel() {
        thrown.expect(is(sameInstance(DELIBERATE_EXCEPTION)));

        LegacyTestSingle<String> single = new LegacyTestSingle<>();
        try {
            listener.listen(doFinally(single, TerminalSignalConsumer.from(() -> {
                throw DELIBERATE_EXCEPTION;
            }))).cancel();
            fail();
        } finally {
            single.verifyCancelled();
        }
    }

    @Test
    public abstract void testCallbackThrowsErrorOnSuccess();

    @Test
    public abstract void testCallbackThrowsErrorOnError();

    protected abstract <T> Single<T> doFinally(Single<T> single, TerminalSignalConsumer signalConsumer);
}
