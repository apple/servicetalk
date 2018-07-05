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

import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.ExecutorRule;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.PublisherWithExecutor;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.concurrent.CountDownLatch;

import static io.servicetalk.concurrent.api.Publisher.just;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItemInArray;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

public class PublishAndSubscribeOnTest {

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExecutorRule executorRule = new ExecutorRule();
    @Rule
    public final ExecutorRule originalSourceExecutorRule = new ExecutorRule();

    @Test
    public void testNoOverride() throws InterruptedException {
        Thread[] capturedThreads = setupAndSubscribe(false);

        assertThat("Threads for subscription and subscriber do not match for original source.",
                capturedThreads[0], is(capturedThreads[1]));
        assertThat("Threads for subscription and subscriber do not match for offloaded source.",
                capturedThreads[2], is(capturedThreads[3]));
        assertThat("Threads for original and offloaded source did not match.",
                capturedThreads[1], not(capturedThreads[2]));
    }

    @Test
    public void testOverride() throws InterruptedException {
        Thread[] capturedThreads = setupAndSubscribe(true);

        assertThat("Threads for subscription and subscriber do not match for original source.",
                capturedThreads[0], is(capturedThreads[1]));
        assertThat("Threads for subscription and subscriber do not match for offloaded source.",
                capturedThreads[2], is(capturedThreads[3]));
        assertThat("Threads for original and offloaded source did not match.",
                capturedThreads[1], is(capturedThreads[2]));
    }

    private Thread[] setupAndSubscribe(boolean override) throws InterruptedException {
        CountDownLatch allDone = new CountDownLatch(1);
        Thread[] capturedThreads = new Thread[4];

        Publisher<String> original = new PublisherWithExecutor<>(originalSourceExecutorRule.getExecutor(),
                just("Hello"))
                .doAfterNext($ -> capturedThreads[0] = Thread.currentThread())
                .doAfterRequest($ -> capturedThreads[1] = Thread.currentThread());

        final Executor executor = executorRule.getExecutor();
        Publisher<String> offloaded = override ? original.publishAndSubscribeOnOverride(executor)
                : original.publishAndSubscribeOn(executor);

        offloaded.doBeforeNext($ -> capturedThreads[2] = Thread.currentThread())
                .doBeforeRequest($ -> capturedThreads[3] = Thread.currentThread())
                .doAfterFinally(allDone::countDown)
                .forEach(val -> { });
        allDone.await();

        assertThat("All threads were not captured.", capturedThreads, hasItemInArray(notNullValue()));

        return capturedThreads;
    }
}
