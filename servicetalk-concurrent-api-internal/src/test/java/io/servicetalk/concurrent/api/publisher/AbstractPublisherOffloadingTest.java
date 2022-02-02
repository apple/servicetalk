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
import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.PublisherOperator;
import io.servicetalk.concurrent.api.TerminalSignalConsumer;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestSubscription;
import io.servicetalk.concurrent.api.internal.AbstractOffloadingTest;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;
import io.servicetalk.context.api.ContextMap;

import java.util.EnumSet;
import java.util.function.BiFunction;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static java.lang.Long.MAX_VALUE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
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
                // Insert a custom value into AsyncContext map
                AsyncContext.put(ASYNC_CONTEXT_CUSTOM_KEY, ASYNC_CONTEXT_VALUE);

                capture(CaptureSlot.APP);

                // Add thread recording test points
                final Publisher<String> original = testPublisher
                        .liftSync((PublisherOperator<? super String, String>) subscriber -> {
                            capture(CaptureSlot.OFFLOADED_SUBSCRIBE);
                            return subscriber;
                        })
                        .beforeOnSubscribe(cancellable -> capture(CaptureSlot.ORIGINAL_ON_SUBSCRIBE))
                        .beforeRequest((requested) ->
                                capture(CaptureSlot.OFFLOADED_REQUEST)
                        )
                        .beforeOnNext((item) ->
                                capture(CaptureSlot.ORIGINAL_ON_NEXT)
                        )
                        .beforeFinally(new TerminalSignalConsumer() {

                            @Override
                            public void onComplete() {
                                capture(CaptureSlot.ORIGINAL_ON_COMPLETE);
                            }

                            @Override
                            public void onError(final Throwable throwable) {
                                capture(CaptureSlot.ORIGINAL_ON_ERROR);
                            }

                            @Override
                            public void cancel() {
                                capture(CaptureSlot.OFFLOADED_CANCEL);
                            }
                        });

                // Perform offloading and add more thread recording test points
                Publisher<String> offloaded = offloadingFunction.apply(original, testExecutor.executor())
                        .liftSync((PublisherOperator<? super String, String>) subscriber -> {
                            capture(CaptureSlot.ORIGINAL_SUBSCRIBE);
                            return subscriber;
                        })
                        .beforeOnSubscribe(cancellable -> capture(CaptureSlot.OFFLOADED_ON_SUBSCRIBE))
                        .beforeRequest((requested) ->
                                capture(CaptureSlot.ORIGINAL_REQUEST)
                        )
                        .beforeOnNext((item) ->
                                capture(CaptureSlot.OFFLOADED_ON_NEXT)
                        )
                        .beforeFinally(new TerminalSignalConsumer() {

                            @Override
                            public void onComplete() {
                                capture(CaptureSlot.OFFLOADED_ON_COMPLETE);
                            }

                            @Override
                            public void onError(final Throwable throwable) {
                                capture(CaptureSlot.OFFLOADED_ON_ERROR);
                            }

                            @Override
                            public void cancel() {
                                capture(CaptureSlot.ORIGINAL_CANCEL);
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
        APP_EXECUTOR_EXT.executor().execute(appCode);

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

        // Ensure that Async Context Map was correctly set during signals
        ContextMap appMap = capturedContexts.captured(CaptureSlot.APP);
        assertThat(appMap, notNullValue());
        ContextMap subscribeMap = capturedContexts.captured(CaptureSlot.ORIGINAL_SUBSCRIBE);
        assertThat(subscribeMap, notNullValue());
        assertThat("Map was shared not copied", subscribeMap, not(sameInstance(appMap)));
        assertThat("Missing custom async context entry ",
                subscribeMap.get(ASYNC_CONTEXT_CUSTOM_KEY), sameInstance(ASYNC_CONTEXT_VALUE));
        EnumSet<CaptureSlot> checkSlots =
                EnumSet.complementOf(EnumSet.of(CaptureSlot.APP, CaptureSlot.ORIGINAL_SUBSCRIBE));
        checkSlots.stream()
                .filter(slot -> null != capturedContexts.captured(slot))
                .forEach(slot -> {
                    ContextMap map = capturedContexts.captured(slot);
                    assertThat("Context map was not captured", map, is(notNullValue()));
                    assertThat("Custom key missing from context map", map.containsKey(ASYNC_CONTEXT_CUSTOM_KEY));
                    assertThat("Unexpected context map @ slot " + slot + " : " + map,
                            map, sameInstance(subscribeMap));
                });

        assertThat("Pending offloading", testExecutor.executor().queuedTasksPending(), is(0));
        return testExecutor.executor().queuedTasksExecuted();
    }
}
