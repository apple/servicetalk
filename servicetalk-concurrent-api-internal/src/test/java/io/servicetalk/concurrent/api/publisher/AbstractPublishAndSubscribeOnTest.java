/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.ExecutorRule;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.PublisherWithExecutor;
import io.servicetalk.concurrent.api.internal.CaptureThreads;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.junit.Rule;
import org.junit.rules.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.function.BiFunction;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static java.lang.Long.MAX_VALUE;

public abstract class AbstractPublishAndSubscribeOnTest {

    protected static final String SOURCE_EXECUTOR_PREFIX = "app";
    protected static final String OFFLOAD_EXECUTOR_PREFIX = "offload";

    protected static final int ORIGINAL_SUBSCRIBER_THREAD = 0;
    protected static final int ORIGINAL_SUBSCRIPTION_THREAD = 1;
    protected static final int OFFLOADED_SUBSCRIBER_THREAD = 2;
    protected static final int OFFLOADED_SUBSCRIPTION_THREAD = 3;
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExecutorRule originalSourceExecutorRule = ExecutorRule.withNamePrefix(SOURCE_EXECUTOR_PREFIX);

    CaptureThreads capturedThreads = new CaptureThreads(4);

    protected Thread[] setupAndSubscribe(
            BiFunction<Publisher<String>, Executor, Publisher<String>> offloadingFunction, Executor executor)
            throws InterruptedException {
        CountDownLatch allDone = new CountDownLatch(1);

        Publisher<String> original = new PublisherWithExecutor<>(originalSourceExecutorRule.executor(),
                from("Hello"))
                .beforeOnNext(__ -> capturedThreads.capture(ORIGINAL_SUBSCRIBER_THREAD))
                .beforeRequest(__ -> capturedThreads.capture(ORIGINAL_SUBSCRIPTION_THREAD));

        Publisher<String> offloaded = offloadingFunction.apply(original, executor);

        toSource(offloaded.beforeOnNext(__ -> capturedThreads.capture(OFFLOADED_SUBSCRIBER_THREAD))
                .beforeRequest(__ -> capturedThreads.capture(OFFLOADED_SUBSCRIPTION_THREAD))
                .afterFinally(allDone::countDown))
                .subscribe(new Subscriber<String>() {
                    @Override
                    public void onSubscribe(final Subscription s) {
                        // Do not request from the caller thread to make sure synchronous request-onNext does not
                        // pollute thread capturing of subscription.
                        executor.execute(() -> s.request(MAX_VALUE));
                    }

                    @Override
                    public void onNext(final String s) {
                        // noop
                    }

                    @Override
                    public void onError(final Throwable t) {
                        // noop
                    }

                    @Override
                    public void onComplete() {
                        // noop
                    }
                });
        allDone.await();

        return capturedThreads.verify();
    }
}
