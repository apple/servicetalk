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
import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.concurrent.api.AsyncContextMap;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.TerminalSignalConsumer;
import io.servicetalk.concurrent.api.TestCancellable;
import io.servicetalk.concurrent.api.TestCompletable;
import io.servicetalk.concurrent.api.internal.AbstractOffloadingTest;
import io.servicetalk.concurrent.test.internal.TestCompletableSubscriber;

import java.util.EnumSet;
import java.util.function.BiFunction;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;

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
                // Insert a custom value into AsyncContext map
                AsyncContext.current().put(ASYNC_CONTEXT_CUSTOM_KEY, ASYNC_CONTEXT_VALUE);

                capture(CaptureSlot.IN_APP);

                // Add thread/context recording test points
                final Completable original = testCompletable
                        .liftSync(subscriber -> {
                            capture(CaptureSlot.IN_OFFLOADED_SUBSCRIBE);
                            return subscriber;
                        })
                        .beforeOnSubscribe(cancellable -> capture(CaptureSlot.IN_ORIGINAL_ON_SUBSCRIBE))
                        .beforeFinally(new TerminalSignalConsumer() {

                            @Override
                            public void onComplete() {
                                capture(CaptureSlot.IN_ORIGINAL_ON_COMPLETE);
                            }

                            @Override
                            public void onError(final Throwable throwable) {
                                capture(CaptureSlot.IN_ORIGINAL_ON_ERROR);
                            }

                            @Override
                            public void cancel() {
                                capture(CaptureSlot.IN_OFFLOADED_CANCEL);
                            }
                        });

                // Perform offloading and add more thread/context recording test points
                Completable offloaded = offloadingFunction.apply(original, testExecutor.executor())
                        .liftSync(subscriber -> {
                            capture(CaptureSlot.IN_ORIGINAL_SUBSCRIBE);
                            return subscriber;
                        })
                        .beforeOnSubscribe(cancellable -> capture(CaptureSlot.IN_OFFLOADED_ON_SUBSCRIBE))
                        .beforeFinally(new TerminalSignalConsumer() {

                            @Override
                            public void onComplete() {
                                capture(CaptureSlot.IN_OFFLOADED_ON_COMPLETE);
                            }

                            @Override
                            public void onError(final Throwable throwable) {
                                capture(CaptureSlot.IN_OFFLOADED_ON_ERROR);
                            }

                            @Override
                            public void cancel() {
                                capture(CaptureSlot.IN_ORIGINAL_CANCEL);
                            }
                        });

                // subscribe and generate terminal
                toSource(offloaded).subscribe(testSubscriber);
                assertThat("Unexpected tasks " + testExecutor.executor().queuedTasksPending(),
                        testExecutor.executor().queuedTasksPending(), lessThan(2));
                if (1 == testExecutor.executor().queuedTasksPending()) {
                    // execute offloaded subscribe
                    testExecutor.executor().executeNextTask();
                }
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

        // Ensure that Async Context Map was correctly set during signals
        AsyncContextMap appMap = capturedContexts.captured(CaptureSlot.IN_APP);
        assertThat(appMap, notNullValue());
        AsyncContextMap subscribeMap = capturedContexts.captured(CaptureSlot.IN_ORIGINAL_SUBSCRIBE);
        assertThat(subscribeMap, notNullValue());
        assertThat("Map was shared not copied", subscribeMap, not(sameInstance(appMap)));
        assertThat("Missing custom async context entry ",
                subscribeMap.get(ASYNC_CONTEXT_CUSTOM_KEY), sameInstance(ASYNC_CONTEXT_VALUE));
        EnumSet<CaptureSlot> checkSlots =
                EnumSet.complementOf(EnumSet.of(CaptureSlot.IN_APP, CaptureSlot.IN_ORIGINAL_SUBSCRIBE));
        checkSlots.stream()
                .filter(slot -> null != capturedContexts.captured(slot))
                .forEach(slot -> {
                    AsyncContextMap map = capturedContexts.captured(slot);
                    assertThat("Custom key missing from context map", map.containsKey(ASYNC_CONTEXT_CUSTOM_KEY));
                    assertThat("Unexpected context map @ slot " + slot + " : " + map,
                             map, sameInstance(subscribeMap));
                });

        // Ensure that all offloading completed.
        assertThat("Offloading pending", testExecutor.executor().queuedTasksPending(), is(0));
        return testExecutor.executor().queuedTasksExecuted();
    }
}
