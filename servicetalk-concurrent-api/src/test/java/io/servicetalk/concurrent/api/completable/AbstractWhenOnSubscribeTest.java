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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.test.internal.TestCompletableSubscriber;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.function.Consumer;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public abstract class AbstractWhenOnSubscribeTest {
    @Rule
    public final ExpectedException thrown = ExpectedException.none();
    final TestCompletableSubscriber listener = new TestCompletableSubscriber();
    private Consumer<Cancellable> doOnListen;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() throws Exception {
        doOnListen = mock(Consumer.class);
    }

    @Test
    public void testOnSubscribe() {
        toSource(doSubscribe(Completable.completed(), doOnListen)).subscribe(listener);
        listener.awaitOnComplete();
        verify(doOnListen).accept(any(Cancellable.class));
    }

    protected abstract Completable doSubscribe(Completable completable, Consumer<Cancellable> consumer);
}
