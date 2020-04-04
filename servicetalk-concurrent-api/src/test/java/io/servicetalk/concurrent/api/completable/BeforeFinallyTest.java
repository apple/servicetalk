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
package io.servicetalk.concurrent.api.completable;

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.TerminalSignalConsumer;
import io.servicetalk.concurrent.internal.DeliberateException;

import org.junit.Test;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class BeforeFinallyTest extends AbstractWhenFinallyTest {
    @Override
    protected Completable doFinally(Completable completable, TerminalSignalConsumer doFinally) {
        return completable.beforeFinally(doFinally);
    }

    @Test
    @Override
    public void testCallbackThrowsErrorOnComplete() {
        TerminalSignalConsumer mock = throwableMock(DELIBERATE_EXCEPTION);
        listener.listen(doFinally(Completable.completed(), mock))
                .verifyFailure(DELIBERATE_EXCEPTION);
        verify(mock).onComplete();
        verifyNoMoreInteractions(mock);
    }

    @Test
    @Override
    public void testCallbackThrowsErrorOnError() {
        DeliberateException exception = new DeliberateException();
        TerminalSignalConsumer mock = throwableMock(exception);
        listener.listen(doFinally(Completable.failed(DELIBERATE_EXCEPTION), mock))
                .verifyFailure(exception)
                .verifySuppressedFailure(DELIBERATE_EXCEPTION);
        verify(mock).onError(DELIBERATE_EXCEPTION);
        verifyNoMoreInteractions(mock);
    }
}
