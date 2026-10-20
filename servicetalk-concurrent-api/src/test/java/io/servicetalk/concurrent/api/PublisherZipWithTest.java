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
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.internal.DeliberateException;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.lang.Math.max;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

final class PublisherZipWithTest {
    private TestSubscription firstSubscription;
    private TestSubscription secondSubscription;
    private TestPublisher<Integer> first;
    private TestPublisher<Double> second;
    private TestPublisherSubscriber<String> subscriber;

    @BeforeEach
    void setUp() {
        firstSubscription = new TestSubscription();
        secondSubscription = new TestSubscription();
        first = new TestPublisher.Builder<Integer>().disableAutoOnSubscribe().build(subscriber1 -> {
            subscriber1.onSubscribe(firstSubscription);
            return subscriber1;
        });
        second = new TestPublisher.Builder<Double>().disableAutoOnSubscribe().build(subscriber1 -> {
            subscriber1.onSubscribe(secondSubscription);
            return subscriber1;
        });
        subscriber = new TestPublisherSubscriber<>();
    }

    @ParameterizedTest(name = "{displayName} [{index}] inOrderCompletion={0} onError={1}")
    @CsvSource(value = {"true,true", "true,false", "false,true", "false,false"})
    void allComplete(boolean inOrderCompletion, boolean onError) throws InterruptedException {
        toSource(first.zipWith(second, PublisherZipWithTest::combine)).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        int i = 3;
        double d = 10.23;
        firstSubscription.awaitRequestN(1);
        secondSubscription.awaitRequestN(1);
        if (inOrderCompletion) {
            first.onNext(i);
            second.onNext(d);
        } else {
            second.onNext(d);
            first.onNext(i);
        }
        assertThat(subscriber.takeOnNext(), equalTo(combine(i, d)));
        terminateSubscribers(inOrderCompletion, onError);
    }

