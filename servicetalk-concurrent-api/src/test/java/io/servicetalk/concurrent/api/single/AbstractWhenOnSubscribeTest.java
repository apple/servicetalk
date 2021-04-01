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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.test.internal.TestSingleSubscriber;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public abstract class AbstractWhenOnSubscribeTest {
    final TestSingleSubscriber<String> listener = new TestSingleSubscriber<>();

    private Consumer<Cancellable> doOnListen;

    @SuppressWarnings("unchecked")
    @BeforeEach
    public void setUp() {
        doOnListen = mock(Consumer.class);
    }

    @Test
    public void testSubscribe() {
        toSource(doSubscribe(Single.succeeded("Hello"), doOnListen)).subscribe(listener);
        assertThat(listener.awaitOnSuccess(), is("Hello"));
        verify(doOnListen).accept(any(Cancellable.class));
    }

    protected abstract <T> Single<T> doSubscribe(Single<T> single, Consumer<Cancellable> consumer);
}
