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
package io.servicetalk.concurrent.api.completable;

import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.api.Completable;

import org.junit.Before;
import org.junit.Test;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class RunnableCompletableTest {

    private Runnable factory;

    @Before
    public void setUp() throws Exception {
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
}
