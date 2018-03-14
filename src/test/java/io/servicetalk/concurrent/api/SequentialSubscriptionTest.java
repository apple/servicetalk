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

import io.servicetalk.concurrent.internal.FlowControlUtil;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.reactivestreams.Subscription;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.ServiceTalkTestTimeout.DEFAULT_TIMEOUT_SECONDS;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.SubscriberUtils.newExceptionForInvalidRequestN;
import static java.util.concurrent.Executors.newCachedThreadPool;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public final class SequentialSubscriptionTest {

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
        executor.shutdown();
        executor.awaitTermination(DEFAULT_TIMEOUT_SECONDS, SECONDS);
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
        verify(s1).cancel();
        verifyNoMoreInteractions(s1);
    }

    @Test
    public void testOldSubscriptionCancelThrows() {
        doThrow(DELIBERATE_EXCEPTION).when(s1).cancel();
        try {
            s.switchTo(s2);
            fail("Switch did not throw when old subscription cancel threw.");
        } catch (DeliberateException de) {
            assertThat("Unexpected exception.", de, sameInstance(DELIBERATE_EXCEPTION));
        }

        verify(s1).cancel();
        verifyNoMoreInteractions(s1);

        verify(s2).cancel();
        Subscription s3 = mock(Subscription.class);
        s.switchTo(s3);
        verify(s3).cancel();
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
        s.request(1);
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
        testConcurrentRequestEmitAndSwitch(10_00_000, 5);
    }

    private void testConcurrentRequestEmitAndSwitch(int totalItems, int switchEvery) throws Exception {
        final AtomicReference<RuntimeException> errorFromSubscription = new AtomicReference<>();
        final AtomicLong requestedReceived = new AtomicLong();
        SequentialSubscription subscription = new SequentialSubscription(newMockSubscription(requestedReceived, errorFromSubscription));
        final CyclicBarrier allStarted = new CyclicBarrier(3);
        final AtomicInteger requested = new AtomicInteger();
        final AtomicInteger sent = new AtomicInteger();
        Future<Void> requester = executor.submit(() -> {
            allStarted.await();
            int requestedCnt = 0;
            while (requestedCnt < totalItems) {
                subscription.request(1);
                requestedCnt++;
            }
            requested.addAndGet(requestedCnt);
            return null;
        });
        Future<Void> sender = executor.submit(() -> {
            allStarted.await();
            int totalSent = 0;
            long sentToCurrentSubscription = 0;
            AtomicLong currentSubscriptionRequested = requestedReceived;
            for (;;) {
                long reqRecvd = currentSubscriptionRequested.get();
                if (errorFromSubscription.get() != null) {
                    throw errorFromSubscription.get();
                }
                if (sentToCurrentSubscription == reqRecvd) {
                    int totalRequested = requested.get();
                    if (totalRequested == totalItems && totalSent == totalRequested) {
                        break;
                    } else {
                        Thread.yield();
                        continue;
                    }
                }
                subscription.itemReceived();
                sentToCurrentSubscription++;
                totalSent++;
                if (sentToCurrentSubscription % switchEvery == 0) {
                    sentToCurrentSubscription = 0;
                    currentSubscriptionRequested = new AtomicLong();
                    subscription.switchTo(newMockSubscription(currentSubscriptionRequested, errorFromSubscription));
                }
            }
            sent.set(totalSent);
            return null;
        });
        allStarted.await();
        requester.get();
        assertThat("Unexpected requested count.", requested.get(), equalTo(totalItems));
        sender.get();
        assertThat("Unexpected sent count.", sent.get(), equalTo(totalItems));
    }

    private static Subscription newMockSubscription(AtomicLong requestedReceived, AtomicReference<RuntimeException> errorFromSubscription) {
        return new Subscription() {
            @Override
            public void request(long n) {
                if (!isRequestNValid(n)) {
                    errorFromSubscription.set(newExceptionForInvalidRequestN(n));
                } else {
                    requestedReceived.accumulateAndGet(n, FlowControlUtil::addWithOverflowProtection);
                }
            }

            @Override
            public void cancel() {
                // Noop
            }
        };
    }

    private void invokeItemReceived(int count) {
        for (int i = 0; i < count; i++) {
            s.itemReceived();
        }
    }
}
