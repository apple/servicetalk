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

import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.api.internal.OffloaderAwareExecutor;
import io.servicetalk.concurrent.internal.DelegatingSignalOffloader;
import io.servicetalk.concurrent.internal.DelegatingSignalOffloaderFactory;
import io.servicetalk.concurrent.internal.SignalOffloader;
import io.servicetalk.concurrent.internal.SignalOffloaderFactory;

import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static io.servicetalk.concurrent.api.ExecutorExtension.withExecutor;
import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static io.servicetalk.concurrent.internal.SignalOffloaders.defaultOffloaderFactory;
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
    private final SignalOffloaderFactory factory;

    protected AbstractHandleSubscribeOffloadedTest() {
        factory = new DelegatingSignalOffloaderFactory(defaultOffloaderFactory()) {
            @Override
            public SignalOffloader newSignalOffloader(final io.servicetalk.concurrent.Executor executor) {
                signalOffloaderCreated.incrementAndGet();
                return new DelegatingSignalOffloader(super.newSignalOffloader(executor)) {
                    @Override
                    public <T> void offloadSubscribe(
                            final PublisherSource.Subscriber<? super T> subscriber,
                            final Consumer<PublisherSource.Subscriber<? super T>> handleSubscribe) {
                        offloadPublisherSubscribeCalled.incrementAndGet();
                        super.offloadSubscribe(subscriber, handleSubscribe);
                    }

                    @Override
                    public <T> void offloadSubscribe(
                            final SingleSource.Subscriber<? super T> subscriber,
                            final Consumer<SingleSource.Subscriber<? super T>> handleSubscribe) {
                        offloadSingleSubscribeCalled.incrementAndGet();
                        super.offloadSubscribe(subscriber, handleSubscribe);
                    }

                    @Override
                    public void offloadSubscribe(final CompletableSource.Subscriber subscriber,
                                                 final Consumer<CompletableSource.Subscriber> handleSubscribe) {
                        offloadCompletableSubscribeCalled.incrementAndGet();
                        super.offloadSubscribe(subscriber, handleSubscribe);
                    }
                };
            }
        };
    }

    protected void verifyHandleSubscribeInvoker() {
        Thread handleSubscribeInvoker = handleSubscribeInvokerRef.get();
        assertThat("handleSubscribe() not called", handleSubscribeInvoker, is(notNullValue()));
        assertThat("Unexpected thread invoked handleSubscribe()", handleSubscribeInvoker.getName(),
                startsWith(OFFLOAD_THREAD_NAME_PREFIX));
    }

    protected void verifyPublisherOffloadCount() {
        assertThat("Unexpected offloader instances created.", signalOffloaderCreated.get(), is(1));
        assertThat("Unexpected calls to offloadSubscribe.", offloadPublisherSubscribeCalled.get(), is(1));
    }

    protected void verifySingleOffloadCount() {
        assertThat("Unexpected offloader instances created.", signalOffloaderCreated.get(), is(1));
        assertThat("Unexpected calls to offloadSubscribe.", offloadSingleSubscribeCalled.get(), is(1));
    }

    protected void verifyCompletableOffloadCount() {
        assertThat("Unexpected offloader instances created.", signalOffloaderCreated.get(), is(1));
        assertThat("Unexpected calls to offloadSubscribe.", offloadCompletableSubscribeCalled.get(), is(1));
    }

    protected OffloaderAwareExecutor newOffloadingAwareExecutor() {
        return new OffloaderAwareExecutor(executorForOffloadRule.executor(), factory);
    }
}
