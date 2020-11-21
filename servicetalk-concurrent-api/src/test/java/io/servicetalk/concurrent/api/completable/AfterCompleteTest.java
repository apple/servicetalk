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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;

public class AfterCompleteTest extends AbstractWhenOnCompleteTest {

    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    @Override
    protected Completable doComplete(Completable completable, Runnable runnable) {
        return completable.afterOnComplete(runnable);
    }

    @Test
    public void testCallbackThrowsError() {
        toSource(doComplete(Completable.completed(), () -> {
            throw DELIBERATE_EXCEPTION;
        })).subscribe(listener);
        listener.awaitOnComplete();
    }
}
