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
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.concurrent.test.internal.TestSingleSubscriber;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import static io.servicetalk.concurrent.api.Single.zip;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class SingleZip4Test {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    private final TestSingle<Integer> first = new TestSingle<>();
    private final TestSingle<Double> second = new TestSingle<>();
    private final TestSingle<Short> third = new TestSingle<>();
    private final TestSingle<Byte> fourth = new TestSingle<>();
    private final TestSingleSubscriber<String> subscriber = new TestSingleSubscriber<>();

    @Test
    public void completeInOrder() {
        allComplete(true);
    }

    @Test
    public void completeOutOfOrder() {
        allComplete(false);
    }

    private void allComplete(boolean inOrderCompletion) {
        toSource(zip(first, second, third, fourth, SingleZip4Test::combine)).subscribe(subscriber);
        subscriber.awaitSubscription();
        int i = 3;
        double d = 10.23;
        short s = 88;
        byte b = 9;
        if (inOrderCompletion) {
            first.onSuccess(i);
            second.onSuccess(d);
            third.onSuccess(s);
            fourth.onSuccess(b);
        } else {
            fourth.onSuccess(b);
            third.onSuccess(s);
            second.onSuccess(d);
            first.onSuccess(i);
        }
        assertThat(subscriber.awaitOnSuccess(), is(combine(i, d, s, b)));
    }

    @Test
    public void justErrorFirst() {
        justError(1);
    }

    @Test
    public void justErrorSecond() {
        justError(2);
    }

    @Test
    public void justErrorThird() {
        justError(3);
    }

    @Test
    public void justErrorForth() {
        justError(4);
    }

    private void justError(int errorNum) {
        toSource(zip(first, second, third, fourth, SingleZip4Test::combine)).subscribe(subscriber);
        subscriber.awaitSubscription();
        if (errorNum == 1) {
            first.onError(DELIBERATE_EXCEPTION);
        } else if (errorNum == 2) {
            second.onError(DELIBERATE_EXCEPTION);
        } else if (errorNum == 3) {
            third.onError(DELIBERATE_EXCEPTION);
        } else {
            fourth.onError(DELIBERATE_EXCEPTION);
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
        toSource(zip(first, second, third, fourth, SingleZip4Test::combine)).subscribe(subscriber);
        subscriber.awaitSubscription();
        if (inOrderCompletion) {
            first.onSuccess(10);
            second.onSuccess(22.2);
            third.onSuccess((short) 22);
            fourth.onError(DELIBERATE_EXCEPTION);
        } else {
            fourth.onSuccess((byte) 9);
            third.onSuccess((short) 22);
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
        TestCancellable cancellable3 = new TestCancellable();
        TestSingle<Short> third = new TestSingle.Builder<Short>().disableAutoOnSubscribe().build(subscriber1 -> {
            subscriber1.onSubscribe(cancellable3);
            return subscriber1;
        });
        TestCancellable cancellable4 = new TestCancellable();
        TestSingle<Byte> fourth = new TestSingle.Builder<Byte>().disableAutoOnSubscribe().build(subscriber1 -> {
            subscriber1.onSubscribe(cancellable4);
            return subscriber1;
        });
        toSource(zip(first, second, third, fourth, SingleZip4Test::combine)).subscribe(subscriber);
        subscriber.awaitSubscription().cancel();
        cancellable1.awaitCancelled();
        cancellable2.awaitCancelled();
        cancellable3.awaitCancelled();
        cancellable4.awaitCancelled();
    }

    @Test
    public void cancelAfterCompleteOutOfOrder() throws InterruptedException {
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
        TestCancellable cancellable4 = new TestCancellable();
        TestSingle<Byte> fourth = new TestSingle.Builder<Byte>().disableAutoOnSubscribe().build(subscriber1 -> {
            subscriber1.onSubscribe(cancellable4);
            return subscriber1;
        });
        toSource(zip(first, second, third, fourth, SingleZip4Test::combine)).subscribe(subscriber);
        Cancellable c = subscriber.awaitSubscription();
        second.onSuccess(10.1);
        c.cancel();
        cancellable1.awaitCancelled();
        cancellable3.awaitCancelled();
        cancellable4.awaitCancelled();
    }

    private static String combine(int i, double d, short s, byte b) {
        return "int: " + i + " double: " + d + " short: " + s + " byte: " + b;
    }
}
