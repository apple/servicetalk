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

import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.servicetalk.concurrent.api.ExecutorExtension.withExecutor;
import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;

public abstract class AbstractHandleSubscribeOffloadedTest {
    private static final String OFFLOAD_THREAD_NAME_PREFIX = "offload-thread";
    private static final String TIMER_THREAD_NAME_PREFIX = "timer-thread";
    @RegisterExtension
    final ExecutorExtension<Executor> executorForOffloadRule =
            withExecutor(() -> newCachedThreadExecutor(new DefaultThreadFactory(OFFLOAD_THREAD_NAME_PREFIX)));
    @RegisterExtension
    protected final ExecutorExtension<Executor> executorForTimerRule =
            withExecutor(() -> newCachedThreadExecutor(new DefaultThreadFactory(TIMER_THREAD_NAME_PREFIX)));
    protected final AtomicReference<Thread> handleSubscribeInvokerRef = new AtomicReference<>();
    private final AtomicInteger offloadPublisherSubscribeCalled = new AtomicInteger();
    private final AtomicInteger offloadSingleSubscribeCalled = new AtomicInteger();
    private final AtomicInteger offloadCompletableSubscribeCalled = new AtomicInteger();
    private final AtomicInteger signalOffloaderCreated = new AtomicInteger();

    protected AbstractHandleSubscribeOffloadedTest() {
    }

    protected void verifyHandleSubscribeInvoker() {
        Thread handleSubscribeInvoker = handleSubscribeInvokerRef.get();
        assertThat("handleSubscribe() not called", handleSubscribeInvoker, is(notNullValue()));
        assertThat("Unexpected thread invoked handleSubscribe()", handleSubscribeInvoker.getName(),
                startsWith(OFFLOAD_THREAD_NAME_PREFIX));
    }

    protected void verifyPublisherOffloadCount() {
        assertThat("Unexpected offloader instances created.", signalOffloaderCreated.get(), is(1));
        assertThat("Unexpected calls to offloadSubscribe(Publisher).",
                offloadPublisherSubscribeCalled.get(), is(1));
    }

    protected void verifySingleOffloadCount() {
        assertThat("Unexpected offloader instances created.", signalOffloaderCreated.get(), is(1));
        assertThat("Unexpected calls to offloadSubscribe(Single).",
                offloadSingleSubscribeCalled.get(), is(1));
    }

    protected void verifyCompletableOffloadCount() {
        assertThat("Unexpected offloader instances created.", signalOffloaderCreated.get(), is(1));
        assertThat("Unexpected calls to offloadSubscribe.(Completable)",
                offloadCompletableSubscribeCalled.get(), is(1));
    }

    protected Executor newOffloadingAwareExecutor() {
        return executorForOffloadRule.executor();
    }
}
