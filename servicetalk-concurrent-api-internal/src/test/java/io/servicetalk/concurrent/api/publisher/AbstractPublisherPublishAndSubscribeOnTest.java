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
package io.servicetalk.concurrent.api.publisher;

import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.internal.AbstractPublishAndSubscribeOnTest;
import io.servicetalk.concurrent.api.internal.CaptureThreads;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static java.lang.Long.MAX_VALUE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

abstract class AbstractPublisherPublishAndSubscribeOnTest extends AbstractPublishAndSubscribeOnTest {
    private static final String ITEM_VALUE = "Hello";

    private static final int APP_THREAD = 0;
    static final int ORIGINAL_SUBSCRIBER_THREAD = 1;
    private static final int ORIGINAL_SUBSCRIPTION_THREAD = 2;
    static final int OFFLOADED_SUBSCRIBER_THREAD = 3;
    static final int OFFLOADED_SUBSCRIPTION_THREAD = 4;

    AbstractPublisherPublishAndSubscribeOnTest() {
        super(new CaptureThreads(5) {
            @Override
            public Thread[] verify() {
                Thread[] asArray = super.verify();

                assertThat("Unexpected executor for app", asArray[ORIGINAL_SUBSCRIBER_THREAD], APP_EXECUTOR);

                return asArray;
            }
        });
    }

    Thread[] setupAndSubscribe(
            BiFunction<Publisher<String>, Executor, Publisher<String>> offloadingFunction, Executor executor)
            throws InterruptedException {
        return setupAndSubscribe(-1, offloadingFunction, executor);
    }

    private Thread[] setupAndSubscribe(int offloadsExpected,
                                       BiFunction<Publisher<String>, Executor, Publisher<String>> offloadingFunction,
                                       Executor executor)
            throws InterruptedException {
        CountDownLatch allDone = new CountDownLatch(1);

        app.executor().execute(() -> {
            capturedThreads.capture(APP_THREAD);

            TestPublisher<String> source = new TestPublisher.Builder<String>().singleSubscriber().build();

            Publisher original = source
                    .beforeOnNext(__ -> capturedThreads.capture(ORIGINAL_SUBSCRIBER_THREAD))
                    .beforeRequest(__ -> capturedThreads.capture(ORIGINAL_SUBSCRIPTION_THREAD));

            Publisher<String> offloaded = offloadingFunction.apply(original, executor);

            toSource(offloaded.beforeOnNext(__ -> capturedThreads.capture(OFFLOADED_SUBSCRIBER_THREAD))
                    .beforeRequest(__ -> capturedThreads.capture(OFFLOADED_SUBSCRIPTION_THREAD))
                    .afterFinally(allDone::countDown))
                    .subscribe(new Subscriber<String>() {
                        @Override
                        public void onSubscribe(final Subscription s) {
                            // Do not request from the caller thread to make sure synchronous request-onNext does not
                            // pollute thread capturing of subscription.
                            executor.execute(() -> s.request(MAX_VALUE));
                        }

                        @Override
                        public void onNext(final String s) {
                            assertThat("Unexpected item", s, is(ITEM_VALUE));
                        }

                        @Override
                        public void onError(final Throwable t) {
                            fail("Unexpected throwable " + t);
                        }

                        @Override
                        public void onComplete() {
                            // noop
                        }
                    });
            source.onNext(ITEM_VALUE);
            source.onComplete();
            try {
                // Waiting for "afterOnComplete" to fire
                allDone.await();
            } catch (InterruptedException woken) {
                Thread.interrupted();
                return;
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
