/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.api.Single;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.Callable;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class CallableSingleTest {

    private Callable<Integer> factory;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() throws Exception {
        factory = mock(Callable.class);
    }

    @Test
    public void testEverySubscribeCalls() throws Exception {
        when(factory.call()).thenReturn(1);

        final Single<Integer> source = Single.fromCallable(factory);
        listenAndVerify(source);
        listenAndVerify(source);

        verify(factory, times(2)).call();
    }

    private static void listenAndVerify(Single<Integer> source) {
        @SuppressWarnings("unchecked")
        final SingleSource.Subscriber<Integer> subscriber = mock(SingleSource.Subscriber.class);
        toSource(source).subscribe(subscriber);
        verify(subscriber).onSubscribe(any());
        verify(subscriber).onSuccess(1);
        verifyNoMoreInteractions(subscriber);
    }

    @Test
    public void testOnError() throws Exception {
        when(factory.call()).thenThrow(IllegalArgumentException.class);

        final Single<Integer> source = Single.fromCallable(factory);
        listenAndVerifyError(source);
        verify(factory).call();
    }

    private static void listenAndVerifyError(Single<Integer> source) {
        @SuppressWarnings("unchecked")
        final SingleSource.Subscriber<Integer> subscriber = mock(SingleSource.Subscriber.class);
        toSource(source).subscribe(subscriber);
        verify(subscriber).onSubscribe(any());
        verify(subscriber).onError(any(IllegalArgumentException.class));
        verifyNoMoreInteractions(subscriber);
    }
}
