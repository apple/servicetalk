/*
 * Copyright © 2020-2021 Apple Inc. and the ServiceTalk project authors
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@ExtendWith(TimeoutTracingInfoExtension.class)
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
    public void singleThreadCancelDeliveredIfRequestThrows() throws InterruptedException {
        CountDownLatch cancelledLatch = new CountDownLatch(1);
        Subscription concurrent = ConcurrentSubscription.wrap(new Subscription() {
            @Override
            public void request(final long n) {
                throw DELIBERATE_EXCEPTION;
            }

            @Override
            public void cancel() {
                cancelledLatch.countDown();
            }
        });
        try {
            concurrent.request(1);
            fail();
        } catch (DeliberateException e) {
            assertSame(DELIBERATE_EXCEPTION, e);
        }
        concurrent.cancel();
        cancelledLatch.await();
    }

    @Test
    public void singleThreadReentrant() {
        final ReentrantSubscription reentrantSubscription = new ReentrantSubscription(50);
        final Subscription concurrent = ConcurrentSubscription.wrap(reentrantSubscription);
        reentrantSubscription.outerSubscription(concurrent);
        concurrent.request(1);
        assertEquals(reentrantSubscription.reentrantLimit, reentrantSubscription.innerSubscription.requested());
    }

    @Test
    public void singleThreadInvalidRequestN() {
        Subscription concurrent = ConcurrentSubscription.wrap(subscription);
        final long invalidN = ThreadLocalRandom.current().nextLong(Long.MIN_VALUE, 1);
        concurrent.request(invalidN);
        assertThat("unexpected requested with invalidN: " + invalidN, subscription.requested(), lessThanOrEqualTo(0L));
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
        ExecutorService executorService = newFixedThreadPool(threads);
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
                        throw new AssertionError(e);
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

    @Test
    public void multiThreadCancelNotDeliveredIfRequestThrows() throws Exception {
        ExecutorService executorService = newFixedThreadPool(1);
        try {
            CyclicBarrier barrier = new CyclicBarrier(2);
            CountDownLatch cancelledLatch = new CountDownLatch(1);
            Subscription concurrent = ConcurrentSubscription.wrap(new Subscription() {
                @Override
                public void request(final long n) {
                    throw DELIBERATE_EXCEPTION;
                }

                @Override
                public void cancel() {
                    cancelledLatch.countDown();
                }
            });

            Future<?> f = executorService.submit(() -> {
                try {
                    barrier.await();
                } catch (Exception e) {
                    throw new AssertionError(e);
                }
                concurrent.request(1);
            });

            // wait for request to start processing so we issue the cancel concurrently
            barrier.await();
            concurrent.cancel();

            // Make sure that cancel is called eventually, despite request throwing.
            try {
                f.get();
                // don't fail, if cancel happens first then the Subscription is terminated.
                cancelledLatch.await();
            } catch (ExecutionException e) {
                assertSame(DELIBERATE_EXCEPTION, e.getCause());
            }
        } finally {
            executorService.shutdown();
        }
    }

    @Test
    public void multiThreadReentrant() throws Exception {
        ExecutorService executorService = newFixedThreadPool(1);
        try {
            final ReentrantSubscription reentrantSubscription = new ReentrantSubscription(50);
            final Subscription concurrent = ConcurrentSubscription.wrap(reentrantSubscription);
            CyclicBarrier barrier = new CyclicBarrier(2);
            reentrantSubscription.outerSubscription(concurrent);
            Future<?> f = executorService.submit(() -> {
                try {
                    barrier.await();
                } catch (Exception e) {
                    throw new AssertionError(e);
                }
                concurrent.request(1);
            });

            barrier.await();
            concurrent.request(1);
            f.get();
            // wait for the expected demand to be delivered.
            reentrantSubscription.innerSubscription.awaitRequestN(reentrantSubscription.reentrantLimit + 1);
        } finally {
            executorService.shutdown();
        }
    }

    @Test
    public void multiThreadInvalidRequestN() throws Exception {
        multiThreadInvalidRequestN(false);
    }

    @Test
    public void multiThreadInvalidRequestNCancel() throws Exception {
        multiThreadInvalidRequestN(true);
    }

    private void multiThreadInvalidRequestN(boolean cancel) throws Exception {
        ExecutorService executorService = newFixedThreadPool(1);
        try {
            Subscription concurrent = ConcurrentSubscription.wrap(subscription);
            CyclicBarrier barrier = new CyclicBarrier(2);
            final long invalidN = ThreadLocalRandom.current().nextLong(Long.MIN_VALUE, 1);
            Future<?> f = executorService.submit(() -> {
                try {
                    barrier.await();
                } catch (Exception e) {
                    throw new AssertionError(e);
                }

                if (cancel) {
                    concurrent.cancel();
                } else {
                    concurrent.request(1);
                }
            });

            barrier.await();
            concurrent.request(invalidN);

            f.get();

            if (cancel) {
                assertTrue(subscription.isCancelled());
            } else {
                assertThat("unexpected requested with invalidN: " + invalidN, subscription.requested(),
                        lessThanOrEqualTo(0L));
            }
        } finally {
            executorService.shutdown();
        }
    }

    private static final class ReentrantSubscription implements Subscription {
        private final TestSubscription innerSubscription = new TestSubscription();
        private final int reentrantLimit;
        private int reentrantCount;
        @Nullable
        private Subscription outerSubscription;

        private ReentrantSubscription(final int reentrantLimit) {
            this.reentrantLimit = reentrantLimit;
        }

        void outerSubscription(Subscription s) {
            outerSubscription = s;
        }

        @Override
        public void request(final long n) {
            assert outerSubscription != null;
            innerSubscription.request(n);
            if (++reentrantCount < reentrantLimit) {
                outerSubscription.request(1);
            }
        }

        @Override
        public void cancel() {
            innerSubscription.cancel();
        }
    }
}
