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
import io.servicetalk.concurrent.Single.Subscriber;
import io.servicetalk.concurrent.api.Executor;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.ExternalResource;
import org.junit.rules.Timeout;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.slf4j.LoggerFactory.getLogger;

public class DefaultSignalOffloaderSingleTest {

    private static final Logger LOGGER = getLogger(DefaultSignalOffloaderSingleTest.class);

    @Rule
    public final ExpectedException expected = ExpectedException.none();
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final OffloaderRule state = new OffloaderRule();

    @Test
    public void offloadingSubscriberShouldNotOffloadCancellable() throws InterruptedException {
        Subscriber<? super String> offloadedSubscriber = state.offloader.offloadCancellable(state.subscriber);
        Subscriber<? super String> offloaded = state.offloader.offloadSubscriber(offloadedSubscriber);
        state.verifyOnSubscribeOffloaded(offloaded);
        state.verifyCancelNotOffloaded(state.cancellable)
                .verifyOnSuccessOffloaded(offloaded)
                .awaitTermination();
    }

    @Test
    public void offloadingCancellableShouldNotOffloadSubscriber() throws InterruptedException {
        Subscriber<? super String> offloaded = state.offloader.offloadCancellable(state.subscriber);

        Cancellable received = state.sendOnSubscribe(offloaded);
        state.verifyOnSubscribeNotOffloaded(offloaded)
                .verifyCancelOffloaded(received)
                .awaitTermination();
    }

    @Test
    public void offloadSubscriber() throws InterruptedException {
        Subscriber<? super String> offloaded = state.offloader.offloadSubscriber(state.subscriber);
        state.verifyOnSubscribeOffloaded(offloaded);
        state.verifyOnSuccessOffloaded(offloaded)
                .awaitTermination();
    }

    @Test
    public void cancelShouldTerminateOffloadingWithMultipleEntities() throws InterruptedException {
        Subscriber<? super String> offloadedCancellable = state.offloader.offloadCancellable(state.subscriber);
        Subscriber<? super String> offloadedSubscriber = state.offloader.offloadSubscriber(offloadedCancellable);
        Cancellable received = state.verifyOnSubscribeOffloaded(offloadedSubscriber);
        state.verifyCancelOffloaded(received)
                .awaitTermination();
    }

    @Test
    public void onErrorShouldTerminateOffloadingWithMultipleEntities() throws InterruptedException {
        Subscriber<? super String> offloadedCancellable = state.offloader.offloadCancellable(state.subscriber);
        Subscriber<? super String> offloadedSubscriber = state.offloader.offloadSubscriber(offloadedCancellable);
        state.verifyOnSubscribeOffloaded(offloadedSubscriber);
        state.verifyOnErrorOffloaded(offloadedSubscriber).awaitTermination();
    }

    @Test
    public void onSuccessShouldTerminateOffloadingWithMultipleEntities() throws InterruptedException {
        Subscriber<? super String> offloadedCancellable = state.offloader.offloadCancellable(state.subscriber);
        Subscriber<? super String> offloadedSubscriber = state.offloader.offloadSubscriber(offloadedCancellable);
        state.verifyOnSubscribeOffloaded(offloadedSubscriber);
        state.verifyOnSuccessOffloaded(offloadedSubscriber).awaitTermination();
    }

    @Test
    public void onSuccessSupportsNull() throws InterruptedException {
        Subscriber<? super String> offloaded = state.offloader.offloadSubscriber(state.subscriber);
        state.verifyOnSubscribeOffloaded(offloaded);
        state.verifyOnSuccessOffloaded(offloaded, null).awaitTermination();
    }

    @Test
    public void onSubscribeThrows() throws InterruptedException {
        Subscriber<? super String> offloaded = state.offloader.offloadSubscriber(state.subscriber);
        state.verifyOnSubscribeOffloadedWhenThrows(offloaded);
        state.awaitTermination();
        verify(state.cancellable).cancel();
    }

    @Test
    public void onSuccessThrows() throws InterruptedException {
        Subscriber<? super String> offloaded = state.offloader.offloadSubscriber(state.subscriber);
        state.verifyOnSubscribeOffloaded(offloaded);
        state.verifyOnSuccessOffloadedWhenThrows(offloaded);
        state.awaitTermination();
    }

    @Test
    public void onErrorThrows() throws InterruptedException {
        Subscriber<? super String> offloaded = state.offloader.offloadSubscriber(state.subscriber);
        state.verifyOnSubscribeOffloaded(offloaded);
        state.verifyOnErrorOffloadedWhenThrows(offloaded);
        state.awaitTermination();
    }

    @Test
    public void offloadPostTermination() throws InterruptedException {
        Subscriber<? super String> offloaded = state.offloader.offloadSubscriber(state.subscriber);
        state.verifyOnSubscribeOffloaded(offloaded);
        state.verifyOnErrorOffloaded(offloaded);
        state.awaitTermination();
        expected.expect(IllegalStateException.class);
        state.offloader.offloadSubscriber(state.subscriber);
    }

    @Test
    public void executorRejectsForHandleSubscribe() {
        DefaultSignalOffloader offloader = new DefaultSignalOffloader(task -> {
            throw new RejectedExecutionException();
        });
        offloader.offloadSubscribe(state.subscriber, __ -> {
        });
        verify(state.subscriber).onSubscribe(any(Cancellable.class));
        ArgumentCaptor<Throwable> errorCaptor = ArgumentCaptor.forClass(Throwable.class);
        verify(state.subscriber).onError(errorCaptor.capture());
        assertThat("Unexpected error received by the subscriber.", errorCaptor.getValue(),
                instanceOf(RejectedExecutionException.class));
    }

