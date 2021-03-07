/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.CompletableSource.Subscriber;
import io.servicetalk.concurrent.api.Executor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.slf4j.LoggerFactory.getLogger;

@ExtendWith(TimeoutTracingInfoExtension.class)
public class SignalOffloaderCompletableTest {

    private static final Logger LOGGER = getLogger(SignalOffloaderCompletableTest.class);

    private OffloaderHolder state;

    private void init(Supplier<OffloaderHolder> stateSupplier) {
        this.state = stateSupplier.get();
    }

    public static Stream<Arguments> offloaders() {
        return Stream.of(
            Arguments.of((Supplier<OffloaderHolder>) () ->
                new OffloaderHolder(ThreadBasedSignalOffloader::new), true),
            Arguments.of((Supplier<OffloaderHolder>) () ->
                new OffloaderHolder(TaskBasedSignalOffloader::new), false));
    }

    @AfterEach
    public void tearDown() {
        if (state != null) {
            state.shutdown();
            state = null;
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] - thread based: {1}")
    @MethodSource("offloaders")
    public void offloadingSubscriberShouldNotOffloadCancellable(Supplier<OffloaderHolder> stateSupplier)
            throws Exception {
        init(stateSupplier);
        Subscriber offloadedSubscriber = state.offloader.offloadCancellable(state.subscriber);
        Subscriber offloaded = state.offloader.offloadSubscriber(offloadedSubscriber);
        state.verifyOnSubscribeOffloaded(offloaded);
        state.verifyCancelNotOffloaded(state.cancellable)
                .verifyOnCompleteOffloaded(offloaded)
                .awaitTermination();
    }

    @ParameterizedTest(name = "{displayName} [{index}] - thread based: {1}")
    @MethodSource("offloaders")
    public void offloadingCancellableShouldNotOffloadSubscriber(Supplier<OffloaderHolder> stateSupplier)
            throws Exception {
        init(stateSupplier);
        Subscriber offloaded = state.offloader.offloadCancellable(state.subscriber);

        Cancellable received = state.sendOnSubscribe(offloaded);
        state.verifyOnSubscribeNotOffloaded(offloaded)
                .verifyCancelOffloaded(received)
                .awaitTermination();
    }

    @ParameterizedTest(name = "{displayName} [{index}] - thread based: {1}")
    @MethodSource("offloaders")
    public void offloadSubscriber(Supplier<OffloaderHolder> stateSupplier) throws Exception {
        init(stateSupplier);
        Subscriber offloaded = state.offloader.offloadSubscriber(state.subscriber);
        state.verifyOnSubscribeOffloaded(offloaded);
        state.verifyOnCompleteOffloaded(offloaded)
                .awaitTermination();
    }

    @ParameterizedTest(name = "{displayName} [{index}] - thread based: {1}")
    @MethodSource("offloaders")
    public void cancelShouldTerminateOffloadingWithMultipleEntities(Supplier<OffloaderHolder> stateSupplier)
            throws Exception {
        init(stateSupplier);
        Subscriber offloadedCancellable = state.offloader.offloadCancellable(state.subscriber);
        Subscriber offloadedSubscriber = state.offloader.offloadSubscriber(offloadedCancellable);
        Cancellable received = state.verifyOnSubscribeOffloaded(offloadedSubscriber);
        state.verifyCancelOffloaded(received)
                .awaitTermination();
    }

    @ParameterizedTest(name = "{displayName} [{index}] - thread based: {1}")
    @MethodSource("offloaders")
    public void onErrorShouldTerminateOffloadingWithMultipleEntities(Supplier<OffloaderHolder> stateSupplier)
            throws Exception {
        init(stateSupplier);
        Subscriber offloadedCancellable = state.offloader.offloadCancellable(state.subscriber);
        Subscriber offloadedSubscriber = state.offloader.offloadSubscriber(offloadedCancellable);
        state.verifyOnSubscribeOffloaded(offloadedSubscriber);
        state.verifyOnErrorOffloaded(offloadedSubscriber).awaitTermination();
    }

    @ParameterizedTest(name = "{displayName} [{index}] - thread based: {1}")
    @MethodSource("offloaders")
    public void onCompleteShouldTerminateOffloadingWithMultipleEntities(Supplier<OffloaderHolder> stateSupplier)
            throws Exception {
        init(stateSupplier);
        Subscriber offloadedCancellable = state.offloader.offloadCancellable(state.subscriber);
        Subscriber offloadedSubscriber = state.offloader.offloadSubscriber(offloadedCancellable);
        state.verifyOnSubscribeOffloaded(offloadedSubscriber);
        state.verifyOnCompleteOffloaded(offloadedSubscriber).awaitTermination();
    }

    @ParameterizedTest(name = "{displayName} [{index}] - thread based: {1}")
    @MethodSource("offloaders")
    public void onSubscribeThrows(Supplier<OffloaderHolder> stateSupplier) throws Exception {
        init(stateSupplier);
        Subscriber offloaded = state.offloader.offloadSubscriber(state.subscriber);
        state.verifyOnSubscribeOffloadedWhenThrows(offloaded);
        state.awaitTermination();
        verify(state.cancellable).cancel();
    }

    @ParameterizedTest(name = "{displayName} [{index}] - thread based: {1}")
    @MethodSource("offloaders")
    public void onSuccessThrows(Supplier<OffloaderHolder> stateSupplier) throws Exception {
        init(stateSupplier);
        Subscriber offloaded = state.offloader.offloadSubscriber(state.subscriber);
        state.verifyOnSubscribeOffloaded(offloaded);
        state.verifyOnCompleteOffloadedWhenThrows(offloaded);
        state.awaitTermination();
    }

    @ParameterizedTest(name = "{displayName} [{index}] - thread based: {1}")
    @MethodSource("offloaders")
    public void onErrorThrows(Supplier<OffloaderHolder> stateSupplier) throws Exception {
        init(stateSupplier);
        Subscriber offloaded = state.offloader.offloadSubscriber(state.subscriber);
        state.verifyOnSubscribeOffloaded(offloaded);
        state.verifyOnErrorOffloadedWhenThrows(offloaded);
        state.awaitTermination();
    }

    @ParameterizedTest(name = "{displayName} [{index}] - thread based: {1}")
    @MethodSource("offloaders")
    public void offloadPostTermination(Supplier<OffloaderHolder> stateSupplier,
                                       boolean supportsTermination) throws Exception {
        assumeTrue(supportsTermination, "Termination test not supported by this offloader.");
        init(stateSupplier);
        Subscriber offloaded = state.offloader.offloadSubscriber(state.subscriber);
        state.verifyOnSubscribeOffloaded(offloaded);
        state.verifyOnErrorOffloaded(offloaded);
        state.awaitTermination();
        assertThrows(IllegalStateException.class, () -> state.offloader.offloadSubscriber(state.subscriber));
    }

    @ParameterizedTest(name = "{displayName} [{index}] - thread based: {1}")
    @MethodSource("offloaders")
    public void executorRejectsForHandleSubscribe(Supplier<OffloaderHolder> stateSupplier) {
        init(stateSupplier);
        ThreadBasedSignalOffloader offloader = new ThreadBasedSignalOffloader(from(task -> {
            throw new RejectedExecutionException();
        }));
        offloader.offloadSubscribe(state.subscriber, __ -> {
        });
        verify(state.subscriber).onSubscribe(any(Cancellable.class));
        ArgumentCaptor<Throwable> errorCaptor = ArgumentCaptor.forClass(Throwable.class);
        verify(state.subscriber).onError(errorCaptor.capture());
        assertThat("Unexpected error received by the subscriber.", errorCaptor.getValue(),
                instanceOf(RejectedExecutionException.class));
    }

    private static final class OffloaderHolder {

        private Executor executor;
        private SignalOffloader offloader;
        private Cancellable cancellable;
        private Subscriber subscriber;

        OffloaderHolder(Function<Executor, SignalOffloader> offloaderFactory) {
            executor = from(java.util.concurrent.Executors.newSingleThreadExecutor());
            offloader = offloaderFactory.apply(executor);
            cancellable = mock(Cancellable.class);
            subscriber = mock(Subscriber.class);
        }

        void shutdown() {
            try {
                executor.closeAsync().toFuture().get();
            } catch (Exception e) {
                LOGGER.warn("Failed to close the executor {}.", executor, e);
            }
        }

        void awaitTermination() throws Exception {
            // Submit a task, since we use a single thread executor, this means all previous tasks have been
            // completed.
            executor.submit(() -> { }).toFuture().get();
        }

        Cancellable sendOnSubscribe(Subscriber offloaded) {
            offloaded.onSubscribe(cancellable);
            Cancellable received = captureReceivedCancellable();
            assertThat("Unexpected subscription received.", received, is(notNullValue()));
            return received;
        }

        OffloaderHolder verifyOnSubscribeNotOffloaded(Subscriber offloaded) throws Exception {
            final Thread invoker = sendOnSubscribeAndReturnCaller(offloaded, NOOP_RUNNABLE);
            assertThat("Unexpected thread invoked offloaded Subscriber.", invoker,
                    sameInstance(currentThread()));
            return this;
        }

        OffloaderHolder verifyCancelNotOffloaded(Cancellable received) throws Exception {
            Thread invoker = sendCancelAndReturnCaller(received);
            assertThat("Unexpected thread invoked offloaded Subscription.", invoker,
                    sameInstance(currentThread()));
            return this;
        }

        Cancellable verifyOnSubscribeOffloaded(Subscriber offloaded) throws Exception {
            final Thread invoker = sendOnSubscribeAndReturnCaller(offloaded, NOOP_RUNNABLE);
            assertThat("Unexpected thread invoked offloaded Subscriber.", invoker,
                    not(sameInstance(currentThread())));
            return captureReceivedCancellable();
        }

        void verifyOnSubscribeOffloadedWhenThrows(Subscriber offloaded) throws Exception {
            final Thread invoker = sendOnSubscribeAndReturnCaller(offloaded, THROWING_RUNNABLE);
            assertThat("Unexpected thread invoked offloaded Subscriber.", invoker,
                    not(sameInstance(currentThread())));
            captureReceivedCancellable();
        }

        OffloaderHolder verifyOnErrorOffloaded(Subscriber offloaded) throws Exception {
            Thread invoker = sendOnErrorAndReturnCaller(offloaded, NOOP_RUNNABLE);
            assertThat("Unexpected thread invoked offloaded Subscriber.", invoker,
                    not(sameInstance(currentThread())));
            return this;
        }

        void verifyOnErrorOffloadedWhenThrows(Subscriber offloaded) throws Exception {
            Thread invoker = sendOnErrorAndReturnCaller(offloaded, THROWING_RUNNABLE);
            assertThat("Unexpected thread invoked offloaded Subscriber.", invoker,
                    not(sameInstance(currentThread())));
        }

        OffloaderHolder verifyOnCompleteOffloaded(Subscriber offloaded) throws Exception {
            Thread invoker = sendOnCompleteAndReturnCaller(offloaded, NOOP_RUNNABLE);
            assertThat("Unexpected thread invoked offloaded Subscriber.", invoker,
                    not(sameInstance(currentThread())));
            return this;
        }

        OffloaderHolder verifyOnCompleteOffloadedWhenThrows(Subscriber offloaded) throws Exception {
            Thread invoker = sendOnCompleteAndReturnCaller(offloaded, THROWING_RUNNABLE);
            assertThat("Unexpected thread invoked offloaded Subscriber.", invoker,
                    not(sameInstance(currentThread())));
            return this;
        }

        OffloaderHolder verifyCancelOffloaded(Cancellable received) throws Exception {
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

        private Thread sendCancelAndReturnCaller(Cancellable received) throws Exception {
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

        private Thread sendOnSubscribeAndReturnCaller(Subscriber offloaded, Runnable doOnSubscribe)
                throws Exception {
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

        private Thread sendOnErrorAndReturnCaller(Subscriber offloaded, Runnable doOnError)
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

        private Thread sendOnCompleteAndReturnCaller(Subscriber offloaded, Runnable doOnComplete)
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
    }
}
