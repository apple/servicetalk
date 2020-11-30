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

import io.servicetalk.concurrent.api.LegacyTestSingle;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.test.internal.TestSingleSubscriber;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mockito;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.verify;

public abstract class AbstractWhenCancelTest {
    @Rule
    public final ExpectedException thrown = ExpectedException.none();
    private final TestSingleSubscriber<String> listener = new TestSingleSubscriber<>();

    @Test
    public void testCancelAfterSuccess() {
        Runnable onCancel = Mockito.mock(Runnable.class);
        toSource(doCancel(Single.succeeded("Hello"), onCancel)).subscribe(listener);
        listener.awaitSubscription().cancel();
        verify(onCancel).run();
    }

    @Test
    public void testCancelNoEmissions() {
        Runnable onCancel = Mockito.mock(Runnable.class);
        toSource(doCancel(Single.<String>never(), onCancel)).subscribe(listener);
        listener.awaitSubscription().cancel();
        verify(onCancel).run();
    }

    @Test
    public void testCallbackThrowsError() {
        thrown.expect(is(sameInstance(DELIBERATE_EXCEPTION)));

        LegacyTestSingle<String> single = new LegacyTestSingle<>();
        try {
            toSource(doCancel(single, () -> {
                throw DELIBERATE_EXCEPTION;
            })).subscribe(listener);
            listener.awaitSubscription().cancel();
        } finally {
            single.verifyCancelled();
        }
    }

    protected abstract <T> Single<T> doCancel(Single<T> single, Runnable runnable);
}
