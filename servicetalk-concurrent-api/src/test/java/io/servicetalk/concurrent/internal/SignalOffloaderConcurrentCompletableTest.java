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
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.internal.TerminalNotification.error;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

class SignalOffloaderConcurrentCompletableTest {

    private enum OffloaderTestParam {
        THREAD_BASED {
            @Override
            OffloaderHolder get() {
                return new OffloaderHolder(ThreadBasedSignalOffloader::new);
            }
        },
        TASK_BASED {
            @Override
            OffloaderHolder get() {
                return new OffloaderHolder(TaskBasedSignalOffloader::new);
            }
        };

        abstract OffloaderHolder get();
    }

    private OffloaderHolder state;

    private void init(OffloaderTestParam offloader) {
        this.state = offloader.get();
    }

    @AfterEach
    void tearDown() throws Exception {
        state.shutdown();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(OffloaderTestParam.class)
    void concurrentSignalsMultipleEntities(OffloaderTestParam offloader)
            throws Exception {
        init(offloader);
        final int entityCount = 100;
        final OffloaderHolder.SubscriberCancellablePair[] pairs =
                new OffloaderHolder.SubscriberCancellablePair[entityCount];
        for (int i = 0; i < entityCount; i++) {
            pairs[i] = state.newPair();
        }

        // Send terminations only after everything is registered (Invariant for the offloader)
        final Completable[] results = new Completable[entityCount];
        for (int i = 0; i < entityCount; i++) {
            results[i] = pairs[i].sendResult();
        }

        completed().mergeDelayError(results).toFuture().get();
        state.awaitTermination();

        for (int i = 0; i < entityCount; i++) {
            pairs[i].subscriber.verifyNoErrors();
            pairs[i].cancellable.verifyNotCancelled();
        }
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

        void shutdown() throws Exception {
            executor.closeAsync().toFuture().get();
            emitters.shutdownNow();
        }

        void awaitTermination() throws Exception {
            // Submit a task, since we use a single thread executor, this means all previous tasks have been
            // completed.
            executor.submit(() -> { }).toFuture().get();
        }

        SubscriberCancellablePair newPair() {
            SubscriberImpl subscriber = new SubscriberImpl();
            CancellableImpl subscription = new CancellableImpl();
            return new SubscriberCancellablePair(subscriber, subscription);
        }

        private final class SubscriberCancellablePair {

            final SubscriberImpl subscriber;
            final CancellableImpl cancellable;
            private Subscriber offloadSubscriber;

            SubscriberCancellablePair(SubscriberImpl subscriber, CancellableImpl cancellable) {
                this.subscriber = subscriber;
                this.cancellable = cancellable;
                Subscriber offloadCancellable = offloader.offloadCancellable(this.subscriber);
                offloadSubscriber = offloader.offloadSubscriber(offloadCancellable);
            }

            Completable sendResult() throws InterruptedException {
                offloadSubscriber.onSubscribe(cancellable);
                this.subscriber.awaitOnSubscribe();
                Future<Void> subscriberEmitter = emitters.submit(() -> {
                    offloadSubscriber.onComplete();
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

    private static final class SubscriberImpl implements Subscriber {

        private final CountDownLatch awaitOnSubscribe = new CountDownLatch(1);
        @Nullable
        private Cancellable cancellable;
        @Nullable
        private TerminalNotification terminalNotification;
        private List<AssertionError> unexpected = new ArrayList<>();

        @Override
        public void onSubscribe(Cancellable cancellable) {
            this.cancellable = cancellable;
            awaitOnSubscribe.countDown();
        }

        @Override
        public void onComplete() {
            if (cancellable == null) {
                unexpected.add(new AssertionError("onSuccess arrived before onSubscribe."));
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
