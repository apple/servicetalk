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

import io.servicetalk.concurrent.api.Executor;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.ExternalResource;
import org.junit.rules.Timeout;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.mockito.ArgumentCaptor;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Executors.newFixedSizeExecutor;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.NoopRunnable.NOOP_RUNNABLE;
import static io.servicetalk.concurrent.internal.ThrowingRunnable.THROWING_RUNNABLE;
import static java.lang.Thread.currentThread;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.slf4j.LoggerFactory.getLogger;

public class DefaultSignalOffloaderPublisherTest {

    private static final Logger LOGGER = getLogger(DefaultSignalOffloaderPublisherTest.class);

    @Rule
    public final ExpectedException expected = ExpectedException.none();
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final OffloaderRule state = new OffloaderRule();

    @Test
    public void offloadSignal() throws InterruptedException {
        state.offloadSignalAndAwait("Hello");
    }

    @Test(expected = IllegalStateException.class)
    public void offloadSignalShouldTerminateOffloader() throws InterruptedException {
        state.offloadSignalAndAwait("Hello")
                .awaitTermination()
                .offloadSignalAndAwait("Hello1");
    }

    @Test
    public void offloadingSubscriberShouldNotOffloadSubscription() throws InterruptedException {
        Subscriber<? super String> offloadSubscription = state.offloader.offloadSubscription(state.subscriber);
        Subscriber<? super String> offloaded = state.offloader.offloadSubscriber(offloadSubscription);

        state.verifyOnSubscribeOffloaded(offloaded);
        state.verifyCancelNotOffloaded(state.subscription)
                .verifyOnCompleteOffloaded(offloaded) // Terminate offloader
                .awaitTermination();
    }

    @Test
    public void offloadingSubscriptionShouldNotOffloadSubscriber() throws InterruptedException {
        Subscriber<? super String> offloaded = state.offloader.offloadSubscription(state.subscriber);

        Subscription received = state.sendOnSubscribe(offloaded);
        state.verifyOnSubscribeNotOffloaded(offloaded)
                .verifyOnNextNotOffloaded(offloaded)
                .verifyRequestNOffloaded(received)
                .verifyCancelOffloaded(received)
                .awaitTermination();
    }

    @Test
    public void offloadSubscriber() throws InterruptedException {
        Subscriber<? super String> offloaded = state.offloader.offloadSubscriber(state.subscriber);
        state.verifyOnSubscribeOffloaded(offloaded);
        state.verifyOnNextOffloaded(offloaded)
                .verifyOnCompleteOffloaded(offloaded)
                .awaitTermination();
    }

    @Test
    public void cancelShouldTerminateOffloadingWithMultipleEntities() throws InterruptedException {
        Subscriber<? super String> offloadedSubscription = state.offloader.offloadSubscription(state.subscriber);
        Subscriber<? super String> offloadedSubscriber = state.offloader.offloadSubscriber(offloadedSubscription);
        Subscription received = state.verifyOnSubscribeOffloaded(offloadedSubscriber);
        state.verifyCancelOffloaded(received)
                .awaitTermination();
    }

    @Test
    public void onErrorShouldTerminateOffloadingWithMultipleEntities() throws InterruptedException {
        Subscriber<? super String> offloadedSubscription = state.offloader.offloadSubscription(state.subscriber);
        Subscriber<? super String> offloadedSubscriber = state.offloader.offloadSubscriber(offloadedSubscription);
        state.verifyOnSubscribeOffloaded(offloadedSubscriber);
        state.verifyOnErrorOffloaded(offloadedSubscriber).awaitTermination();
    }

    @Test
    public void onCompleteShouldTerminateOffloadingWithMultipleEntities() throws InterruptedException {
        Subscriber<? super String> offloadedSubscription = state.offloader.offloadSubscription(state.subscriber);
        Subscriber<? super String> offloadedSubscriber = state.offloader.offloadSubscriber(offloadedSubscription);
        state.verifyOnSubscribeOffloaded(offloadedSubscriber);
        state.verifyOnCompleteOffloaded(offloadedSubscriber).awaitTermination();
    }

    @Test
    public void onNextSupportsNull() throws InterruptedException {
        Subscriber<? super String> offloaded = state.offloader.offloadSubscriber(state.subscriber);
        state.verifyOnSubscribeOffloaded(offloaded);
        state.verifyOnNextOffloaded(offloaded, null)
                .verifyOnCompleteOffloaded(offloaded)
                .awaitTermination();
    }

