/*
 * Copyright © 2018-2019, 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.api.publisher;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestSubscription;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public final class ForEachTest {

    private TestPublisher<Integer> source;
    private Consumer<Integer> forEach;
    private Cancellable cancellable;
    private TestSubscription subscription = new TestSubscription();

    @SuppressWarnings("unchecked")
    @BeforeEach
    public void setUp() {
        source = new TestPublisher.Builder<Integer>().disableAutoOnSubscribe().build();
        forEach = (Consumer<Integer>) mock(Consumer.class);
        cancellable = source.forEach(forEach);
        source.onSubscribe(subscription);
    }

    @Test
    public void testRequestedMax() {
        source.onNext(1, 2, 3); // Not requested explicitly
        verify(forEach).accept(1);
        verify(forEach).accept(2);
        verify(forEach).accept(3);
        verifyNoMoreInteractions(forEach);
    }

    @Test
    public void testCancel() {
        assertFalse(subscription.isCancelled());
        cancellable.cancel();
        assertTrue(subscription.isCancelled());
    }
}
