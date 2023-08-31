/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.internal.DeliberateException;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.empty;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Publisher.never;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.api.SwitchMapSignal.toSwitchFunction;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

final class PublisherSwitchMapTest {
    private final TestSubscription testSubscription = new TestSubscription();
    private final TestPublisher<Integer> publisher = new TestPublisher.Builder<Integer>()
            .disableAutoOnSubscribe().build(sub -> {
                sub.onSubscribe(testSubscription);
                return sub;
            });
    private final TestPublisherSubscriber<SwitchMapSignal<String>> subscriber =
            new TestPublisherSubscriber<>();

    @ParameterizedTest(name = "delayError={0}")
    @ValueSource(booleans = {true, false})
    void cancelPropagatedMappedAndUpstream(boolean delayError) throws InterruptedException {
        final TestPublisherSubscriber<String> subscriber = new TestPublisherSubscriber<>();
        final TestSubscription testSubscription2 = new TestSubscription();
        final TestPublisher<String> publisher2 = new TestPublisher.Builder<String>()
                .disableAutoOnSubscribe().build(sub -> {
                    sub.onSubscribe(testSubscription2);
                    return sub;
                });
        toSource((delayError ?
                publisher.<String>switchMapDelayError(i -> publisher2) : publisher.switchMap(i -> publisher2)))
                .subscribe(subscriber);

        PublisherSource.Subscription subscription = subscriber.awaitSubscription();
        subscription.request(1);
        testSubscription.awaitRequestN(1);
        publisher.onNext(1);

        publisher2.awaitSubscribed();
        subscription.cancel();
        testSubscription2.awaitCancelled();
        testSubscription.awaitCancelled();
    }

    @ParameterizedTest(name = "onError={0}, delayError={1}")
    @CsvSource({"true,true", "true,false", "false,true", "false,false"})
    void noSignalsTerminal(boolean onError, boolean delayError) {
        final TestPublisherSubscriber<String> subscriber = new TestPublisherSubscriber<>();
        toSource((delayError ? publisher.<String>switchMapDelayError(i -> never()) : publisher.switchMap(i -> never())))
                .subscribe(subscriber);

        validateTerminal(publisher, subscriber, onError);
    }

    @ParameterizedTest(name = "onError={0}, delayError={1}")
    @CsvSource({"true,true", "true,false", "false,true", "false,false"})
    void noSwitchMultipleSignals(boolean onError, boolean delayError) throws InterruptedException {
        final TestSubscription testSubscription2 = new TestSubscription();
        final TestPublisher<String> publisher2 = new TestPublisher.Builder<String>()
                .disableAutoOnSubscribe().build(sub -> {
                    sub.onSubscribe(testSubscription2);
                    return sub;
                });

        final int firstT = 0;
        final String firstR = "foo";
        final String secondR = "bar";
        Function<Integer, Publisher<? extends SwitchMapSignal<String>>> func =
                toSwitchFunction(i -> i == firstT ? publisher2 : never());
        toSource(delayError ? publisher.switchMapDelayError(func) : publisher.switchMap(func))
                .subscribe(subscriber);

        subscriber.awaitSubscription().request(2);
        testSubscription.awaitRequestN(1);
        publisher.onNext(firstT);
        // We want to verify cancel at the end, if the source completes we won't cancel so skip completion.
        if (!(onError && !delayError)) {
            publisher.onComplete();
        }

        assertThat(subscriber.pollOnNext(10, TimeUnit.MILLISECONDS), nullValue());

        testSubscription2.awaitRequestN(1);
        publisher2.onNext(firstR);
        validateSignal(subscriber.takeOnNext(), firstR, false);

        testSubscription2.awaitRequestN(2);
        publisher2.onNext(secondR);
        validateSignal(subscriber.takeOnNext(), secondR, false);

        validateTerminal(publisher2, subscriber, onError);
        if (onError && !delayError) {
            testSubscription.awaitCancelled();
        }
    }

