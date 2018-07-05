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

import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.ExecutorRule;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.SingleWithExecutor;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.concurrent.CountDownLatch;

import static io.servicetalk.concurrent.api.Single.success;
import static org.hamcrest.MatcherAssert.assertThat;
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

        assertThat("Threads for original and offloaded source did not match.",
                capturedThreads[0], not(capturedThreads[1]));
    }

    @Test
    public void testOverride() throws InterruptedException {
        Thread[] capturedThreads = setupAndSubscribe(true);

        assertThat("Threads for original and offloaded source did not match.",
                capturedThreads[0], is(capturedThreads[1]));
    }

    @Test
    public void testNoOverrideWithCancel() throws InterruptedException {
        Thread[] capturedThreads = setupForCancelAndSubscribe(false);

        assertThat("Threads for original and offloaded source did not match.",
                capturedThreads[0], not(capturedThreads[1]));
    }

    @Test
    public void testOverrideWithCancel() throws InterruptedException {
        Thread[] capturedThreads = setupForCancelAndSubscribe(true);

        assertThat("Threads for original and offloaded source did not match.",
                capturedThreads[0], is(capturedThreads[1]));
    }

    private Thread[] setupAndSubscribe(boolean override) throws InterruptedException {
        CountDownLatch allDone = new CountDownLatch(1);
        Thread[] capturedThreads = new Thread[2];

        Single<String> original = new SingleWithExecutor<>(originalSourceExecutorRule.getExecutor(), success("Hello"))
                .doBeforeSuccess($ -> capturedThreads[0] = Thread.currentThread());

        final Executor executor = executorRule.getExecutor();
        Single<String> offloaded = override ? original.publishAndSubscribeOnOverride(executor)
                : original.publishAndSubscribeOn(executor);

        offloaded.doAfterFinally(allDone::countDown)
                .doBeforeSuccess($ -> capturedThreads[1] = Thread.currentThread())
                .subscribe(val -> { });
        allDone.await();

        verifyCapturedThreads(capturedThreads);
        return capturedThreads;
    }

    private Thread[] setupForCancelAndSubscribe(boolean override) throws InterruptedException {
        CountDownLatch allDone = new CountDownLatch(1);
        Thread[] capturedThreads = new Thread[2];

        Single<String> original = new SingleWithExecutor<>(originalSourceExecutorRule.getExecutor(),
                Single.<String>never())
                .doAfterCancel(() -> {
                    capturedThreads[0] = Thread.currentThread();
                    allDone.countDown();
                });

        final Executor executor = executorRule.getExecutor();
        Single<String> offloaded = override ? original.publishAndSubscribeOnOverride(executor)
                : original.publishAndSubscribeOn(executor);

        offloaded.doBeforeCancel(() -> capturedThreads[1] = Thread.currentThread())
                .subscribe(val -> { }).cancel();
        allDone.await();

        verifyCapturedThreads(capturedThreads);
        return capturedThreads;
    }

    private void verifyCapturedThreads(final Thread[] capturedThreads) {
        for (int i = 0; i < capturedThreads.length; i++) {
            final Thread capturedThread = capturedThreads[i];
            assertThat("Unexpected captured thread at index: " + i, capturedThread, notNullValue());
        }
    }
}
