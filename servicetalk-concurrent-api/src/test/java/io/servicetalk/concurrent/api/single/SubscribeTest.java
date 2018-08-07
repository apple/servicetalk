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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.TestSingle;

import org.junit.Before;
import org.junit.Test;

import java.util.function.Consumer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public final class SubscribeTest {

    private TestSingle<Integer> source;
    private Consumer<Integer> resultConsumer;
    private Cancellable cancellable;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() {
        source = new TestSingle<>();
        resultConsumer = (Consumer<Integer>) mock(Consumer.class);
        cancellable = source.subscribe(resultConsumer);
    }

    @Test
    public void testSubscribe() {
        source.onSuccess(1);
        verify(resultConsumer).accept(1);
        verifyNoMoreInteractions(resultConsumer);
    }

    @Test
    public void testCancel() {
        source.verifyNotCancelled();
        cancellable.cancel();
        source.verifyCancelled();
    }
}
