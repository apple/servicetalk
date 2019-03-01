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
import io.servicetalk.concurrent.api.TestPublisherSubscriber;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static java.lang.Thread.currentThread;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

public class CompletableToSingleTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExecutorRule executorRule = ExecutorRule.newRule();

    private TestPublisherSubscriber<String> subscriber = new TestPublisherSubscriber<>();

    @Test
    public void noTerminalSucceeds() {
        toSource(Completable.completed().<String>toSingle()).subscribe(subscriber.forSingle());
        subscriber.request(1);
        assertThat(subscriber.takeTerminal(), is(complete()));
    }

    @Test
    public void subscribeOnOriginalIsPreserved() throws InterruptedException {
        final Thread testThread = currentThread();
        final CountDownLatch analyzed = new CountDownLatch(1);
        ConcurrentLinkedQueue<AssertionError> errors = new ConcurrentLinkedQueue<>();
        Completable.never().doBeforeCancel(() -> {
            if (currentThread() == testThread) {
                errors.add(new AssertionError("Invalid thread invoked cancel. Thread: " +
                        currentThread()));
            }
            analyzed.countDown();
        }).subscribeOn(executorRule.executor()).toSingle().subscribe(__ -> { }).cancel();
        analyzed.await();
        assertThat("Unexpected errors observed: " + errors, errors, hasSize(0));
    }
}
