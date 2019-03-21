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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.ExecutorRule;
import io.servicetalk.concurrent.api.TestCompletable;
import io.servicetalk.concurrent.api.TestPublisherSubscriber;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.concurrent.internal.TerminalNotification;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static java.lang.Thread.currentThread;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CompletableToPublisherTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExecutorRule executorRule = ExecutorRule.newRule();

    private TestPublisherSubscriber<String> subscriber = new TestPublisherSubscriber<>();

    @Test
    public void noTerminalSucceeds() {
        toSource(Completable.completed().<String>toPublisher()).subscribe(subscriber);
        subscriber.request(1);
        assertThat(subscriber.takeTerminal(), is(complete()));
    }

    @Test
    public void subscribeOnOriginalIsPreserved() throws Exception {
        final Thread testThread = currentThread();
        final CountDownLatch completableSubscribed = new CountDownLatch(1);
        final CountDownLatch analyzed = new CountDownLatch(1);
        ConcurrentLinkedQueue<AssertionError> errors = new ConcurrentLinkedQueue<>();
        Cancellable c = Completable.never()
                .doAfterSubscribe(__ -> completableSubscribed.countDown())
                .doBeforeCancel(() -> {
                    if (currentThread() == testThread) {
                        errors.add(new AssertionError("Invalid thread invoked cancel. Thread: " +
                                currentThread()));
                    }
                    analyzed.countDown();
                })
                .subscribeOn(executorRule.executor())
                .toPublisher()
                .forEach(__ -> { });
        // toPublisher does not subscribe to the Completable, till data is requested. Since subscription is offloaded,
        // cancel may be called before request-n is sent to the offloaded subscription, which would ignore request-n
        // and only propagate cancel. In such a case, original Completable will not be subscribed and hence
        // doBeforeCancel above may never be invoked.
        completableSubscribed.await();
        c.cancel();
        analyzed.await();
        assertThat("Unexpected errors observed: " + errors, errors, hasSize(0));
    }

    @Test
    public void sequentialOnSubscribeOnCompleteInOrder() {
        sequentialOnSubscribeInOrder(true);
    }

    @Test
    public void sequentialOnSubscribeOnErrorInOrder() {
        sequentialOnSubscribeInOrder(false);
    }

    private void sequentialOnSubscribeInOrder(boolean onComplete) {
        TestCompletable completable = new TestCompletable();
        toSource(completable.<String>toPublisher()).subscribe(subscriber);
        assertTrue(completable.isSubscribed());
        if (onComplete) {
            completable.onComplete();
            assertTrue(subscriber.isCompleted());
        } else {
            completable.onError(DELIBERATE_EXCEPTION);
            assertEquals(DELIBERATE_EXCEPTION, subscriber.takeError());
        }
    }

    @Test
    public void invalidRequestNBeforeOnCompleteResultsInOnError() {
        invalidRequestNBeforeTerminateResultsInOnError(true);
    }

    @Test
    public void invalidRequestNBeforeOnErrorResultsInOnError() {
        invalidRequestNBeforeTerminateResultsInOnError(false);
    }

    private void invalidRequestNBeforeTerminateResultsInOnError(boolean onComplete) {
        TestCompletable completable = new TestCompletable();
        toSource(completable.<String>toPublisher()).subscribe(subscriber);
        assertTrue(completable.isSubscribed());
        subscriber.request(-1);
        if (onComplete) {
            completable.onComplete();
        } else {
            completable.onError(DELIBERATE_EXCEPTION);
        }
        assertThat(subscriber.takeError(), instanceOf(IllegalArgumentException.class));
    }

    @Test
    public void invalidRequestNAfterOnCompleteIgnored() {
        invalidRequestNAfterTerminateIgnored(true);
    }

    @Test
    public void invalidRequestNAfterOnErrorIgnored() {
        invalidRequestNAfterTerminateIgnored(false);
    }

    private void invalidRequestNAfterTerminateIgnored(boolean onComplete) {
        TestCompletable completable = new TestCompletable();
        toSource(completable.<String>toPublisher()).subscribe(subscriber);
        assertTrue(completable.isSubscribed());
        if (onComplete) {
            completable.onComplete();
            assertEquals(TerminalNotification.complete(), subscriber.takeTerminal());
        } else {
            completable.onError(DELIBERATE_EXCEPTION);
            assertEquals(DELIBERATE_EXCEPTION, subscriber.takeError());
        }
        subscriber.request(-1);
        assertNull(subscriber.takeTerminal());
    }
}