    @ParameterizedTest(name = "onError={0}, delayError={1}")
    @CsvSource({"true,true", "true,false", "false,true", "false,false"})
    void multipleSwitches(boolean onError, boolean delayError) throws InterruptedException {
        final TestSubscription testSubscription2 = new TestSubscription();
        final TestPublisher<String> publisher2 = new TestPublisher.Builder<String>()
                .disableAutoOnSubscribe().build(sub -> {
                    sub.onSubscribe(testSubscription2);
                    return sub;
                });
        final TestSubscription testSubscription3 = new TestSubscription();
        final TestPublisher<String> publisher3 = new TestPublisher.Builder<String>()
                .disableAutoOnSubscribe().build(sub -> {
                    sub.onSubscribe(testSubscription3);
                    return sub;
                });
        final TestSubscription testSubscription4 = new TestSubscription();
        final TestPublisher<String> publisher4 = new TestPublisher.Builder<String>()
                .disableAutoOnSubscribe().build(sub -> {
                    sub.onSubscribe(testSubscription4);
                    return sub;
                });

        final int firstT = 0;
        final int secondT = 1;
        final int thirdT = 2;
        final String firstR = "foo";
        final String secondR = "bar";
        final String thirdR = "baz";
        final String ignoredR = "IGNORED";
        Function<Integer, Publisher<? extends SwitchMapSignal<String>>> func = toSwitchFunction(
                i -> i == firstT ? publisher2 :
                        i == secondT ? publisher3 :
                                i == thirdT ? publisher4 : never());
        toSource(delayError ? publisher.switchMapDelayError(func, 2) : publisher.switchMap(func))
                .subscribe(subscriber);

        subscriber.awaitSubscription().request(3);
        testSubscription.awaitRequestN(1);
        publisher.onNext(firstT);

        testSubscription2.awaitRequestN(1);
        publisher2.onNext(firstR);
        validateSignal(subscriber.takeOnNext(), firstR, false);

        // Verify that when delay error is activated, terminating a mapped publisher will keep switching to new
        // sources and also is delivered when the source publisher terminates.
        DeliberateException deliberateException = new DeliberateException();
        if (delayError) {
            publisher2.onError(deliberateException);
        } else {
            publisher2.onComplete();
        }

        testSubscription.awaitRequestN(2);
        publisher.onNext(secondT);

        testSubscription2.awaitCancelled();
        testSubscription3.awaitRequestN(2);

        // Don't emit any items, and assert that switch is still done
        testSubscription.awaitRequestN(3);
        publisher.onNext(thirdT);
        // We want to verify cancel at the end, if the source completes we won't cancel so skip completion.
        if (!(onError && !delayError)) {
            publisher.onComplete();
        }
        testSubscription3.awaitCancelled();
        testSubscription4.awaitRequestN(2);

        // Send signals on "old" publishers that we switched from an ignore all the signals.
        publisher3.onNext(ignoredR);
        if (onError) {
            publisher3.onError(new IllegalStateException("should be ignored"));
        } else {
            publisher3.onComplete();
        }

        publisher4.onNext(secondR, thirdR);
        validateSignal(subscriber.takeOnNext(), secondR, true);
        validateSignal(subscriber.takeOnNext(), thirdR, false);

        if (delayError) {
            publisher4.onError(DELIBERATE_EXCEPTION);
            Throwable cause = subscriber.awaitOnError();
            assertThat(cause, is(deliberateException));
            assertThat(asList(cause.getSuppressed()), contains(DELIBERATE_EXCEPTION));
        } else {
            validateTerminal(publisher4, subscriber, onError);
        }
        if (onError && !delayError) {
            testSubscription.awaitCancelled();
        }
    }

