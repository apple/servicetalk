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
package io.servicetalk.concurrent.api.single;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.test.internal.TestSingleSubscriber;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.function.Consumer;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.verify;

public abstract class AbstractWhenOnSuccessTest {
    final TestSingleSubscriber<String> listener = new TestSingleSubscriber<>();

    @Test
    void testSuccess() {
        @SuppressWarnings("unchecked")
        Consumer<String> onSuccess = Mockito.mock(Consumer.class);
        toSource(doSuccess(Single.succeeded("Hello"), onSuccess)).subscribe(listener);
        verify(onSuccess).accept("Hello");
        assertThat(listener.awaitOnSuccess(), is("Hello"));
    }

    @Test
    abstract void testCallbackThrowsError();

    protected abstract <T> Single<T> doSuccess(Single<T> single, Consumer<T> consumer);
}
