/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Exchanger;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Executors.from;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.startsWith;

public class SchedulerOffloadTest {

    public static final String EXPECTED_THREAD_PREFIX = "jdk-executor";
    @Nullable
    private Executor executor;

    public SchedulerOffloadTest() {
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (executor != null) {
            executor.closeAsync().toFuture().get();
        }
    }

    @Test
    public void userSchedulerInvokesUserCode() throws InterruptedException {
        verifyInvokerThread(
                from(java.util.concurrent.Executors.newCachedThreadPool(new DefaultThreadFactory("foo")),
                        java.util.concurrent.Executors.newScheduledThreadPool(1,
                                new DefaultThreadFactory(EXPECTED_THREAD_PREFIX))));
    }

    @Test
    public void globalSchedulerDoesNotInvokeUserCode() throws InterruptedException {
        verifyInvokerThread(from(java.util.concurrent.Executors.newFixedThreadPool(1,
                new DefaultThreadFactory(EXPECTED_THREAD_PREFIX))));
    }

    private void verifyInvokerThread(final Executor executor) throws InterruptedException {
        this.executor = executor;
        Exchanger<Thread> invoker = new Exchanger<>();
        executor.schedule(() -> {
            try {
                invoker.exchange(Thread.currentThread());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Exchange interrupted.");
            }
        }, 1, TimeUnit.MILLISECONDS);
        Thread taskInvoker = invoker.exchange(Thread.currentThread());
        assertThat("Unexpected thread invoked the task.", taskInvoker.getName(),
                startsWith(EXPECTED_THREAD_PREFIX));
    }
}
