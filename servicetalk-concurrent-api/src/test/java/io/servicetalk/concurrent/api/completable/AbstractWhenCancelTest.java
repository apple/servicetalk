/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.internal.DeliberateException;
import io.servicetalk.concurrent.test.internal.TestCompletableSubscriber;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

public abstract class AbstractWhenCancelTest {
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
        LegacyTestCompletable completable = new LegacyTestCompletable();
        DeliberateException e = assertThrows(DeliberateException.class, () -> {
            try {
                toSource(doCancel(completable, () -> {
                    throw DELIBERATE_EXCEPTION;
                })).subscribe(listener);
                listener.awaitSubscription().cancel();
            } finally {
                completable.verifyCancelled();
            }
        });
        assertThat(e, is(sameInstance(DELIBERATE_EXCEPTION)));
    }

    protected abstract Completable doCancel(Completable completable, Runnable runnable);
}
