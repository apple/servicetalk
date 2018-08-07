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

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public abstract class AbstractDoFinallyTest {

    @Rule
    public final MockedSingleListenerRule<String> listener = new MockedSingleListenerRule<>();

    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    private Runnable doFinally;

    @Before
    public void setUp() throws Exception {
        doFinally = mock(Runnable.class);
    }

    @Test
    public void testForCancel() {
        listener.listen(Single.<String>never().doAfterFinally(doFinally));
        listener.cancel();
        verify(doFinally).run();
    }

    @Test
    public void testForCancelPostSuccess() {
        listener.listen(doFinally(Single.success("Hello"), doFinally));
        listener.cancel();
        verify(doFinally).run();
    }

    @Test
    public void testForCancelPostError() {
        listener.listen(doFinally(Single.error(DELIBERATE_EXCEPTION), doFinally));
        listener.cancel();
        verify(doFinally).run();
    }

    @Test
    public void testForSuccess() {
        listener.listen(doFinally(Single.success("Hello"), doFinally));
        listener.verifySuccess("Hello").cancel();
        verify(doFinally).run();
    }

    @Test
    public void testForError() {
        listener.listen(doFinally(Single.error(DELIBERATE_EXCEPTION), doFinally));
        listener.verifyFailure(DELIBERATE_EXCEPTION);
        verify(doFinally).run();
    }

    @Test
    public void testCallbackThrowsErrorOnCancel() {
        thrown.expect(is(sameInstance(DELIBERATE_EXCEPTION)));

        TestSingle<String> single = new TestSingle<>();
        try {
            listener.listen(doFinally(single, () -> {
                throw DELIBERATE_EXCEPTION;
            })).cancel();
            fail();
        } finally {
            single.verifyCancelled();
        }
    }

    @Test
    public abstract void testCallbackThrowsErrorOnSuccess();

    @Test
    public abstract void testCallbackThrowsErrorOnError();

    protected abstract <T> Single<T> doFinally(Single<T> single, Runnable runnable);
}
