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
package io.servicetalk.concurrent.api.single;

import io.servicetalk.concurrent.api.LegacyMockedSingleListenerRule;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.DeliberateException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mockito;

import java.util.function.Consumer;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.mockito.Mockito.verify;

public abstract class AbstractDoErrorTest {

    @Rule
    public final LegacyMockedSingleListenerRule<String> listener = new LegacyMockedSingleListenerRule<>();

    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    @Test
    public void testError() {
        @SuppressWarnings("unchecked")
        Consumer<Throwable> onError = Mockito.mock(Consumer.class);
        listener.listen(doError(Single.failed(DELIBERATE_EXCEPTION), onError));
        verify(onError).accept(DELIBERATE_EXCEPTION);
        listener.verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testCallbackThrowsError() {
        DeliberateException srcEx = new DeliberateException();
        listener.listen(doError(Single.failed(srcEx), t -> {
            throw DELIBERATE_EXCEPTION;
        })).verifyFailure(srcEx);
    }

    protected abstract <T> Single<T> doError(Single<T> single, Consumer<Throwable> consumer);
}
