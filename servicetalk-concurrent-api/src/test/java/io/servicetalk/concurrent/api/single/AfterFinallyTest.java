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
package io.servicetalk.concurrent.api.single;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.SingleTerminalSignalConsumer;
import io.servicetalk.concurrent.internal.DeliberateException;

import org.junit.jupiter.api.Test;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class AfterFinallyTest extends AbstractWhenFinallyTest {
    @Override
    protected <T> Single<T> doFinally(Single<T> single, SingleTerminalSignalConsumer<T> doFinally) {
        return single.afterFinally(doFinally);
    }

    @Test
    @Override
    public void testCallbackThrowsErrorOnSuccess() {
        SingleTerminalSignalConsumer<String> mock = throwableMock(DELIBERATE_EXCEPTION);
        String result = "Hello";
        toSource(doFinally(Single.succeeded(result), mock)).subscribe(listener);
        assertThat(listener.awaitOnSuccess(), is(result));
        verify(mock).onSuccess(result);
        verifyNoMoreInteractions(mock);
    }

    @Test
    @Override
    public void testCallbackThrowsErrorOnError() {
        SingleTerminalSignalConsumer<String> mock = throwableMock(new DeliberateException());
        toSource(doFinally(Single.failed(DELIBERATE_EXCEPTION), mock)).subscribe(listener);
        assertThat(listener.awaitOnError(), is(DELIBERATE_EXCEPTION));
        verify(mock).onError(DELIBERATE_EXCEPTION);
        verifyNoMoreInteractions(mock);
    }
}
