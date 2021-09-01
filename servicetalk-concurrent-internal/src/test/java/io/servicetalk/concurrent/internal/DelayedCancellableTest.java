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
package io.servicetalk.concurrent.internal;

import io.servicetalk.concurrent.Cancellable;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;

import static io.servicetalk.concurrent.internal.TestTimeoutConstants.DEFAULT_TIMEOUT_SECONDS;
import static java.util.concurrent.Executors.newCachedThreadPool;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class DelayedCancellableTest {
    private final DelayedCancellable delayedCancellable = new DelayedCancellable();
    private Cancellable c1;
    private Cancellable c2;
    private ExecutorService executor;

    @BeforeEach
    void setup() {
        c1 = mock(Cancellable.class);
        c2 = mock(Cancellable.class);
        executor = newCachedThreadPool();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        executor.shutdown();
        executor.awaitTermination(DEFAULT_TIMEOUT_SECONDS, SECONDS);
    }

    @Test
    void multipleDelayedSubscriptionCancels() {
        delayedCancellable.delayedCancellable(c1);
        delayedCancellable.delayedCancellable(c2);
        verifyNoMoreInteractions(c1);
        verify(c2).cancel();
    }

    @Test
    void delayedCancelIsDelivered() {
        delayedCancellable.cancel();
        delayedCancellable.delayedCancellable(c1);
        verify(c1).cancel();
    }

    @Test
    void signalsAfterDelayedArePassedThrough() {
        delayedCancellable.delayedCancellable(c1);
        delayedCancellable.cancel();
        verify(c1).cancel();
    }

    @Test
    void setDelayedFromAnotherThreadIsVisible() throws Exception {
        delayedCancellable.cancel();
        executor.submit(() -> delayedCancellable.delayedCancellable(c1)).get();
        verify(c1).cancel();
    }
}
