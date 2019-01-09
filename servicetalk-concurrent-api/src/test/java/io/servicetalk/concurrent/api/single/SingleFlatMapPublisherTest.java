/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.MockedSubscriberRule;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestSingle;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.error;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.lang.Thread.currentThread;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

public final class SingleFlatMapPublisherTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExecutorRule executorRule = new ExecutorRule();
    @Rule
    public final MockedSubscriberRule<String> subscriber = new MockedSubscriberRule<>();

    private final TestPublisher<String> publisher = new TestPublisher<>();
    private final TestSingle<String> single = new TestSingle<>();

    @Test
    public void testFirstAndSecondPropagate() {
        subscriber.subscribe(success(1).flatMapPublisher(s -> from(new String[]{"Hello1", "Hello2"}).map(str -> str + s))).request(2);
        subscriber.verifySuccess("Hello11", "Hello21");
    }

    @Test
    public void testSuccess() {
        subscriber.subscribe(success(1).flatMapPublisher(s -> publisher.sendOnSubscribe())).request(2);
        publisher.sendItems("Hello1", "Hello2").onComplete();
        subscriber.verifySuccess("Hello1", "Hello2");
    }

    @Test
    public void testPublisherEmitsError() {
        subscriber.subscribe(success(1).flatMapPublisher(s -> publisher.sendOnSubscribe())).request(1);
        publisher.fail();
        subscriber.verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testSingleEmitsError() {
        subscriber.subscribe(error(DELIBERATE_EXCEPTION).flatMapPublisher(s -> publisher.sendOnSubscribe())).request(1);
        subscriber.verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testCancelBeforeNextPublisher() {
        subscriber.subscribe(single.flatMapPublisher(s -> publisher)).request(2);
        subscriber.cancel();
        assertThat("Original single not cancelled.", single.isCancelled(), is(true));
    }

    @Test
    public void testCancelNoRequest() {
        subscriber.subscribe(single.flatMapPublisher(s -> publisher));
        subscriber.cancel();
        subscriber.request(1);
        single.verifyListenNotCalled();
    }

    @Test
    public void testCancelBeforeOnSubscribe() {
        subscriber.subscribe(single.flatMapPublisher(s -> publisher)).request(2);
        single.onSuccess("Hello");
        subscriber.cancel();
        single.verifyCancelled();
        publisher.sendOnSubscribe().verifyCancelled();
        subscriber.verifyNoEmissions();
    }

    @Test
    public void testCancelPostOnSubscribe() {
        subscriber.subscribe(success(1).flatMapPublisher(s -> publisher.sendOnSubscribe())).request(2);
        subscriber.cancel();
        publisher.verifyCancelled();
    }

    @Test
    public void exceptionInTerminalCallsOnError() {
        subscriber.subscribe(success(1).flatMapPublisher(s -> {
            throw DELIBERATE_EXCEPTION;
        })).request(2);
        single.onSuccess("Hello");
        subscriber.verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void nullInTerminalCallsOnError() {
        subscriber.subscribe(success(1).flatMapPublisher(s -> null)).request(2);
        single.onSuccess("Hello");
        subscriber.verifyFailure(NullPointerException.class);
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
                .subscribeOn(executorRule.getExecutor())
                .flatMapPublisher(t -> Publisher.never())
                .forEach(__ -> { }).cancel();
        analyzed.await();
        assertThat("Unexpected errors observed: " + errors, errors, hasSize(0));
    }
}
