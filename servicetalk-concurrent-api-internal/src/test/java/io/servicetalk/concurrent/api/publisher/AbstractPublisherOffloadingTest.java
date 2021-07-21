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

import java.util.function.BiFunction;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static java.lang.Long.MAX_VALUE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;

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
                capturedThreads.capture(CaptureSlot.IN_APP);

                // Add thread recording test points
                final Publisher<String> original = testPublisher
                        .liftSync((PublisherOperator<? super String, String>) subscriber -> {
                            capturedThreads.capture(CaptureSlot.IN_OFFLOADED_SUBSCRIBE);
                            return subscriber;
                        })
                        .beforeFinally(new TerminalSignalConsumer() {

                            @Override
                            public void onComplete() {
                                capturedThreads.capture(CaptureSlot.IN_ORIGINAL_SUBSCRIBER);
                            }

                            @Override
                            public void onError(final Throwable throwable) {
                                capturedThreads.capture(CaptureSlot.IN_ORIGINAL_SUBSCRIBER);
                            }

                            @Override
                            public void cancel() {
                                capturedThreads.capture(CaptureSlot.IN_OFFLOADED_SUBSCRIPTION);
                            }
                        });

                // Perform offloading and add more thread recording test points
                Publisher<String> offloaded = offloadingFunction.apply(original, testExecutor.executor())
                        .liftSync((PublisherOperator<? super String, String>) subscriber -> {
                            capturedThreads.capture(CaptureSlot.IN_ORIGINAL_SUBSCRIBE);
                            return subscriber;
                        })
                        .beforeFinally(new TerminalSignalConsumer() {

                            @Override
                            public void onComplete() {
                                capturedThreads.capture(CaptureSlot.IN_OFFLOADED_SUBSCRIBER);
                            }

                            @Override
                            public void onError(final Throwable throwable) {
                                capturedThreads.capture(CaptureSlot.IN_OFFLOADED_SUBSCRIBER);
                            }

                            @Override
                            public void cancel() {
                                capturedThreads.capture(CaptureSlot.IN_ORIGINAL_SUBSCRIPTION);
                            }
                        });

                // subscribe
                toSource(offloaded).subscribe(testSubscriber);
                assertThat("Unexpected tasks " + testExecutor.executor().queuedTasksPending(),
                        testExecutor.executor().queuedTasksPending(), lessThan(2));
                if (1 == testExecutor.executor().queuedTasksPending()) {
                    // execute offloaded subscribe
                    testExecutor.executor().executeNextTask();
                }
                PublisherSource.Subscription subscription = testSubscriber.awaitSubscription();
                assertThat("No Subscription", subscription, notNullValue());
                testPublisher.awaitSubscribed();
                assertThat("Source is not subscribed", testPublisher.isSubscribed());
                assertThat("Thread was interrupted", !Thread.currentThread().isInterrupted());

                // generate demand
                subscription.request(MAX_VALUE);
                assertThat("Unexpected tasks " + testExecutor.executor().queuedTasksPending(),
                        testExecutor.executor().queuedTasksPending(), lessThan(2));
                if (1 == testExecutor.executor().queuedTasksPending()) {
                    // execute offloaded request
                    testExecutor.executor().executeNextTask();
                }
                testSubscription.awaitRequestN(1);

                // perform terminal
                switch (terminal) {
                    case CANCEL:
                        subscription.cancel();
                        break;
                    case COMPLETE:
                        testPublisher.onNext(ITEM_VALUE);
                        assertThat("Unexpected tasks " + testExecutor.executor().queuedTasksPending(),
                                testExecutor.executor().queuedTasksPending(), lessThan(2));
                        if (1 == testExecutor.executor().queuedTasksPending()) {
                            // execute offloaded onNext
                            testExecutor.executor().executeNextTask();
                        }
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
                assertThat("Unexpected tasks " + testExecutor.executor().queuedTasksPending(),
                        testExecutor.executor().queuedTasksPending(), lessThan(2));
                if (1 == testExecutor.executor().queuedTasksPending()) {
                    // execute offloaded terminal
                    testExecutor.executor().executeNextTask();
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

        assertThat("Pending offloading", testExecutor.executor().queuedTasksPending(), is(0));
        return testExecutor.executor().queuedTasksExecuted();
    }
}
