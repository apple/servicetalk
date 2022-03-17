/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.function.Function;

import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.time.Duration.ofMillis;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.sameInstance;

class PublisherFlatMapConcatSingleTest {
    private final TestPublisherSubscriber<String> subscriber = new TestPublisherSubscriber<>();
    private final TestSubscription subscription = new TestSubscription();
    private static Executor executor;

    @BeforeAll
    static void beforeClass() {
        executor = newCachedThreadExecutor();
    }

    @AfterAll
    static void afterClass() throws Exception {
        executor.closeAsync().toFuture().get();
    }

    @ParameterizedTest
    @CsvSource(value = {"0,5,10,false", "0,25,10,false", "0,5,10,true", "0,25,10,true"})
    void orderPreservedInRangeWithConcurrency(int begin, int end, int maxConcurrency, boolean delayError) {
        toSource(concatSingle(Publisher.range(begin, end),
                i -> executor.timer(ofMillis(1)).toSingle().map(__ -> i + "x"), maxConcurrency, delayError)
        ).subscribe(subscriber);
        String[] expected = expected(begin, end);
        subscriber.awaitSubscription().request(expected.length);
        assertThat(subscriber.takeOnNext(expected.length), contains(expected));
        subscriber.awaitOnComplete();
    }

    @ParameterizedTest
    @CsvSource(value = {"0,5,10,false", "0,25,10,false", "0,5,10,true", "0,25,10,true"})
    void errorPropagatedInOrderLast(int begin, int end, int maxConcurrency, boolean delayError) {
        final int endLessOne = end - 1;
        String[] expected = expected(begin, endLessOne);
        toSource(concatSingle(Publisher.range(begin, end), i -> executor.timer(ofMillis(1)).toSingle().map(__ -> {
                    if (i == endLessOne) {
                        throw DELIBERATE_EXCEPTION;
                    }
                    return i + "x";
                }), maxConcurrency, delayError)
        ).subscribe(subscriber);
        subscriber.awaitSubscription().request(end - begin);
        assertThat(subscriber.takeOnNext(expected.length), contains(expected));
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @ParameterizedTest
    @CsvSource(value = {"0,5,10,false", "0,25,10,false", "0,5,10,true", "0,25,10,true"})
    void errorPropagatedInOrderFirst(int begin, int end, int maxConcurrency, boolean delayError) {
        toSource(concatSingle(Publisher.range(begin, end), i -> executor.timer(ofMillis(1)).toSingle().map(__ -> {
                    if (i == begin) {
                        throw DELIBERATE_EXCEPTION;
                    }
                    return i + "x";
                }), maxConcurrency, delayError)
        ).subscribe(subscriber);
        subscriber.awaitSubscription().request(end - begin);
        if (delayError) {
            String[] expected = expected(begin + 1, end);
            assertThat(subscriber.takeOnNext(expected.length), contains(expected));
        }
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void cancellationPropagated(boolean delayError) throws InterruptedException {
        TestPublisher<Integer> source = new TestPublisher.Builder<Integer>().disableAutoOnSubscribe().build(
                subscriber1 -> {
            subscriber1.onSubscribe(subscription);
            return subscriber1;
        });
        final TestCancellable cancellable = new TestCancellable();
        final TestSingle<String> singleSource = new TestSingle.Builder<String>().disableAutoOnSubscribe()
                .build(subscriber1 -> {
            subscriber1.onSubscribe(cancellable);
            return subscriber1;
        });
        toSource(concatSingle(source, i -> singleSource, 2, delayError))
                .subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        subscription.awaitRequestN(1);
        source.onNext(0);
        singleSource.awaitSubscribed();
        subscriber.awaitSubscription().cancel();
        cancellable.awaitCancelled();
        subscription.awaitCancelled();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void singleTerminalThrows(boolean delayError) {
        toSource(concatSingle(Publisher.range(0, 2), i -> Single.succeeded(i + "x"), 2, delayError)
                .<String>map(x -> {
                    throw DELIBERATE_EXCEPTION;
                })
        ).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        assertThat(subscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    private static <T, R> Publisher<R> concatSingle(Publisher<T> publisher, Function<T, Single<R>> mapper,
                                                    int maxConcurrency, boolean delayError) {
        return delayError ?
                publisher.flatMapConcatSingleDelayError(mapper, maxConcurrency) :
                publisher.flatMapConcatSingle(mapper, maxConcurrency);
    }

    private static String[] expected(int begin, int end) {
        String[] expected = new String[end - begin];
        for (int i = begin; i < end; ++i) {
            expected[i - begin] = i + "x";
        }
        return expected;
    }
}
