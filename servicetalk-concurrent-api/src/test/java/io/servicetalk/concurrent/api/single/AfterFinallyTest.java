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

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.Single.TerminalSignalConsumer;
import io.servicetalk.concurrent.internal.DeliberateException;

import org.junit.Test;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;

public class AfterFinallyTest extends AbstractWhenFinallyTest {
    @Override
    protected <T> Single<T> doFinally(Single<T> single, TerminalSignalConsumer<T> doFinally) {
        return single.afterFinally(doFinally);
    }

    @Test
    @Override
    public void testCallbackThrowsErrorOnSuccess() {
        listener.listen(doFinally(Single.succeeded("Hello"), TerminalSignalConsumer.from(() -> {
            throw DELIBERATE_EXCEPTION;
        }))).verifySuccess("Hello");
    }

    @Test
    @Override
    public void testCallbackThrowsErrorOnError() {
        DeliberateException exception = new DeliberateException();
        listener.listen(doFinally(Single.failed(exception), TerminalSignalConsumer.from(() -> {
            throw DELIBERATE_EXCEPTION;
        }))).verifyFailure(exception);
    }
}
