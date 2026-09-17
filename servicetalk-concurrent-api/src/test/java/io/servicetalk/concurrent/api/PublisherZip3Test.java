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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.zip;
import static io.servicetalk.concurrent.api.Publisher.zipDelayError;
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

final class PublisherZip3Test {
    private TestSubscription firstSubscription;
    private TestSubscription secondSubscription;
    private TestSubscription thirdSubscription;
    private TestPublisher<Integer> first;
    private TestPublisher<Double> second;
    private TestPublisher<Short> third;
    private TestPublisherSubscriber<String> subscriber;

    @BeforeEach
    void setUp() {
        firstSubscription = new TestSubscription();
        secondSubscription = new TestSubscription();
        thirdSubscription = new TestSubscription();
        first = new TestPublisher.Builder<Integer>().disableAutoOnSubscribe().build(subscriber1 -> {
            subscriber1.onSubscribe(firstSubscription);
            return subscriber1;
        });
        second = new TestPublisher.Builder<Double>().disableAutoOnSubscribe().build(subscriber1 -> {
            subscriber1.onSubscribe(secondSubscription);
            return subscriber1;
        });
        third = new TestPublisher.Builder<Short>().disableAutoOnSubscribe().build(subscriber1 -> {
            subscriber1.onSubscribe(thirdSubscription);
            return subscriber1;
        });
        subscriber = new TestPublisherSubscriber<>();
    }

    @ParameterizedTest(name = "{displayName} [{index}] inOrderCompletion={0} onError={1}")
    @CsvSource(value = {"true,true", "true,false", "false,true", "false,false"})
    void allComplete(boolean inOrderCompletion, boolean onError) throws InterruptedException {
        toSource(zip(first, second, third, PublisherZip3Test::combine)).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        int i = 3;
        double d = 10.23;
        short s = 88;
        firstSubscription.awaitRequestN(1);
        secondSubscription.awaitRequestN(1);
        thirdSubscription.awaitRequestN(1);
        if (inOrderCompletion) {
            first.onNext(i);
            second.onNext(d);
            third.onNext(s);
        } else {
            third.onNext(s);
            second.onNext(d);
            first.onNext(i);
        }
        assertThat(subscriber.takeOnNext(), equalTo(combine(i, d, s)));
        terminateSubscribers(inOrderCompletion, onError);
    }