    @SuppressWarnings("unchecked")
    @ParameterizedTest(name = "offloadFirstDemand={0}, delayError={1}")
    @CsvSource({"true,true", "true,false", "false,true", "false,false"})
    void reentry(boolean offloadFirstDemand, boolean delayError) throws InterruptedException, ExecutionException {
        final String completeSignal = "complete";
        BlockingQueue<Object> signals = new LinkedBlockingQueue<>();
        Executor executor = Executors.newCachedThreadExecutor();
        try {
            Function<Integer, Publisher<? extends SwitchMapSignal<Integer>>> func = toSwitchFunction(i -> i == 1 ?
                    fromSource(new ReentryPublisher(100, 103)) : never());
            Publisher<Integer> pub = from(1);
            toSource(delayError ? pub.switchMapDelayError(func) : pub.switchMap(func)
            ).subscribe(new PublisherSource.Subscriber<SwitchMapSignal<Integer>>() {
                @Nullable
                private PublisherSource.Subscription subscription;
                private boolean seenOnNext;

                @Override
                public void onSubscribe(PublisherSource.Subscription s) {
                    subscription = s;
                    subscription.request(1);
                }

                @Override
                public void onNext(@Nullable SwitchMapSignal<Integer> next) {
                    assert subscription != null;
                    signals.add(requireNonNull(next));
                    final boolean localSeenOnNext = seenOnNext;
                    seenOnNext = true;
                    if (localSeenOnNext || !offloadFirstDemand) {
                        subscription.request(1);
                    } else {
                        // SequentialSubscription will prevent reentry from onNext when invoked from switchTo, so we
                        // offload here just to be sure reentry cases are covered.
                        executor.execute(() -> {
                            try {
                                // If this task executes quickly the Publisher may see demand in its loop from
                                // the request(n) in onSubscribe without triggering reentry from onNext.
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                            subscription.request(1);
                        });
                    }
                }

                @Override
                public void onError(Throwable t) {
                    signals.add(t);
                }

                @Override
                public void onComplete() {
                    signals.add(completeSignal);
                }
            });

            Object signal = signals.take();
            assertThat(signal, instanceOf(SwitchMapSignal.class));
            validateSignal((SwitchMapSignal<Integer>) signal, 100, false);
            signal = signals.take();
            assertThat(signal, instanceOf(SwitchMapSignal.class));
            validateSignal((SwitchMapSignal<Integer>) signal, 101, false);
            signal = signals.take();
            assertThat(signal, instanceOf(SwitchMapSignal.class));
            validateSignal((SwitchMapSignal<Integer>) signal, 102, false);
            signal = signals.take();
            assertThat(signal, is(completeSignal));
        } finally {
            executor.closeAsync().toFuture().get();
        }
    }

    @ParameterizedTest(name = "delayError={0}")
    @ValueSource(booleans = {true, false})
    void nullPublisher(boolean delayError) throws InterruptedException {
        Function<Integer, Publisher<? extends SwitchMapSignal<String>>> func = toSwitchFunction(i -> null);
        toSource(delayError ? publisher.switchMapDelayError(func) : publisher.switchMap(func))
                .subscribe(subscriber);

        subscriber.awaitSubscription().request(1);
        testSubscription.awaitRequestN(1);
        publisher.onNext(0);
        assertThat(subscriber.awaitOnError(), instanceOf(NullPointerException.class));
    }

    @ParameterizedTest(name = "delayError={0}")
    @ValueSource(booleans = {true, false})
    void emptyPublisher(boolean delayError) throws InterruptedException {
        Function<Integer, Publisher<? extends SwitchMapSignal<String>>> func =
                toSwitchFunction(i -> i == 0 ? empty() : from("foo"));
        toSource(delayError ? publisher.switchMapDelayError(func) : publisher.switchMap(func))
                .subscribe(subscriber);

        subscriber.awaitSubscription().request(1);
        testSubscription.awaitRequestN(1);

        publisher.onNext(0);

        testSubscription.awaitRequestN(2);
        publisher.onNext(1);

        validateSignal(subscriber.takeOnNext(), "foo", true);
        validateTerminal(publisher, subscriber, false);
    }

    @ParameterizedTest(name = "onError={0}")
    @ValueSource(booleans = {true, false})
    void delayErrorDeliverAfterOuterTerminates(boolean onError) throws InterruptedException {
        final TestSubscription testSubscription2 = new TestSubscription();
        final TestPublisher<String> publisher2 = new TestPublisher.Builder<String>()
                .disableAutoOnSubscribe().build(sub -> {
                    sub.onSubscribe(testSubscription2);
                    return sub;
                });
        toSource(publisher.switchMapDelayError(toSwitchFunction(i -> publisher2), 2))
                .subscribe(subscriber);

        subscriber.awaitSubscription().request(1);
        testSubscription.awaitRequestN(1);

        publisher.onNext(0);
        testSubscription.awaitRequestN(2);
        DeliberateException deliberateException = new DeliberateException();
        publisher.onError(deliberateException);

        testSubscription2.awaitRequestN(1);
        publisher2.onNext("foo");

        validateSignal(subscriber.takeOnNext(), "foo", false);

        if (onError) {
            publisher2.onError(DELIBERATE_EXCEPTION);
        } else {
            publisher2.onComplete();
        }

        Throwable cause = subscriber.awaitOnError();
        assertThat(cause, is(deliberateException));
        if (onError) {
            assertThat(asList(cause.getSuppressed()), contains(DELIBERATE_EXCEPTION));
        }
    }

    @ParameterizedTest(name = "onError={0}")
    @ValueSource(booleans = {true, false})
    void nonDelayedIgnoresAfterError(boolean onError) throws InterruptedException {
        final TestSubscription testSubscription2 = new TestSubscription();
        final TestPublisher<String> publisher2 = new TestPublisher.Builder<String>()
                .disableAutoOnSubscribe().build(sub -> {
                    sub.onSubscribe(testSubscription2);
                    return sub;
                });
        toSource(publisher.switchMap(toSwitchFunction(i -> publisher2)))
                .subscribe(subscriber);

        subscriber.awaitSubscription().request(1);
        testSubscription.awaitRequestN(1);

        publisher.onNext(0);
        testSubscription.awaitRequestN(2);
        if (onError) {
            publisher.onError(DELIBERATE_EXCEPTION);
        } else {
            publisher.onComplete();
        }

        testSubscription2.awaitRequestN(1);
        publisher2.onNext("foo");

        if (!onError) {
            validateSignal(subscriber.takeOnNext(), "foo", false);
            publisher2.onComplete();
        }

        if (onError) {
            assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
        } else {
            subscriber.awaitOnComplete();
        }
    }

    @ParameterizedTest(name = "delayError={0}, onComplete={1} rootErrorFirst={2}")
    @CsvSource({"true,true,true", "true,false,true", "false,true,true",
                "true,true,false", "true,false,false", "false,true,false"})
    void onErrorCancelMappedPublisher(boolean delayError, boolean onComplete,
                                      boolean rootErrorFirst) throws InterruptedException {
        final TestSubscription testSubscription2 = new TestSubscription();
        final TestPublisher<String> publisher2 = new TestPublisher.Builder<String>()
                .disableAutoOnSubscribe().build(sub -> {
                    sub.onSubscribe(testSubscription2);
                    return sub;
                });

        Function<Integer, Publisher<? extends SwitchMapSignal<String>>> func = toSwitchFunction(i -> publisher2);
        toSource(delayError ? publisher.switchMapDelayError(func, 2) : publisher.switchMap(func))
                .subscribe(subscriber);

        subscriber.awaitSubscription().request(1);
        testSubscription.awaitRequestN(1);
        publisher.onNext(0);

        testSubscription2.awaitRequestN(1);
        publisher2.onNext("foo");
        validateSignal(subscriber.takeOnNext(), "foo", false);

        if (delayError) {
            DeliberateException deliberateException = new DeliberateException();
            if (rootErrorFirst) {
                publisher.onError(deliberateException);
            }

            Throwable secondCause = new IllegalStateException("second exception");
            if (onComplete) {
                publisher2.onComplete();
            } else {
                publisher2.onError(secondCause);
            }
            // With delayError, terminating the mapped publisher must request more demand from the source publisher
            // as otherwise the source publisher may not terminate.
            testSubscription.awaitRequestN(2);

            if (rootErrorFirst) {
                Throwable cause = subscriber.awaitOnError();
                assertThat(cause, is(deliberateException));
                if (!onComplete) {
                    assertThat(asList(cause.getSuppressed()), contains(secondCause));
                }
            } else {
                publisher.onError(deliberateException);
                Throwable cause = subscriber.awaitOnError();
                if (onComplete) {
                    assertThat(cause, is(deliberateException));
                } else {
                    assertThat(cause, is(secondCause));
                    assertThat(asList(cause.getSuppressed()), contains(deliberateException));
                }
            }
            assertThat(testSubscription2.isCancelled(), equalTo(false));
        } else {
            publisher.onError(DELIBERATE_EXCEPTION);
            testSubscription2.awaitCancelled();
            assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
        }
    }

    private static <R> void validateSignal(@Nullable SwitchMapSignal<R> signal, R data, boolean isSwitched) {
        assertThat(signal, notNullValue());
        assertThat(signal.isSwitched(), equalTo(isSwitched));
        assertThat(signal.onNext(), equalTo(data));
    }

    private static <P, S> void validateTerminal(TestPublisher<P> publisher, TestPublisherSubscriber<S> subscriber,
                                                boolean onError) {
        if (onError) {
            publisher.onError(DELIBERATE_EXCEPTION);
            assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
        } else {
            publisher.onComplete();
            subscriber.awaitOnComplete();
        }
    }
}
