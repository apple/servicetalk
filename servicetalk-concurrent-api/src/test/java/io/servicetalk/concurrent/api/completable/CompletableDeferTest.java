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

import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.api.Completable;

import org.junit.Before;
import org.junit.Test;

import java.util.function.Supplier;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class CompletableDeferTest {

    private Supplier<Completable> factory;

    @Before
    public void setUp() throws Exception {
        @SuppressWarnings("unchecked")
        Supplier<Completable> mock = mock(Supplier.class);
        when(mock.get()).thenReturn(completed());
        factory = mock;
    }

    @Test
    public void testEverySubscribeCreatesNew() throws Exception {
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
