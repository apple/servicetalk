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
package io.servicetalk.concurrent.api.publisher;

import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.api.DeferredEmptySubscription;
import io.servicetalk.concurrent.api.ExecutorRule;
import io.servicetalk.concurrent.api.LegacyMockedCompletableListenerRule;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.concurrent.internal.TerminalNotification;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;

import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static java.lang.Thread.currentThread;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

public class PubToCompletableTest {

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final LegacyMockedCompletableListenerRule listenerRule = new LegacyMockedCompletableListenerRule();
    @Rule
    public final ExecutorRule executorRule = ExecutorRule.newRule();

    @Test
    public void testSuccess() {
        listen(just("Hello")).verifyCompletion();
    }

    @Test
    public void testError() {
        listen(Publisher.error(DELIBERATE_EXCEPTION)).verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testEmpty() {
        listen(Publisher.empty()).verifyCompletion();
    }

    @Test
    public void testEmptyFromRequestN() {
        listen(new Publisher<String>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super String> subscriber) {
                subscriber.onSubscribe(new DeferredEmptySubscription(subscriber, complete()));
            }
        }).verifyCompletion();
    }

    @Test
    public void testErrorFromRequestN() {
        listen(new Publisher<String>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super String> subscriber) {
                subscriber.onSubscribe(new DeferredEmptySubscription(subscriber, TerminalNotification.error(DELIBERATE_EXCEPTION)));
            }
        }).verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void subscribeOnOriginalIsPreserved() throws Exception {
        final Thread testThread = currentThread();
        final CountDownLatch analyzed = new CountDownLatch(1);
        ConcurrentLinkedQueue<AssertionError> errors = new ConcurrentLinkedQueue<>();
        just("Hello").doBeforeRequest(__ -> {
            if (currentThread() == testThread) {
                errors.add(new AssertionError("Invalid thread invoked request-n. Thread: " +
                        currentThread()));
            }
            analyzed.countDown();
        }).subscribeOn(executorRule.executor()).ignoreElements().toFuture().get();
        analyzed.await();
        assertThat("Unexpected errors observed: " + errors, errors, hasSize(0));
    }

    private LegacyMockedCompletableListenerRule listen(Publisher<String> src) {
        return listenerRule.listen(src.ignoreElements());
    }
}
