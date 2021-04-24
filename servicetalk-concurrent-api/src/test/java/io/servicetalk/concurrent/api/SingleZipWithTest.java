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

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

public class SingleZipWithTest {
    private TestSingle<Integer> first;
    private TestSingle<Double> second;
    private TestSingleSubscriber<String> subscriber;

    @BeforeEach
    public void setUp() {
        first = new TestSingle<>();
        second = new TestSingle<>();
        subscriber = new TestSingleSubscriber<>();
    }

    @Test
    public void bothCompleteInOrder() {
        bothComplete(true);
    }

    @Test
    public void bothCompleteOutOfOrder() {
        bothComplete(false);
    }

    private void bothComplete(boolean inOrderCompletion) {
        toSource(first.zipWith(second, SingleZipWithTest::combine)).subscribe(subscriber);
        subscriber.awaitSubscription();
        int i = 3;
        double d = 10.23;
        if (inOrderCompletion) {
            first.onSuccess(i);
            second.onSuccess(d);
        } else {
            second.onSuccess(d);
            first.onSuccess(i);
        }
        assertThat(subscriber.awaitOnSuccess(), is(combine(i, d)));
    }

    @Test
    public void justErrorFirst() {
        justError(true);
    }

    @Test
    public void justErrorSecond() {
        justError(false);
    }

    private void justError(boolean errorFirst) {
        toSource(first.zipWith(second, SingleZipWithTest::combine)).subscribe(subscriber);
        subscriber.awaitSubscription();
        if (errorFirst) {
            first.onError(DELIBERATE_EXCEPTION);
        } else {
            second.onError(DELIBERATE_EXCEPTION);
        }
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    public void errorAfterCompleteInOrder() {
        errorAfterComplete(true);
    }

    @Test
    public void errorAfterCompleteOutOfOrder() {
        errorAfterComplete(false);
    }

    private void errorAfterComplete(boolean inOrderCompletion) {
        toSource(first.zipWith(second, SingleZipWithTest::combine)).subscribe(subscriber);
        subscriber.awaitSubscription();
        if (inOrderCompletion) {
            first.onSuccess(10);
            second.onError(DELIBERATE_EXCEPTION);
        } else {
            second.onSuccess(10.1);
            first.onError(DELIBERATE_EXCEPTION);
        }
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    public void justCancel() throws InterruptedException {
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
        toSource(first.zipWith(second, SingleZipWithTest::combine)).subscribe(subscriber);
        subscriber.awaitSubscription().cancel();
        cancellable1.awaitCancelled();
        cancellable2.awaitCancelled();
    }

    @Test
    public void cancelAfterCompleteOutOfOrder() throws InterruptedException {
        TestCancellable cancellable = new TestCancellable();
        TestSingle<Integer> first = new TestSingle.Builder<Integer>().disableAutoOnSubscribe().build(subscriber1 -> {
            subscriber1.onSubscribe(cancellable);
            return subscriber1;
        });
        toSource(first.zipWith(second, SingleZipWithTest::combine)).subscribe(subscriber);
        Cancellable c = subscriber.awaitSubscription();
        second.onSuccess(10.1);
        c.cancel();
        cancellable.awaitCancelled();
    }

    @Test
    public void delayErrorOneFail() {
        toSource(first.zipWithDelayError(second, SingleZipWithTest::combine)).subscribe(subscriber);
        subscriber.awaitSubscription();
        DeliberateException e1 = new DeliberateException();
        second.onError(e1);
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), nullValue());
        first.onSuccess(1);
        assertThat(subscriber.awaitOnError(), is(e1));
    }

    @Test
    public void delayErrorAllFail() {
        toSource(first.zipWithDelayError(second, SingleZipWithTest::combine)).subscribe(subscriber);
        subscriber.awaitSubscription();
        DeliberateException e1 = new DeliberateException();
        second.onError(e1);
        assertThat(subscriber.pollTerminal(10, MILLISECONDS), nullValue());
        DeliberateException e2 = new DeliberateException();
        first.onError(e2);
        assertThat(subscriber.awaitOnError(), is(e1));
        assertThat(asList(e1.getSuppressed()), contains(e2));
    }

    private static String combine(int i, double d) {
        return "int: " + i + " double: " + d;
    }
}
