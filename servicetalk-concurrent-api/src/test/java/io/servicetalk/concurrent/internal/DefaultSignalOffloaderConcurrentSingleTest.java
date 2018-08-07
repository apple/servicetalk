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
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Executors.newFixedSizeExecutor;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.concurrent.internal.TerminalNotification.error;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.slf4j.LoggerFactory.getLogger;

public class DefaultSignalOffloaderConcurrentSingleTest {

    private static final Logger LOGGER = getLogger(DefaultSignalOffloaderConcurrentSingleTest.class);

    @Rule
    public final OffloaderRule state = new OffloaderRule();

    @Test
    public void concurrentSignalsMultipleEntities() throws Exception {
        final int entityCount = 100;
        final OffloaderRule.SubscriberCancellablePair[] pairs = new OffloaderRule.SubscriberCancellablePair[entityCount];
        for (int i = 0; i < entityCount; i++) {
            pairs[i] = state.newPair(i);
        }

        // Send terminations only after everything is registered (Invariant for the offloader)
        final Completable[] results = new Completable[entityCount];
        for (int i = 0; i < entityCount; i++) {
            results[i] = pairs[i].sendResult(i);
        }

        awaitIndefinitely(completed().mergeDelayError(results));
        state.awaitTermination();

        for (int i = 0; i < entityCount; i++) {
            pairs[i].subscriber.verifyNoErrors();
            pairs[i].cancellable.verifyNotCancelled();
        }
    }

    private static final class OffloaderRule extends ExternalResource {

        private ExecutorService emitters;
        private Executor executor;
        private DefaultSignalOffloader offloader;

        @Override
        protected void before() {
            emitters = java.util.concurrent.Executors.newCachedThreadPool();
            executor = newFixedSizeExecutor(1);
            offloader = new DefaultSignalOffloader(executor::execute);
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

        SubscriberCancellablePair newPair(int expectedResult) {
            SubscriberImpl subscriber = new SubscriberImpl(expectedResult);
            CancellableImpl subscription = new CancellableImpl();
            return new SubscriberCancellablePair(subscriber, subscription);
        }

        void awaitTermination() throws InterruptedException {
            while (!offloader.isTerminated()) {
                Thread.sleep(10);
            }
        }

        private final class SubscriberCancellablePair {

            final SubscriberImpl subscriber;
            final CancellableImpl cancellable;
            private Subscriber<? super Integer> offloadCancellable;
            private Subscriber<? super Integer> offloadSubscriber;

            SubscriberCancellablePair(SubscriberImpl subscriber, CancellableImpl cancellable) {
                this.subscriber = subscriber;
                this.cancellable = cancellable;
                offloadCancellable = offloader.offloadCancellable(this.subscriber);
                offloadSubscriber = offloader.offloadSubscriber(offloadCancellable);
            }

            Completable sendResult(int result) throws InterruptedException {
                offloadSubscriber.onSubscribe(cancellable);
                this.subscriber.awaitOnSubscribe();
                Future<Void> subscriberEmitter = emitters.submit(() -> {
                    offloadSubscriber.onSuccess(result);
                    return null;
                });

                return new Completable() {
                    @Override
                    protected void handleSubscribe(Subscriber subscriber) {
                        subscriber.onSubscribe(IGNORE_CANCEL);
                        try {
                            subscriberEmitter.get();
                            subscriber.onComplete();
                        } catch (InterruptedException | ExecutionException e) {
                            subscriber.onError(e);
                        }
                    }
                };
            }
        }
    }

    private static final class SubscriberImpl implements Subscriber<Integer> {

        private final CountDownLatch awaitOnSubscribe = new CountDownLatch(1);
        @Nullable
        private Cancellable cancellable;
        @Nullable
        private TerminalNotification terminalNotification;
        private List<AssertionError> unexpected = new ArrayList<>();
        private final int expectedValue;

        private SubscriberImpl(int expectedValue) {
            this.expectedValue = expectedValue;
        }

        @Override
        public void onSubscribe(Cancellable cancellable) {
            this.cancellable = cancellable;
            awaitOnSubscribe.countDown();
        }

        @Override
        public void onSuccess(Integer val) {
            if (cancellable == null) {
                unexpected.add(new AssertionError("onSuccess arrived before onSubscribe."));
            }
            if (expectedValue != val) {
                unexpected.add(new AssertionError("Unexpected result. Expected: " + expectedValue
                        + ", got: " + val));
            }
        }

        @Override
        public void onError(Throwable t) {
            if (cancellable == null) {
                unexpected.add(new AssertionError("OnError arrived before onSubscribe."));
            }
            setTerminal(error(t));
        }

        void verifyNoErrors() {
            assertThat("Unexpected errors on Subscriber.", unexpected, is(empty()));
        }

        void awaitOnSubscribe() throws InterruptedException {
            awaitOnSubscribe.await();
            assert cancellable != null;
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

    private static final class CancellableImpl implements Cancellable {

        private boolean cancelled;

        @Override
        public void cancel() {
            cancelled = true;
        }

        void verifyNotCancelled() {
            assertThat("Unexpectedly cancelled.", cancelled, is(false));
        }
    }
}
