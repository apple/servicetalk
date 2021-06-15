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

import io.servicetalk.concurrent.PublisherSource.Subscription;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static io.servicetalk.concurrent.internal.TestTimeoutConstants.DEFAULT_TIMEOUT_SECONDS;
import static java.util.concurrent.Executors.newCachedThreadPool;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.AdditionalMatchers.leq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class DelayedSubscriptionTest {
    @Rule
    public final ServiceTalkTestTimeout timeout = new ServiceTalkTestTimeout();

    private final DelayedSubscription delayedSubscription = new DelayedSubscription();
    private Subscription s1;
    private Subscription s2;
    private ExecutorService executor;

    @Before
    public void setup() {
        s1 = mock(Subscription.class);
        s2 = mock(Subscription.class);
        executor = newCachedThreadPool();
    }

    @After
    public void tearDown() throws InterruptedException {
        executor.shutdown();
        executor.awaitTermination(DEFAULT_TIMEOUT_SECONDS, SECONDS);
    }

    @Test
    public void multipleDelayedSubscriptionCancels() {
        delayedSubscription.delayedSubscription(s1);
        delayedSubscription.delayedSubscription(s2);
        verifyNoMoreInteractions(s1);
        verify(s2).cancel();
        verifyNoMoreInteractions(s2);
    }

    @Test
    public void delaySubscriptionIsRequested() {
        delayedSubscription.request(100);
        delayedSubscription.request(5);
        delayedSubscription.delayedSubscription(s1);
        verify(s1).request(105);
        verifyNoMoreInteractions(s1);
    }

    @Test
    public void delaySubscriptionIsCancelled() {
        delayedSubscription.request(100);
        delayedSubscription.request(5);
        delayedSubscription.cancel();
        delayedSubscription.delayedSubscription(s1);
        verify(s1).cancel();
        verifyNoMoreInteractions(s1);
    }

    @Test
    public void invalidRequestNIsPassedThrough() {
        delayedSubscription.request(100);
        delayedSubscription.request(-1);
        delayedSubscription.delayedSubscription(s1);
        verify(s1).request(-1);
        verifyNoMoreInteractions(s1);
    }

    @Test
    public void invalidRequestNZeroIsNotPassedThrough() {
        delayedSubscription.request(100);
        delayedSubscription.request(0);
        delayedSubscription.delayedSubscription(s1);
        verify(s1).request(leq(0L));
        verifyNoMoreInteractions(s1);
    }

    @Test
    public void signalsAfterDelayedArePassedThrough() {
        delayedSubscription.request(2);
        delayedSubscription.delayedSubscription(s1);
        verify(s1).request(2);
        verifyNoMoreInteractions(s1);
        delayedSubscription.request(3);
        verify(s1).request(3);
        verifyNoMoreInteractions(s1);
        delayedSubscription.cancel();
        verify(s1).cancel();
        verifyNoMoreInteractions(s1);
    }

    @Test
    public void setDelayedFromAnotherThreadIsVisible() throws Exception {
        delayedSubscription.request(2);
        executor.submit(() -> delayedSubscription.delayedSubscription(s1)).get();
        verify(s1).request(2);
        verifyNoMoreInteractions(s1);
    }

    @Test
    public void concurrentRequestAndSwap() throws Exception {
        for (int i = 0; i < 1000; i++) {
            doConcurrentRequestAndSwap();
        }
    }

    private void doConcurrentRequestAndSwap() throws InterruptedException, ExecutionException {
        DelayedSubscription ds = new DelayedSubscription();
        CountingSubscription s = new CountingSubscription();
        CyclicBarrier barrier = new CyclicBarrier(2);
        Future<Void> requester = executor.submit(() -> {
            for (int i = 0; i < 10_000; i++) {
                if (i == 5_000) {
                    barrier.await();
                }
                ds.request(1);
            }
            return null;
        });
        Future<Void> swapper = executor.submit(() -> {
            barrier.await();
            ds.delayedSubscription(s);
            return null;
        });
        swapper.get();
        requester.get();
        assertThat("Unexpected items requested.", s.requested(), is(10_000));
    }

    private static class CountingSubscription implements Subscription {
        private int requested;

        @Override
        public void request(long n) {
            requested += n;
        }

        @Override
        public void cancel() {
            requested = -1;
        }

        int requested() {
            return requested;
        }
    }
}
