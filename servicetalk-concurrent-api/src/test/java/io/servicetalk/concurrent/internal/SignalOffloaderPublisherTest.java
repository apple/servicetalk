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

import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.Executor;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Executors.from;
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
import static org.junit.Assume.assumeThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.slf4j.LoggerFactory.getLogger;

@RunWith(Parameterized.class)
public class SignalOffloaderPublisherTest {

    private static final Logger LOGGER = getLogger(SignalOffloaderPublisherTest.class);

    @Rule
    public final ExpectedException expected = ExpectedException.none();
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    public final OffloaderHolder state;
    private final boolean supportsTermination;

    public SignalOffloaderPublisherTest(Supplier<OffloaderHolder> state, boolean supportsTermination) {
        this.state = state.get();
        this.supportsTermination = supportsTermination;
    }

    @Parameterized.Parameters(name = "{index} - thread based: {1}")
    public static Collection<Object[]> offloaders() {
        Collection<Object[]> offloaders = new ArrayList<>();
        offloaders.add(new Object[]{(Supplier<OffloaderHolder>) () ->
                new OffloaderHolder(ThreadBasedSignalOffloader::new), true});
        offloaders.add(new Object[]{(Supplier<OffloaderHolder>) () ->
                new OffloaderHolder(TaskBasedSignalOffloader::new), false});
        return offloaders;
    }

    @After
    public void tearDown() throws Exception {
        state.shutdown();
    }

    @Test
    public void offloadSignal() throws Exception {
        state.offloadSignalAndAwait("Hello");
    }

    @Test(expected = IllegalStateException.class)
    public void offloadSignalShouldTerminateOffloader() throws Exception {
        assumeThat("Termination test not supported by this offloader.", supportsTermination, is(true));
        state.offloadSignalAndAwait("Hello")
                .awaitTermination()
                .offloadSignalAndAwait("Hello1");
    }

    @Test
    public void offloadingSubscriberShouldNotOffloadSubscription() throws Exception {
        Subscriber<? super String> offloadSubscription = state.offloader.offloadSubscription(state.subscriber);
        Subscriber<? super String> offloaded = state.offloader.offloadSubscriber(offloadSubscription);

        state.verifyOnSubscribeOffloaded(offloaded);
        state.verifyCancelNotOffloaded(state.subscription)
                .verifyOnCompleteOffloaded(offloaded) // Terminate offloader
                .awaitTermination();
    }

    @Test
    public void offloadingSubscriptionShouldNotOffloadSubscriber() throws Exception {
        Subscriber<? super String> offloaded = state.offloader.offloadSubscription(state.subscriber);

        Subscription received = state.sendOnSubscribe(offloaded);
        state.verifyOnSubscribeNotOffloaded(offloaded)
                .verifyOnNextNotOffloaded(offloaded)
                .verifyRequestNOffloaded(received)
                .verifyCancelOffloaded(received)
                .awaitTermination();
    }

    @Test
    public void offloadSubscriber() throws Exception {
        Subscriber<? super String> offloaded = state.offloader.offloadSubscriber(state.subscriber);
        state.verifyOnSubscribeOffloaded(offloaded);
        state.verifyOnNextOffloaded(offloaded)
                .verifyOnCompleteOffloaded(offloaded)
                .awaitTermination();
    }

    @Test
    public void cancelShouldTerminateOffloadingWithMultipleEntities() throws Exception {
        Subscriber<? super String> offloadedSubscription = state.offloader.offloadSubscription(state.subscriber);
        Subscriber<? super String> offloadedSubscriber = state.offloader.offloadSubscriber(offloadedSubscription);
        Subscription received = state.verifyOnSubscribeOffloaded(offloadedSubscriber);
        state.verifyCancelOffloaded(received)
                .awaitTermination();
    }

