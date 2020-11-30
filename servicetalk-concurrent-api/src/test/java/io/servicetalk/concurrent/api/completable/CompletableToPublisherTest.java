/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.ExecutorRule;
import io.servicetalk.concurrent.api.TestCancellable;
import io.servicetalk.concurrent.api.TestCompletable;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.lang.Thread.currentThread;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

public class CompletableToPublisherTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExecutorRule executorRule = ExecutorRule.newRule();

    private TestPublisherSubscriber<String> subscriber = new TestPublisherSubscriber<>();

    @Test
    public void invalidRequestNCancelsCompletable() {
        TestCompletable completable = new TestCompletable.Builder().disableAutoOnSubscribe().build();
        toSource(completable.<String>toPublisher()).subscribe(subscriber);
        TestCancellable cancellable = new TestCancellable();
        completable.onSubscribe(cancellable);
        subscriber.awaitSubscription().request(-1);
        assertThat(subscriber.awaitOnError(), is(instanceOf(IllegalArgumentException.class)));
        assertThat("Completable not cancelled for invalid request-n", cancellable.isCancelled(), is(true));
    }

    @Test
    public void noTerminalSucceeds() {
        toSource(Completable.completed().<String>toPublisher()).subscribe(subscriber);
        subscriber.awaitSubscription().request(1);
        subscriber.awaitOnComplete();
    }

    @Test
    public void subscribeOnOriginalIsPreserved() throws Exception {
        final Thread testThread = currentThread();
        final CountDownLatch analyzed = new CountDownLatch(1);
        ConcurrentLinkedQueue<AssertionError> errors = new ConcurrentLinkedQueue<>();
        TestCompletable completable = new TestCompletable.Builder().disableAutoOnSubscribe().build();
        TestPublisherSubscriber<String> subscriber = new TestPublisherSubscriber<>();
        toSource(completable.beforeCancel(() -> {
            if (currentThread() == testThread) {
                errors.add(new AssertionError("Invalid thread invoked cancel. Thread: " +
                        currentThread()));
            }
        })
                .afterCancel(analyzed::countDown)
                .subscribeOn(executorRule.executor())
                .<String>toPublisher())
                .subscribe(subscriber);
        TestCancellable cancellable = new TestCancellable();
        completable.onSubscribe(cancellable); // waits till subscribed.
        assertThat("Completable not subscribed.", completable.isSubscribed(), is(true));
        subscriber.awaitSubscription().cancel();
        analyzed.await();
        assertThat("Completable did not get a cancel.", cancellable.isCancelled(), is(true));
        assertThat("Unexpected errors observed: " + errors, errors, hasSize(0));
    }

    @Test
    public void publishOnOriginalIsPreservedOnComplete() throws Exception {
        ConcurrentLinkedQueue<AssertionError> errors = new ConcurrentLinkedQueue<>();
        TestPublisherSubscriber<String> subscriber = new TestPublisherSubscriber<>();
        TestCompletable completable = new TestCompletable();
        CountDownLatch analyzed = publishOnOriginalIsPreserved0(errors, subscriber, completable);
        completable.onComplete();
        analyzed.await();
        assertThat("Unexpected errors observed: " + errors, errors, hasSize(0));
        subscriber.awaitOnComplete();
    }

    @Test
    public void publishOnOriginalIsPreservedOnError() throws Exception {
        ConcurrentLinkedQueue<AssertionError> errors = new ConcurrentLinkedQueue<>();
        TestPublisherSubscriber<String> subscriber = new TestPublisherSubscriber<>();
        TestCompletable completable = new TestCompletable();
        CountDownLatch analyzed = publishOnOriginalIsPreserved0(errors, subscriber, completable);
        completable.onError(DELIBERATE_EXCEPTION);
        analyzed.await();
        assertThat("Unexpected errors observed: " + errors, errors, hasSize(0));
        Throwable err = subscriber.awaitOnError();
        assertThat("Wrong error received.", err, is(sameInstance(DELIBERATE_EXCEPTION)));
    }

    @Test
    public void publishOnOriginalIsPreservedOnInvalidRequestN() throws Exception {
        ConcurrentLinkedQueue<AssertionError> errors = new ConcurrentLinkedQueue<>();
        TestPublisherSubscriber<String> subscriber = new TestPublisherSubscriber<>();
        TestCompletable completable = new TestCompletable();
        CountDownLatch analyzed = publishOnOriginalIsPreserved0(errors, subscriber, completable);
        subscriber.awaitSubscription().request(-1);
        analyzed.await();
        assertThat("Unexpected errors observed: " + errors, errors, hasSize(0));
        Throwable err = subscriber.awaitOnError();
        assertThat("Wrong error received.", err, is(instanceOf(IllegalArgumentException.class)));
    }

    private CountDownLatch publishOnOriginalIsPreserved0(final ConcurrentLinkedQueue<AssertionError> errors,
                                                         final TestPublisherSubscriber<String> subscriber,
                                                         final TestCompletable completable) {
        final Thread testThread = currentThread();
        CountDownLatch analyzed = new CountDownLatch(1);
        CountDownLatch receivedOnSubscribe = new CountDownLatch(1);
        toSource(completable.publishOn(executorRule.executor())
                .beforeOnComplete(() -> {
                    if (currentThread() == testThread) {
                        errors.add(new AssertionError("Invalid thread invoked onComplete " +
                                "(from Completable). Thread: " + currentThread()));
                    }
                })
                .beforeOnError(__ -> {
                    if (currentThread() == testThread) {
                        errors.add(new AssertionError("Invalid thread invoked onError" +
                                "(from Completable). Thread: " + currentThread()));
                    }
                })
                .<String>toPublisher()
                .beforeOnComplete(() -> {
                    if (currentThread() == testThread) {
                        errors.add(new AssertionError("Invalid thread invoked onComplete " +
                                "(from Publisher). Thread: " + currentThread()));
                    }
                })
                .beforeOnError(__ -> {
                    if (currentThread() == testThread) {
                        errors.add(new AssertionError("Invalid thread invoked onError " +
                                "(from Publisher). Thread: " + currentThread()));
                    }
                })
                .afterOnComplete(analyzed::countDown)
                .afterOnError(__ -> analyzed.countDown())
                .afterOnSubscribe(__ -> receivedOnSubscribe.countDown())
        )
                .subscribe(subscriber);
        assertThat("Completable not subscribed.", completable.isSubscribed(), is(true));
        return analyzed;
    }
}
