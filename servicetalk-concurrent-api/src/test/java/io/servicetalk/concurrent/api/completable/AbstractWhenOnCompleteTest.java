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
import io.servicetalk.concurrent.test.internal.TestCompletableSubscriber;

import org.junit.Test;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public abstract class AbstractWhenOnCompleteTest {
    final TestCompletableSubscriber listener = new TestCompletableSubscriber();

    @Test
    public void testComplete() {
        Runnable onComplete = mock(Runnable.class);
        toSource(doComplete(Completable.completed(), onComplete)).subscribe(listener);
        verify(onComplete).run();
    }

    @Test
    public abstract void testCallbackThrowsError();

    protected abstract Completable doComplete(Completable completable, Runnable runnable);
}