    @Test
    public void onErrorShouldTerminateOffloadingWithMultipleEntities() throws Exception {
        Subscriber<? super String> offloadedSubscription = state.offloader.offloadSubscription(state.subscriber);
        Subscriber<? super String> offloadedSubscriber = state.offloader.offloadSubscriber(offloadedSubscription);
        state.verifyOnSubscribeOffloaded(offloadedSubscriber);
        state.verifyOnErrorOffloaded(offloadedSubscriber).awaitTermination();
    }

    @Test
    public void onCompleteShouldTerminateOffloadingWithMultipleEntities() throws Exception {
        Subscriber<? super String> offloadedSubscription = state.offloader.offloadSubscription(state.subscriber);
        Subscriber<? super String> offloadedSubscriber = state.offloader.offloadSubscriber(offloadedSubscription);
        state.verifyOnSubscribeOffloaded(offloadedSubscriber);
        state.verifyOnCompleteOffloaded(offloadedSubscriber).awaitTermination();
    }

    @Test
    public void onNextSupportsNull() throws Exception {
        Subscriber<? super String> offloaded = state.offloader.offloadSubscriber(state.subscriber);
        state.verifyOnSubscribeOffloaded(offloaded);
        state.verifyOnNextOffloaded(offloaded, null)
                .verifyOnCompleteOffloaded(offloaded)
                .awaitTermination();
    }

    @Test
    public void onSubscribeThrows() throws Exception {
        Subscriber<? super String> offloaded = state.offloader.offloadSubscriber(state.subscriber);
        state.verifyOnSubscribeOffloadedWhenThrows(offloaded);
        state.awaitTermination();
        verify(state.subscription).cancel();
    }

    @Test
    public void onNextThrows() throws Exception {
        Subscriber<? super String> offloaded = state.offloader.offloadSubscriber(state.subscriber);
        state.verifyOnSubscribeOffloaded(offloaded);
        state.verifyOnNextOffloadedWhenThrows(offloaded);
        state.awaitTermination();
        verify(state.subscriber).onError(any(DeliberateException.class));
        verify(state.subscription).cancel();
    }

    @Test
    public void onErrorThrows() throws Exception {
        Subscriber<? super String> offloaded = state.offloader.offloadSubscriber(state.subscriber);
        state.verifyOnSubscribeOffloaded(offloaded);
        state.verifyOnErrorOffloadedWhenThrows(offloaded);
        state.awaitTermination();
    }

    @Test
    public void onCompleteThrows() throws Exception {
        Subscriber<? super String> offloaded = state.offloader.offloadSubscriber(state.subscriber);
        state.verifyOnSubscribeOffloaded(offloaded);
        state.verifyOnCompleteOffloadedWhenThrows(offloaded);
        state.awaitTermination();
    }

    @Test
    public void offloadPostTermination() throws Exception {
        assumeThat("Termination test not supported by this offloader.", supportsTermination, is(true));
        Subscriber<? super String> offloaded = state.offloader.offloadSubscriber(state.subscriber);
        state.verifyOnSubscribeOffloaded(offloaded);
        state.verifyOnCompleteOffloaded(offloaded);
        state.awaitTermination();
        expected.expect(instanceOf(IllegalStateException.class));
        state.offloader.offloadSubscriber(state.subscriber);
    }

    @Test
    public void negative1RequestIsPropagated() throws Exception {
        testInvalidRequestNIsPropagated(-1, Long.MIN_VALUE);
    }

    @Test
    public void negative2RequestIsPropagated() throws Exception {
        testInvalidRequestNIsPropagated(-2, Long.MIN_VALUE);
    }

    @Test
    public void negative3RequestIsPropagated() throws Exception {
        testInvalidRequestNIsPropagated(-3, -3);
    }

    @Test
    public void zeroRequestIsPropagated() throws Exception {
        testInvalidRequestNIsPropagated(0, Long.MIN_VALUE);
    }

