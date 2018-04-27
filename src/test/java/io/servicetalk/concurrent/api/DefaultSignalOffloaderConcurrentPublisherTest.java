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
import io.servicetalk.concurrent.internal.TerminalNotification;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Executors.newFixedSizeExecutor;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
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

public class DefaultSignalOffloaderConcurrentPublisherTest {

    private static final Logger LOGGER = getLogger(DefaultSignalOffloaderConcurrentPublisherTest.class);

    @Rule
    public final OffloaderRule state = new OffloaderRule();

    @Test
    public void concurrentSignalsMultipleEntities() throws Exception {
        final int entityCount = 100;
        final OffloaderRule.SubscriberSubscriptionPair[] pairs = new OffloaderRule.SubscriberSubscriptionPair[entityCount];
        for (int i = 0; i < entityCount; i++) {
            pairs[i] = state.newPair((i + 1) * 100);
        }

        // Send terminations only after everything is registered (Invariant for the offloader)
        final Completable[] results = new Completable[entityCount];
        for (int i = 0; i < entityCount; i++) {
            results[i] = pairs[i].sendItems((i + 1) * 100);
        }

        awaitIndefinitely(completed().mergeDelayError(results));
        state.awaitTermination();

        for (int i = 0; i < entityCount; i++) {
            pairs[i].subscriber.verifyNoErrors();
            pairs[i].subscription.verifyRequested((i + 1) * 100);
        }
    }

    @Test
    public void concurrentSignalsFromSubscriberAndSubscription() throws InterruptedException, ExecutionException {
        OffloaderRule.SubscriberSubscriptionPair pair = state.newPair(10_000);
        awaitIndefinitely(pair.sendItems(10_000));
        state.awaitTermination();
        pair.subscriber.verifyNoErrors();
        pair.subscription.verifyRequested(10_000);
    }

    private static final class OffloaderRule extends ExternalResource {

        private ExecutorService emitters;
        private Executor executor;
        private DefaultSignalOffloader offloader;

        @Override
        protected void before() {
            emitters = java.util.concurrent.Executors.newCachedThreadPool();
            executor = newFixedSizeExecutor(1);
            offloader = new DefaultSignalOffloader(executor);
        }

        @Override
        protected void after() {
            try {
                awaitIndefinitely(executor.closeAsync());
                emitters.shutdownNow();
            } catch (Exception e) {
                LOGGER.warn("Failed to close the executor {}.", executor, e);
            }
        }

        SubscriberSubscriptionPair newPair(int expectedItems) {
            AtomicInteger demand = new AtomicInteger();
            SubscriberImpl subscriber = new SubscriberImpl(expectedItems);
            SubscriptionImpl subscription = new SubscriptionImpl(subscriber, demand);
            return new SubscriberSubscriptionPair(subscriber, subscription, demand);
        }

        void awaitTermination() throws InterruptedException {
            while (!offloader.isTerminated()) {
                Thread.sleep(10);
            }
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

    private static final class SubscriberImpl implements org.reactivestreams.Subscriber<Integer> {

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

        void verifyRequested(String errorMessage, long expected) {
            assertThat(errorMessage, requested, is(expected));
        }
    }
}