    private static final class OffloaderRule extends ExternalResource {

        private Executor executor;
        private DefaultSignalOffloader offloader;
        private Cancellable cancellable;
        private Subscriber<String> subscriber;

        @Override
        public Statement apply(Statement base, Description description) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    executor = newFixedSizeExecutor(1);
                    offloader = new DefaultSignalOffloader(executor::execute);
                    cancellable = mock(Cancellable.class);
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

        void awaitTermination() throws InterruptedException {
            while (!offloader.isTerminated()) {
                Thread.sleep(10);
            }
        }

        Cancellable sendOnSubscribe(Subscriber<? super String> offloaded) {
            offloaded.onSubscribe(cancellable);
            Cancellable received = captureReceivedCancellable();
            assertThat("Unexpected subscription received.", received, is(notNullValue()));
            return received;
        }

        OffloaderRule verifyOnSubscribeNotOffloaded(Subscriber<? super String> offloaded) throws InterruptedException {
            final Thread invoker = sendOnSubscribeAndReturnCaller(offloaded, NOOP_RUNNABLE);
            assertThat("Unexpected thread invoked offloaded Subscriber.", invoker,
                    sameInstance(currentThread()));
            return this;
        }

        OffloaderRule verifyCancelNotOffloaded(Cancellable received) throws InterruptedException {
            Thread invoker = sendCancelAndReturnCaller(received);
            assertThat("Unexpected thread invoked offloaded Subscription.", invoker,
                    sameInstance(currentThread()));
            return this;
        }

        Cancellable verifyOnSubscribeOffloaded(Subscriber<? super String> offloaded) throws InterruptedException {
            final Thread invoker = sendOnSubscribeAndReturnCaller(offloaded, NOOP_RUNNABLE);
            assertThat("Unexpected thread invoked offloaded Subscriber.", invoker,
                    not(sameInstance(currentThread())));
            return captureReceivedCancellable();
        }

        void verifyOnSubscribeOffloadedWhenThrows(Subscriber<? super String> offloaded)
                throws InterruptedException {
            final Thread invoker = sendOnSubscribeAndReturnCaller(offloaded, THROWING_RUNNABLE);
            assertThat("Unexpected thread invoked offloaded Subscriber.", invoker,
                    not(sameInstance(currentThread())));
            captureReceivedCancellable();
        }

        OffloaderRule verifyOnErrorOffloaded(Subscriber<? super String> offloaded) throws InterruptedException {
            Thread invoker = sendOnErrorAndReturnCaller(offloaded, NOOP_RUNNABLE);
            assertThat("Unexpected thread invoked offloaded Subscriber.", invoker,
                    not(sameInstance(currentThread())));
            return this;
        }

        void verifyOnErrorOffloadedWhenThrows(Subscriber<? super String> offloaded) throws InterruptedException {
            Thread invoker = sendOnErrorAndReturnCaller(offloaded, THROWING_RUNNABLE);
            assertThat("Unexpected thread invoked offloaded Subscriber.", invoker,
                    not(sameInstance(currentThread())));
        }

        OffloaderRule verifyOnSuccessOffloaded(Subscriber<? super String> offloaded) throws InterruptedException {
            return verifyOnSuccessOffloaded(offloaded, "Hello");
        }

        OffloaderRule verifyOnSuccessOffloadedWhenThrows(Subscriber<? super String> offloaded) throws InterruptedException {
            Thread invoker = sendOnSuccessAndReturnCaller(offloaded, "Hello", THROWING_RUNNABLE);
            assertThat("Unexpected thread invoked offloaded Subscriber.", invoker,
                    not(sameInstance(currentThread())));
            return this;
        }

        OffloaderRule verifyOnSuccessOffloaded(Subscriber<? super String> offloaded, @Nullable String item)
                throws InterruptedException {
            Thread invoker = sendOnSuccessAndReturnCaller(offloaded, item, NOOP_RUNNABLE);
            assertThat("Unexpected thread invoked offloaded Subscriber.", invoker,
                    not(sameInstance(currentThread())));
            return this;
        }

        OffloaderRule verifyCancelOffloaded(Cancellable received) throws InterruptedException {
            Thread invoker = sendCancelAndReturnCaller(received);
            assertThat("Unexpected thread invoked offloaded Subscription.", invoker,
                    not(sameInstance(currentThread())));
            return this;
        }

        private Cancellable captureReceivedCancellable() {
            ArgumentCaptor<Cancellable> captor = forClass(Cancellable.class);
            verify(subscriber).onSubscribe(captor.capture());
            return captor.getValue();
        }

        private Thread sendCancelAndReturnCaller(Cancellable received) throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(1);
            final AtomicReference<Thread> invoker = new AtomicReference<>();
            doAnswer(invocation -> {
                invoker.set(currentThread());
                latch.countDown();
                return null;
            }).when(cancellable).cancel();
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
            }).when(subscriber).onSubscribe(any(Cancellable.class));
            offloaded.onSubscribe(cancellable);
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

        private Thread sendOnSuccessAndReturnCaller(Subscriber<? super String> offloaded, @Nullable String item,
                                                    Runnable doOnComplete) throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(1);
            final AtomicReference<Thread> subscriberInvoker = new AtomicReference<>();
            doAnswer(invocation -> {
                subscriberInvoker.set(currentThread());
                latch.countDown();
                doOnComplete.run();
                return null;
            }).when(subscriber).onSuccess(item);
            offloaded.onSuccess(item);
            latch.await();
            return subscriberInvoker.get();
        }
    }
}
