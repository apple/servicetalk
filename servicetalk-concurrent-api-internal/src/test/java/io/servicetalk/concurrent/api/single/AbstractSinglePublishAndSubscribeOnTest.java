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
package io.servicetalk.concurrent.api.single;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestSingle;
import io.servicetalk.concurrent.api.internal.AbstractPublishAndSubscribeOnTest;
import io.servicetalk.concurrent.api.internal.CaptureThreads;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

abstract class AbstractSinglePublishAndSubscribeOnTest extends AbstractPublishAndSubscribeOnTest {

    private static final String ITEM_VALUE = "Hello";

    private static final int APP_THREAD = 0;
    static final int ORIGINAL_SUBSCRIBER_THREAD = 1;
    static final int TERMINAL_SIGNAL_THREAD = 2;

    AbstractSinglePublishAndSubscribeOnTest() {
        super(new CaptureThreads(3) {
            @Override
            public Thread[] verify() {
                Thread[] asArray = super.verify();

                assertThat("Unexpected executor for app", asArray[APP_THREAD], APP_EXECUTOR);

                return asArray;
            }
        });
    }

    Thread[] setupAndSubscribe(BiFunction<Single<String>, Executor, Single<String>> offloadingFunction,
                               Executor executor) throws InterruptedException {
        return setupAndSubscribe(-1, offloadingFunction, executor);
    }

    Thread[] setupAndSubscribe(int offloadsExpected,
                               BiFunction<Single<String>, Executor, Single<String>> offloadingFunction,
                               Executor executor) throws InterruptedException {
        TestSingle<String> source = new TestSingle.Builder<String>().singleSubscriber().build();
        CountDownLatch subscribed = new CountDownLatch(1 + (offloadsExpected > 0 ? (offloadsExpected - 1) : 0));
        CountDownLatch allDone = new CountDownLatch(1);

        app.executor().execute(() -> {
            capturedThreads.capture(APP_THREAD);

            afterOffload = subscribed::countDown;

            Single<String> original = source
                    .beforeOnSuccess(__ -> capturedThreads.capture(ORIGINAL_SUBSCRIBER_THREAD));

            Single<String> offloaded = offloadingFunction.apply(original, executor);

            Cancellable cancel = offloaded.afterFinally(allDone::countDown)
                    .afterOnSubscribe(cancellable -> subscribed.countDown())
                    .afterOnSuccess(__ -> capturedThreads.capture(TERMINAL_SIGNAL_THREAD))
                    .subscribe(val -> assertThat("Unexpected item", val, is(ITEM_VALUE)));
            subscribed.countDown();
            try {
                // Still waiting for "afterOnSubscribe" to fire
                subscribed.await();
            } catch (InterruptedException woken) {
                Thread.interrupted();
                return;
            }
            source.onSuccess(ITEM_VALUE);
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

    Thread[] setupForCancelAndSubscribe(
            BiFunction<Single<String>, Executor, Single<String>> offloadingFunction,
            Executor executor) throws InterruptedException {
        return setupForCancelAndSubscribe(-1, offloadingFunction, executor);
    }

    private Thread[] setupForCancelAndSubscribe(int offloadsExpected,
                                                BiFunction<Single<String>, Executor, Single<String>> offloadingFunction,
                                                Executor executor) throws InterruptedException {
        TestSingle<String> source = new TestSingle.Builder<String>().singleSubscriber().build();
        CountDownLatch subscribed = new CountDownLatch(1 + (offloadsExpected > 0 ? (offloadsExpected - 1) : 0));
        CountDownLatch allDone = new CountDownLatch(1);

        app.executor().execute(() -> {
            capturedThreads.capture(APP_THREAD);

            afterOffload = subscribed::countDown;

            Single<String> original = source.afterCancel(() -> {
                capturedThreads.capture(ORIGINAL_SUBSCRIBER_THREAD);
                allDone.countDown();
            });

            Single<String> offloaded = offloadingFunction.apply(original, executor);

            Cancellable cancel = offloaded.afterOnSubscribe(cancellable -> subscribed.countDown())
                    .afterCancel(() -> capturedThreads.capture(TERMINAL_SIGNAL_THREAD))
                    .afterFinally(allDone::countDown)
                    .subscribe(__ -> fail("success not expected"));
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
}