    @Test
    public void executorRejectsForHandleSubscribe() {
        ThreadBasedSignalOffloader offloader = new ThreadBasedSignalOffloader(from(task -> {
            throw new RejectedExecutionException();
        }));
        offloader.offloadSubscribe(state.subscriber, __ -> {
        });
        verify(state.subscriber).onSubscribe(any(Subscription.class));
        ArgumentCaptor<Throwable> errorCaptor = ArgumentCaptor.forClass(Throwable.class);
        verify(state.subscriber).onError(errorCaptor.capture());
        assertThat("Unexpected error received by the subscriber.", errorCaptor.getValue(),
                instanceOf(RejectedExecutionException.class));
    }

    private void testInvalidRequestNIsPropagated(long toRequest, long expected) throws Exception {
        Subscriber<? super String> offloaded = state.offloader.offloadSubscription(state.subscriber);
        Subscription received = state.sendOnSubscribe(offloaded);
        state.verifyRequestNOffloaded(received, toRequest, expected);
    }

    private static final class OffloaderHolder {

        private Executor executor;
        private SignalOffloader offloader;
        private Subscription subscription;
        private Subscriber<String> subscriber;

        OffloaderHolder(Function<Executor, SignalOffloader> offloaderFactory) {
            executor = from(java.util.concurrent.Executors.newSingleThreadExecutor());
            offloader = offloaderFactory.apply(executor);
            subscription = mock(Subscription.class);
            subscriber = mockSubscriber();
        }

        void shutdown() {
            try {
                executor.closeAsync().toVoidFuture().get();
            } catch (Exception e) {
                LOGGER.warn("Failed to close the executor {}.", executor, e);
            }
        }

        OffloaderHolder awaitTermination() throws Exception {
            // Submit a task, since we use a single thread executor, this means all previous tasks have been
            // completed.
            executor.submit(() -> { }).toVoidFuture().get();
            return this;
        }

        OffloaderHolder offloadSignalAndAwait(String signal) throws Exception {
            CountDownLatch latch = new CountDownLatch(1);
            offloader.offloadSignal(signal, str -> latch.countDown());
            latch.await();
            return this;
        }

        Subscription sendOnSubscribe(Subscriber<? super String> offloaded) {
            offloaded.onSubscribe(subscription);
            Subscription received = captureReceivedSubscription();
            assertThat("Unexpected subscription received.", received, is(notNullValue()));
            return received;
        }

        Subscription verifyOnSubscribeOffloadedWhenThrows(Subscriber<? super String> offloaded) throws Exception {
            final Thread invoker = sendOnSubscribeAndReturnCaller(offloaded, () -> {
                throw DELIBERATE_EXCEPTION;
            });
            assertThat("Unexpected thread invoked offloaded Subscriber.", invoker,
                    not(sameInstance(currentThread())));
            return captureReceivedSubscription();
        }

        OffloaderHolder verifyOnSubscribeNotOffloaded(Subscriber<? super String> offloaded) throws Exception {
            final Thread invoker = sendOnSubscribeAndReturnCaller(offloaded, NOOP_RUNNABLE);
            assertThat("Unexpected thread invoked offloaded Subscriber.", invoker,
                    sameInstance(currentThread()));
            return this;
        }

        OffloaderHolder verifyOnNextNotOffloaded(Subscriber<? super String> offloaded) throws Exception {
            Thread invoker = sendOnNextAndReturnCaller(offloaded, "Hello", NOOP_RUNNABLE);
            assertThat("Unexpected thread invoked offloaded Subscriber.", invoker,
                    sameInstance(currentThread()));
            return this;
        }

        OffloaderHolder verifyRequestNNotOffloaded(Subscription received) throws Exception {
            Thread invoker = sendRequestNAndReturnCaller(received, 10, 10);
            assertThat("Unexpected thread invoked offloaded Subscription.", invoker,
                    sameInstance(currentThread()));
            return this;
        }

        OffloaderHolder verifyCancelNotOffloaded(Subscription received) throws Exception {
            Thread invoker = sendCancelAndReturnCaller(received);
            assertThat("Unexpected thread invoked offloaded Subscription.", invoker,
                    sameInstance(currentThread()));
            return this;
        }