    @ParameterizedTest(name = "{displayName} [{index}] inOrderCompletion={0}")
    @ValueSource(booleans = {true, false})
    void justError(boolean inOrderCompletion) {
        toSource(first.zipWith(second, PublisherZipWithTest::combine)).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        if (inOrderCompletion) {
            first.onError(DELIBERATE_EXCEPTION);
        } else {
            second.onError(DELIBERATE_EXCEPTION);
        }
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void justCancel() throws InterruptedException {
        toSource(first.zipWith(second, PublisherZipWithTest::combine)).subscribe(subscriber);
        subscriber.awaitSubscription().cancel();
        firstSubscription.awaitCancelled();
        secondSubscription.awaitCancelled();
    }

    @ParameterizedTest(name = "{displayName} [{index}] inOrderCompletion={0}")
    @ValueSource(booleans = {true, false})
    void cancelWhilePending(boolean inOrderCompletion) throws InterruptedException {
        toSource(first.zipWith(second, PublisherZipWithTest::combine)).subscribe(subscriber);
        Subscription s = subscriber.awaitSubscription();
        s.request(1);
        int i = 3;
        double d = 10.23;
        firstSubscription.awaitRequestN(1);
        secondSubscription.awaitRequestN(1);
        if (inOrderCompletion) {
            first.onNext(i);
        } else {
            second.onNext(d);
        }
        s.cancel();
        firstSubscription.awaitCancelled();
        secondSubscription.awaitCancelled();
    }

    @ParameterizedTest(name = "{displayName} [{index}] inOrderCompletion={0} onError={1}")
    @CsvSource(value = {"true,true", "true,false", "false,true", "false,false"})
    void terminateWithOutstandingSignals(boolean inOrderCompletion, boolean onError) throws InterruptedException {
        toSource(first.zipWith(second, PublisherZipWithTest::combine)).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        int i = 3;
        double d = 10.23;
        firstSubscription.awaitRequestN(1);
        secondSubscription.awaitRequestN(1);
        if (inOrderCompletion) {
            first.onNext(i);
            if (onError) {
                second.onError(DELIBERATE_EXCEPTION);
            } else {
                first.onComplete();
                second.onComplete();
            }
        } else {
            second.onNext(d);
            if (onError) {
                first.onError(DELIBERATE_EXCEPTION);
            } else {
                second.onComplete();
                first.onComplete();
            }
        }
        if (onError) {
            assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
        } else {
            assertThat(subscriber.awaitOnError(), instanceOf(IllegalStateException.class));
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] inOrderCompletion={0} onError={1}")
    @CsvSource(value = {"true,true", "true,false", "false,true", "false,false"})
    void maxOutstandingDemand(boolean inOrderCompletion, boolean onError) throws InterruptedException {
        toSource(first.zipWith(second, PublisherZipWithTest::combine, 1)).subscribe(subscriber);
        subscriber.awaitSubscription().request(Long.MAX_VALUE);
        int i = 3;
        double d = 10.23;
        firstSubscription.awaitRequestN(1);
        assertThat(firstSubscription.requested(), equalTo(1L));
        secondSubscription.awaitRequestN(1);
        assertThat(secondSubscription.requested(), equalTo(1L));

        if (inOrderCompletion) {
            first.onNext(i);
            assertThat(firstSubscription.requested(), equalTo(1L));
            second.onNext(d);
        } else {
            second.onNext(d);
            assertThat(secondSubscription.requested(), equalTo(1L));
            first.onNext(i);
        }
        assertThat(subscriber.takeOnNext(), equalTo(combine(i, d)));

        firstSubscription.awaitRequestN(2);
        assertThat(firstSubscription.requested(), equalTo(2L));
        secondSubscription.awaitRequestN(2);
        assertThat(secondSubscription.requested(), equalTo(2L));

        i = 5;
        d = 6.7;
        if (inOrderCompletion) {
            first.onNext(i);
            second.onNext(d);
        } else {
            second.onNext(d);
            first.onNext(i);
        }
        assertThat(subscriber.takeOnNext(), equalTo(combine(i, d)));
        terminateSubscribers(inOrderCompletion, onError);
    }

    @ParameterizedTest(name = "{displayName} [{index}] begin={0} end={1}")
    @CsvSource(value = {"0,2", "0,4", "0,10"})
    void reentry(int begin, int end) throws InterruptedException {
        final String completeSignal = "complete";
        final BlockingQueue<Object> signals = new LinkedBlockingQueue<>();
        toSource(fromSource(new ReentryPublisher(begin, end)).zipWith(fromSource(new ReentryPublisher(begin, end)),
                        PublisherZipWithTest::combineReentry, 1))
                .subscribe(new PublisherSource.Subscriber<String>() {
            @Nullable
            private Subscription subscription;

            @Override
            public void onSubscribe(Subscription s) {
                subscription = s;
                // Request more than 1 bcz a potential reentry condition is triggered from inside the zip operator
                // due to how demand is managed, and we want pending downstream demand to allow for reentry delivery.
                subscription.request(max(end, 10));
            }

            @Override
            public void onNext(@Nullable String next) {
                assert subscription != null;
                signals.add(requireNonNull(next));
                subscription.request(1);
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
        Object signal;
        for (int i = begin; i < end; ++i) {
            signal = signals.take();
            assertThat(signal, equalTo(combineReentry(i, i)));
        }
        signal = signals.take();
        assertThat(signal, is(completeSignal));
        assertThat(signals.isEmpty(), equalTo(true));
    }

    @ParameterizedTest(name = "{displayName} [{index}] inOrderCompletion={0} onError={1}")
    @CsvSource(value = {"true,true", "true,false", "false,true", "false,false"})
    void delayError(boolean inOrderCompletion, boolean onError) throws InterruptedException {
        toSource(first.zipWithDelayError(second, PublisherZipWithTest::combine)).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        int i = 3;
        double d = 10.23;
        firstSubscription.awaitRequestN(1);
        secondSubscription.awaitRequestN(1);
        DeliberateException e1 = new DeliberateException();
        if (inOrderCompletion) {
            first.onNext(i);
            second.onError(e1);
        } else {
            second.onNext(d);
            first.onError(e1);
        }
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), nullValue());
        if (inOrderCompletion) {
            if (onError) {
                first.onError(DELIBERATE_EXCEPTION);
            } else {
                first.onComplete();
            }
        } else if (onError) {
            second.onError(DELIBERATE_EXCEPTION);
        } else {
            second.onComplete();
        }
        assertThat(subscriber.awaitOnError(), is(e1));
        if (onError) {
            assertThat(asList(e1.getSuppressed()), contains(DELIBERATE_EXCEPTION));
        }
    }

    private void terminateSubscribers(boolean inOrderCompletion, boolean onError) {
        if (onError) {
            if (inOrderCompletion) {
                first.onError(DELIBERATE_EXCEPTION);
            } else {
                second.onError(DELIBERATE_EXCEPTION);
            }
            assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
        } else {
            if (inOrderCompletion) {
                first.onComplete();
                second.onComplete();
            } else {
                second.onComplete();
                first.onComplete();
            }
            subscriber.awaitOnComplete();
        }
    }

    private static String combineReentry(int i, int d) {
        return "first: " + i + " second: " + d;
    }

    private static String combine(int i, double d) {
        return "int: " + i + " double: " + d;
    }
}
