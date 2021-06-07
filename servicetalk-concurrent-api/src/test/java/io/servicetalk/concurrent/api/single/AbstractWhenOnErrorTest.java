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
import io.servicetalk.concurrent.internal.DeliberateException;
import io.servicetalk.concurrent.test.internal.TestSingleSubscriber;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.function.Consumer;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.verify;

public abstract class AbstractWhenOnErrorTest {
    private final TestSingleSubscriber<String> listener = new TestSingleSubscriber<>();

    @Test
    void testError() {
        @SuppressWarnings("unchecked")
        Consumer<Throwable> onError = Mockito.mock(Consumer.class);
        toSource(doError(Single.<String>failed(DELIBERATE_EXCEPTION), onError)).subscribe(listener);
        verify(onError).accept(DELIBERATE_EXCEPTION);
        assertThat(listener.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void testCallbackThrowsError() {
        DeliberateException srcEx = new DeliberateException();
        toSource(doError(Single.<String>failed(srcEx), t -> {
            throw DELIBERATE_EXCEPTION;
        })).subscribe(listener);
        assertThat(listener.awaitOnError(), is(srcEx));
    }

    protected abstract <T> Single<T> doError(Single<T> single, Consumer<Throwable> consumer);
}
