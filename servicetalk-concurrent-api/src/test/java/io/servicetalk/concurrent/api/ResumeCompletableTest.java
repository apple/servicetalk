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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.internal.DeliberateException;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public final class ResumeCompletableTest {

    @Rule
    public final LegacyMockedCompletableListenerRule listener = new LegacyMockedCompletableListenerRule();

    private LegacyTestCompletable first;
    private LegacyTestCompletable second;

    @Before
    public void setUp() {
        first = new LegacyTestCompletable();
        second = new LegacyTestCompletable();
        listener.listen(first.onErrorResume(throwable -> second));
    }

    @Test
    public void testFirstComplete() {
        first.onComplete();
        listener.verifyCompletion();
    }

    @Test
    public void testFirstErrorSecondComplete() {
        first.onError(DELIBERATE_EXCEPTION);
        listener.verifyNoEmissions();
        second.onComplete();
        listener.verifyCompletion();
    }

    @Test
    public void testFirstErrorSecondError() {
        first.onError(new DeliberateException());
        listener.verifyNoEmissions();
        second.onError(DELIBERATE_EXCEPTION);
        listener.verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testCancelFirstActive() {
        listener.cancel();
        first.verifyCancelled();
        listener.verifyNoEmissions();
    }

    @Test
    public void testCancelSecondActive() {
        first.onError(DELIBERATE_EXCEPTION);
        listener.verifyNoEmissions();
        listener.cancel();
        second.verifyCancelled();
        first.verifyNotCancelled();
    }

    @Test
    public void testErrorSuppressOriginalException() {
        listener.reset();
        DeliberateException ex = new DeliberateException();
        listener.listen(first.onErrorResume(throwable -> {
            throw ex;
        }));

        first.onError(DELIBERATE_EXCEPTION);
        listener.verifyFailure(ex);
        assertEquals(1, ex.getSuppressed().length);
        assertSame(DELIBERATE_EXCEPTION, ex.getSuppressed()[0]);
    }
}
