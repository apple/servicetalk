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

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.PublisherOperator;
import io.servicetalk.concurrent.api.TerminalSignalConsumer;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestSubscription;
import io.servicetalk.concurrent.api.internal.AbstractOffloadingTest;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static java.lang.Long.MAX_VALUE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

public abstract class AbstractPublisherOffloadingTest extends AbstractOffloadingTest {
    protected static final String ITEM_VALUE = "Hello";

    final TestSubscription testSubscription = new TestSubscription();
    final TestPublisher<String> testPublisher = new TestPublisher.Builder<String>().singleSubscriber()
            .disableAutoOnSubscribe().build(
                    (subscriber) -> {
                        subscriber.onSubscribe(testSubscription);
                        return subscriber;
                    }
            );
    final TestPublisherSubscriber<String> testSubscriber = new TestPublisherSubscriber<>();

    protected int testOffloading(BiFunction<Publisher<String>, Executor, Publisher<String>> offloadingFunction,
                                 TerminalOperation terminal) throws InterruptedException {
        Runnable appCode = () -> {
            try {
                capturedThreads.capture(CaptureSlot.APP_THREAD);

                // Add thread recording test points
                final Publisher<String> original = testPublisher
                        .liftSync((PublisherOperator<? super String, String>) subscriber -> {
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
                Publisher<String> offloaded = offloadingFunction.apply(original, offload.executor())
                        .liftSync((PublisherOperator<? super String, String>) subscriber -> {
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

                // subscribe
                toSource(offloaded).subscribe(testSubscriber);
                PublisherSource.Subscription subscription = testSubscriber.awaitSubscription();
                assertThat("No Subscription", subscription, notNullValue());
                testPublisher.awaitSubscribed();
                assertThat("Source is not subscribed", testPublisher.isSubscribed());
                assertThat("Thread was interrupted", !Thread.currentThread().isInterrupted());

                // generate demand
                subscription.request(MAX_VALUE);
                testSubscription.awaitRequestN(1);

                // perform terminal
                switch (terminal) {
                    case CANCEL:
                        subscription.cancel();
                        break;
                    case COMPLETE:
                        testPublisher.onNext(ITEM_VALUE);
                        String result = testSubscriber.takeOnNext();
                        assertThat("result is unexpected value", result, sameInstance(ITEM_VALUE));
                        testPublisher.onComplete();
                        break;
                    case ERROR:
                        testPublisher.onError(DELIBERATE_EXCEPTION);
                        break;
                    default:
                        throw new AssertionError("unexpected terminal mode");
                }
            } catch (Throwable all) {
                AbstractOffloadingTest.LOGGER.warn("Unexpected throwable", all);
                testSubscriber.onError(all);
            }
        };
        app.executor().execute(appCode);

        // Ensure we reached the correct terminal condition
        switch (terminal) {
            case CANCEL:
                testSubscription.awaitCancelled();
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
