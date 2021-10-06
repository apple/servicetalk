/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.api.Single;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class CallableSingleTest {
    private Callable<Integer> factory;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        factory = mock(Callable.class);
    }

    @Test
    void testEverySubscribeCalls() throws Exception {
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
    void testOnError() throws Exception {
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

    @Test
    void cancelInterrupts() throws Exception {
        final Single<Integer> source = Single.fromCallable(factory);
        final CountDownLatch latch = new CountDownLatch(1);

        when(factory.call()).then(invocation -> {
            try {
                // await till interrupted.
                latch.await();
            } catch (InterruptedException e) {
                latch.countDown();
                Thread.currentThread().interrupt();
                throw e;
            }
            return 1;
        });

        toSource(source).subscribe(new SingleSource.Subscriber<Integer>() {
            @Override
            public void onSubscribe(final Cancellable cancellable) {
                cancellable.cancel();
            }

            @Override
            public void onSuccess(@Nullable final Integer result) {
                // noop
            }

            @Override
            public void onError(final Throwable t) {
                // noop
            }
        });

        latch.await();
    }

    @Test
    void onSubscribeThrows() {
        final Single<Integer> source = Single.fromCallable(factory);

        @SuppressWarnings("unchecked")
        final SingleSource.Subscriber<Integer> subscriber = mock(SingleSource.Subscriber.class);
        doThrow(DELIBERATE_EXCEPTION).when(subscriber).onSubscribe(any());

        toSource(source).subscribe(subscriber);
        verify(subscriber).onSubscribe(any());
        verify(subscriber).onError(DELIBERATE_EXCEPTION);
        verifyNoMoreInteractions(subscriber);
    }
}
