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

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.concurrent.CountDownLatch;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Completable.failed;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

class SimpleSubscribeTest {

    @Test
    void noRunnable() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        completed().afterFinally(latch::countDown).subscribe();
        latch.await();
    }

    @Test
    void runnableThrows() throws Exception {
        Runnable onComplete = Mockito.mock(Runnable.class);
        doThrow(DELIBERATE_EXCEPTION).when(onComplete).run();
        CountDownLatch latch = new CountDownLatch(1);
        completed().afterFinally(latch::countDown).subscribe(onComplete);
        latch.await();
        verify(onComplete).run();
    }

    @Test
    void runnableIsInvokedOnComplete() throws Exception {
        Runnable onComplete = Mockito.mock(Runnable.class);
        CountDownLatch latch = new CountDownLatch(1);
        completed().afterFinally(latch::countDown).subscribe(onComplete);
        latch.await();
        verify(onComplete).run();
    }

    @Test
    void runnableIsNotInvokedOnError() throws Exception {
        Runnable onComplete = Mockito.mock(Runnable.class);
        CountDownLatch latch = new CountDownLatch(1);
        failed(DELIBERATE_EXCEPTION).afterFinally(latch::countDown).subscribe(onComplete);
        latch.await();
        verifyZeroInteractions(onComplete);
    }

    @Test
    void runnableIsNotInvokedWhenCancelled() throws Exception {
        Runnable onComplete = Mockito.mock(Runnable.class);
        CountDownLatch latch = new CountDownLatch(1);
        failed(DELIBERATE_EXCEPTION).afterFinally(latch::countDown).subscribe(onComplete).cancel();
        latch.await();
        verifyZeroInteractions(onComplete);
    }
}
