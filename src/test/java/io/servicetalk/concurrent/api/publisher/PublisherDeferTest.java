/**
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
package io.servicetalk.concurrent.api.publisher;

import io.servicetalk.concurrent.api.Publisher;
import org.junit.Before;
import org.junit.Test;
import org.reactivestreams.Subscriber;

import java.util.function.Supplier;

import static io.servicetalk.concurrent.api.Publisher.empty;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class PublisherDeferTest {

    private Supplier<Publisher<Integer>> factory;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() throws Exception {
        factory = mock(Supplier.class);
        when(factory.get()).thenReturn(empty());
    }

    @Test
    public void testEverySubscribeCreatesNew() throws Exception {
        Publisher<Integer> source = Publisher.defer(factory);
        subscribeAndVerify(source);
        subscribeAndVerify(source);
        verify(factory, times(2)).get();
    }

    private static void subscribeAndVerify(Publisher<Integer> source) {
        @SuppressWarnings("unchecked") Subscriber<Integer> subscriber = mock(Subscriber.class);
        source.subscribe(subscriber);
        verify(subscriber).onSubscribe(any());
        verify(subscriber).onComplete();
        verifyNoMoreInteractions(subscriber);
    }
}