    @Test
    public void onSubscribeThrows() throws InterruptedException {
        Subscriber<? super String> offloaded = state.offloader.offloadSubscriber(state.subscriber);
        state.verifyOnSubscribeOffloadedWhenThrows(offloaded);
        state.awaitTermination();
        verify(state.subscription).cancel();
    }

    @Test
    public void onNextThrows() throws InterruptedException {
        Subscriber<? super String> offloaded = state.offloader.offloadSubscriber(state.subscriber);
        state.verifyOnSubscribeOffloaded(offloaded);
        state.verifyOnNextOffloadedWhenThrows(offloaded);
        state.awaitTermination();
        verify(state.subscriber).onError(any(DeliberateException.class));
        verify(state.subscription).cancel();
    }

    @Test
    public void onErrorThrows() throws InterruptedException {
        Subscriber<? super String> offloaded = state.offloader.offloadSubscriber(state.subscriber);
        state.verifyOnSubscribeOffloaded(offloaded);
        state.verifyOnErrorOffloadedWhenThrows(offloaded);
        state.awaitTermination();
    }

    @Test
    public void onCompleteThrows() throws InterruptedException {
        Subscriber<? super String> offloaded = state.offloader.offloadSubscriber(state.subscriber);
        state.verifyOnSubscribeOffloaded(offloaded);
        state.verifyOnCompleteOffloadedWhenThrows(offloaded);
        state.awaitTermination();
    }

    @Test
    public void offloadPostTermination() throws InterruptedException {
        Subscriber<? super String> offloaded = state.offloader.offloadSubscriber(state.subscriber);
        state.verifyOnSubscribeOffloaded(offloaded);
        state.verifyOnCompleteOffloaded(offloaded);
        state.awaitTermination();
        expected.expect(instanceOf(IllegalStateException.class));
        state.offloader.offloadSubscriber(state.subscriber);
    }

    @Test
    public void negative1RequestIsPropagated() throws InterruptedException {
        testInvalidRequestNIsPropagated(-1, Long.MIN_VALUE);
    }

    @Test
    public void negative2RequestIsPropagated() throws InterruptedException {
        testInvalidRequestNIsPropagated(-2, Long.MIN_VALUE);
    }

    @Test
    public void negative3RequestIsPropagated() throws InterruptedException {
        testInvalidRequestNIsPropagated(-3, -3);
    }

    @Test
    public void zeroRequestIsPropagated() throws InterruptedException {
        testInvalidRequestNIsPropagated(0, Long.MIN_VALUE);
    }

    private void testInvalidRequestNIsPropagated(long toRequest, long expected) throws InterruptedException {
        Subscriber<? super String> offloaded = state.offloader.offloadSubscription(state.subscriber);
        Subscription received = state.sendOnSubscribe(offloaded);
        state.verifyRequestNOffloaded(received, toRequest, expected);
    }

    private static final class OffloaderRule extends ExternalResource {

        private Executor executor;
        private DefaultSignalOffloader offloader;
        private Subscription subscription;
        private Subscriber<String> subscriber;

        @Override
        public Statement apply(Statement base, Description description) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    executor = newFixedSizeExecutor(1);
                    offloader = new DefaultSignalOffloader(executor::execute);
                    subscription = mock(Subscription.class);
                    subscriber = mockSubscriber();
                    base.evaluate();
                }

