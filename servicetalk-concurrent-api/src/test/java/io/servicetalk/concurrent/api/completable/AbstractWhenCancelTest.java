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
package io.servicetalk.concurrent.api.completable;

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.LegacyTestCompletable;
import io.servicetalk.concurrent.test.internal.TestCompletableSubscriber;

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
    final TestCompletableSubscriber listener = new TestCompletableSubscriber();

    @Test
    public void testCancelNoEmissions() {
        Runnable onCancel = Mockito.mock(Runnable.class);
        LegacyTestCompletable completable = new LegacyTestCompletable();
        toSource(doCancel(completable, onCancel)).subscribe(listener);
        listener.awaitSubscription().cancel();
        verify(onCancel).run();
        completable.verifyCancelled();
    }

    @Test
    public void testCallbackThrowsError() {
        thrown.expect(is(sameInstance(DELIBERATE_EXCEPTION)));

        LegacyTestCompletable completable = new LegacyTestCompletable();
        try {
            toSource(doCancel(completable, () -> {
                throw DELIBERATE_EXCEPTION;
            })).subscribe(listener);
            listener.awaitSubscription().cancel();
        } finally {
            completable.verifyCancelled();
        }
    }

    protected abstract Completable doCancel(Completable completable, Runnable runnable);
}
