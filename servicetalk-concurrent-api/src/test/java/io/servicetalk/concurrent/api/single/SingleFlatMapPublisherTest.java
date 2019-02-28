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
package io.servicetalk.concurrent.api.single;

import io.servicetalk.concurrent.api.ExecutorRule;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestPublisherSubscriber;
import io.servicetalk.concurrent.api.TestSingle;
import io.servicetalk.concurrent.api.TestSubscription;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.error;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.lang.Thread.currentThread;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class SingleFlatMapPublisherTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExecutorRule executorRule = ExecutorRule.newRule();

    private final TestPublisherSubscriber<String> subscriber = new TestPublisherSubscriber<>();
    private final TestPublisher<String> publisher = new TestPublisher.Builder<String>()
            .disableAutoOnSubscribe().build();
    private final TestSingle<String> single = new TestSingle<>();
    private final TestSubscription subscription = new TestSubscription();

    @Test
    public void testFirstAndSecondPropagate() {
        toSource(success(1).flatMapPublisher(s1 -> from(new String[]{"Hello1", "Hello2"}).map(str1 -> str1 + s1)))
                .subscribe(subscriber);
        subscriber.request(2);
        assertThat(subscriber.items(), contains("Hello11", "Hello21"));
        assertTrue(subscriber.isCompleted());
    }

    @Test
    public void testSuccess() {
        toSource(success(1).flatMapPublisher(s1 -> publisher)).subscribe(subscriber);
        subscriber.request(2);
        publisher.onNext("Hello1", "Hello2");
        publisher.onComplete();
        assertThat(subscriber.items(), contains("Hello1", "Hello2"));
        assertTrue(subscriber.isCompleted());
    }

    @Test
    public void testPublisherEmitsError() {
        toSource(success(1).flatMapPublisher(s1 -> publisher)).subscribe(subscriber);
        subscriber.request(1);
        publisher.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.error(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testSingleEmitsError() {
        toSource(error(DELIBERATE_EXCEPTION).flatMapPublisher(s1 -> publisher)).subscribe(subscriber);
        subscriber.request(1);
        assertFalse(publisher.isSubscribed());
        assertThat(subscriber.error(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testCancelBeforeNextPublisher() {
        toSource(single.flatMapPublisher(s1 -> publisher)).subscribe(subscriber);
        subscriber.request(2);
        subscriber.cancel();
        assertThat("Original single not cancelled.", single.isCancelled(), is(true));
    }

    @Test
    public void testCancelNoRequest() {
        toSource(single.flatMapPublisher(s -> publisher)).subscribe(subscriber);
        subscriber.cancel();
        subscriber.request(1);
        single.verifyListenNotCalled();
    }

    @Test
    public void testCancelBeforeOnSubscribe() {
        toSource(single.flatMapPublisher(s1 -> publisher)).subscribe(subscriber);
        subscriber.request(2);
        single.onSuccess("Hello");
        subscriber.cancel();
        single.verifyCancelled();
        publisher.onSubscribe(subscription);
        assertTrue(subscription.isCancelled());
        assertTrue(subscriber.subscriptionReceived());
        assertThat(subscriber.items(), hasSize(0));
        assertFalse(subscriber.isTerminated());
    }

    @Test
    public void testCancelPostOnSubscribe() {
        toSource(success(1).flatMapPublisher(s1 -> publisher)).subscribe(subscriber);
        subscriber.request(2);
        publisher.onSubscribe(subscription);
        subscriber.cancel();
        assertTrue(subscription.isCancelled());
    }

    @Test
    public void exceptionInTerminalCallsOnError() {
        toSource(success(1).<String>flatMapPublisher(s1 -> {
            throw DELIBERATE_EXCEPTION;
        })).subscribe(subscriber);
        subscriber.request(2);
        single.onSuccess("Hello");
        assertThat(subscriber.error(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void nullInTerminalCallsOnError() {
        toSource(success(1).<String>flatMapPublisher(s1 -> null)).subscribe(subscriber);
        subscriber.request(2);
        single.onSuccess("Hello");
        assertThat(subscriber.error(), instanceOf(NullPointerException.class));
    }

    @Test
    public void subscribeOnOriginalIsPreserved() throws Exception {
        final Thread testThread = currentThread();
        final CountDownLatch analyzed = new CountDownLatch(1);
        ConcurrentLinkedQueue<AssertionError> errors = new ConcurrentLinkedQueue<>();
        Single.never()
                .doBeforeCancel(() -> {
                    if (currentThread() == testThread) {
                        errors.add(new AssertionError("Invalid thread invoked cancel. Thread: " +
                                currentThread()));
                    }
                    analyzed.countDown();
                })
                .subscribeOn(executorRule.executor())
                .flatMapPublisher(t -> Publisher.never())
                .forEach(__ -> { }).cancel();
        analyzed.await();
        assertThat("Unexpected errors observed: " + errors, errors, hasSize(0));
    }
}
