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
package io.servicetalk.concurrent.api.completable;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.api.Completable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class RunnableCompletableTest {
    private static final RuntimeException DELIBERATE_EXCEPTION = new IllegalArgumentException();

    private Runnable factory;

    @BeforeEach
    public void setUp() {
        factory = mock(Runnable.class);
    }

    @Test
    public void testEverySubscribeRuns() {
        Completable source = Completable.fromRunnable(factory);
        listenAndVerify(source);
        listenAndVerify(source);
        verify(factory, times(2)).run();
    }

    private static void listenAndVerify(Completable source) {
        CompletableSource.Subscriber subscriber = mock(CompletableSource.Subscriber.class);
        toSource(source).subscribe(subscriber);
        verify(subscriber).onSubscribe(any());
        verify(subscriber).onComplete();
        verifyNoMoreInteractions(subscriber);
    }

    @Test
    public void testOnError() {
        doThrow(IllegalArgumentException.class).when(factory).run();
        Completable source = Completable.fromRunnable(factory);
        listenAndVerifyError(source);
        verify(factory).run();
    }

    private static void listenAndVerifyError(Completable source) {
        CompletableSource.Subscriber subscriber = mock(CompletableSource.Subscriber.class);
        toSource(source).subscribe(subscriber);
        verify(subscriber).onSubscribe(any());
        verify(subscriber).onError(any(IllegalArgumentException.class));
        verifyNoMoreInteractions(subscriber);
    }

    @Test
    public void cancelInterrupts() throws Exception {
        final Completable source = Completable.fromRunnable(factory);
        final CountDownLatch latch = new CountDownLatch(1);

        doAnswer(invocation -> {
            try {
                // await till interrupted.
                latch.await();
            } catch (InterruptedException e) {
                latch.countDown();
            }
            return 1;
        }).when(factory).run();

        toSource(source).subscribe(new CompletableSource.Subscriber() {
            @Override
            public void onSubscribe(final Cancellable cancellable) {
                cancellable.cancel();
            }

            @Override
            public void onComplete() {
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
    public void onSubscribeThrows() {
        final Completable source = Completable.fromRunnable(factory);

        final CompletableSource.Subscriber subscriber = mock(CompletableSource.Subscriber.class);
        doThrow(DELIBERATE_EXCEPTION).when(subscriber).onSubscribe(any());

        toSource(source).subscribe(subscriber);
        verify(subscriber).onSubscribe(any());
        verify(subscriber).onError(DELIBERATE_EXCEPTION);
        verifyNoMoreInteractions(subscriber);
    }
}
