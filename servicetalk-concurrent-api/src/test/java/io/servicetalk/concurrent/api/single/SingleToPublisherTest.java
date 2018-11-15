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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.ExecutorRule;
import io.servicetalk.concurrent.api.MockedSubscriberRule;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.lang.Thread.currentThread;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

public class SingleToPublisherTest {

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExecutorRule executorRule = new ExecutorRule();
    @Rule
    public MockedSubscriberRule<String> verifier = new MockedSubscriberRule<>();

    @Test
    public void testSuccessfulFuture() {
        verifier.subscribe(Single.success("Hello")).verifySuccess("Hello");
    }

    @Test
    public void testFailedFuture() {
        verifier.subscribe(Single.error(DELIBERATE_EXCEPTION)).requestAndVerifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testCancelBeforeRequest() {
        verifier.subscribe(Single.success("Hello")).cancel().verifyNoEmissions();
    }

    @Test
    public void testCancelAfterRequest() {
        verifier.subscribe(Single.success("Hello")).verifySuccess("Hello").cancel();
    }

    @Test
    public void testInvalidRequestN() {
        verifier.subscribe(Single.success("Hello")).request(-1).verifyFailure(IllegalArgumentException.class);
    }

    @Test
    public void exceptionInTerminalCallsOnError() {
        verifier.subscribe(Single.success("Hello"));
        // The mock behavior must be applied after subscribe, because a new mock is created as part of this process.
        doAnswer(invocation -> {
            throw DELIBERATE_EXCEPTION;
        }).when(verifier.getSubscriber()).onNext(any());
        verifier.request(1).verifyItems("Hello").verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void subscribeOnOriginalIsPreserved() throws Exception {
        final Thread testThread = currentThread();
        final CountDownLatch singleSubscribed = new CountDownLatch(1);
        final CountDownLatch analyzed = new CountDownLatch(1);
        ConcurrentLinkedQueue<AssertionError> errors = new ConcurrentLinkedQueue<>();
        Cancellable c = Single.never()
                .doAfterSubscribe(__ -> singleSubscribed.countDown())
                .doBeforeCancel(() -> {
                    if (currentThread() == testThread) {
                        errors.add(new AssertionError("Invalid thread invoked cancel. Thread: " +
                                currentThread()));
                    }
                    analyzed.countDown();
                })
                .subscribeOn(executorRule.getExecutor())
                .toPublisher()
                .forEach(__ -> { });
        // toPublisher does not subscribe to the Single, till data is requested. Since subscription is offloaded,
        // cancel may be called before request-n is sent to the offloaded subscription, which would ignore request-n
        // and only propagate cancel. In such a case, original Single will not be subscribed and hence doBeforeCancel
        // above may never be invoked.
        singleSubscribed.await();
        c.cancel();
        analyzed.await();
        assertThat("Unexpected errors observed: " + errors, errors, hasSize(0));
    }
}