    @ParameterizedTest(name = "{displayName} [{index}] errorNum={0}")
    @ValueSource(ints = {1, 2, 3})
    void justError(int errorNum) {
        toSource(zip(first, second, third, PublisherZip3Test::combine)).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        if (errorNum == 1) {
            first.onError(DELIBERATE_EXCEPTION);
        } else if (errorNum == 2) {
            second.onError(DELIBERATE_EXCEPTION);
        } else {
            third.onError(DELIBERATE_EXCEPTION);
        }
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void justCancel() throws InterruptedException {
        toSource(zip(first, second, third, PublisherZip3Test::combine)).subscribe(subscriber);
        subscriber.awaitSubscription().cancel();
        firstSubscription.awaitCancelled();
        secondSubscription.awaitCancelled();
        thirdSubscription.awaitCancelled();
    }

    @ParameterizedTest(name = "{displayName} [{index}] inOrderCompletion={0}")
    @ValueSource(booleans = {true, false})
    void cancelWhilePending(boolean inOrderCompletion) throws InterruptedException {
        toSource(zip(first, second, third, PublisherZip3Test::combine)).subscribe(subscriber);
        PublisherSource.Subscription sub = subscriber.awaitSubscription();
        sub.request(1);
        int i = 3;
        double d = 10.23;
        short s = 88;
        firstSubscription.awaitRequestN(1);
        secondSubscription.awaitRequestN(1);
        thirdSubscription.awaitRequestN(1);
        if (inOrderCompletion) {
            first.onNext(i);
            second.onNext(d);
        } else {
            third.onNext(s);
            second.onNext(d);
        }
        sub.cancel();
        firstSubscription.awaitCancelled();
        secondSubscription.awaitCancelled();
        thirdSubscription.awaitCancelled();
    }

    @ParameterizedTest(name = "{displayName} [{index}] inOrderCompletion={0} onError={1}")
    @CsvSource(value = {"true,true", "true,false", "false,true", "false,false"})
    void terminateWithOutstandingSignals(boolean inOrderCompletion, boolean onError) throws InterruptedException {
        toSource(zip(first, second, third, PublisherZip3Test::combine)).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        int i = 3;
        double d = 10.23;
        short s = 88;
        firstSubscription.awaitRequestN(1);
        secondSubscription.awaitRequestN(1);
        thirdSubscription.awaitRequestN(1);
        if (inOrderCompletion) {
            first.onNext(i);
            second.onNext(d);
            if (onError) {
                third.onError(DELIBERATE_EXCEPTION);
            } else {
                first.onComplete();
                second.onComplete();
                third.onComplete();
            }
        } else {
            third.onNext(s);
            second.onNext(d);
            if (onError) {
                first.onError(DELIBERATE_EXCEPTION);
            } else {
                third.onComplete();
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
        toSource(zip(first, second, third, PublisherZip3Test::combine, 1)).subscribe(subscriber);
        subscriber.awaitSubscription().request(Long.MAX_VALUE);
        int i = 3;
        double d = 10.23;
        short s = 88;
        firstSubscription.awaitRequestN(1);
        assertThat(firstSubscription.requested(), equalTo(1L));
        secondSubscription.awaitRequestN(1);
        assertThat(secondSubscription.requested(), equalTo(1L));
        thirdSubscription.awaitRequestN(1);
        assertThat(thirdSubscription.requested(), equalTo(1L));

        if (inOrderCompletion) {
            first.onNext(i);
            assertThat(firstSubscription.requested(), equalTo(1L));
            second.onNext(d);
            assertThat(secondSubscription.requested(), equalTo(1L));
            third.onNext(s);
        } else {
            third.onNext(s);
            assertThat(thirdSubscription.requested(), equalTo(1L));
            second.onNext(d);
            assertThat(secondSubscription.requested(), equalTo(1L));
            first.onNext(i);
        }
        assertThat(subscriber.takeOnNext(), equalTo(combine(i, d, s)));

        firstSubscription.awaitRequestN(2);
        assertThat(firstSubscription.requested(), equalTo(2L));
        secondSubscription.awaitRequestN(2);
        assertThat(secondSubscription.requested(), equalTo(2L));
        thirdSubscription.awaitRequestN(2);
        assertThat(thirdSubscription.requested(), equalTo(2L));

        i = 5;
        d = 6.7;
        s = 12;
        if (inOrderCompletion) {
            first.onNext(i);
            second.onNext(d);
            third.onNext(s);
        } else {
            third.onNext(s);
            second.onNext(d);
            first.onNext(i);
        }
        assertThat(subscriber.takeOnNext(), equalTo(combine(i, d, s)));
        terminateSubscribers(inOrderCompletion, onError);
    }

    @ParameterizedTest(name = "{displayName} [{index}] begin={0} end={1}")
    @CsvSource(value = {"0,2", "0,4", "0,10"})
    void reentry(int begin, int end) throws InterruptedException {
        final String completeSignal = "complete";
        final BlockingQueue<Object> signals = new LinkedBlockingQueue<>();
        toSource(zip(fromSource(new ReentryPublisher(begin, end)),
                fromSource(new ReentryPublisher(begin, end)),
                fromSource(new ReentryPublisher(begin, end)),
                PublisherZip3Test::combineReentry, 1))
                .subscribe(new PublisherSource.Subscriber<String>() {
                    @Nullable
                    private PublisherSource.Subscription subscription;

                    @Override
                    public void onSubscribe(PublisherSource.Subscription s) {
                        subscription = s;
                        // Request more than 1 bcz a potential reentry condition is triggered from inside the zip
                        // operator due to how demand is managed, and we want pending downstream demand to allow for
                        // reentry delivery.
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
            assertThat(signal, equalTo(combineReentry(i, i, i)));
        }
        signal = signals.take();
        assertThat(signal, is(completeSignal));
        assertThat(signals.isEmpty(), equalTo(true));
    }

    @ParameterizedTest(name = "{displayName} [{index}] inOrderCompletion={0} onError={1}")
    @CsvSource(value = {"true,true", "true,false", "false,true", "false,false"})
    void delayError(boolean inOrderCompletion, boolean onError) throws InterruptedException {
        toSource(zipDelayError(first, second, third, PublisherZip3Test::combine)).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        int i = 3;
        double d = 10.23;
        short s = 88;
        firstSubscription.awaitRequestN(1);
        secondSubscription.awaitRequestN(1);
        thirdSubscription.awaitRequestN(1);
        DeliberateException e1 = new DeliberateException();
        DeliberateException e2 = new DeliberateException();
        DeliberateException e3 = new DeliberateException();
        if (inOrderCompletion) {
            first.onNext(i);
            second.onNext(d);
            third.onError(e1);
        } else {
            third.onNext(s);
            second.onNext(d);
            first.onError(e1);
        }
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), nullValue());
        if (inOrderCompletion) {
            if (onError) {
                first.onError(e2);
                second.onError(e3);
            } else {
                first.onComplete();
                second.onComplete();
            }
        } else if (onError) {
            third.onError(e2);
            second.onError(e3);
        } else {
            third.onComplete();
            second.onComplete();
        }
        assertThat(subscriber.awaitOnError(), is(e1));
        if (onError) {
            assertThat(asList(e1.getSuppressed()), contains(e2, e3));
        }
    }

    private void terminateSubscribers(boolean inOrderCompletion, boolean onError) {
        if (onError) {
            if (inOrderCompletion) {
                first.onError(DELIBERATE_EXCEPTION);
            } else {
                third.onError(DELIBERATE_EXCEPTION);
            }
            assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
        } else {
            if (inOrderCompletion) {
                first.onComplete();
                second.onComplete();
                third.onComplete();
            } else {
                third.onComplete();
                second.onComplete();
                first.onComplete();
            }
            subscriber.awaitOnComplete();
        }
    }

    private static String combineReentry(int i, int d, int s) {
        return "first: " + i + " second: " + d + " third: " + s;
    }

    private static String combine(int i, double d, short s) {
        return "int: " + i + " double: " + d + " short: " + s;
    }
}
