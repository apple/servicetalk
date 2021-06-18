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

import org.junit.jupiter.api.Test;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class BeforeCompleteTest extends AbstractWhenOnCompleteTest {
    @Override
    protected Completable doComplete(Completable completable, Runnable runnable) {
        return completable.beforeOnComplete(runnable);
    }

    @Test
    void testCallbackThrowsError() {
        toSource(doComplete(Completable.completed(), () -> {
            throw DELIBERATE_EXCEPTION;
        })).subscribe(listener);
        assertThat(listener.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }
}
