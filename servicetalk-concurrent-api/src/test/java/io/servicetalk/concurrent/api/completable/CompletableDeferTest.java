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

import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.api.Completable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class CompletableDeferTest {

    private Supplier<Completable> factory;

    @BeforeEach
    void setUp() {
        @SuppressWarnings("unchecked")
        Supplier<Completable> mock = mock(Supplier.class);
        when(mock.get()).thenReturn(completed());
        factory = mock;
    }

    @Test
    void testEverySubscribeCreatesNew() {
        Completable source = Completable.defer(factory);
        listenAndVerify(source);
        listenAndVerify(source);
        verify(factory, times(2)).get();
    }

    private static void listenAndVerify(Completable source) {
        CompletableSource.Subscriber subscriber = mock(CompletableSource.Subscriber.class);
        toSource(source).subscribe(subscriber);
        verify(subscriber).onSubscribe(any());
        verify(subscriber).onComplete();
        verifyNoMoreInteractions(subscriber);
    }
}
