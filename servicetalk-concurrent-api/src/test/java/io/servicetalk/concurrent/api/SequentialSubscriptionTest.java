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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.internal.DeliberateException;
import io.servicetalk.concurrent.internal.FlowControlUtils;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.ServiceTalkTestTimeout.DEFAULT_TIMEOUT_SECONDS;
import static io.servicetalk.concurrent.internal.SubscriberUtils.newExceptionForInvalidRequestN;
import static java.lang.Long.MAX_VALUE;
import static java.lang.Long.MIN_VALUE;
import static java.lang.Math.min;
import static java.util.concurrent.Executors.newCachedThreadPool;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.fail;
import static org.mockito.AdditionalMatchers.leq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public final class SequentialSubscriptionTest {
    private static final int ITERATIONS_FOR_CONCURRENT_TESTS = 500;
    @Rule
    public final ServiceTalkTestTimeout timeout = new ServiceTalkTestTimeout();

    private SequentialSubscription s;
    private Subscription s1;
    private Subscription s2;
    private ExecutorService executor;

    @Before
    public void setUp() {
        s1 = mock(Subscription.class);
        s = new SequentialSubscription(s1);
        s2 = mock(Subscription.class);
        executor = newCachedThreadPool();
    }

    @After
    public void tearDown() throws Exception {
        executor.shutdownNow();
        executor.awaitTermination(DEFAULT_TIMEOUT_SECONDS, SECONDS);
    }

    @Test
    public void testInvalidRequestNNegative1() {
        s.request(-1);
        verify(s1).request(-1);
    }

    @Test
    public void testInvalidRequestNZero() {
        s.request(0);
        verify(s1).request(leq(0L));
    }

    @Test
    public void testInvalidRequestNLongMin() {
        s.request(MIN_VALUE);
        verify(s1).request(leq(0L));
    }

    @Test
    public void testInvalidRequestNDefaultConstructorPropagatedAfterSwitch() {
        s = new SequentialSubscription();
        s.request(-1);
        s.switchTo(s1);
        verify(s1).request(leq(0L));
        s.switchTo(s2);
        verify(s2).request(leq(0L));
    }

    @Test
    public void testRequestNIncremental() {
        s.request(1);
        verify(s1).request(1);
        verifyNoMoreInteractions(s1);
        s.request(3);
        verify(s1).request(3);
        verifyNoMoreInteractions(s1);
    }

    @Test
    public void testCancel() {
        s.cancel();

        verify(s1).cancel();
        verifyNoMoreInteractions(s1);

        s.switchTo(s2);
        verify(s2).cancel();
        verifyNoMoreInteractions(s2);
    }

    @Test
    public void testPendingRequest() {
        s.request(5);
        verify(s1).request(5);
        verifyNoMoreInteractions(s1);
        invokeItemReceived(2);

        s.switchTo(s2);
        verify(s2).request(3);
        verifyNoMoreInteractions(s2);
        verifyNoMoreInteractions(s1);
    }

    @Test
    public void testOldSubscriptionIsNotCancelled() {
        s.switchTo(s2);
        verifyNoMoreInteractions(s1);
    }

    @Test(expected = NullPointerException.class)
    public void testSwitchToNull() {
        s.switchTo(null);
    }

    @Test
    public void testCancelAfterRequest() {
        s.cancel();
        verify(s1).cancel();
        s.request(1);
        verify(s1, never()).request(anyLong());
    }

    @Test
    public void testSubscriptionRequestThrows() {
        doThrow(DELIBERATE_EXCEPTION).when(s1).request(anyLong());
        try {
            s.request(1);
            fail("Request-n did not throw when subscription.request() threw.");
        } catch (DeliberateException de) {
            assertThat("Unexpected exception.", de, sameInstance(DELIBERATE_EXCEPTION));
        }

        verify(s1).request(1);
        s.cancel();
        verify(s1).cancel();
        verifyNoMoreInteractions(s1);

        s.switchTo(s2);
        verify(s2).cancel();
        verifyNoMoreInteractions(s2);
    }

    @Test
    public void testConcurrentNoSwitch() throws Exception {
        testConcurrentRequestEmitAndSwitch(1000, 1000);
    }

    @Test
    public void testConcurrentWithSwitch() throws Exception {
        testConcurrentRequestEmitAndSwitch(1000, 5);
    }

    @Test
    public void testConcurrentLargeRequestedWithSwitch() throws Exception {
        testConcurrentRequestEmitAndSwitch(1_000_000, 10_000);
    }

    @Test
    public void testRequestNAlwaysDirectedTowardSwitchedSubscription() throws Exception {
        for (int i = 0; i < ITERATIONS_FOR_CONCURRENT_TESTS; ++i) {
            requestNAlwaysDirectedTowardSwitchedSubscription();
        }
    }

    @Test
    public void requestNNegative1AlwaysDirectedTowardSwitchedSubscription() throws Exception {
        for (int i = 0; i < ITERATIONS_FOR_CONCURRENT_TESTS; ++i) {
            invalidRequestNAlwaysDirectedTowardSwitchedSubscription(-1, matchValueOrCancelled(is(-1L)));
        }
    }

    @Test
    public void requestNZeroAlwaysDirectedTowardSwitchedSubscription() throws Exception {
        for (int i = 0; i < ITERATIONS_FOR_CONCURRENT_TESTS; ++i) {
            invalidRequestNAlwaysDirectedTowardSwitchedSubscription(0, matchValueOrCancelled(lessThanOrEqualTo(0L)));
        }
    }

    @Test
    public void requestNLongMinAlwaysDirectedTowardSwitchedSubscription() throws Exception {
        for (int i = 0; i < ITERATIONS_FOR_CONCURRENT_TESTS; ++i) {
            invalidRequestNAlwaysDirectedTowardSwitchedSubscription(MIN_VALUE, matchValueOrCancelled(is(MIN_VALUE)));
        }
    }

    private void testConcurrentRequestEmitAndSwitch(int totalItems, int maxDeliveryPerSubscription) throws Exception {
        final SequentialSubscription subscription = new SequentialSubscription();
        final CyclicBarrier allStarted = new CyclicBarrier(3);
        Future<Void> requester = executor.submit(() -> {
            allStarted.await();
            for (long requestedCnt = 0; requestedCnt < totalItems; ++requestedCnt) {
                subscription.request(1);
            }
            return null;
        });
        Future<Long> sender = executor.submit(() -> {
            long totalSent = 0;
            CountingSubscription lastSourceSubscription = new CountingSubscription();
            subscription.switchTo(lastSourceSubscription);
            allStarted.await();
            while (totalSent < totalItems) {
                long reqRecvd = lastSourceSubscription.requestedReceived();
                if (reqRecvd != 0) {
                    long itemReceivedCount = min(reqRecvd, maxDeliveryPerSubscription);
                    // Simulate delivering itemReceivedCount items which is a snapshot of what has been requested from
                    // the lastSourceSubscription, and then terminating a source.
                    for (long i = 0; i < itemReceivedCount; ++i) {
                        subscription.itemReceived();
                        if (totalSent < totalItems) {
                            ++totalSent;
                        } else {
                            break;
                        }
                    }
                    lastSourceSubscription = new CountingSubscription();
                    subscription.switchTo(lastSourceSubscription);
                } else {
                    // Simulating switching with no items delivered (e.g. Completable, or empty Publisher).
                    lastSourceSubscription = new CountingSubscription();
                    subscription.switchTo(lastSourceSubscription);
                    Thread.yield();
                }
            }
            return totalSent;
        });
        allStarted.await();
        requester.get();
        assertThat("Unexpected itemReceived.", sender.get(), equalTo((long) totalItems));
    }

    private void requestNAlwaysDirectedTowardSwitchedSubscription() throws Exception {
        final SequentialSubscription subscription = new SequentialSubscription();
        final CyclicBarrier allStarted = new CyclicBarrier(3);
        Future<Void> requester = executor.submit(() -> {
            allStarted.await();
            subscription.request(1);
            return null;
        });
        Future<Long> sender = executor.submit(() -> {
            allStarted.await();
            CountingSubscription lastSourceSubscription = new CountingSubscription();
            subscription.switchTo(lastSourceSubscription);
            long reqRecvd;
            while ((reqRecvd = lastSourceSubscription.requestedReceived()) == 0) {
                Thread.yield();
            }
            return reqRecvd;
        });
        allStarted.await();
        requester.get();
        assertThat("Unexpected itemReceived.", sender.get(), equalTo((long) 1));
    }

    private void invalidRequestNAlwaysDirectedTowardSwitchedSubscription(long n,
                                                                         Matcher<? super CountingSubscription> matcher)
            throws Exception {
        CountingSubscription firstSourceSubscription = new CountingSubscription(true);
        final SequentialSubscription subscription = new SequentialSubscription(firstSourceSubscription);
        final CyclicBarrier allStarted = new CyclicBarrier(3);
        Future<Void> requester = executor.submit(() -> {
            allStarted.await();
            subscription.request(n);
            return null;
        });
        Future<CountingSubscription> sender = executor.submit(() -> {
            allStarted.await();
            CountingSubscription lastSourceSubscription = new CountingSubscription(true);
            subscription.switchTo(lastSourceSubscription);
            while (lastSourceSubscription.requestedReceived() == 0) {
                Thread.yield();
            }
            return lastSourceSubscription;
        });
        allStarted.await();
        requester.get();
        CountingSubscription countingSubscription = sender.get();
        assertThat("Unexpected itemReceived: " + countingSubscription, countingSubscription, matcher);
    }

    private static final class CountingSubscription implements Subscription {
        private final AtomicLong requestedReceived = new AtomicLong();
        private volatile boolean cancelled;
        private boolean allowInvalidN;

        CountingSubscription() {
            this(false);
        }

        CountingSubscription(boolean allowInvalidN) {
            this.allowInvalidN = allowInvalidN;
        }

        @Override
        public void request(long n) {
            if (allowInvalidN) {
                requestedReceived.addAndGet(n);
            } else if (n <= 0) {
                // set to MAX_VALUE so tests will complete and the exception will propagate, instead of the switch
                // thread being hung waiting for demand.
                requestedReceived.set(MAX_VALUE);
                throw newExceptionForInvalidRequestN(n);
            } else {
                requestedReceived.accumulateAndGet(n, FlowControlUtils::addWithOverflowProtection);
            }
        }

        @Override
        public void cancel() {
            cancelled = true;
            requestedReceived.set(MAX_VALUE);
        }

        boolean isCancelled() {
            return cancelled;
        }

        long requestedReceived() {
            return requestedReceived.get();
        }

        @Override
        public String toString() {
            return "requestedReceived: " + requestedReceived.get() + " cancelled: " + cancelled;
        }
    }

    private static Matcher<? super CountingSubscription> matchValueOrCancelled(
            Matcher<? super Long> valueMatcher) {
        return new BaseMatcher<CountingSubscription>() {
            @Override
            public void describeTo(final Description description) {
                valueMatcher.describeTo(description);
                description.appendText(" or not cancelled.");
            }

            @Override
            public boolean matches(final Object o) {
                if (!(o instanceof CountingSubscription)) {
                    return false;
                }
                CountingSubscription s = (CountingSubscription) o;
                return valueMatcher.matches(s.requestedReceived()) || s.isCancelled();
            }
        };
    }

    private void invokeItemReceived(int count) {
        for (int i = 0; i < count; i++) {
            s.itemReceived();
        }
    }
}
