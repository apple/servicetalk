/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.api.completable;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.TestCompletable;
import io.servicetalk.concurrent.api.internal.AbstractPublishAndSubscribeOnTest;
import io.servicetalk.concurrent.api.internal.CaptureThreads;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

abstract class AbstractCompletablePublishAndSubscribeOnTest extends AbstractPublishAndSubscribeOnTest {

    static final int APP_THREAD = 0;
    static final int ON_SUBSCRIBE_THREAD = 1;
    static final int TERMINAL_SIGNAL_THREAD = 2;

    protected AbstractCompletablePublishAndSubscribeOnTest() {
        super(new CaptureThreads(3) {
            @Override
            public Thread[] verify() {
                Thread[] asArray = super.verify();

                assertThat("Unexpected executor for app", asArray[APP_THREAD], APP_EXECUTOR);

                return asArray;
            }
        });
    }

   protected Thread[] setupAndSubscribe(int offloadsExpected,
                                         BiFunction<Completable, Executor, Completable> offloadingFunction,
                                         Executor executor)
            throws InterruptedException {
        TestCompletable source = new TestCompletable.Builder().singleSubscriber().build();
        CountDownLatch subscribed = new CountDownLatch(1 + (offloadsExpected > 0 ? (offloadsExpected - 1) : 0));
        CountDownLatch allDone = new CountDownLatch(1);

        app.executor().execute(() -> {
            capturedThreads.capture(APP_THREAD);

            afterOffload = subscribed::countDown;

            Completable offloaded = offloadingFunction.apply(source, executor);

            Cancellable cancel = offloaded.afterFinally(() -> allDone.countDown())
                    .afterOnSubscribe(__ -> capturedThreads.capture(ON_SUBSCRIBE_THREAD))
                    .afterOnComplete(() -> capturedThreads.capture(TERMINAL_SIGNAL_THREAD))
                    .subscribe();
            subscribed.countDown();
            try {
                // Still waiting for "afterOnSubscribe" to fire
                subscribed.await();
            } catch (InterruptedException woken) {
                Thread.interrupted();
                return;
            }
            source.onComplete();
            try {
                // Waiting for "afterFinally" to fire
                allDone.await();
            } catch (InterruptedException woken) {
                Thread.interrupted();
                return;
            }
            if (cancel.hashCode() != cancel.hashCode()) {
                throw new RuntimeException("impossible, but keeps cancel alive.");
            }
        });
        // Waiting for "afterFinally" to fire
        allDone.await();
        offloadExecutorService.shutdown();
        boolean shutdown = offloadExecutorService.awaitTermination(5, TimeUnit.SECONDS);
        assertThat("Executor still active " + offloadExecutorService, shutdown, is(true));

        assertThat("Started offloads != finished", offloadsFinished.intValue(), is(offloadsStarted.intValue()));
        if (-1 != offloadsExpected) {
            assertThat("Unexpected offloads", offloadsFinished.intValue(), is(offloadsExpected));
        }

        return capturedThreads.verify();
    }

    protected Thread[] setupAndCancel(int offloadsExpected,
                                      BiFunction<Completable, Executor, Completable> offloadingFunction,
                                      Executor executor)
            throws InterruptedException {
        TestCompletable source = new TestCompletable.Builder().singleSubscriber().build();
        CountDownLatch subscribed = new CountDownLatch(1 + (offloadsExpected > 0 ? (offloadsExpected - 1) : 0));
        CountDownLatch allDone = new CountDownLatch(1);

        app.executor().execute(() -> {
            capturedThreads.capture(APP_THREAD);

            afterOffload = subscribed::countDown;

            Completable offloaded = offloadingFunction.apply(source, executor);

            Cancellable cancel = offloaded.afterOnSubscribe(cancellable -> {
                capturedThreads.capture(ON_SUBSCRIBE_THREAD);
                subscribed.countDown();
            })
                    .afterCancel(() -> capturedThreads.capture(TERMINAL_SIGNAL_THREAD))
                    .afterFinally(allDone::countDown)
                    .subscribe();
            subscribed.countDown();
            try {
                // Still waiting for "afterOnSubscribe" to fire
                subscribed.await();
            } catch (InterruptedException woken) {
                Thread.interrupted();
                return;
            }
            cancel.cancel();
            try {
                // Waiting for "afterFinally" to fire
                allDone.await();
            } catch (InterruptedException woken) {
                Thread.interrupted();
                return;
            }
            if (cancel.hashCode() != cancel.hashCode()) {
                throw new RuntimeException("impossible, but keeps cancel alive.");
            }
        });
        // Waiting for "afterOnComplete" to fire
        allDone.await();
        offloadExecutorService.shutdown();
        boolean shutdown = offloadExecutorService.awaitTermination(5, TimeUnit.SECONDS);
        assertThat("Executor still active " + offloadExecutorService, shutdown, is(true));

        assertThat("Started offloads != finished", offloadsFinished.intValue(), is(offloadsStarted.intValue()));
        if (-1 != offloadsExpected) {
            assertThat("Unexpected offloads", offloadsFinished.intValue(), is(offloadsExpected));
        }

        return capturedThreads.verify();
    }
}
