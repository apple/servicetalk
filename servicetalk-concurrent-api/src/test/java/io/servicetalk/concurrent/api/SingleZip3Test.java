/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.internal.DeliberateException;
import io.servicetalk.concurrent.test.internal.TestSingleSubscriber;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.servicetalk.concurrent.api.Single.zip;
import static io.servicetalk.concurrent.api.Single.zipDelayError;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class SingleZip3Test {
    private TestSingle<Integer> first;
    private TestSingle<Double> second;
    private TestSingle<Short> third;
    private TestSingleSubscriber<String> subscriber;

    @BeforeEach
    void setUp() {
        first = new TestSingle<>();
        second = new TestSingle<>();
        third = new TestSingle<>();
        subscriber = new TestSingleSubscriber<>();
    }

    @Test
    void completeInOrder() {
        allComplete(true);
    }

    @Test
    void completeOutOfOrder() {
        allComplete(false);
    }

    private void allComplete(boolean inOrderCompletion) {
        toSource(zip(first, second, third, SingleZip3Test::combine)).subscribe(subscriber);
        subscriber.awaitSubscription();
        int i = 3;
        double d = 10.23;
        short s = 88;
        if (inOrderCompletion) {
            first.onSuccess(i);
            second.onSuccess(d);
            third.onSuccess(s);
        } else {
            third.onSuccess(s);
            second.onSuccess(d);
            first.onSuccess(i);
        }
        assertThat(subscriber.awaitOnSuccess(), is(combine(i, d, s)));
    }

    @Test
    void justErrorFirst() {
        justError(1);
    }

    @Test
    void justErrorSecond() {
        justError(2);
    }

    @Test
    void justErrorThird() {
        justError(3);
    }

    private void justError(int errorNum) {
        toSource(zip(first, second, third, SingleZip3Test::combine)).subscribe(subscriber);
        subscriber.awaitSubscription();
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
    void errorAfterCompleteInOrder() {
        errorAfterComplete(true);
    }

    @Test
    void errorAfterCompleteOutOfOrder() {
        errorAfterComplete(false);
    }

    private void errorAfterComplete(boolean inOrderCompletion) {
        toSource(zip(first, second, third, SingleZip3Test::combine)).subscribe(subscriber);
        subscriber.awaitSubscription();
        if (inOrderCompletion) {
            first.onSuccess(10);
            second.onSuccess(22.2);
            third.onError(DELIBERATE_EXCEPTION);
        } else {
            third.onSuccess((short) 22);
            second.onSuccess(10.1);
            first.onError(DELIBERATE_EXCEPTION);
        }
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void justCancel() throws InterruptedException {
        TestCancellable cancellable1 = new TestCancellable();
        TestSingle<Integer> first = new TestSingle.Builder<Integer>().disableAutoOnSubscribe().build(subscriber1 -> {
            subscriber1.onSubscribe(cancellable1);
            return subscriber1;
        });
        TestCancellable cancellable2 = new TestCancellable();
        TestSingle<Double> second = new TestSingle.Builder<Double>().disableAutoOnSubscribe().build(subscriber1 -> {
            subscriber1.onSubscribe(cancellable2);
            return subscriber1;
        });
        TestCancellable cancellable3 = new TestCancellable();
        TestSingle<Short> third = new TestSingle.Builder<Short>().disableAutoOnSubscribe().build(subscriber1 -> {
            subscriber1.onSubscribe(cancellable3);
            return subscriber1;
        });
        toSource(zip(first, second, third, SingleZip3Test::combine)).subscribe(subscriber);
        subscriber.awaitSubscription().cancel();
        cancellable1.awaitCancelled();
        cancellable2.awaitCancelled();
        cancellable3.awaitCancelled();
    }

    @Test
    void cancelAfterCompleteOutOfOrder() throws InterruptedException {
        TestCancellable cancellable1 = new TestCancellable();
        TestSingle<Integer> first = new TestSingle.Builder<Integer>().disableAutoOnSubscribe().build(subscriber1 -> {
            subscriber1.onSubscribe(cancellable1);
            return subscriber1;
        });
        TestCancellable cancellable3 = new TestCancellable();
        TestSingle<Short> third = new TestSingle.Builder<Short>().disableAutoOnSubscribe().build(subscriber1 -> {
            subscriber1.onSubscribe(cancellable3);
            return subscriber1;
        });
        toSource(zip(first, second, third, SingleZip3Test::combine)).subscribe(subscriber);
        Cancellable c = subscriber.awaitSubscription();
        second.onSuccess(10.1);
        c.cancel();
        cancellable1.awaitCancelled();
        cancellable3.awaitCancelled();
    }

    @Test
    void delayErrorOneFail() {
        toSource(zipDelayError(first, second, third, SingleZip3Test::combine)).subscribe(subscriber);
        subscriber.awaitSubscription();
        DeliberateException e1 = new DeliberateException();
        second.onError(e1);
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), nullValue());
        first.onSuccess(1);
        third.onSuccess((short) 22);
        assertThat(subscriber.awaitOnError(), is(e1));
    }

    @Test
    void delayErrorAllFail() {
        toSource(zipDelayError(first, second, third, SingleZip3Test::combine)).subscribe(subscriber);
        subscriber.awaitSubscription();
        DeliberateException e1 = new DeliberateException();
        second.onError(e1);
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), nullValue());
        DeliberateException e2 = new DeliberateException();
        first.onError(e2);
        DeliberateException e3 = new DeliberateException();
        third.onError(e3);
        assertThat(subscriber.awaitOnError(), is(e1));
        assertThat(asList(e1.getSuppressed()), contains(e2, e3));
    }

    private static String combine(int i, double d, short s) {
        return "int: " + i + " double: " + d + " short: " + s;
    }
}
