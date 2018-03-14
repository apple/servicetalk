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

import io.servicetalk.concurrent.api.MockedSingleListenerRule;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestSingle;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mockito;

import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.verify;

public abstract class AbstractDoCancelTest {

    @Rule
    public final MockedSingleListenerRule<String> listener = new MockedSingleListenerRule<>();

    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    @Test
    public void testCancelAfterSuccess() {
        Runnable onCancel = Mockito.mock(Runnable.class);
        listener.listen(doCancel(Single.success("Hello"), onCancel)).cancel();
        verify(onCancel).run();
    }

    @Test
    public void testCancelNoEmissions() {
        Runnable onCancel = Mockito.mock(Runnable.class);
        listener.listen(doCancel(Single.never(), onCancel)).cancel();
        verify(onCancel).run();
    }

    @Test
    public void testCallbackThrowsError() {
        thrown.expect(is(sameInstance(DELIBERATE_EXCEPTION)));

        TestSingle<String> single = new TestSingle<>();
        try {
            listener.listen(doCancel(single, () -> {
                throw DELIBERATE_EXCEPTION;
            })).cancel();
        } finally {
            single.verifyCancelled();
        }
    }

    protected abstract <T> Single<T> doCancel(Single<T> single, Runnable runnable);
}
