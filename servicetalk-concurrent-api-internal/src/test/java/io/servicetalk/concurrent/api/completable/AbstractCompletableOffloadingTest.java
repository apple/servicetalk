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
import io.servicetalk.concurrent.api.TerminalSignalConsumer;
import io.servicetalk.concurrent.api.TestCancellable;
import io.servicetalk.concurrent.api.TestCompletable;
import io.servicetalk.concurrent.api.internal.AbstractOffloadingTest;
import io.servicetalk.concurrent.test.internal.TestCompletableSubscriber;

import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

public abstract class AbstractCompletableOffloadingTest extends AbstractOffloadingTest {

    final TestCancellable testCancellable = new TestCancellable();
    final TestCompletable testCompletable = new TestCompletable.Builder().build(
                    (subscriber) -> {
                        subscriber.onSubscribe(testCancellable);
                        return subscriber;
                    }
            );
    final TestCompletableSubscriber testSubscriber = new TestCompletableSubscriber();

    protected int testOffloading(BiFunction<Completable, Executor, Completable> offloadingFunction,
                                 TerminalOperation terminal) throws InterruptedException {
        Runnable appCode = () -> {
            try {
                capturedThreads.capture(CaptureSlot.APP_THREAD);

                // Add thread recording test points
                final Completable original = testCompletable
                        .liftSync(subscriber -> {
                            capturedThreads.capture(CaptureSlot.OFFLOADED_SUBSCRIBE_THREAD);
                            return subscriber;
                        })
                        .beforeFinally(new TerminalSignalConsumer() {

                            @Override
                            public void onComplete() {
                                capturedThreads.capture(CaptureSlot.ORIGINAL_SUBSCRIBER_THREAD);
                            }

                            @Override
                            public void onError(final Throwable throwable) {
                                capturedThreads.capture(CaptureSlot.ORIGINAL_SUBSCRIBER_THREAD);
                            }

                            @Override
                            public void cancel() {
                                capturedThreads.capture(CaptureSlot.OFFLOADED_SUBSCRIPTION_THREAD);
                            }
                        });

                // Perform offloading and add more thread recording test points
                Completable offloaded = offloadingFunction.apply(original, offload.executor())
                        .liftSync(subscriber -> {
                            capturedThreads.capture(CaptureSlot.ORIGINAL_SUBSCRIBE_THREAD);
                            return subscriber;
                        })
                        .beforeFinally(new TerminalSignalConsumer() {

                            @Override
                            public void onComplete() {
                                capturedThreads.capture(CaptureSlot.OFFLOADED_SUBSCRIBER_THREAD);
                            }

                            @Override
                            public void onError(final Throwable throwable) {
                                capturedThreads.capture(CaptureSlot.OFFLOADED_SUBSCRIBER_THREAD);
                            }

                            @Override
                            public void cancel() {
                                capturedThreads.capture(CaptureSlot.ORIGINAL_SUBSCRIPTION_THREAD);
                            }
                        });

                // subscribe and generate terminal
                toSource(offloaded).subscribe(testSubscriber);
                Cancellable cancellable = testSubscriber.awaitSubscription();
                assertThat("No Cancellable", cancellable, notNullValue());
                testCompletable.awaitSubscribed();
                assertThat("Source is not subscribed", testCompletable.isSubscribed());
                assertThat("Thread was interrupted", !Thread.currentThread().isInterrupted());
                switch (terminal) {
                    case CANCEL:
                        cancellable.cancel();
                        break;
                    case COMPLETE:
                        testCompletable.onComplete();
                        break;
                    case ERROR:
                        testCompletable.onError(DELIBERATE_EXCEPTION);
                        break;
                    default:
                        throw new AssertionError("unexpected terminal mode");
                }
            } catch (Throwable all) {
                AbstractOffloadingTest.LOGGER.warn("Unexpected throwable", all);
                testSubscriber.onError(all);
            }
        };
        if (APP_ISOLATION) {
            app.executor().execute(appCode);
        } else {
            appCode.run();
        }

        // Ensure we reached the correct terminal condition
        switch (terminal) {
            case CANCEL:
                testCancellable.awaitCancelled();
                break;
            case ERROR:
                Throwable thrown = testSubscriber.awaitOnError();
                assertThat("unexpected exception " + thrown, thrown, sameInstance(DELIBERATE_EXCEPTION));
                break;
            case COMPLETE:
                testSubscriber.awaitOnComplete();
                break;
            default:
                throw new AssertionError("unexpected terminal mode");
        }

        // Ensure that all offloading completed.
        offloadExecutorService.shutdown();
        boolean shutdown = offloadExecutorService.awaitTermination(5, TimeUnit.SECONDS);
        assertThat("Executor still active " + offloadExecutorService, shutdown, is(true));

        assertThat("Started offloads != finished", offloadsFinished.intValue(), is(offloadsStarted.intValue()));

        capturedThreads.assertCaptured();

        return offloadsFinished.intValue();
    }
}
