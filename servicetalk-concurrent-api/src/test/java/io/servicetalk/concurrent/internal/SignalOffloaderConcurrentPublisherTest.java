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
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Executors;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.internal.FlowControlUtil.addWithOverflowProtectionIfNotNegative;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.SubscriberUtils.newExceptionForInvalidRequestN;
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static io.servicetalk.concurrent.internal.TerminalNotification.error;
import static java.lang.Math.min;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.slf4j.LoggerFactory.getLogger;

@RunWith(Parameterized.class)
public class SignalOffloaderConcurrentPublisherTest {
    private static final Logger LOGGER = getLogger(SignalOffloaderConcurrentPublisherTest.class);

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    public final OffloaderHolder state;

    public SignalOffloaderConcurrentPublisherTest(Supplier<OffloaderHolder> state,
                                                  @SuppressWarnings("unused") boolean supportsTermination) {
        this.state = state.get();
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
    public void concurrentSignalsMultipleEntities() throws Exception {
        final int entityCount = 100;
        final OffloaderHolder.SubscriberSubscriptionPair[] pairs =
                new OffloaderHolder.SubscriberSubscriptionPair[entityCount];
        for (int i = 0; i < entityCount; i++) {
            pairs[i] = state.newPair((i + 1) * 100);
        }

        // Send terminations only after everything is registered (Invariant for the offloader)
        final Completable[] results = new Completable[entityCount];
        for (int i = 0; i < entityCount; i++) {
            results[i] = pairs[i].sendItems((i + 1) * 100);
        }

        completed().mergeDelayError(results).toFuture().get();
        state.awaitTermination();

        for (int i = 0; i < entityCount; i++) {
            pairs[i].subscriber.verifyNoErrors();
            pairs[i].subscription.verifyRequested((i + 1) * 100);
        }
    }

    @Test
    public void concurrentSignalsFromSubscriberAndSubscription() throws Exception {
        OffloaderHolder.SubscriberSubscriptionPair pair = state.newPair(10_000);
        pair.sendItems(10_000).toFuture().get();
        state.awaitTermination();
        pair.subscriber.verifyNoErrors();
        pair.subscription.verifyRequested(10_000);
    }

    private static final class OffloaderHolder {

        private ExecutorService emitters;
        private Executor executor;
        private SignalOffloader offloader;

        OffloaderHolder(Function<Executor, SignalOffloader> offloaderFactory) {
            emitters = java.util.concurrent.Executors.newCachedThreadPool();
            executor = Executors.from(java.util.concurrent.Executors.newSingleThreadExecutor());
            offloader = offloaderFactory.apply(executor);
        }

        void shutdown() {
            try {
                executor.closeAsync().toFuture().get();
                emitters.shutdownNow();
            } catch (Exception e) {
                LOGGER.warn("Failed to close the executor {}.", executor, e);
            }
        }

        void awaitTermination() throws Exception {
            // Submit a task, since we use a single thread executor, this means all previous tasks have been
            // completed.
            executor.submit(() -> { }).toFuture().get();
        }

        SubscriberSubscriptionPair newPair(int expectedItems) {
            AtomicInteger demand = new AtomicInteger();
            SubscriberImpl subscriber = new SubscriberImpl(expectedItems);
            SubscriptionImpl subscription = new SubscriptionImpl(subscriber, demand);
            return new SubscriberSubscriptionPair(subscriber, subscription, demand);
        }

        private final class SubscriberSubscriptionPair {

            final SubscriberImpl subscriber;
            final SubscriptionImpl subscription;
            private final AtomicInteger demand;
            private Subscriber<? super Integer> offloadSubscription;
            private Subscriber<? super Integer> offloadSubscriber;

            SubscriberSubscriptionPair(SubscriberImpl subscriber, SubscriptionImpl subscription, AtomicInteger demand) {
                this.subscriber = subscriber;
                this.subscription = subscription;
                this.demand = demand;
                offloadSubscription = offloader.offloadSubscription(this.subscriber);
                offloadSubscriber = offloader.offloadSubscriber(offloadSubscription);
            }

            Completable sendItems(int expectedItems) throws InterruptedException {
                offloadSubscriber.onSubscribe(subscription);
                final Subscription subscription = this.subscriber.awaitOnSubscribe();
                CyclicBarrier awaitBothEmitters = new CyclicBarrier(2);
                Future<Void> subscriberEmitter = emitters.submit(() -> {
                    awaitBothEmitters.await();
                    int nextItem = 1;
                    for (;;) {
                        int toEmit = demand.getAndSet(0);
                        for (int i = 0; i < toEmit; i++) {
                            offloadSubscriber.onNext(nextItem++);
                        }
                        if (nextItem > expectedItems) {
                            break;
                        } else {
                            Thread.yield();
                        }
                    }
                    offloadSubscriber.onComplete();
                    return null;
                });
                Future<Void> subscriptionEmitter = emitters.submit(() -> {
                    int totalRequested = 0;
                    for (int i = 0; i < expectedItems; i++) {
                        if (i == min(100, expectedItems / 2)) {
                            awaitBothEmitters.await();
                        }
                        subscription.request(1);
                        ++totalRequested;
                    }
                    return null;
                });

                return new Completable() {
                    @Override
                    protected void handleSubscribe(Subscriber subscriber) {
                        subscriber.onSubscribe(IGNORE_CANCEL);
                        try {
                            subscriberEmitter.get();
                            subscriptionEmitter.get();
                            subscriber.onComplete();
                        } catch (InterruptedException | ExecutionException e) {
                            subscriber.onError(e);
                        }
                    }
                };
            }
        }
    }

    private static final class SubscriberImpl implements io.servicetalk.concurrent.PublisherSource.Subscriber<Integer> {

        private final CountDownLatch awaitOnSubscribe = new CountDownLatch(1);
        @Nullable
        private Subscription subscription;
        private int lastReceived;
        @Nullable
        private TerminalNotification terminalNotification;
        private List<AssertionError> unexpected = new ArrayList<>();
        private final int lastExpectedValue;

        private SubscriberImpl(int lastExpectedValue) {
            this.lastExpectedValue = lastExpectedValue;
        }

        @Override
        public void onSubscribe(Subscription s) {
            subscription = s;
            awaitOnSubscribe.countDown();
        }

        @Override
        public void onNext(Integer val) {
            if (subscription == null) {
                unexpected.add(new AssertionError("OnNext arrived before onSubscribe."));
            }
            if (val <= lastReceived) {
                unexpected.add(new AssertionError("OnNext arrived out of order. Last received: "
                        + lastReceived + ", new: " + val));
            } else {
                lastReceived = val;
            }
        }

        @Override
        public void onError(Throwable t) {
            if (subscription == null) {
                unexpected.add(new AssertionError("OnError arrived before onSubscribe."));
            }
            setTerminal(error(t));
        }

        @Override
        public void onComplete() {
            if (subscription == null) {
                unexpected.add(new AssertionError("OnComplete arrived before onSubscribe."));
            }
            setTerminal(complete());
            if (lastReceived != lastExpectedValue) {
                unexpected.add(new AssertionError("Not enough values received. Expected: "
                        + lastExpectedValue + ", actual: " + lastReceived));
            }
        }

        void verifyNoErrors() {
            assertThat("Unexpected errors on Subscriber.", unexpected, is(empty()));
        }

        Subscription awaitOnSubscribe() throws InterruptedException {
            awaitOnSubscribe.await();
            assert subscription != null;
            return subscription;
        }

        private void setTerminal(TerminalNotification error) {
            if (terminalNotification != null) {
                unexpected.add(new AssertionError("Duplicate terminal notification. Existing: "
                        + terminalNotification + ", new: " + error));
            } else {
                terminalNotification = error;
            }
        }
    }

    private static final class SubscriptionImpl implements Subscription {

        private final SubscriberImpl subscriber;
        private final AtomicInteger demand;
        private long requested;

        private SubscriptionImpl(SubscriberImpl subscriber, final AtomicInteger demand) {
            this.subscriber = subscriber;
            this.demand = demand;
        }

        @Override
        public void request(long n) {
            if (!isRequestNValid(n)) {
                subscriber.onError(newExceptionForInvalidRequestN(n));
            } else {
                requested = addWithOverflowProtectionIfNotNegative(requested, n);
                demand.accumulateAndGet((int) n, FlowControlUtil::addWithOverflowProtectionIfNotNegative);
            }
        }

        @Override
        public void cancel() {
            requested = -1;
        }

        void verifyRequested(long expected) {
            assertThat("Unexpected items requested.", requested, is(expected));
        }
    }
}