                @SuppressWarnings("unchecked")
                private Subscriber<String> mockSubscriber() {
                    return mock(Subscriber.class);
                }
            };
        }

        @Override
        protected void after() {
            try {
                Await.awaitIndefinitely(executor.closeAsync());
            } catch (Exception e) {
                LOGGER.warn("Failed to close the executor {}.", executor, e);
            }
        }

        OffloaderRule offloadSignalAndAwait(String signal) throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(1);
            offloader.offloadSignal(signal, str -> latch.countDown());
            latch.await();
            return this;
        }

        OffloaderRule awaitTermination() throws InterruptedException {
            while (!offloader.isTerminated()) {
                Thread.sleep(10);
            }
            return this;
        }

        Subscription sendOnSubscribe(Subscriber<? super String> offloaded) {
            offloaded.onSubscribe(subscription);
            Subscription received = captureReceivedSubscription();
            assertThat("Unexpected subscription received.", received, is(notNullValue()));
            return received;
        }

        Subscription verifyOnSubscribeOffloadedWhenThrows(Subscriber<? super String> offloaded) throws InterruptedException {
            final Thread invoker = sendOnSubscribeAndReturnCaller(offloaded, () -> {
                throw DELIBERATE_EXCEPTION;
            });
            assertThat("Unexpected thread invoked offloaded Subscriber.", invoker,
                    not(sameInstance(currentThread())));
            return captureReceivedSubscription();
        }

        OffloaderRule verifyOnSubscribeNotOffloaded(Subscriber<? super String> offloaded) throws InterruptedException {
            final Thread invoker = sendOnSubscribeAndReturnCaller(offloaded, NOOP_RUNNABLE);
            assertThat("Unexpected thread invoked offloaded Subscriber.", invoker,
                    sameInstance(currentThread()));
            return this;
        }

        OffloaderRule verifyOnNextNotOffloaded(Subscriber<? super String> offloaded) throws InterruptedException {
            Thread invoker = sendOnNextAndReturnCaller(offloaded, "Hello", NOOP_RUNNABLE);
            assertThat("Unexpected thread invoked offloaded Subscriber.", invoker,
                    sameInstance(currentThread()));
            return this;
        }

        OffloaderRule verifyRequestNNotOffloaded(Subscription received) throws InterruptedException {
            Thread invoker = sendRequestNAndReturnCaller(received, 10, 10);
            assertThat("Unexpected thread invoked offloaded Subscription.", invoker,
                    sameInstance(currentThread()));
            return this;
        }

        OffloaderRule verifyCancelNotOffloaded(Subscription received) throws InterruptedException {
            Thread invoker = sendCancelAndReturnCaller(received);
            assertThat("Unexpected thread invoked offloaded Subscription.", invoker,
                    sameInstance(currentThread()));
            return this;
        }

        Subscription verifyOnSubscribeOffloaded(Subscriber<? super String> offloaded) throws InterruptedException {
            final Thread invoker = sendOnSubscribeAndReturnCaller(offloaded, NOOP_RUNNABLE);
            assertThat("Unexpected thread invoked offloaded Subscriber.", invoker,
                    not(sameInstance(currentThread())));
            return captureReceivedSubscription();
        }

        OffloaderRule verifyOnNextOffloaded(Subscriber<? super String> offloaded) throws InterruptedException {
            Thread invoker = sendOnNextAndReturnCaller(offloaded, "Hello", NOOP_RUNNABLE);
            assertThat("Unexpected thread invoked offloaded Subscriber.", invoker,
                    not(sameInstance(currentThread())));
            return this;
        }

        OffloaderRule verifyOnNextOffloadedWhenThrows(Subscriber<? super String> offloaded) throws InterruptedException {
            Thread invoker = sendOnNextAndReturnCaller(offloaded, "Hello", THROWING_RUNNABLE);
            assertThat("Unexpected thread invoked offloaded Subscriber.", invoker,
                    not(sameInstance(currentThread())));
            return this;
        }

        OffloaderRule verifyOnNextOffloaded(Subscriber<? super String> offloaded, @Nullable String item)
                throws InterruptedException {
            Thread invoker = sendOnNextAndReturnCaller(offloaded, item, NOOP_RUNNABLE);
            assertThat("Unexpected thread invoked offloaded Subscriber.", invoker,
                    not(sameInstance(currentThread())));
            return this;
        }

        OffloaderRule verifyOnErrorOffloaded(Subscriber<? super String> offloaded) throws InterruptedException {
            Thread invoker = sendOnErrorAndReturnCaller(offloaded, NOOP_RUNNABLE);
            assertThat("Unexpected thread invoked offloaded Subscriber.", invoker,
                    not(sameInstance(currentThread())));
            return this;
        }

        OffloaderRule verifyOnErrorOffloadedWhenThrows(Subscriber<? super String> offloaded) throws InterruptedException {
            Thread invoker = sendOnErrorAndReturnCaller(offloaded, THROWING_RUNNABLE);
            assertThat("Unexpected thread invoked offloaded Subscriber.", invoker,
                    not(sameInstance(currentThread())));
            return this;
        }

        OffloaderRule verifyOnCompleteOffloaded(Subscriber<? super String> offloaded) throws InterruptedException {
            Thread invoker = sendOnCompleteAndReturnCaller(offloaded, NOOP_RUNNABLE);
            assertThat("Unexpected thread invoked offloaded Subscriber.", invoker,
                    not(sameInstance(currentThread())));
            return this;
        }

        OffloaderRule verifyOnCompleteOffloadedWhenThrows(Subscriber<? super String> offloaded) throws InterruptedException {
            Thread invoker = sendOnCompleteAndReturnCaller(offloaded, THROWING_RUNNABLE);
            assertThat("Unexpected thread invoked offloaded Subscriber.", invoker,
                    not(sameInstance(currentThread())));
            return this;
        }

        OffloaderRule verifyRequestNOffloaded(Subscription received) throws InterruptedException {
            return verifyRequestNOffloaded(received, 10L, 10L);
        }

        OffloaderRule verifyRequestNOffloaded(Subscription received, long toRequest, long expected)
                throws InterruptedException {
            Thread invoker = sendRequestNAndReturnCaller(received, toRequest, expected);
            assertThat("Unexpected thread invoked offloaded Subscription.", invoker,
                    not(sameInstance(currentThread())));
            return this;
        }

        OffloaderRule verifyCancelOffloaded(Subscription received) throws InterruptedException {
            Thread invoker = sendCancelAndReturnCaller(received);
            assertThat("Unexpected thread invoked offloaded Subscription.", invoker,
                    not(sameInstance(currentThread())));
            return this;
        }

        private Subscription captureReceivedSubscription() {
            ArgumentCaptor<Subscription> subscriptionCaptor = forClass(Subscription.class);
            verify(subscriber).onSubscribe(subscriptionCaptor.capture());
            return subscriptionCaptor.getValue();
        }

        private Thread sendRequestNAndReturnCaller(Subscription received, long toRequest, long expected)
                throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(1);
            final AtomicReference<Thread> invoker = new AtomicReference<>();
            doAnswer(invocation -> {
                invoker.set(currentThread());
                latch.countDown();
                return null;
            }).when(subscription).request(anyLong());
            received.request(toRequest);
            latch.await();
            verify(subscription).request(expected);
            return invoker.get();
        }

        private Thread sendCancelAndReturnCaller(Subscription received) throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(1);
            final AtomicReference<Thread> invoker = new AtomicReference<>();
            doAnswer(invocation -> {
                invoker.set(currentThread());
                latch.countDown();
                return null;
            }).when(subscription).cancel();
            received.cancel();
            latch.await();
            return invoker.get();
        }

        private Thread sendOnSubscribeAndReturnCaller(Subscriber<? super String> offloaded, Runnable doOnSubscribe)
                throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(1);
            final AtomicReference<Thread> subscriberInvoker = new AtomicReference<>();
            doAnswer(invocation -> {
                subscriberInvoker.set(currentThread());
                latch.countDown();
                doOnSubscribe.run();
                return null;
            }).when(subscriber).onSubscribe(any(Subscription.class));
            offloaded.onSubscribe(subscription);
            latch.await();
            return subscriberInvoker.get();
        }

        private Thread sendOnNextAndReturnCaller(Subscriber<? super String> offloaded, @Nullable String item,
                                                 Runnable doOnNext)
                throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(1);
            final AtomicReference<Thread> subscriberInvoker = new AtomicReference<>();
            doAnswer(invocation -> {
                subscriberInvoker.set(currentThread());
                latch.countDown();
                doOnNext.run();
                return null;
            }).when(subscriber).onNext(item);
            offloaded.onNext(item);
            latch.await();
            return subscriberInvoker.get();
        }

        private Thread sendOnErrorAndReturnCaller(Subscriber<? super String> offloaded, Runnable doOnError)
                throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(1);
            final AtomicReference<Thread> subscriberInvoker = new AtomicReference<>();
            doAnswer(invocation -> {
                subscriberInvoker.set(currentThread());
                latch.countDown();
                doOnError.run();
                return null;
            }).when(subscriber).onError(any(Throwable.class));
            offloaded.onError(DELIBERATE_EXCEPTION);
            latch.await();
            return subscriberInvoker.get();
        }

        private Thread sendOnCompleteAndReturnCaller(Subscriber<? super String> offloaded, Runnable doOnComplete)
                throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(1);
            final AtomicReference<Thread> subscriberInvoker = new AtomicReference<>();
            doAnswer(invocation -> {
                subscriberInvoker.set(currentThread());
                latch.countDown();
                doOnComplete.run();
                return null;
            }).when(subscriber).onComplete();
            offloaded.onComplete();
            latch.await();
            return subscriberInvoker.get();
        }
    }
}