        Subscription verifyOnSubscribeOffloaded(Subscriber<? super String> offloaded) throws Exception {
            final Thread invoker = sendOnSubscribeAndReturnCaller(offloaded, NOOP_RUNNABLE);
            assertThat("Unexpected thread invoked offloaded Subscriber.", invoker,
                    not(sameInstance(currentThread())));
            return captureReceivedSubscription();
        }

        OffloaderHolder verifyOnNextOffloaded(Subscriber<? super String> offloaded) throws Exception {
            Thread invoker = sendOnNextAndReturnCaller(offloaded, "Hello", NOOP_RUNNABLE);
            assertThat("Unexpected thread invoked offloaded Subscriber.", invoker,
                    not(sameInstance(currentThread())));
            return this;
        }

        OffloaderHolder verifyOnNextOffloadedWhenThrows(Subscriber<? super String> offloaded) throws Exception {
            Thread invoker = sendOnNextAndReturnCaller(offloaded, "Hello", THROWING_RUNNABLE);
            assertThat("Unexpected thread invoked offloaded Subscriber.", invoker,
                    not(sameInstance(currentThread())));
            return this;
        }

        OffloaderHolder verifyOnNextOffloaded(Subscriber<? super String> offloaded, @Nullable String item)
                throws Exception {
            Thread invoker = sendOnNextAndReturnCaller(offloaded, item, NOOP_RUNNABLE);
            assertThat("Unexpected thread invoked offloaded Subscriber.", invoker,
                    not(sameInstance(currentThread())));
            return this;
        }

        OffloaderHolder verifyOnErrorOffloaded(Subscriber<? super String> offloaded) throws Exception {
            Thread invoker = sendOnErrorAndReturnCaller(offloaded, NOOP_RUNNABLE);
            assertThat("Unexpected thread invoked offloaded Subscriber.", invoker,
                    not(sameInstance(currentThread())));
            return this;
        }

        OffloaderHolder verifyOnErrorOffloadedWhenThrows(Subscriber<? super String> offloaded) throws Exception {
            Thread invoker = sendOnErrorAndReturnCaller(offloaded, THROWING_RUNNABLE);
            assertThat("Unexpected thread invoked offloaded Subscriber.", invoker,
                    not(sameInstance(currentThread())));
            return this;
        }

        OffloaderHolder verifyOnCompleteOffloaded(Subscriber<? super String> offloaded) throws Exception {
            Thread invoker = sendOnCompleteAndReturnCaller(offloaded, NOOP_RUNNABLE);
            assertThat("Unexpected thread invoked offloaded Subscriber.", invoker,
                    not(sameInstance(currentThread())));
            return this;
        }

        OffloaderHolder verifyOnCompleteOffloadedWhenThrows(Subscriber<? super String> offloaded) throws Exception {
            Thread invoker = sendOnCompleteAndReturnCaller(offloaded, THROWING_RUNNABLE);
            assertThat("Unexpected thread invoked offloaded Subscriber.", invoker,
                    not(sameInstance(currentThread())));
            return this;
        }

        OffloaderHolder verifyRequestNOffloaded(Subscription received) throws Exception {
            return verifyRequestNOffloaded(received, 10L, 10L);
        }

        OffloaderHolder verifyRequestNOffloaded(Subscription received, long toRequest, long expected)
                throws Exception {
            Thread invoker = sendRequestNAndReturnCaller(received, toRequest, expected);
            assertThat("Unexpected thread invoked offloaded Subscription.", invoker,
                    not(sameInstance(currentThread())));
            return this;
        }

        OffloaderHolder verifyCancelOffloaded(Subscription received) throws Exception {
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
                throws Exception {
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

        private Thread sendCancelAndReturnCaller(Subscription received) throws Exception {
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
                throws Exception {
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
                throws Exception {
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
                throws Exception {
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
                throws Exception {
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

        @SuppressWarnings("unchecked")
        private Subscriber<String> mockSubscriber() {
            return mock(Subscriber.class);
        }
    }
}
