/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.TestSubscription;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConcurrentSubscriptionTest {
    private final TestSubscription subscription = new TestSubscription();

    @Test
    public void singleThreadSingleRequest() throws InterruptedException {
        Subscription concurrent = ConcurrentSubscription.wrap(subscription);
        final long demand = Long.MAX_VALUE;
        concurrent.request(demand);
        subscription.awaitRequestN(demand);
        assertFalse(subscription.isCancelled());
    }

    @Test
    public void singleThreadMultipleRequest() throws InterruptedException {
        Subscription concurrent = ConcurrentSubscription.wrap(subscription);
        final int demand = 100;
        for (int i = 0; i < demand; ++i) {
            concurrent.request(1);
        }
        subscription.awaitRequestN(demand);
        assertFalse(subscription.isCancelled());
    }

    @Test
    public void singleThreadCancel() {
        Subscription concurrent = ConcurrentSubscription.wrap(subscription);
        concurrent.cancel();
        assertTrue(subscription.isCancelled());
    }

    @Test
    public void multiThreadRequest() throws ExecutionException, InterruptedException {
        multiThread(300, false);
    }

    @Test
    public void multiThreadCancel() throws ExecutionException, InterruptedException {
        multiThread(250, true);
    }

    private void multiThread(final int threads, boolean cancel) throws ExecutionException, InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>(threads);
            Subscription concurrent = ConcurrentSubscription.wrap(subscription);
            CyclicBarrier barrier = new CyclicBarrier(threads);
            for (int i = 0; i < threads; ++i) {
                final int finalI = i;
                futures.add(executorService.submit(() -> {
                    try {
                        barrier.await();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    concurrent.request(1);
                    if (cancel && finalI % 2 == 0) {
                        concurrent.cancel();
                    }
                }));
            }

            for (Future<?> f : futures) {
                f.get();
            }
            if (cancel) {
                assertTrue(subscription.isCancelled());
            } else {
                subscription.awaitRequestN(threads);
                assertFalse(subscription.isCancelled());
            }
        } finally {
            executorService.shutdown();
        }
    }
}
